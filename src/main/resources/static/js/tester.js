// Data.gov.in Scraper Studio - Live Runner & Tester Script (tester.js)

let lastTestResponseText = "";

document.addEventListener('DOMContentLoaded', () => {
    const autoId = localStorage.getItem('DATAGOV_TESTER_AUTOLOAD_ID');
    const resIdInp = document.getElementById('test-resource-id');

    if (autoId && resIdInp) {
        resIdInp.value = autoId;
        localStorage.removeItem('DATAGOV_TESTER_AUTOLOAD_ID');
    } else if (resIdInp && !resIdInp.value) {
        resIdInp.value = '8d3b6596-b09e-4077-aebf-425193185a5b';
    }

    updateLiveCurl();
});

function pasteSampleResourceId() {
    const inp = document.getElementById('test-resource-id');
    if (inp) {
        inp.value = '8d3b6596-b09e-4077-aebf-425193185a5b';
        updateLiveCurl();
        showToast('Sample Crude Oil Resource ID loaded', 'info');
    }
}

function updateLiveCurl() {
    const resIdInp = document.getElementById('test-resource-id');
    const resourceId = resIdInp && resIdInp.value.trim() ? resIdInp.value.trim() : '8d3b6596-b09e-4077-aebf-425193185a5b';
    const apiKey = getGlobalApiKey();
    const limit = document.getElementById('test-limit')?.value || 10;
    const offset = document.getElementById('test-offset')?.value || 0;
    const format = document.getElementById('test-format')?.value || 'json';

    const filterKey = document.getElementById('test-filter-key')?.value.trim();
    const filterVal = document.getElementById('test-filter-val')?.value.trim();

    let url = `https://api.data.gov.in/resource/${resourceId}?api-key=${encodeURIComponent(apiKey)}&format=${format}&offset=${offset}&limit=${limit}`;
    if (filterKey && filterVal) {
        url += `&filters[${encodeURIComponent(filterKey)}]=${encodeURIComponent(filterVal)}`;
    }

    const acceptHeader = format === 'xml' ? 'application/xml' : (format === 'csv' ? 'text/csv' : 'application/json');
    const curl = `curl -X GET \\\n  '${url}' \\\n  -H 'Accept: ${acceptHeader}'`;

    const preview = document.getElementById('test-curl-preview');
    if (preview) preview.innerText = curl;
}

function copyTesterCurl() {
    const preview = document.getElementById('test-curl-preview');
    if (preview) {
        copyToClipboard(preview.innerText, 'cURL command copied to clipboard!');
    }
}

async function openTesterSchema() {
    const resourceId = document.getElementById('test-resource-id')?.value.trim();
    if (!resourceId) {
        showToast('Please enter a Resource ID first', 'warn');
        return;
    }

    try {
        const res = await fetch(`/api/resources/by-uuid/${encodeURIComponent(resourceId)}`);
        if (res.ok) {
            const item = await res.json();
            if (item && item.id) {
                openDetailsModalById(item.id);
                return;
            }
        }
        showToast('This Resource ID is not in local DB. Try scraping it or test directly.', 'info');
    } catch (e) {
        showToast('Error opening schema: ' + e.message, 'error');
    }
}

async function runApiTest() {
    await executeApiTest();
}

async function executeApiTest() {
    const resourceId = document.getElementById('test-resource-id')?.value.trim();
    if (!resourceId) {
        showToast('Please enter a Resource ID / UUID', 'warn');
        return;
    }

    const apiKey = getGlobalApiKey();
    const limit = parseInt(document.getElementById('test-limit')?.value, 10) || 10;
    const offset = parseInt(document.getElementById('test-offset')?.value, 10) || 0;
    const format = document.getElementById('test-format')?.value || 'json';

    const filterKey = document.getElementById('test-filter-key')?.value.trim();
    const filterVal = document.getElementById('test-filter-val')?.value.trim();

    const payload = {
        resourceId,
        apiKey,
        format,
        limit,
        offset
    };
    if (filterKey && filterVal) {
        payload.filters = { [filterKey]: filterVal };
    }

    const btn = document.getElementById('btn-run-test');
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = `<i class="fa-solid fa-spinner fa-spin mr-1.5"></i> <span>Executing...</span>`;
    }

    const statusBadge = document.getElementById('test-status');
    const countBadge = document.getElementById('test-records-count');
    const viewer = document.getElementById('test-response-viewer');

    if (statusBadge) {
        statusBadge.innerText = 'Calling...';
        statusBadge.className = 'px-2.5 py-1 bg-amber-900/60 text-amber-300 rounded-lg font-mono text-[11px] border border-amber-800 animate-pulse';
    }
    if (viewer) {
        viewer.innerText = '// Sending GET request to api.data.gov.in gateway...\n// Using active API key: ' + apiKey.substring(0, 8) + '...';
    }

    try {
        const res = await fetch('/api/resources/test-call', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();

        if (statusBadge) {
            if (data.statusCode >= 200 && data.statusCode < 300) {
                statusBadge.innerText = `HTTP ${data.statusCode} OK (${data.executionTimeMs}ms)`;
                statusBadge.className = 'px-2.5 py-1 bg-emerald-950 text-emerald-400 rounded-lg font-mono text-[11px] border border-emerald-800 font-bold';
            } else {
                statusBadge.innerText = `HTTP ${data.statusCode || 500} (${data.executionTimeMs || 0}ms)`;
                statusBadge.className = 'px-2.5 py-1 bg-rose-950 text-rose-400 rounded-lg font-mono text-[11px] border border-rose-800 font-bold';
            }
        }

        let body = data.responseBody || data.errorMessage || 'Empty response';
        try {
            const parsed = JSON.parse(body);
            body = JSON.stringify(parsed, null, 2);

            // Update records count badge
            if (countBadge) {
                const count = parsed.count !== undefined ? parsed.count : (Array.isArray(parsed.records) ? parsed.records.length : null);
                if (count !== null) {
                    countBadge.innerText = `${count} Records`;
                    countBadge.classList.remove('hidden');
                } else {
                    countBadge.classList.add('hidden');
                }
            }
        } catch (ignored) {}

        lastTestResponseText = body;
        if (viewer) viewer.innerText = body;

        showToast(`API call finished with HTTP ${data.statusCode} (${data.executionTimeMs}ms)`, data.statusCode === 200 ? 'success' : 'warn');

    } catch (e) {
        console.error('API Test error:', e);
        if (statusBadge) {
            statusBadge.innerText = 'HTTP Error';
            statusBadge.className = 'px-2.5 py-1 bg-rose-950 text-rose-400 rounded-lg font-mono text-[11px] border border-rose-800 font-bold';
        }
        if (viewer) viewer.innerText = `// Execution failed:\n${e.message}`;
        showToast('API execution failed: ' + e.message, 'error');
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = `<i class="fa-solid fa-play text-xs"></i> <span>Execute Request</span>`;
        }
    }
}

function copyTestResponse() {
    if (!lastTestResponseText) {
        showToast('No response data to copy', 'warn');
        return;
    }
    copyToClipboard(lastTestResponseText, 'Response JSON copied to clipboard!');
}
