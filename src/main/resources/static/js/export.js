// Data.gov.in Scraper Studio - Export Page Script (export.js)

document.addEventListener('DOMContentLoaded', () => {
    // Export page initialization
});

function copyPythonScript() {
    const script = `import argparse
import requests
import json
import time

def scrape_datagov(total_limit=50, batch_size=10, rate_per_min=30, sector=None, search=None, output="data_gov_apis.json"):
    base_url = "https://api.data.gov.in/lists"
    offset = 0
    scraped = []
    delay = max(0.5, 60.0 / rate_per_min)
    
    print(f"[*] Starting Scraper with target limit: {total_limit}, batch size: {batch_size}, delay: {delay:.2f}s")
    
    while len(scraped) < total_limit:
        params = {
            "format": "json",
            "notfilters[source]": "visualize.data.gov.in",
            "filters[active]": "1",
            "limit": min(batch_size, total_limit - len(scraped)),
            "offset": offset,
            "sort[created]": "desc"
        }
        if sector and sector.upper() != "ALL":
            params["filters[sector]"] = sector
        if search:
            params["query"] = search
            
        headers = {"User-Agent": "Mozilla/5.0"}
        resp = requests.get(base_url, params=params, headers=headers)
        if resp.status_code != 200:
            print(f"[!] HTTP Error {resp.status_code}, retrying...")
            time.sleep(3)
            continue
            
        data = resp.json()
        records = data.get("records", [])
        if not records:
            print("[*] No more records found.")
            break
            
        for r in records:
            scraped.append({
                "resource_id": r.get("index_name") or r.get("id"),
                "title": r.get("title"),
                "desc": r.get("desc"),
                "sectors": r.get("sector", []),
                "orgs": r.get("org", []),
                "fields": r.get("field", []),
                "api_url": f"https://api.data.gov.in/resource/{r.get('index_name') or r.get('id')}"
            })
            if len(scraped) >= total_limit:
                break
                
        offset += len(records)
        print(f"[+] Scraped {len(scraped)} / {total_limit} APIs...")
        time.sleep(delay)
        
    with open(output, "w", encoding="utf-8") as f:
        json.dump(scraped, f, indent=2, ensure_ascii=False)
    print(f"[OK] Saved {len(scraped)} APIs to {output}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, default=50)
    parser.add_argument("--batch-size", type=int, default=10)
    parser.add_argument("--rate-limit", type=int, default=30)
    parser.add_argument("--sector", type=str, default=None)
    parser.add_argument("--search", type=str, default=None)
    parser.add_argument("--output", type=str, default="data_gov_apis.json")
    args = parser.parse_args()
    scrape_datagov(args.limit, args.batch_size, args.rate_limit, args.sector, args.search, args.output)
`;
    copyToClipboard(script, 'Python script copied to clipboard!');
}
