// Data.gov.in Scraper Studio - Catalog Page Script (catalog.js)

let currentCatalogPage = 0;
let catalogViewMode = 'cards';
let catalogSearchTimeout = null;

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
    const cardsEl = document.getElementById('catalog-cards-container') || document.getElementById('catalog-container');
    const tableEl = document.getElementById('catalog-table-container');
    const btnGrid = document.getElementById('btn-view-grid') || document.getElementById('btn-view-cards');
    const btnList = document.getElementById('btn-view-list') || document.getElementById('btn-view-table');

    if (mode === 'grid' || mode === 'cards') {
        if (cardsEl) {
            cardsEl.classList.remove('hidden', 'flex', 'flex-col');
            cardsEl.classList.add('grid', 'grid-cols-1', 'md:grid-cols-2', 'lg:grid-cols-3', 'gap-6');
        }
        if (tableEl) tableEl.classList.add('hidden');
        if (btnGrid) btnGrid.className = "p-1.5 px-3 rounded-lg bg-white shadow-sm text-blue-600 transition flex items-center space-x-1 font-bold text-xs";
        if (btnList) btnList.className = "p-1.5 px-3 rounded-lg text-slate-500 hover:text-slate-700 transition flex items-center space-x-1 font-bold text-xs";
    } else {
        if (cardsEl) {
            cardsEl.classList.remove('grid', 'grid-cols-1', 'md:grid-cols-2', 'lg:grid-cols-3');
            cardsEl.classList.add('flex', 'flex-col', 'space-y-4');
        }
        if (btnGrid) btnGrid.className = "p-1.5 px-3 rounded-lg text-slate-500 hover:text-slate-700 transition flex items-center space-x-1 font-bold text-xs";
        if (btnList) btnList.className = "p-1.5 px-3 rounded-lg bg-white shadow-sm text-blue-600 transition flex items-center space-x-1 font-bold text-xs";
    }
}

function saveFiltersState() {
    const searchInp = document.getElementById('catalog-search');
    const sectorInp = document.getElementById('catalog-sector-filter');
    const domainInp = document.getElementById('catalog-domain-filter');
    const orgInp = document.getElementById('catalog-org-filter');
    const stateInp = document.getElementById('catalog-state-filter');

    const state = {
        search: searchInp ? searchInp.value.trim() : '',
        sector: sectorInp ? sectorInp.value : 'ALL',
        domain: domainInp ? domainInp.value : 'ALL',
        org: orgInp ? orgInp.value : 'ALL',
        stateName: stateInp ? stateInp.value : 'ALL'
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
        const domainInp = document.getElementById('catalog-domain-filter');
        const orgInp = document.getElementById('catalog-org-filter');
        const stateInp = document.getElementById('catalog-state-filter');

        if (state.search && searchInp) searchInp.value = state.search;
        if (state.sector && sectorInp) sectorInp.value = state.sector;
        if (state.domain && domainInp) domainInp.value = state.domain;
        if (state.org && orgInp) orgInp.value = state.org;
        if (state.stateName && stateInp) stateInp.value = state.stateName;
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
        size: 9,
        sort: sortVal
    });
    if (search) params.append('search', search);
    if (sector && sector !== 'ALL') params.append('sector', sector);

    try {
        const res = await fetch(`/api/resources?${params.toString()}`);
        const data = await res.json();
        renderCatalogData(data);
    } catch (e) {
        console.error('Error fetching catalog:', e);
    }
}

function renderCatalogData(data) {
    const list = data.content || [];
    const total = data.totalElements || 0;
    const page = data.page || 0;
    const size = data.size || 9;
    const totalPages = data.totalPages || 1;

    const cardsContainer = document.getElementById('catalog-container') || document.getElementById('catalog-cards-container');
    if (!cardsContainer) return;

    if (list.length === 0) {
        cardsContainer.innerHTML = `
            <div class="col-span-full py-16 flex flex-col items-center justify-center text-center bg-white rounded-2xl border border-slate-200 p-8 shadow-xs">
                <div class="w-16 h-16 rounded-2xl bg-blue-50 text-blue-500 flex items-center justify-center text-2xl mb-4">
                    <i class="fa-solid fa-box-open"></i>
                </div>
                <h3 class="text-slate-800 font-bold text-base">No APIs Found in Local Database</h3>
                <p class="text-slate-500 text-xs mt-1 max-w-sm">Try running a scraper job in Scraper Studio or adjust your search filter.</p>
                <a href="/" class="mt-4 px-4 py-2 bg-blue-600 text-white rounded-xl text-xs font-bold shadow-sm shadow-blue-500/25 hover:bg-blue-700 transition">
                    Go to Scraper Studio
                </a>
            </div>
        `;
        return;
    }

    cardsContainer.innerHTML = list.map(item => renderApiCard(item)).join('');
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
    const createdDateFormatted = item.createdDate ? item.createdDate.substring(0, 10) : 'Active Dataset';

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
                    <button type="button" onclick="copyToClipboard('${escapeJsString(curlCommand)}', 'cURL command copied!')" class="p-1.5 px-2.5 rounded-xl bg-white border border-slate-200 text-slate-600 hover:text-blue-600 text-xs font-bold transition shadow-2xs" title="Copy cURL">
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

function prevCatalogPage() {
    if (currentCatalogPage > 0) {
        loadCatalog(currentCatalogPage - 1);
    }
}

function nextCatalogPage() {
    loadCatalog(currentCatalogPage + 1);
}
