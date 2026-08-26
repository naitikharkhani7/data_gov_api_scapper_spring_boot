// Data.gov.in Scraper Studio - Settings Page Script (settings.js)

let visibleKeyIds = new Set();

document.addEventListener('DOMContentLoaded', () => {
    renderSettingsApiKeys();
});

function toggleKeyVisibility(id) {
    if (visibleKeyIds.has(id)) {
        visibleKeyIds.delete(id);
    } else {
        visibleKeyIds.add(id);
    }
    renderSettingsApiKeys();
}

function copyApiKeyToClipboard(key) {
    if (!key) return;
    copyToClipboard(key, 'Full API Key copied to clipboard!');
}

function renderSettingsApiKeys() {
    const tbody = document.getElementById('api-keys-table-body');
    if (!tbody) return;
    
    const keys = getAllApiKeys();
    if (keys.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="5" class="text-center py-10">
                    <div class="w-12 h-12 rounded-full bg-blue-50 text-blue-500 flex items-center justify-center mx-auto mb-2 text-xl">
                        <i class="fa-solid fa-key"></i>
                    </div>
                    <div class="text-slate-700 font-bold text-sm">No API Keys Added Yet</div>
                    <div class="text-slate-400 text-xs mt-0.5">Click "Add New Key" to add multiple API keys for rotation.</div>
                </td>
            </tr>`;
        return;
    }
    
    tbody.innerHTML = keys.map(k => {
        const isVisible = visibleKeyIds.has(k.id);
        const displayKey = isVisible 
            ? k.key 
            : (k.key.length > 14 ? k.key.substring(0, 8) + '••••••••••••' + k.key.substring(k.key.length - 4) : '••••••••••••');
        const eyeIcon = isVisible ? 'fa-eye-slash text-blue-600' : 'fa-eye text-slate-400 hover:text-slate-700';
        const eyeTitle = isVisible ? 'Hide full key' : 'Click to show full key';
        
        return `
            <tr class="hover:bg-slate-50/80 transition group ${k.isActive ? 'bg-blue-50/40' : ''}">
                <!-- Email -->
                <td class="px-4 py-3.5">
                    <div class="flex items-center space-x-2.5">
                        <div class="w-8 h-8 rounded-xl ${k.isActive ? 'bg-blue-600 text-white' : 'bg-slate-100 text-slate-500'} flex items-center justify-center font-bold text-xs shrink-0 shadow-2xs">
                            <i class="fa-solid fa-user-circle"></i>
                        </div>
                        <div>
                            <div class="font-bold text-xs text-slate-900">${escapeHtml(k.email || 'No Email')}</div>
                            <div class="text-[10px] text-slate-400">Account ID</div>
                        </div>
                    </div>
                </td>

                <!-- Description -->
                <td class="px-4 py-3.5">
                    <div class="text-xs font-medium text-slate-700">${escapeHtml(k.description || 'Custom API Key')}</div>
                </td>

                <!-- API Key Token with View/Eye and Copy -->
                <td class="px-4 py-3.5">
                    <div class="flex items-center space-x-2">
                        <span class="font-mono text-xs ${isVisible ? 'bg-blue-50 text-blue-700 font-bold px-2.5 py-1 rounded-lg border border-blue-200 select-all' : 'text-slate-600 font-medium'}">
                            ${escapeHtml(displayKey)}
                        </span>
                        <button type="button" onclick="toggleKeyVisibility('${k.id}')" class="p-1.5 px-2 rounded-lg bg-slate-100 hover:bg-blue-50 hover:text-blue-600 transition text-xs border border-slate-200 shadow-2xs cursor-pointer" title="${eyeTitle}">
                            <i class="fa-solid ${eyeIcon}"></i>
                        </button>
                        <button type="button" onclick="copyApiKeyToClipboard('${escapeHtml(k.key)}')" class="p-1.5 px-2 rounded-lg bg-slate-100 hover:bg-blue-50 hover:text-blue-600 text-slate-500 transition text-xs border border-slate-200 shadow-2xs cursor-pointer" title="Copy Full Key">
                            <i class="fa-regular fa-copy"></i>
                        </button>
                    </div>
                </td>

                <!-- Status / Set Active -->
                <td class="px-4 py-3.5">
                    ${k.isActive ? `
                        <span class="inline-flex items-center px-3 py-1 rounded-full text-[11px] font-extrabold bg-emerald-100 text-emerald-800 border border-emerald-300 shadow-2xs">
                            <i class="fa-solid fa-circle-check text-emerald-600 mr-1.5"></i> ACTIVE
                        </span>
                    ` : `
                        <button type="button" onclick="setActiveApiKey('${k.id}')" class="px-3 py-1 rounded-lg text-xs font-bold bg-slate-100 hover:bg-blue-600 hover:text-white text-slate-700 border border-slate-200 hover:border-blue-600 transition shadow-2xs flex items-center space-x-1.5 group cursor-pointer" title="Click to use this key everywhere">
                            <i class="fa-regular fa-circle text-slate-400 group-hover:text-white text-[10px]"></i>
                            <span>Set Active</span>
                        </button>
                    `}
                </td>

                <!-- Actions (Edit / Delete) -->
                <td class="px-4 py-3.5 text-right">
                    <div class="flex items-center justify-end space-x-1.5">
                        <button type="button" onclick="openEditKeyModal('${k.id}')" class="p-1.5 px-2 rounded-lg bg-slate-100 hover:bg-blue-50 text-slate-500 hover:text-blue-600 border border-slate-200 transition shadow-2xs cursor-pointer" title="Edit Key Details">
                            <i class="fa-solid fa-pen-to-square text-xs"></i>
                        </button>
                        <button type="button" onclick="deleteApiKey('${k.id}')" class="p-1.5 px-2 rounded-lg bg-slate-100 hover:bg-rose-50 text-slate-500 hover:text-rose-600 border border-slate-200 transition shadow-2xs cursor-pointer" title="Delete Key">
                            <i class="fa-solid fa-trash-can text-xs"></i>
                        </button>
                    </div>
                </td>
            </tr>
        `;
    }).join('');
}
