// Data.gov.in Scraper Studio - Live Runner & Tester Script (tester.js)

let lastTestResponseText = "";

document.addEventListener('DOMContentLoaded', () => {
    const autoId = localStorage.getItem('DATAGOV_TESTER_AUTOLOAD_ID');
    if (autoId) {
        const inp = document.getElementById('test-resource-id');
        if (inp) inp.value = autoId;
        localStorage.removeItem('DATAGOV_TESTER_AUTOLOAD_ID');
    }
    updateLiveCurl();
});

function updateLiveCurl() {
    const resIdInp = document.getElementById('test-resource-id');
    const resourceId = resIdInp && resIdInp.value.trim() ? resIdInp.value.trim() : '14613c4e-5ab0-4705-b440-e4e49ae345de';
    const apiKey = getGlobalApiKey();
    const limit = document.getElementById('test-limit')?.value || 10;
    const offset = document.getElementById('test-offset')?.value || 0;

    const url = `https://api.data.gov.in/resource/${resourceId}?api-key=${encodeURIComponent(apiKey)}&format=json&offset=${offset}&limit=${limit}`;
    const curl = `curl -X GET '${url}' \\\n  -H 'Accept: application/json'`;

    const preview = document.getElementById('test-curl-preview');
    if (preview) preview.value = curl;
}

function copyTesterCurl() {
    const preview = document.getElementById('test-curl-preview');
    if (preview) {
        copyToClipboard(preview.value, 'cURL copied to clipboard!');
    }
}

async function runApiTest() {
    await executeApiTest();
}

async function executeApiTest() {
    const resourceId = document.getElementById('test-resource-id').value.trim();
    if (!resourceId) {
        showToast('Please enter a Resource ID / Index name', 'warn');
        return;
    }

    const apiKey = getGlobalApiKey();
    const limit = parseInt(document.getElementById('test-limit')?.value, 10) || 10;
    const offset = parseInt(document.getElementById('test-offset')?.value, 10) || 0;

    const payload = {
        resourceId,
        apiKey,
        format: 'json',
        limit,
        offset
    };

    const btn = document.getElementById('btn-run-test') || document.getElementById('btn-execute-test');
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = `<i class="fa-solid fa-spinner fa-spin mr-2"></i> Executing API call...`;
    }

    const statusBadge = document.getElementById('test-status') || document.getElementById('resp-status-badge');
    const viewer = document.getElementById('test-response-viewer') || document.getElementById('resp-body-viewer');

    if (statusBadge) {
        statusBadge.innerText = 'Calling...';
        statusBadge.className = 'px-2 py-1 bg-amber-900/60 text-amber-300 rounded-md font-mono text-[10px] border border-amber-800';
    }
    if (viewer) {
        viewer.innerText = '// Sending request to data.gov.in gateway with active API key...';
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
                statusBadge.className = 'px-2 py-1 bg-emerald-950 text-emerald-400 rounded-md font-mono text-[10px] border border-emerald-800';
            } else {
                statusBadge.innerText = `HTTP ${data.statusCode} (${data.executionTimeMs}ms)`;
                statusBadge.className = 'px-2 py-1 bg-rose-950 text-rose-400 rounded-md font-mono text-[10px] border border-rose-800';
            }
        }

        let body = data.responseBody || data.errorMessage || 'Empty response';
        try {
            const parsed = JSON.parse(body);
            body = JSON.stringify(parsed, null, 2);
        } catch (ignored) {}

        lastTestResponseText = body;
        if (viewer) viewer.innerText = body;
        showToast(`API Response received (${data.statusCode}) in ${data.executionTimeMs}ms`, data.success ? 'success' : 'warn');

    } catch (e) {
        if (statusBadge) {
            statusBadge.innerText = 'HTTP 500 Client Error';
            statusBadge.className = 'px-2 py-1 bg-rose-950 text-rose-400 rounded-md font-mono text-[10px] border border-rose-800';
        }
        if (viewer) viewer.innerText = `Request execution failed: ${e.message}`;
        showToast('Execution error: ' + e.message, 'error');
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = `<i class="fa-solid fa-play mr-1.5"></i> Execute Request`;
        }
    }
}

function copyTestResponse() {
    if (!lastTestResponseText) {
        showToast('No response content to copy', 'warn');
        return;
    }
    copyToClipboard(lastTestResponseText, 'Response body copied to clipboard!');
}

function copyResponseBody() {
    copyTestResponse();
}
