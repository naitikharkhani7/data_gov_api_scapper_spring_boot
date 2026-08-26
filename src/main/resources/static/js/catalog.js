// Data.gov.in Scraper Studio - Catalog Page Script (catalog.js)

let currentCatalogPage = 0;
let catalogPageSize = 9;
let catalogViewMode = 'grid'; // 'grid' or 'list'
let catalogSearchTimeout = null;
let catalogTotalPages = 1;

document.addEventListener('DOMContentLoaded', () => {
    restoreFiltersState();
    loadCatalog(0);
});

function debounceLoadCatalog() {
    clearTimeout(catalogSearchTimeout);
    catalogSearchTimeout = setTimeout(() => {
        loadCatalog(0);
    }, 300);
}

function filterCatalog() {
    debounceLoadCatalog();
}

function setCatalogView(mode) {
    catalogViewMode = mode;
    const btnGrid = document.getElementById('btn-view-grid');
    const btnList = document.getElementById('btn-view-list');

    if (mode === 'grid') {
        if (btnGrid) btnGrid.className = "p-1.5 px-3 rounded-lg bg-white shadow-2xs text-blue-600 font-bold text-xs transition flex items-center space-x-1.5 cursor-pointer";
        if (btnList) btnList.className = "p-1.5 px-3 rounded-lg text-slate-500 hover:text-slate-900 font-bold text-xs transition flex items-center space-x-1.5 cursor-pointer";
    } else {
        if (btnGrid) btnGrid.className = "p-1.5 px-3 rounded-lg text-slate-500 hover:text-slate-900 font-bold text-xs transition flex items-center space-x-1.5 cursor-pointer";
        if (btnList) btnList.className = "p-1.5 px-3 rounded-lg bg-white shadow-2xs text-blue-600 font-bold text-xs transition flex items-center space-x-1.5 cursor-pointer";
    }
    loadCatalog(currentCatalogPage);
}

function changePageSize(val) {
    catalogPageSize = parseInt(val, 10) || 9;
    loadCatalog(0);
}

function jumpToPage(e) {
    if (e) e.preventDefault();
    const jumpInp = document.getElementById('pag-jump-input');
    if (!jumpInp) return;
    let target = parseInt(jumpInp.value, 10);
    if (isNaN(target) || target < 1) target = 1;
    if (target > catalogTotalPages) target = catalogTotalPages;
    loadCatalog(target - 1);
    jumpInp.value = '';
}

function goToCatalogPage(p) {
    if (p < 0 || p >= catalogTotalPages) return;
    loadCatalog(p);
}

function saveFiltersState() {
    const searchInp = document.getElementById('catalog-search');
    const sectorInp = document.getElementById('catalog-sector-filter');
    const sortInp = document.getElementById('catalog-sort');

    const state = {
        search: searchInp ? searchInp.value.trim() : '',
        sector: sectorInp ? sectorInp.value : 'ALL',
        sort: sortInp ? sortInp.value : 'id,desc',
        size: catalogPageSize
    };
    sessionStorage.setItem('DATAGOV_CATALOG_FILTERS', JSON.stringify(state));
}

function restoreFiltersState() {
    const raw = sessionStorage.getItem('DATAGOV_CATALOG_FILTERS');
    if (!raw) return;
    try {
        const state = JSON.parse(raw);
        const searchInp = document.getElementById('catalog-search');
        const sectorInp = document.getElementById('catalog-sector-filter');
        const sortInp = document.getElementById('catalog-sort');
        const sizeInp = document.getElementById('catalog-page-size');

        if (state.search !== undefined && searchInp) searchInp.value = state.search;
        if (state.sector !== undefined && sectorInp) sectorInp.value = state.sector;
        if (state.sort !== undefined && sortInp) sortInp.value = state.sort;
        if (state.size !== undefined) {
            catalogPageSize = state.size;
            if (sizeInp) sizeInp.value = state.size;
        }
    } catch (e) {
        console.error('Error restoring filters', e);
    }
}

async function loadCatalog(page = 0) {
    currentCatalogPage = page;
    saveFiltersState();

    const searchInp = document.getElementById('catalog-search');
    const sectorInp = document.getElementById('catalog-sector-filter');
    const sortInp = document.getElementById('catalog-sort');

    const search = searchInp ? searchInp.value.trim() : '';
    const sector = sectorInp ? sectorInp.value : 'ALL';
    const sortVal = sortInp ? sortInp.value : 'id,desc';

    const params = new URLSearchParams({
        page: page,
        size: catalogPageSize,
        sort: sortVal
    });
    if (search) params.append('search', search);
    if (sector && sector !== 'ALL') params.append('sector', sector);

    const cardsContainer = document.getElementById('catalog-container');
    if (cardsContainer && page === 0 && !cardsContainer.innerHTML.includes('fa-spin')) {
        cardsContainer.innerHTML = `
            <div class="col-span-full py-20 flex flex-col items-center justify-center text-center">
                <i class="fa-solid fa-circle-notch fa-spin text-3xl text-blue-600 mb-3"></i>
                <h3 class="text-slate-800 font-bold text-sm">Loading Government Datasets...</h3>
                <p class="text-slate-400 text-xs mt-1">Connecting to local database</p>
            </div>
        `;
    }

    try {
        const res = await fetch(`/api/resources?${params.toString()}`);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();
        renderCatalogData(data);
    } catch (e) {
        console.error('Error fetching catalog:', e);
        if (cardsContainer) {
            cardsContainer.innerHTML = `
                <div class="col-span-full py-16 flex flex-col items-center justify-center text-center bg-white rounded-2xl border border-slate-200 p-8 shadow-xs">
                    <div class="w-14 h-14 rounded-2xl bg-rose-50 text-rose-500 flex items-center justify-center text-2xl mb-3">
                        <i class="fa-solid fa-triangle-exclamation"></i>
                    </div>
                    <h3 class="text-slate-800 font-bold text-sm">Unable to load catalog</h3>
                    <p class="text-slate-500 text-xs mt-1">${escapeHtml(e.message)}</p>
                    <button onclick="loadCatalog(0)" class="mt-4 px-4 py-2 bg-blue-600 text-white rounded-xl text-xs font-bold shadow-sm shadow-blue-500/25 hover:bg-blue-700 transition">
                        Retry Loading
                    </button>
                </div>
            `;
        }
    }
}

function renderCatalogData(data) {
    const list = data.content || [];
    const total = data.totalElements || 0;
    const page = data.page || 0;
    const size = data.size || catalogPageSize;
    const totalPages = data.totalPages || 1;
    catalogTotalPages = totalPages;

    // Pagination numbers
    const startIdx = total === 0 ? 0 : page * size + 1;
    const endIdx = Math.min((page + 1) * size, total);

    const startEl = document.getElementById('pag-showing-start');
    if (startEl) startEl.innerText = startIdx.toLocaleString();

    const endEl = document.getElementById('pag-showing-end');
    if (endEl) endEl.innerText = endIdx.toLocaleString();

    const totalEl = document.getElementById('pag-total-items');
    if (totalEl) totalEl.innerText = total.toLocaleString();

    const prevBtn = document.getElementById('pag-btn-prev');
    if (prevBtn) prevBtn.disabled = (page === 0);

    const nextBtn = document.getElementById('pag-btn-next');
    if (nextBtn) nextBtn.disabled = (page >= totalPages - 1 || data.last);

    const jumpInp = document.getElementById('pag-jump-input');
    if (jumpInp) jumpInp.max = totalPages;

    // Render smart numeric page pills: [1] [2] [3] ... [20122]
    renderPaginationPills(page, totalPages);

    const countHeader = document.getElementById('catalog-count-header');
    if (countHeader) countHeader.innerText = `${startIdx.toLocaleString()} - ${endIdx.toLocaleString()} of ${total.toLocaleString()} APIs`;

    const cardsContainer = document.getElementById('catalog-container');
    if (!cardsContainer) return;

    if (list.length === 0) {
        cardsContainer.className = "grid grid-cols-1 gap-6";
        cardsContainer.innerHTML = `
            <div class="py-16 flex flex-col items-center justify-center text-center bg-white rounded-2xl border border-slate-200 p-8 shadow-xs">
                <div class="w-16 h-16 rounded-2xl bg-blue-50 text-blue-500 flex items-center justify-center text-2xl mb-4">
                    <i class="fa-solid fa-box-open"></i>
                </div>
                <h3 class="text-slate-800 font-bold text-base">No APIs Found</h3>
                <p class="text-slate-500 text-xs mt-1 max-w-sm">Try adjusting your search keywords or sector filter.</p>
                <button onclick="document.getElementById('catalog-search').value=''; document.getElementById('catalog-sector-filter').value='ALL'; loadCatalog(0);" class="mt-4 px-4 py-2 bg-blue-600 text-white rounded-xl text-xs font-bold shadow-sm shadow-blue-500/25 hover:bg-blue-700 transition cursor-pointer">
                    Clear Filters
                </button>
            </div>
        `;
        return;
    }

    if (catalogViewMode === 'list') {
        cardsContainer.className = "flex flex-col space-y-3";
        cardsContainer.innerHTML = list.map(item => renderApiListRow(item)).join('');
    } else {
        cardsContainer.className = "grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6";
        cardsContainer.innerHTML = list.map(item => renderApiCard(item)).join('');
    }
}

function renderPaginationPills(currentPage, totalPages) {
    const container = document.getElementById('pag-number-pills');
    if (!container) return;

    if (totalPages <= 1) {
        container.innerHTML = `<span class="px-2.5 py-1 rounded-lg bg-blue-600 text-white text-xs font-bold font-mono">1</span>`;
        return;
    }

    let pages = [];
    const delta = 2; // Number of items before and after current

    for (let i = 0; i < totalPages; i++) {
        if (i === 0 || i === totalPages - 1 || (i >= currentPage - delta && i <= currentPage + delta)) {
            pages.push(i);
        }
    }

    // Build buttons with ellipses
    let html = '';
    let lastP = -1;

    for (const p of pages) {
        if (lastP !== -1 && p - lastP > 1) {
            html += `<span class="px-1 text-slate-400 text-xs font-mono font-bold">...</span>`;
        }

        if (p === currentPage) {
            html += `<button type="button" class="min-w-[32px] h-8 px-2 rounded-lg bg-blue-600 text-white text-xs font-bold font-mono shadow-sm shadow-blue-500/25 cursor-default">${p + 1}</button>`;
        } else {
            html += `<button type="button" onclick="goToCatalogPage(${p})" class="min-w-[32px] h-8 px-2 rounded-lg bg-slate-100 hover:bg-blue-50 text-slate-700 hover:text-blue-600 text-xs font-bold font-mono transition cursor-pointer">${p + 1}</button>`;
        }
        lastP = p;
    }

    container.innerHTML = html;
}

function renderApiCard(item) {
    const sectors = parseJsonList(item.sectors);
    const orgs = parseJsonList(item.organizations);
    const fields = parseJsonList(item.fieldsJson);
    const activeKey = getGlobalApiKey();

    const sectorName = sectors[0] || item.sector || 'General';
    const orgName = orgs[0] || 'Ministry / Department of Government of India';
    const resId = item.resourceId || item.indexName || item.id;
    const curlCommand = `curl -X GET 'https://api.data.gov.in/resource/${resId}?api-key=${encodeURIComponent(activeKey)}&format=json&offset=0&limit=10' -H 'Accept: application/json'`;

    return `
        <div class="bg-white rounded-2xl border border-slate-200 card-shadow hover:border-blue-300 card-shadow-hover transition flex flex-col justify-between overflow-hidden group">
            <div class="p-5 flex-1 space-y-3.5">
                <div class="flex items-start justify-between">
                    <span class="px-2.5 py-1 rounded-lg bg-blue-50 text-blue-700 text-[10px] font-bold uppercase tracking-wider border border-blue-100">${escapeHtml(sectorName)}</span>
                    <span class="text-xs font-mono text-slate-400 bg-slate-50 px-2 py-0.5 rounded border border-slate-100 truncate max-w-[140px]" title="${resId}">${resId}</span>
                </div>
                <h3 class="text-sm font-bold text-slate-900 line-clamp-2 group-hover:text-blue-600 transition leading-snug" title="${escapeHtml(item.title)}">
                    ${escapeHtml(item.title || 'Untitled API Resource')}
                </h3>
                <p class="text-xs text-slate-500 line-clamp-2" title="${escapeHtml(item.description)}">
                    ${escapeHtml(item.description || 'No detailed description available for this dataset.')}
                </p>
                
                <div class="flex flex-wrap gap-2 pt-1">
                    <span class="px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 text-[11px] font-medium border border-slate-200">
                        <i class="fa-solid fa-building text-slate-400 mr-1"></i> <span class="truncate max-w-[180px] inline-block align-bottom">${escapeHtml(orgName)}</span>
                    </span>
                    <span class="px-2 py-0.5 rounded-full bg-blue-50 text-blue-700 text-[11px] font-mono font-semibold border border-blue-200">
                        ${fields.length} columns
                    </span>
                </div>
            </div>

            <!-- Card Footer Actions -->
            <div class="bg-slate-50 border-t border-slate-100 p-4 flex items-center justify-between">
                <button type="button" onclick="openDetailsModalById(${item.id})" class="text-xs font-bold text-slate-600 hover:text-blue-600 transition flex items-center space-x-1.5 cursor-pointer">
                    <i class="fa-solid fa-sitemap text-blue-600"></i>
                    <span>Schema</span>
                </button>
                <div class="flex items-center space-x-2">
                    <button type="button" onclick="copyToClipboard('${escapeJsString(curlCommand)}', 'cURL command copied!')" class="p-1.5 px-2.5 rounded-xl bg-white border border-slate-200 text-slate-600 hover:text-blue-600 text-xs font-bold transition shadow-2xs cursor-pointer" title="Copy cURL">
                        <i class="fa-solid fa-share-nodes text-[11px]"></i>
                    </button>
                    <button type="button" onclick="openInRunner('${resId}')" class="px-3.5 py-1.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold transition flex items-center space-x-1.5 shadow-sm shadow-blue-500/25 cursor-pointer">
                        <i class="fa-solid fa-play text-[10px]"></i>
                        <span>Run API</span>
                    </button>
                </div>
            </div>
        </div>
    `;
}

function renderApiListRow(item) {
    const sectors = parseJsonList(item.sectors);
    const orgs = parseJsonList(item.organizations);
    const fields = parseJsonList(item.fieldsJson);
    const activeKey = getGlobalApiKey();

    const sectorName = sectors[0] || item.sector || 'General';
    const orgName = orgs[0] || 'Ministry / Department of Government of India';
    const resId = item.resourceId || item.indexName || item.id;
    const curlCommand = `curl -X GET 'https://api.data.gov.in/resource/${resId}?api-key=${encodeURIComponent(activeKey)}&format=json&offset=0&limit=10' -H 'Accept: application/json'`;

    return `
        <div class="bg-white p-4 rounded-2xl border border-slate-200 card-shadow hover:border-blue-300 transition flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div class="flex-1 space-y-1">
                <div class="flex items-center space-x-2">
                    <span class="px-2 py-0.5 rounded-md bg-blue-50 text-blue-700 text-[10px] font-bold uppercase">${escapeHtml(sectorName)}</span>
                    <span class="font-mono text-xs text-slate-400">${resId}</span>
                </div>
                <h3 class="text-sm font-bold text-slate-900 leading-snug">${escapeHtml(item.title || 'Untitled')}</h3>
                <div class="text-xs text-slate-500 flex items-center space-x-3">
                    <span><i class="fa-solid fa-building text-slate-400 mr-1"></i> ${escapeHtml(orgName)}</span>
                    <span>•</span>
                    <span class="font-mono text-blue-600 font-semibold">${fields.length} columns</span>
                </div>
            </div>
            <div class="flex items-center space-x-2 shrink-0">
                <button type="button" onclick="openDetailsModalById(${item.id})" class="px-3 py-1.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-bold transition flex items-center space-x-1 cursor-pointer">
                    <i class="fa-solid fa-sitemap text-blue-600 text-xs"></i>
                    <span>Schema</span>
                </button>
                <button type="button" onclick="copyToClipboard('${escapeJsString(curlCommand)}', 'cURL command copied!')" class="p-1.5 px-3 rounded-xl bg-white border border-slate-200 hover:bg-blue-50 text-slate-600 hover:text-blue-600 text-xs font-bold transition cursor-pointer">
                    <i class="fa-solid fa-share-nodes mr-1 text-xs"></i> cURL
                </button>
                <button type="button" onclick="openInRunner('${resId}')" class="px-3.5 py-1.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold transition shadow-sm shadow-blue-500/25 flex items-center space-x-1 cursor-pointer">
                    <i class="fa-solid fa-play text-[10px]"></i>
                    <span>Run API</span>
                </button>
            </div>
        </div>
    `;
}

function prevCatalogPage() {
    if (currentCatalogPage > 0) {
        loadCatalog(currentCatalogPage - 1);
    }
}

function nextCatalogPage() {
    if (currentCatalogPage < catalogTotalPages - 1) {
        loadCatalog(currentCatalogPage + 1);
    }
}
