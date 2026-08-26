import argparse
import requests
import json
import time
import os
import sqlite3

def init_sqlite_db(db_path="data_gov_apis.db"):
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS api_resources (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            resource_id TEXT UNIQUE,
            title TEXT,
            description TEXT,
            api_url TEXT,
            sectors TEXT,
            organizations TEXT,
            fields_json TEXT,
            created_date TEXT,
            updated_date TEXT,
            scraped_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """)
    conn.commit()
    return conn

def scrape_datagov(total_limit=50, batch_size=10, rate_per_min=30, sector=None, search=None, output="data_gov_apis.json", save_db=True):
    base_url = "https://api.data.gov.in/lists"
    offset = 0
    scraped = []
    delay = max(0.2, 60.0 / rate_per_min)
    sample_api_key = "579b464db66ec23bdd000001cdd3946e44ce4aad7209ff7b23ac571b"

    print("=" * 70)
    print(" [*] DATA.GOV.IN API SCRAPER ENGINE")
    print(f" Target Limit   : {total_limit if total_limit else 'ALL'}")
    print(f" Batch Size     : {batch_size}")
    print(f" Rate Throttle  : {rate_per_min} req/min (Delay: {delay:.2f}s)")
    print(f" Sector Filter  : {sector if sector else 'ALL'}")
    print(f" Search Query   : {search if search else 'None'}")
    print("=" * 70)

    db_conn = init_sqlite_db() if save_db else None

    while True:
        if total_limit and len(scraped) >= total_limit:
            break

        current_limit = min(batch_size, total_limit - len(scraped)) if total_limit else batch_size
        params = {
            "format": "json",
            "notfilters[source]": "visualize.data.gov.in",
            "filters[active]": "1",
            "limit": current_limit,
            "offset": offset,
            "sort[created]": "desc"
        }
        if sector and sector.upper() != "ALL":
            params["filters[sector]"] = sector
        if search:
            params["query"] = search

        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Accept": "application/json, text/plain, */*"
        }

        try:
            resp = requests.get(base_url, params=params, headers=headers, timeout=15)
            if resp.status_code != 200:
                print(f"[!] HTTP Error {resp.status_code}. Waiting 3 seconds before retry...")
                time.sleep(3)
                continue

            data = resp.json()
            total_server = data.get("total", 0)
            records = data.get("records", [])

            if not records:
                print("[*] No more records available.")
                break

            for r in records:
                res_id = r.get("index_name") or r.get("id")
                if not res_id:
                    continue

                item = {
                    "resource_id": res_id,
                    "title": r.get("title", ""),
                    "description": r.get("desc", ""),
                    "sectors": r.get("sector", []),
                    "organizations": r.get("org", []),
                    "fields": r.get("field", []),
                    "api_url": f"https://api.data.gov.in/resource/{res_id}",
                    "curl_example": f"curl -X GET 'https://api.data.gov.in/resource/{res_id}?api-key={sample_api_key}&format=json&offset=0&limit=10' -H 'Accept: application/json'",
                    "created_date": r.get("created_date", ""),
                    "updated_date": r.get("updated_date", "")
                }
                scraped.append(item)

                if db_conn:
                    cursor = db_conn.cursor()
                    cursor.execute("""
                        INSERT OR REPLACE INTO api_resources 
                        (resource_id, title, description, api_url, sectors, organizations, fields_json, created_date, updated_date)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, (
                        item["resource_id"],
                        item["title"],
                        item["description"],
                        item["api_url"],
                        json.dumps(item["sectors"]),
                        json.dumps(item["organizations"]),
                        json.dumps(item["fields"]),
                        item["created_date"],
                        item["updated_date"]
                    ))
                    db_conn.commit()

                if total_limit and len(scraped) >= total_limit:
                    break

            offset += len(records)
            print(f"[+] Scraped {len(scraped)} APIs (Offset: {offset}, Total on server: {total_server})")

            if len(records) < current_limit or offset >= total_server:
                print("[✓] Reached end of catalog.")
                break

            time.sleep(delay)

        except Exception as e:
            print(f"[!] Exception during scraping: {e}")
            time.sleep(3)

    if db_conn:
        db_conn.close()

    # Save to JSON file
    with open(output, "w", encoding="utf-8") as f:
        json.dump(scraped, f, indent=2, ensure_ascii=False)

    print("=" * 70)
    print(f" [OK] Scraping finished!")
    print(f" Total APIs Saved to JSON : {len(scraped)} -> {os.path.abspath(output)}")
    if save_db:
        print(f" Local SQLite Database     : {os.path.abspath('data_gov_apis.db')}")
    print("=" * 70)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Data.gov.in API Scraper CLI")
    parser.add_argument("--limit", type=int, default=50, help="Total APIs to scrape (default 50, 0 for all)")
    parser.add_argument("--batch-size", type=int, default=10, help="Batch size per page (default 10)")
    parser.add_argument("--rate-limit", type=int, default=30, help="Requests per minute rate limit (default 30)")
    parser.add_argument("--sector", type=str, default=None, help="Sector filter (e.g. Health, Agriculture)")
    parser.add_argument("--search", type=str, default=None, help="Keyword query search")
    parser.add_argument("--output", type=str, default="data_gov_apis.json", help="JSON output file name")
    parser.add_argument("--no-db", action="store_true", help="Disable SQLite DB save")

    args = parser.parse_args()
    limit = args.limit if args.limit > 0 else None
    scrape_datagov(
        total_limit=limit,
        batch_size=args.batch_size,
        rate_per_min=args.rate_limit,
        sector=args.sector,
        search=args.search,
        output=args.output,
        save_db=not args.no_db
    )
