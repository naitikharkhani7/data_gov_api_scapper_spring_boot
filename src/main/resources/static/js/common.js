// Data.gov.in Scraper Studio - Common Shared Utilities & Key Management

const DEFAULT_SAMPLE_KEY = "579b464db66ec23bdd000001cdd3946e44ce4aad7209ff7b23ac571b";
const STORAGE_KEY_NAME = "DATAGOV_GLOBAL_API_KEY";

let eventSource = null;
let currentModalResource = null;

document.addEventListener('DOMContentLoaded', () => {
    initSSE();
    syncApiKeyUI();
});

// ==========================================
// 1. Global API Key Management System
// ==========================================
function getAllApiKeys() {
    try {
        const raw = localStorage.getItem(STORAGE_KEY_NAME);
        if (!raw) {
            const defaultEntry = {
                id: 'key-default',
                key: DEFAULT_SAMPLE_KEY,
                email: 'default@data.gov.in',
                description: 'Default Sandbox Key',
                isActive: true
            };
            localStorage.setItem(STORAGE_KEY_NAME, JSON.stringify([defaultEntry]));
            return [defaultEntry];
        }
        
        // Backward compatibility for single string keys
        if (!raw.startsWith('[')) {
            const legacyKey = {
                id: 'key-' + Date.now(),
                key: raw.trim(),
                email: 'user@data.gov.in',
                description: 'Custom API Key',
                isActive: true
            };
            localStorage.setItem(STORAGE_KEY_NAME, JSON.stringify([legacyKey]));
            return [legacyKey];
        }
        
        const parsed = JSON.parse(raw);
        if (!Array.isArray(parsed) || parsed.length === 0) {
            const defaultEntry = {
                id: 'key-default',
                key: DEFAULT_SAMPLE_KEY,
                email: 'default@data.gov.in',
                description: 'Default Sandbox Key',
                isActive: true
            };
            localStorage.setItem(STORAGE_KEY_NAME, JSON.stringify([defaultEntry]));
            return [defaultEntry];
        }
        return parsed;
    } catch (e) {
        return [];
    }
}

function getGlobalApiKey() {
    const keys = getAllApiKeys();
    const active = keys.find(k => k.isActive);
    if (active && active.key && active.key.trim()) return active.key.trim();
    return DEFAULT_SAMPLE_KEY;
}

function isSampleKey() {
    return getGlobalApiKey() === DEFAULT_SAMPLE_KEY;
}

function saveApiKeysArray(arr) {
    localStorage.setItem(STORAGE_KEY_NAME, JSON.stringify(arr));
    syncApiKeyUI();
    if (typeof renderSettingsApiKeys === 'function') renderSettingsApiKeys();
    if (typeof loadCatalog === 'function' && window.location.pathname === '/catalog') loadCatalog(currentCatalogPage || 0); 
    if (typeof updateLiveCurl === 'function') updateLiveCurl(); 
}

function addOrEditApiKey(id, key, email, description) {
    const arr = getAllApiKeys();
    if (id) {
        const existingIdx = arr.findIndex(k => k.id === id);
        if (existingIdx >= 0) {
            arr[existingIdx].key = key.trim();
            arr[existingIdx].email = email.trim();
            arr[existingIdx].description = description.trim();
        }
    } else {
        const isFirst = arr.length === 0;
        arr.push({
            id: 'key-' + Date.now(),
            key: key.trim(),
            email: email ? email.trim() : 'user@data.gov.in',
            description: description ? description.trim() : 'Custom Key',
            isActive: isFirst
        });
    }
    saveApiKeysArray(arr);
    showToast('API Key saved successfully!', 'success');
}

function deleteApiKey(id) {
    let arr = getAllApiKeys();
    const idx = arr.findIndex(k => k.id === id);
    if (idx >= 0) {
        const wasActive = arr[idx].isActive;
        arr.splice(idx, 1);
        if (wasActive && arr.length > 0) {
            arr[0].isActive = true; // Auto-activate the first available
        }
        saveApiKeysArray(arr);
        showToast('API Key deleted.', 'info');
    }
}

function setActiveApiKey(id) {
    let arr = getAllApiKeys();
    let selectedKey = null;
    arr.forEach(k => {
        k.isActive = (k.id === id);
        if (k.isActive) selectedKey = k;
    });
    saveApiKeysArray(arr);
    const keyName = selectedKey && selectedKey.email ? selectedKey.email : 'selected key';
    showToast(`Active API Key switched to: ${keyName}`, 'success');
}

function resetGlobalApiKey() {
    localStorage.removeItem(STORAGE_KEY_NAME);
    syncApiKeyUI();
    if (typeof renderSettingsApiKeys === 'function') renderSettingsApiKeys();
    if (typeof loadCatalog === 'function' && window.location.pathname === '/catalog') loadCatalog(0);
    if (typeof updateLiveCurl === 'function') updateLiveCurl();
    showToast('All custom keys removed. Using default sample key.', 'info');
}

function syncApiKeyUI() {
    const key = getGlobalApiKey();
    const isSample = (key === DEFAULT_SAMPLE_KEY);
    const shortKey = key.length > 10 ? key.substring(0, 8) + '...' : key;

    // Header Pill
    const headerPreview = document.getElementById('header-api-key-preview');
    if (headerPreview) headerPreview.innerText = shortKey;

    // Sidebar indicator
    const sidebarLabel = document.getElementById('sidebar-key-label');
    const sidebarSubtext = document.getElementById('sidebar-key-subtext');
    if (sidebarLabel) {
        if (isSample) {
            sidebarLabel.innerText = "Sample Key";
        } else {
            const keys = getAllApiKeys();
            const active = keys.find(k => k.isActive);
            sidebarLabel.innerText = active && active.email ? active.email.split('@')[0] : "Custom Key";
        }
    }
    if (sidebarSubtext) sidebarSubtext.innerText = shortKey;

    // KPI Card 4
    const statTitle = document.getElementById('stat-key-title');
    const statStatus = document.getElementById('stat-key-status');
    if (statTitle) statTitle.innerText = shortKey;
    if (statStatus) statStatus.innerText = isSample ? "Default Sample Key" : "Custom User Key Active";

    // Runner Input field
    const runnerInput = document.getElementById('test-api-key');
    if (runnerInput && runnerInput.value !== key) {
        runnerInput.value = key;
    }
}

// Modal management for quick API key modal
function openApiKeyModal() {
    const currentKey = getGlobalApiKey();
    const modalInput = document.getElementById('modal-api-key-input');
    if (modalInput) {
        modalInput.value = currentKey;
        setTimeout(() => modalInput.focus(), 100);
    }
    const modal = document.getElementById('api-key-modal');
    if(modal) modal.classList.remove('hidden');
}

function closeApiKeyModal() {
    const modal = document.getElementById('api-key-modal');
    if(modal) modal.classList.add('hidden');
}

function saveGlobalApiKeyFromModal() {
    const val = document.getElementById('modal-api-key-input').value;
    addOrEditApiKey(null, val, 'quick-add@data.gov.in', 'Added from quick modal');
    closeApiKeyModal();
}

function resetGlobalApiKeyFromModal() {
    resetGlobalApiKey();
    closeApiKeyModal();
}

function openMultiKeyModal() {
    openEditKeyModal(null);
}

function openEditKeyModal(id = null) {
    const modal = document.getElementById('multi-api-key-modal');
    if (!modal) return;
    
    const idInput = document.getElementById('modal-key-id');
    const keyInput = document.getElementById('modal-key-value');
    const emailInput = document.getElementById('modal-key-email');
    const descInput = document.getElementById('modal-key-desc');
    
    if (id) {
        const k = getAllApiKeys().find(x => x.id === id);
        if (k) {
            idInput.value = k.id;
            keyInput.value = k.key;
            emailInput.value = k.email || '';
            descInput.value = k.description || '';
            document.getElementById('multi-key-modal-title').innerText = "Edit API Key";
        }
    } else {
        idInput.value = '';
        keyInput.value = '';
        emailInput.value = '';
        descInput.value = '';
        document.getElementById('multi-key-modal-title').innerText = "Add New API Key";
    }
    
    modal.classList.remove('hidden');
}

function closeMultiKeyModal() {
    const modal = document.getElementById('multi-api-key-modal');
    if (modal) modal.classList.add('hidden');
}

function saveMultiKeyForm(e) {
    e.preventDefault();
    const id = document.getElementById('modal-key-id').value;
    const key = document.getElementById('modal-key-value').value;
    const email = document.getElementById('modal-key-email').value;
    const desc = document.getElementById('modal-key-desc').value;
    
    if (!key.trim()) {
        showToast('API Key is required', 'warn');
        return;
    }
    
    addOrEditApiKey(id || null, key, email, desc);
    closeMultiKeyModal();
}

async function pasteFromClipboardToKeyInput() {
    try {
        const text = await navigator.clipboard.readText();
        if (text && text.trim()) {
            document.getElementById('modal-api-key-input').value = text.trim();
            showToast('Key pasted from clipboard', 'info');
        }
    } catch (e) {
        showToast('Clipboard access unavailable: ' + e.message, 'warn');
    }
}

function onApiKeyInputChange(value) {
    if (value && value.trim()) {
        localStorage.setItem(STORAGE_KEY_NAME, value.trim());
        syncApiKeyUI();
        if (typeof updateLiveCurl === 'function') updateLiveCurl();
    }
}

// ==========================================
// 2. Server-Sent Events (SSE) & Live Telemetry
// ==========================================
function initSSE() {
    if (eventSource) {
        eventSource.close();
    }

    eventSource = new EventSource('/api/scraper/stream');

    eventSource.addEventListener('status', (e) => {
        try {
            const status = JSON.parse(e.data);
            updateUIFromStatus(status);
        } catch (err) {
            console.error('Failed to parse SSE status:', err);
        }
    });

    eventSource.onopen = () => {
        const ind = document.getElementById('sse-status-indicator');
        if (ind) {
            ind.className = "flex items-center space-x-2 px-3 py-1.5 rounded-full bg-emerald-50 text-xs font-semibold text-emerald-700 border border-emerald-200";
        }
        const sseText = document.getElementById('sse-status-text');
        if (sseText) sseText.innerText = "Live Stream Connected";
    };

    eventSource.onerror = () => {
        const ind = document.getElementById('sse-status-indicator');
        if (ind) {
            ind.className = "flex items-center space-x-2 px-3 py-1.5 rounded-full bg-rose-50 text-xs font-semibold text-rose-700 border border-rose-200";
        }
        const sseText = document.getElementById('sse-status-text');
        if (sseText) sseText.innerText = "Reconnecting...";
    };
}

function updateUIFromStatus(status) {
    // Stats cards (KPI cards)
    const totalSavedStr = (status.totalSavedInDb || 0).toLocaleString();
    const statTotalSaved = document.getElementById('stat-total-saved');
    if (statTotalSaved) statTotalSaved.innerText = totalSavedStr;

    const sidebarCountBadge = document.getElementById('sidebar-count-badge');
    if (sidebarCountBadge) sidebarCountBadge.innerText = totalSavedStr;

    const sidebarScraped = document.getElementById('sidebar-scraped-count');
    if (sidebarScraped) sidebarScraped.innerText = (status.totalScrapedInCurrentJob || 0).toLocaleString();

    const sidebarSpeed = document.getElementById('sidebar-speed');
    if (sidebarSpeed) sidebarSpeed.innerText = `${status.currentSpeedReqPerMin || 0} req/m`;

    const statSpeed = document.getElementById('stat-speed');
    if (statSpeed) statSpeed.innerText = `${status.currentSpeedReqPerMin || 0} req/min`;

    const statCurrentAction = document.getElementById('stat-current-action');
    if (statCurrentAction) statCurrentAction.innerText = status.currentAction || 'Idle';

    // Status Badge & Sidebar Pill
    const badge = document.getElementById('stat-status-badge');
    if (badge) {
        badge.innerText = status.state;
        if (status.state === 'RUNNING') {
            badge.className = "px-3 py-1 rounded-full text-xs font-bold bg-emerald-50 text-emerald-700 border border-emerald-300 animate-pulse";
        } else if (status.state === 'PAUSED') {
            badge.className = "px-3 py-1 rounded-full text-xs font-bold bg-amber-50 text-amber-700 border border-amber-300";
        } else if (status.state === 'COMPLETED') {
            badge.className = "px-3 py-1 rounded-full text-xs font-bold bg-blue-50 text-blue-700 border border-blue-300";
        } else if (status.state === 'ERROR') {
            badge.className = "px-3 py-1 rounded-full text-xs font-bold bg-rose-50 text-rose-700 border border-rose-300";
        } else {
            badge.className = "px-3 py-1 rounded-full text-xs font-bold bg-slate-100 text-slate-700 border border-slate-200";
        }
    }

    // Progress Card (Dashboard specific)
    const progressFill = document.getElementById('progress-bar-fill');
    if (progressFill) progressFill.style.width = `${status.progressPercent}%`;

    const percentBadge = document.getElementById('monitor-percent-badge');
    if (percentBadge) percentBadge.innerText = `${status.progressPercent}%`;

    const monTarget = document.getElementById('mon-target');
    if (monTarget) monTarget.innerText = status.totalTarget ? status.totalTarget.toLocaleString() : 'ALL';

    const monScraped = document.getElementById('mon-scraped');
    if (monScraped) monScraped.innerText = (status.totalScrapedInCurrentJob || 0).toLocaleString();

    const monServerTotal = document.getElementById('mon-server-total');
    if (monServerTotal) monServerTotal.innerText = status.totalFoundOnServer ? status.totalFoundOnServer.toLocaleString() : '-';

    // Buttons disable/enable state
    const btnStart = document.getElementById('btn-start');
    if (btnStart) {
        const isRunning = status.state === 'RUNNING';
        const isPaused = status.state === 'PAUSED';
        btnStart.disabled = isRunning || isPaused;
        btnStart.classList.toggle('opacity-50', isRunning || isPaused);

        const btnPause = document.getElementById('btn-pause');
        if (btnPause) btnPause.disabled = !isRunning;

        const btnResume = document.getElementById('btn-resume');
        if (btnResume) btnResume.disabled = !isPaused;

        const btnStop = document.getElementById('btn-stop');
        if (btnStop) btnStop.disabled = !(isRunning || isPaused);
    }

    // Logs in Console
    if (status.recentLogs && status.recentLogs.length > 0) {
        const consoleEl = document.getElementById('console-logs-container');
        if (consoleEl) {
            consoleEl.innerHTML = status.recentLogs.map(line => {
                let color = 'text-slate-300';
                if (line.includes('[ERROR]')) color = 'text-rose-400 font-bold';
                else if (line.includes('[WARN]')) color = 'text-amber-400';
                else if (line.includes('Scraped batch:')) color = 'text-emerald-300 font-semibold';
                return `<div class="${color}">${escapeHtml(line)}</div>`;
            }).join('');
            consoleEl.scrollTop = consoleEl.scrollHeight;
        }
    }
}

// ==========================================
// 3. Details / Schema Modal
// ==========================================
async function openDetailsModalById(id) {
    try {
        const res = await fetch(`/api/resources/${id}`);
        if (!res.ok) throw new Error('Resource not found');
        const item = await res.json();
        currentModalResource = item;

        document.getElementById('modal-title').innerText = item.title || 'Untitled';
        document.getElementById('modal-uuid').innerText = item.resourceId;
        document.getElementById('modal-desc').innerText = item.description || 'No description available.';

        const sectors = parseJsonList(item.sectors);
        document.getElementById('modal-sectors').innerHTML = sectors.length ? sectors.map(s => `<span class="px-2.5 py-0.5 rounded-full text-xs font-bold bg-blue-50 text-blue-700 border border-blue-200">${escapeHtml(s)}</span>`).join('') : '<span class="text-xs text-slate-400">None</span>';

        const orgs = parseJsonList(item.organizations);
        document.getElementById('modal-orgs').innerHTML = orgs.length ? orgs.map(o => `<span class="px-2.5 py-0.5 rounded-full text-xs font-bold bg-indigo-50 text-indigo-700 border border-indigo-200">${escapeHtml(o)}</span>`).join('') : '<span class="text-xs text-slate-400">None</span>';

        const fields = parseJsonList(item.fieldsJson);
        document.getElementById('modal-field-count').innerText = `${fields.length} Columns`;
        const tbody = document.getElementById('modal-fields-tbody');
        if (fields.length === 0) {
            tbody.innerHTML = `<tr><td colspan="3" class="p-4 text-center text-slate-400">No field specifications found.</td></tr>`;
        } else {
            tbody.innerHTML = fields.map(f => `
                <tr class="hover:bg-slate-50 font-mono text-[11px]">
                    <td class="p-2.5 pl-3.5 text-blue-600 font-bold">${escapeHtml(f.id || '-')}</td>
                    <td class="p-2.5 text-slate-800">${escapeHtml(f.name || f.id || '-')}</td>
                    <td class="p-2.5 pr-3.5 text-indigo-600 font-semibold">${escapeHtml(f.type || 'string')}</td>
                </tr>
            `).join('');
        }

        const activeKey = getGlobalApiKey();
        const curlCmd = `curl -X GET 'https://api.data.gov.in/resource/${item.resourceId}?api-key=${encodeURIComponent(activeKey)}&format=json&offset=0&limit=10' \\\n  -H 'Accept: application/json'`;
        document.getElementById('modal-curl').innerText = curlCmd;

        document.getElementById('details-modal').classList.remove('hidden');
    } catch (e) {
        showToast('Error loading details: ' + e.message, 'error');
    }
}

function closeDetailsModal() {
    const modal = document.getElementById('details-modal');
    if (modal) modal.classList.add('hidden');
    currentModalResource = null;
}

function copyModalCurl() {
    const text = document.getElementById('modal-curl').innerText;
    copyToClipboard(text, 'cURL command copied with active key!');
}

function testInStudioFromModal() {
    if (currentModalResource) {
        const id = currentModalResource.resourceId;
        closeDetailsModal();
        openInRunner(id);
    }
}

function openInRunner(resourceId) {
    localStorage.setItem('DATAGOV_TESTER_AUTOLOAD_ID', resourceId);
    showToast(`Loading API ${resourceId} into live runner...`, 'info');
    setTimeout(() => {
        window.location.href = '/tester';
    }, 400);
}

// ==========================================
// 4. Global Utility Functions
// ==========================================
function parseJsonList(raw) {
    if (!raw) return [];
    try {
        const parsed = JSON.parse(raw);
        return Array.isArray(parsed) ? parsed : [raw];
    } catch (e) {
        return [raw];
    }
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
}

function escapeJsString(str) {
    if (!str) return '';
    return String(str).replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/\n/g, '\\n').replace(/\r/g, '');
}

function copyToClipboard(text, successMsg = 'Copied to clipboard!') {
    if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(text).then(() => {
            showToast(successMsg, 'success');
        }).catch(err => fallbackCopyTextToClipboard(text, successMsg));
    } else {
        fallbackCopyTextToClipboard(text, successMsg);
    }
}

function fallbackCopyTextToClipboard(text, successMsg = 'Copied to clipboard!') {
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.left = '-999999px';
    document.body.appendChild(textarea);
    textarea.focus();
    textarea.select();
    try {
        document.execCommand('copy');
        showToast(successMsg, 'success');
    } catch (err) {
        showToast('Failed to copy', 'warn');
    }
    document.body.removeChild(textarea);
}

function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;
    
    const toast = document.createElement('div');
    
    let bg = 'bg-white border-slate-200 text-slate-800 shadow-xl';
    let icon = 'fa-info-circle text-blue-600';
    if (type === 'success') {
        bg = 'bg-white border-emerald-200 text-emerald-800 shadow-xl';
        icon = 'fa-circle-check text-emerald-600';
    } else if (type === 'warn') {
        bg = 'bg-white border-amber-200 text-amber-800 shadow-xl';
        icon = 'fa-triangle-exclamation text-amber-600';
    } else if (type === 'error') {
        bg = 'bg-white border-rose-200 text-rose-800 shadow-xl';
        icon = 'fa-circle-xmark text-rose-600';
    }

    toast.className = `pointer-events-auto flex items-center space-x-2.5 px-4 py-3 rounded-2xl border text-xs font-bold backdrop-blur-md transform transition-all duration-300 translate-y-2 opacity-0 ${bg}`;
    toast.innerHTML = `<i class="fa-solid ${icon} text-base"></i><span>${escapeHtml(message)}</span>`;

    container.appendChild(toast);
    requestAnimationFrame(() => {
        toast.classList.remove('translate-y-2', 'opacity-0');
    });

    setTimeout(() => {
        toast.classList.add('opacity-0', 'translate-y-2');
        setTimeout(() => toast.remove(), 300);
    }, 3500);
}
