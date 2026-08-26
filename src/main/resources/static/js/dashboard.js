// Data.gov.in Scraper Studio - Dashboard Page Script (dashboard.js)

document.addEventListener('DOMContentLoaded', () => {
    // Initial setup if needed for dashboard
});

async function startScraper() {
    const totalLimitEl = document.getElementById('cfg-total-limit');
    const batchSizeEl = document.getElementById('cfg-batch-size');
    const rateLimitEl = document.getElementById('cfg-rate-limit');
    const sectorEl = document.getElementById('cfg-sector');
    const searchEl = document.getElementById('cfg-search-query');
    const fetchSwaggerEl = document.getElementById('cfg-fetch-swagger');

    const totalLimitVal = totalLimitEl ? totalLimitEl.value.trim() : '';
    const batchSizeVal = batchSizeEl ? parseInt(batchSizeEl.value, 10) : 10;
    const rateLimitVal = rateLimitEl ? parseInt(rateLimitEl.value, 10) : 30;
    const sectorVal = sectorEl ? sectorEl.value : null;
    const searchVal = searchEl ? searchEl.value.trim() : null;
    const fetchSwaggerVal = fetchSwaggerEl ? fetchSwaggerEl.checked : false;

    const payload = {
        totalLimit: totalLimitVal ? parseInt(totalLimitVal, 10) : null,
        batchSize: batchSizeVal || 10,
        rateLimitPerMin: rateLimitVal || 30,
        sector: sectorVal || null,
        searchQuery: searchVal || null,
        fetchSwagger: fetchSwaggerVal
    };

    try {
        const res = await fetch('/api/scraper/start', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        showToast(data.message, data.success ? 'success' : 'warn');
    } catch (e) {
        showToast('Error starting scraper: ' + e.message, 'error');
    }
}

async function pauseScraper() {
    try {
        const res = await fetch('/api/scraper/pause', { method: 'POST' });
        const data = await res.json();
        showToast('Scraper Paused', 'warn');
    } catch (e) {
        showToast('Error: ' + e.message, 'error');
    }
}

async function resumeScraper() {
    try {
        const res = await fetch('/api/scraper/resume', { method: 'POST' });
        const data = await res.json();
        showToast('Scraper Resumed', 'success');
    } catch (e) {
        showToast('Error: ' + e.message, 'error');
    }
}

async function stopScraper() {
    try {
        const res = await fetch('/api/scraper/stop', { method: 'POST' });
        const data = await res.json();
        showToast('Scraper Stopped', 'info');
    } catch (e) {
        showToast('Error: ' + e.message, 'error');
    }
}

async function restartScraper() {
    startScraper();
}

async function clearDataModal() {
    if (confirm('Are you sure you want to clear all scraped APIs from the local database? This cannot be undone.')) {
        try {
            const res = await fetch('/api/scraper/clear', { method: 'DELETE' });
            const data = await res.json();
            showToast(data.message, 'info');
        } catch (e) {
            showToast('Error: ' + e.message, 'error');
        }
    }
}

function clearConsoleView() {
    const el = document.getElementById('console-logs-container');
    if (el) {
        el.innerHTML = '<div class="text-slate-500">[Screen Cleared]</div>';
    }
}
