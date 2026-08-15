const form = document.getElementById("predictionForm");
const submitButton = document.getElementById("submitButton");
const exportButton = document.getElementById("exportButton");
const responsePanel = document.getElementById("responsePanel");
const modelBadge = document.getElementById("modelBadge");
const summaryGrid = document.getElementById("summaryGrid");
const warningsSection = document.getElementById("warningsSection");
const warningsList = document.getElementById("warningsList");
const emptyState = document.getElementById("emptyState");
const batchList = document.getElementById("batchList");
const fixtureDateFrom = document.getElementById("fixtureDateFrom");
const fixtureDateTo = document.getElementById("fixtureDateTo");
const toast = document.getElementById("toast");

const modalOverlay = document.getElementById("fixtureModal");
const modalCloseBtn = document.getElementById("modalCloseBtn");
const modalTitle = document.getElementById("modalTitle");
const modalLoading = document.getElementById("modalLoading");
const modalError = document.getElementById("modalError");
const modalTableWrap = document.getElementById("modalTableWrap");
const modalTableBody = document.getElementById("modalTableBody");

const filterControls = document.getElementById("filterControls");
const searchFilter = document.getElementById("searchFilter");
const leagueFilter = document.getElementById("leagueFilter");
const marketFilter = document.getElementById("marketFilter");
const sortFilter = document.getElementById("sortFilter");
const leagueChoiceGrid = document.getElementById("leagueChoiceGrid");
const leagueSearch = document.getElementById("leagueSearch");
const leagueSelectionCount = document.getElementById("leagueSelectionCount");
const leagueAvailabilityHint = document.getElementById("leagueAvailabilityHint");
const marketSearch = document.getElementById("marketSearch");
const marketVisibleCount = document.getElementById("marketVisibleCount");

let lastRequest = null;
let lastResponse = null;
const MAX_DATE_RANGE_DAYS = 14;
const DEFAULT_DATE_RANGE_DAYS = 1;
const DEFAULT_SELECTED_LEAGUES = new Set(["PREMIER_LEAGUE", "LA_LIGA", "SERIE_A"]);
let leagueCatalog = [];
let selectedLeagueCodes = new Set(DEFAULT_SELECTED_LEAGUES);

initializeDates();
initializeLeagueSelector();
initializeSelectionControls();
initializeMarketSearch();

// --- Section Toggle Logic ---
const sectionToggles = document.querySelectorAll('.section-toggle');
sectionToggles.forEach(btn => {
    btn.addEventListener('click', () => {
        const targetId = btn.getAttribute('aria-controls');
        const target = document.getElementById(targetId);
        const isExpanded = btn.getAttribute('aria-expanded') === 'true';
        
        btn.setAttribute('aria-expanded', !isExpanded);
        target.classList.toggle('hidden', isExpanded);
        
        localStorage.setItem(`pitchedge_section_${targetId}`, !isExpanded);
    });
});

function restoreSectionState() {
    sectionToggles.forEach(btn => {
        const targetId = btn.getAttribute('aria-controls');
        const target = document.getElementById(targetId);
        const savedState = localStorage.getItem(`pitchedge_section_${targetId}`);
        
        if (savedState !== null) {
            const isExpanded = savedState === 'true';
            btn.setAttribute('aria-expanded', isExpanded);
            if (target) target.classList.toggle('hidden', !isExpanded);
        }
    });
}

function updateTopSummary() {
    const leaguesCount = selectedLeagueCodes.size || document.querySelectorAll('input[name="leagueCodes"]:checked').length;
    const marketsCount = document.querySelectorAll('input[name="marketCodes"]:checked').length;
    
    const sumLeagues = document.getElementById('sumLeagues');
    const sumMarkets = document.getElementById('sumMarkets');
    const sumDates = document.getElementById('sumDates');
    const sumStrategy = document.getElementById('sumStrategy');
    
    if (sumLeagues) sumLeagues.textContent = leaguesCount;
    if (sumMarkets) sumMarkets.textContent = marketsCount;
    
    const from = fixtureDateFrom.value;
    const to = fixtureDateTo.value;
    if (from && to && sumDates) {
        sumDates.textContent = `${from.slice(5)} to ${to.slice(5)}`;
    }
    
    if (sumStrategy && form.elements.strategy) {
        sumStrategy.textContent = formatEnum(form.elements.strategy.value);
    }
}
form.addEventListener('change', updateTopSummary);

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    await generatePredictions();
});

exportButton.addEventListener("click", async () => {
    const request = buildRequest();
    if (!request) {
        return;
    }
    await downloadExcel(request);
});

modalCloseBtn.addEventListener("click", () => {
    modalOverlay.classList.add("hidden");
});

modalOverlay.addEventListener("click", (e) => {
    if (e.target === modalOverlay) {
        modalOverlay.classList.add("hidden");
    }
});

[searchFilter, leagueFilter, marketFilter, sortFilter].forEach(el => {
    el?.addEventListener("input", () => {
        if (lastResponse && lastResponse.batches) {
            renderBatches(lastResponse.batches);
        }
    });
});

function formatEnum(str) {
    if (!str || str === 'UNRATED') return 'Unrated';
    if (str === 'NO_ODDS') return 'No odds';
    if (str === 'INSUFFICIENT_DATA') return 'Insufficient data';
    return String(str).split('_').map(word => {
        if (word === 'FIFA') return 'FIFA';
        if (word === 'EV') return 'EV';
        return word.charAt(0).toUpperCase() + word.slice(1).toLowerCase();
    }).join(' ');
}

function initializeDates() {
    const today = new Date();
    const toDate = new Date(today);
    toDate.setDate(today.getDate() + DEFAULT_DATE_RANGE_DAYS - 1);
    fixtureDateFrom.value = toIsoDate(today);
    fixtureDateTo.value = toIsoDate(toDate);

    fixtureDateFrom?.addEventListener("change", () => {
        if (fixtureDateFrom.value && (!fixtureDateTo.value || fixtureDateTo.value < fixtureDateFrom.value)) {
            const fromDate = new Date(`${fixtureDateFrom.value}T00:00:00Z`);
            const nextDate = new Date(fromDate);
            nextDate.setDate(fromDate.getDate() + DEFAULT_DATE_RANGE_DAYS - 1);
            fixtureDateTo.value = toIsoDate(nextDate);
            updateTopSummary();
        }
    });
}

function initializeSelectionControls() {
    document.querySelectorAll("[data-select-all]").forEach((button) => {
        button.addEventListener("click", () => setChecked(button.dataset.selectAll, true));
    });
    document.querySelectorAll("[data-clear-all]").forEach((button) => {
        button.addEventListener("click", () => setChecked(button.dataset.clearAll, false));
    });
}

function initializeMarketSearch() {
    if (!marketSearch) return;
    const filterMarkets = () => {
        const query = marketSearch.value.trim().toLowerCase();
        let visible = 0;
        document.querySelectorAll('.market-grid label').forEach((label) => {
            const checkbox = label.querySelector('input[name="marketCodes"]');
            const searchable = `${label.textContent} ${checkbox?.value || ''}`.toLowerCase();
            const matches = !query || searchable.includes(query);
            label.hidden = !matches;
            if (matches) visible++;
        });
        document.querySelectorAll('.market-option-group').forEach((group) => {
            const visibleLabels = group.querySelectorAll('.market-grid label:not([hidden])');
            group.hidden = visibleLabels.length === 0;
        });
        if (marketVisibleCount) {
            marketVisibleCount.textContent = `${visible} market${visible === 1 ? '' : 's'}`;
        }
    };
    marketSearch.addEventListener('input', filterMarkets);
    filterMarkets();
}

function setChecked(name, checked) {
    if (name === "leagueCodes" && leagueCatalog.length) {
        selectedLeagueCodes = checked
            ? new Set(leagueCatalog.filter(isLeagueSelectable).map(league => league.leagueCode))
            : new Set();
        renderLeagueSelector();
        updateTopSummary();
        return;
    }
    form.querySelectorAll(`input[name="${name}"]`).forEach((input) => {
        if (!input.disabled) {
            input.checked = checked;
        }
    });
    updateTopSummary();
}

async function initializeLeagueSelector() {
    if (!leagueChoiceGrid) {
        return;
    }
    leagueSearch?.addEventListener("input", renderLeagueSelector);
    try {
        const response = await fetch("/api/v1/platform/leagues", {
            headers: { "Accept": "application/json" }
        });
        if (!response.ok) {
            throw new Error(await readError(response));
        }
        leagueCatalog = await response.json();
        selectedLeagueCodes = new Set(
            Array.from(selectedLeagueCodes).filter(code => leagueCatalog.some(league => league.leagueCode === code && isLeagueSelectable(league)))
        );
        if (selectedLeagueCodes.size === 0) {
            selectedLeagueCodes = new Set(
                Array.from(DEFAULT_SELECTED_LEAGUES).filter(code => leagueCatalog.some(league => league.leagueCode === code && isLeagueSelectable(league)))
            );
        }
        renderLeagueSelector();
    } catch (error) {
        leagueChoiceGrid.innerHTML = `<div class="empty-state">Could not load leagues from the local catalog.</div>`;
        if (leagueAvailabilityHint) {
            leagueAvailabilityHint.textContent = safeInlineError(error, "League catalog failed to load.");
        }
    }
}

function renderLeagueSelector() {
    if (!leagueChoiceGrid) {
        return;
    }
    const P = window.PitchEdge;
    const query = (leagueSearch?.value || "").trim().toLowerCase();
    const filtered = leagueCatalog.filter(league => {
        const text = `${league.name || ""} ${league.country || ""} ${league.leagueCode || ""}`.toLowerCase();
        return !query || text.includes(query);
    });
    const selectableCount = leagueCatalog.filter(isLeagueSelectable).length;
    const pendingCount = leagueCatalog.filter(league => !isLeagueSelectable(league)).length;
    if (leagueSelectionCount) {
        leagueSelectionCount.textContent = `${selectedLeagueCodes.size} selected`;
    }
    if (leagueAvailabilityHint) {
        leagueAvailabilityHint.textContent = `${selectableCount} ready for predictions, ${pendingCount} import pending`;
    }
    if (!filtered.length) {
        leagueChoiceGrid.innerHTML = `<div class="empty-state">No leagues match your search.</div>`;
        return;
    }

    const groups = groupLeagues(filtered);
    leagueChoiceGrid.innerHTML = Array.from(groups.entries()).map(([group, leagues]) => `
        <div class="league-option-group">
            <div class="league-option-group-title">${P.escapeHtml(group)}</div>
            <div class="choice-grid league-choice-grid">
                ${leagues.map(renderLeagueOption).join("")}
            </div>
        </div>
    `).join("");

    leagueChoiceGrid.querySelectorAll('input[name="leagueCodes"]').forEach(input => {
        input.addEventListener("change", () => {
            if (input.checked) {
                selectedLeagueCodes.add(input.value);
            } else {
                selectedLeagueCodes.delete(input.value);
            }
            updateTopSummary();
            if (leagueSelectionCount) {
                leagueSelectionCount.textContent = `${selectedLeagueCodes.size} selected`;
            }
        });
    });
}

function renderLeagueOption(league) {
    const P = window.PitchEdge;
    const selectable = isLeagueSelectable(league);
    const checked = selectable && selectedLeagueCodes.has(league.leagueCode);
    const status = selectable ? "Ready" : importStatusLabel(league);
    return `
        <label class="league-option ${selectable ? "" : "disabled"}">
            <input type="checkbox" name="leagueCodes" value="${P.escapeHtml(league.leagueCode)}"${checked ? " checked" : ""}${selectable ? "" : " disabled"}>
            <span class="league-option-body">
                <strong>${P.escapeHtml(league.name || formatEnum(league.leagueCode))}</strong>
                <small>${P.escapeHtml(league.country || "International")} - ${P.escapeHtml(status)}</small>
            </span>
        </label>
    `;
}

function groupLeagues(leagues) {
    const preferredOrder = [
        "England", "Spain", "Italy", "Germany", "France", "Europe", "Other Europe",
        "South America", "North America", "Asia", "Africa", "Oceania"
    ];
    const groups = new Map();
    leagues.forEach(league => {
        const group = leagueGroup(league);
        if (!groups.has(group)) {
            groups.set(group, []);
        }
        groups.get(group).push(league);
    });
    groups.forEach(list => sortLeaguesInGroup(list));
    return new Map(Array.from(groups.entries()).sort((a, b) => {
        const ai = preferredOrder.indexOf(a[0]);
        const bi = preferredOrder.indexOf(b[0]);
        if (ai !== -1 || bi !== -1) {
            return (ai === -1 ? 999 : ai) - (bi === -1 ? 999 : bi);
        }
        return a[0].localeCompare(b[0]);
    }));
}

function sortLeaguesInGroup(leagues) {
    return leagues.sort((a, b) => {
        const weightA = leagueTierWeight(a);
        const weightB = leagueTierWeight(b);
        if (weightA !== weightB) {
            return weightA - weightB;
        }
        const nameA = a.name || formatEnum(a.leagueCode || "");
        const nameB = b.name || formatEnum(b.leagueCode || "");
        return nameA.localeCompare(nameB);
    });
}

function leagueTierWeight(league) {
    const code = (league.leagueCode || "").toUpperCase();
    const name = (league.name || "").toUpperCase();
    if (code === "PREMIER_LEAGUE" || code === "LA_LIGA" || code === "SERIE_A" || code === "BUNDESLIGA" || code === "LIGUE_1" || code === "UEFA_CHAMPIONS_LEAGUE") return 1;
    if (code === "CHAMPIONSHIP" || code.includes("2") || name.includes(" 2") || name.endsWith(" II") || code.includes("SERIE_B") || code.includes("LIGUE_2")) return 3;
    if (code.includes("3") || name.includes(" 3") || name.endsWith(" III") || code.includes("LEAGUE_ONE") || code.includes("LEAGUE_TWO") || code.includes("LEAGUE_1")) return 4;
    return 2;
}

function leagueGroup(league) {
    const country = league.country || "";
    const code = league.leagueCode || "";
    if (["England", "Spain", "Italy", "Germany", "France"].includes(country)) return country;
    if (country === "Europe" || code.startsWith("UEFA_")) return "Europe";
    if (["Portugal", "Netherlands", "Belgium", "Scotland", "Turkey", "Denmark", "Switzerland", "Austria", "Poland", "Czechia", "Croatia", "Serbia", "Romania", "Greece", "Ukraine", "Slovakia", "Sweden", "Norway", "Finland", "Ireland", "Iceland", "Estonia", "Lithuania", "Latvia", "Kazakhstan"].includes(country)) {
        return "Other Europe";
    }
    if (["South America", "Brazil", "Argentina", "Chile", "Colombia", "Peru", "Uruguay", "Paraguay", "Ecuador"].includes(country)) return "South America";
    if (["United States", "Canada", "Mexico"].includes(country)) return "North America";
    if (["China", "South Korea", "Japan", "Saudi Arabia", "United Arab Emirates", "Qatar", "Thailand", "India", "Indonesia", "Uzbekistan"].includes(country)) return "Asia";
    if (["Africa", "Egypt", "South Africa", "Morocco", "Tunisia", "Nigeria"].includes(country)) return "Africa";
    if (country === "Australia") return "Oceania";
    return country || "Other";
}

function isLeagueSelectable(league) {
    if (league.predictionSelectable !== undefined) {
        return Boolean(league.predictionSelectable);
    }
    return Number(league.matches || 0) > 0 && league.importEnabled !== false;
}

function importStatusLabel(league) {
    if (league.importStatus === "IMPORT_PENDING" || Number(league.matches || 0) === 0) {
        return "Import pending";
    }
    if (league.importStatus === "IMPORTED") {
        return "Imported";
    }
    return formatEnum(league.importStatus || league.historyStatus || "Import pending");
}

function safeInlineError(error, fallback) {
    return error?.message && error.message.length < 120 ? error.message : fallback;
}

async function generatePredictions() {
    const request = buildRequest();
    if (!request) {
        return;
    }

    submitButton.disabled = true;
    let overlay = document.querySelector('.pe-loading-overlay');
    if (!overlay) {
        overlay = document.createElement('div');
        overlay.className = 'pe-loading-overlay';
        overlay.innerHTML = `
            <div class="pe-spinner"></div>
            <div class="pe-loading-text">Generating Predictions...</div>
        `;
        document.body.appendChild(overlay);
    }
    void overlay.offsetWidth; // Force reflow
    overlay.classList.add('active');
    try {
        const csrfToken = await csrfTokenValue();
        const response = await fetch("/api/v1/predictions/form", {
            method: "POST",
            headers: {
                "Accept": "application/json",
                "Content-Type": "application/json",
                "X-XSRF-TOKEN": csrfToken
            },
            body: JSON.stringify(request)
        });

        if (!response.ok) {
            throw new Error(await readError(response));
        }

        const payload = await response.json();
        lastRequest = request;
        lastResponse = payload;
        
        if (payload.requestId) {
            const id = payload.requestId;
            try {
                localStorage.setItem(`pitchedge_run_${id}`, JSON.stringify(payload));
                localStorage.setItem('pitchedge_latest_run_id', id);
            } catch (e) {
                console.warn('Could not cache payload to localStorage:', e);
            }
            window.location.href = `/prediction-results.html?runId=${encodeURIComponent(id)}`;
            return;
        }
        
        const sumRunStatus = document.getElementById('sumRunStatus');
        if (sumRunStatus) sumRunStatus.textContent = payload.status || 'Complete';
        
        const resultsBtn = document.querySelector('[aria-controls="responsePanel"]');
        const resultsPanel = document.getElementById('responsePanel');
        if (resultsBtn && resultsPanel) {
            resultsBtn.setAttribute('aria-expanded', 'true');
            resultsPanel.classList.remove('hidden');
            localStorage.setItem(`pitchedge_section_responsePanel`, 'true');
        }
        
        renderResponse(payload);
        showToast("Prediction response loaded.");
    } catch (error) {
        showToast(error.message);
    } finally {
        submitButton.disabled = false;
        const overlay = document.querySelector('.pe-loading-overlay');
        if (overlay) {
            overlay.classList.remove('active');
        }
    }
}

async function downloadExcel(request) {
    exportButton.disabled = true;
    try {
        const csrfToken = await csrfTokenValue();
        const response = await fetch("/api/v1/predictions/form/export", {
            method: "POST",
            headers: {
                "Accept": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "Content-Type": "application/json",
                "X-XSRF-TOKEN": csrfToken
            },
            body: JSON.stringify(request)
        });

        if (!response.ok) {
            throw new Error(await readError(response));
        }

        const blob = await response.blob();
        const filename = filenameFromHeader(response.headers.get("Content-Disposition"))
            || `pitchedge-predictions-${request.fixtureDateFrom}-${request.fixtureDateTo}.xlsx`;
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(url);
        showToast("Excel export downloaded.");
    } catch (error) {
        showToast(error.message);
    } finally {
        exportButton.disabled = false;
    }
}

async function csrfTokenValue() {
    const response = await fetch("/api/v1/auth/csrf", {
        method: "GET",
        headers: {
            "Accept": "application/json"
        }
    });
    if (!response.ok) {
        throw new Error(await readError(response));
    }
    const payload = await response.json();
    return payload.token;
}

function buildRequest() {
    const leagueCodes = checkedValues("leagueCodes");
    const marketCodes = checkedValues("marketCodes");
    const from = form.elements.fixtureDateFrom.value;
    const to = form.elements.fixtureDateTo.value;
    const strategy = form.elements.strategy.value || "BALANCED";
    const numberOfBatches = Number(form.elements.numberOfBatches.value);
    const minimumSelections = Number(form.elements.minimumSelections.value);
    const maximumSelections = Number(form.elements.maximumSelections.value);

    if (leagueCodes.length === 0) {
        showToast("Select at least one league.");
        return null;
    }
    if (marketCodes.length === 0) {
        showToast("Select at least one market.");
        return null;
    }
    if (!from || !to || from > to) {
        showToast("Choose a valid fixture date range.");
        return null;
    }
    if (inclusiveDateRangeDays(from, to) > MAX_DATE_RANGE_DAYS) {
        showToast(`Fixture date range cannot exceed ${MAX_DATE_RANGE_DAYS} days.`);
        return null;
    }
    if (!Number.isInteger(numberOfBatches) || numberOfBatches < 1 || numberOfBatches > 100) {
        showToast("Batch count must be between 1 and 100.");
        return null;
    }
    if (!Number.isInteger(minimumSelections) || minimumSelections < 1 || minimumSelections > 500) {
        showToast("Minimum picks must be between 1 and 500.");
        return null;
    }
    if (!Number.isInteger(maximumSelections) || maximumSelections < minimumSelections || maximumSelections > 500) {
        showToast("Maximum picks must be between the minimum and 500.");
        return null;
    }

    const request = {
        sport: "FOOTBALL",
        leagueCodes,
        marketCodes,
        fixtureDateFrom: from,
        fixtureDateTo: to,
        strategy,
        minimumSelections,
        maximumSelections,
        numberOfBatches,
        allowMultipleSelectionsFromSameMatch: false,
        maximumSelectionsPerMatch: 1,
        requireMultipleLeagues: leagueCodes.length > 1,
        minimumDistinctLeagues: leagueCodes.length > 1 ? 2 : 1,
        avoidCorrelatedSelections: true,
        allowRepeatSelectionsAcrossBatches: false,
        minimumBatchDifferencePercentage: 0.40
    };

    putOptionalProbability(request, "minimumModelProbability");
    putOptionalProbability(request, "maximumModelProbability");
    putOptionalProbability(request, "minimumDataQuality");
    putOptionalNumber(request, "minimumExpectedValue");
    putOptionalNumber(request, "minimumProbabilityEdge");
    putOptionalString(request, "minimumConfidence");
    putOptionalString(request, "rankingMode");

    const marketRanges = getMarketRanges();
    if (marketRanges) {
        request.marketProbabilityRanges = marketRanges;
    }

    return request;
}

function checkedValues(name) {
    if (name === "leagueCodes" && leagueCatalog.length) {
        return Array.from(selectedLeagueCodes);
    }
    return Array.from(form.querySelectorAll(`input[name="${name}"]:checked`))
        .map((input) => input.value);
}

function renderResponse(payload) {
    if (!responsePanel) return;
    responsePanel.classList.remove("hidden");
    if (modelBadge) modelBadge.textContent = payload.modelVersion || "No model";
    
    if (leagueFilter) leagueFilter.innerHTML = '<option value="">All Leagues</option>';
    if (marketFilter) marketFilter.innerHTML = '<option value="">All Markets</option>';

    renderSummary(payload);
    renderWarnings(payload.warnings || []);
    renderBatches(payload.batches || []);
}

function renderSummary(payload) {
    summaryGrid.replaceChildren();

    const items = [
        ["Generated", formatDateTime(payload.generatedAt)],
        ["Match Statuses", (payload.matchStatusesUsed || []).join(", ") || "-"],
        ["Fixtures", payload.fixturesConsidered],
        ["Candidates", payload.candidateSelections],
        ["Qualified", payload.qualifiedSelectionsFound],
        ["Returned", payload.returnedSelections ?? payload.selectionsReturned],
        ["Batches", (payload.batches || []).length],
        ["Status", payload.status || "-"],
        ["Strategy", payload.input?.strategy || "BALANCED"],
        ["Range", `${payload.requestedMinimumSelections ?? "-"}-${payload.requestedMaximumSelections ?? "-"}`]
    ];

    items.forEach(([label, value]) => {
        const item = document.createElement("div");
        item.className = "summary-item";

        const labelElement = document.createElement("span");
        labelElement.textContent = label;

        const valueElement = document.createElement("strong");
        valueElement.textContent = value === null || value === undefined || value === "" ? "-" : String(value);

        item.append(labelElement, valueElement);
        summaryGrid.appendChild(item);
    });
}

function renderWarnings(warnings) {
    warningsList.replaceChildren();
    warningsSection.classList.toggle("hidden", warnings.length === 0);

    warnings.forEach((warning) => {
        const item = document.createElement("li");
        item.textContent = warning;
        warningsList.appendChild(item);
    });
}

function filterSelections(selections) {
    const searchStr = searchFilter.value.toLowerCase();
    const leagueStr = leagueFilter.value;
    const marketStr = marketFilter.value;
    const sortVal = sortFilter.value;

    let filtered = selections.filter(s => {
        if (searchStr && !s.fixture.toLowerCase().includes(searchStr)) return false;
        if (leagueStr && s.leagueCode !== leagueStr) return false;
        if (marketStr && (s.marketName || s.marketCode) !== marketStr) return false;
        return true;
    });

    filtered.sort((a, b) => {
        if (sortVal === "time") return new Date(a.kickoffAt) - new Date(b.kickoffAt);
        if (sortVal === "prob_desc") {
            const pA = a.tunedModelProbability ?? a.probability ?? 0;
            const pB = b.tunedModelProbability ?? b.probability ?? 0;
            return pB - pA;
        }
        if (sortVal === "edge_desc") {
            const eA = a.probabilityEdge ?? -1;
            const eB = b.probabilityEdge ?? -1;
            return eB - eA;
        }
        return 0;
    });

    return filtered;
}

function renderBatches(batches) {
    batchList.replaceChildren();
    if (filterControls) filterControls.classList.toggle("hidden", batches.length === 0);

    // Update filter dropdowns if they are empty
    if (leagueFilter.options.length <= 1 && batches.length > 0) {
        const leagues = new Set();
        const markets = new Set();
        batches.forEach(b => {
            (b.selections || []).forEach(s => {
                leagues.add(s.leagueCode);
                markets.add(s.marketName || s.marketCode);
            });
        });
        Array.from(leagues).sort().forEach(l => {
            const opt = document.createElement("option");
            opt.value = l;
            opt.textContent = formatEnum(l);
            leagueFilter.appendChild(opt);
        });
        Array.from(markets).sort().forEach(m => {
            const opt = document.createElement("option");
            opt.value = m;
            opt.textContent = formatEnum(m);
            marketFilter.appendChild(opt);
        });
    }

    let totalFiltered = 0;
    batches.forEach((batch) => {
        const filteredSelections = filterSelections(batch.selections || []);
        if (filteredSelections.length > 0) {
            batchList.appendChild(batchCard(batch, filteredSelections));
            totalFiltered += filteredSelections.length;
        }
    });

    emptyState.classList.toggle("hidden", totalFiltered > 0);
}

function batchCard(batch, filteredSelections) {
    const card = document.createElement("article");
    card.className = "batch-card";

    const head = document.createElement("div");
    head.className = "batch-head";

    const title = document.createElement("h3");
    const totalCount = batch.returnedSelections ?? batch.selectionCount;
    if (filteredSelections.length < totalCount) {
        title.textContent = `Batch ${batch.batchNumber} - ${filteredSelections.length} of ${totalCount} selections`;
    } else {
        title.textContent = `Batch ${batch.batchNumber} - ${totalCount} selections`;
    }

    const risk = document.createElement("span");
    const riskBand = String(batch.riskLevel || batch.risk?.riskBand || "UNKNOWN").toLowerCase();
    risk.className = `risk ${riskBand}`;
    risk.textContent = formatEnum(batch.riskLevel || batch.risk?.riskBand || "UNKNOWN");

    head.append(title, risk);
    card.appendChild(head);

    card.appendChild(riskGrid(batch.risk));

    if (batch.status && batch.status !== "COMPLETE") {
        const status = document.createElement("p");
        status.className = "variance";
        status.textContent = `${batch.status}: ${batch.warningMessage || "Batch was returned with warnings."}`;
        card.appendChild(status);
    }

    // Expandable variance warning
    if (batch.risk?.varianceWarning) {
        const details = document.createElement("details");
        details.style.marginBottom = "16px";
        const summary = document.createElement("summary");
        summary.textContent = "Accumulator Warning";
        summary.style.cursor = "pointer";
        summary.style.color = "var(--muted)";
        summary.style.fontSize = "13px";
        const variance = document.createElement("p");
        variance.className = "variance";
        variance.style.marginTop = "8px";
        variance.textContent = batch.risk.varianceWarning;
        details.append(summary, variance);
        card.appendChild(details);
    }

    card.appendChild(selectionGrid(filteredSelections));
    return card;
}

function riskGrid(risk) {
    const grid = document.createElement("div");
    grid.className = "risk-grid";

    [
        ["Batch Probability", percent(risk?.jointProbability)],
        ["Avg Probability", percent(risk?.averageIndividualProbability)],
        ["Priced", `${risk?.pricedSelectionCount ?? 0}`],
        ["Positive Value", `${risk?.positiveValueSelectionCount ?? 0}`],
        ["Avg EV", signedDecimal(risk?.averageExpectedValue)],
        ["Total EV", signedDecimal(risk?.accumulatorExpectedValue)]
    ].forEach(([label, value]) => {
        const item = document.createElement("div");
        item.className = "risk-metric";

        const labelElement = document.createElement("span");
        labelElement.textContent = label;

        const valueElement = document.createElement("strong");
        valueElement.textContent = value;

        item.append(labelElement, valueElement);
        grid.appendChild(item);
    });

    return grid;
}

function selectionGrid(filteredSelections) {
    const grid = document.createElement("div");
    grid.className = "selection-grid";

    filteredSelections.forEach(selection => {
        const card = document.createElement("div");
        card.className = "prediction-card";

        const header = document.createElement("div");
        header.className = "prediction-card-header";
        
        const league = document.createElement("div");
        league.className = "prediction-card-league";
        league.textContent = formatEnum(selection.leagueCode);
        league.title = formatEnum(selection.leagueCode);

        const fixture = document.createElement("div");
        fixture.className = "prediction-card-fixture";
        fixture.textContent = selection.fixture;
        fixture.title = selection.fixture;

        const time = document.createElement("div");
        time.className = "prediction-card-time";
        time.textContent = formatDateTime(selection.kickoffAt);

        header.append(league, fixture, time);
        const indicators = fixtureIndicatorRow(selection);
        if (indicators) {
            header.appendChild(indicators);
        }

        const body = document.createElement("div");
        body.className = "prediction-card-body";

        const probValue = selection.tunedModelProbability ?? selection.probability;
        const confidenceVal = selection.confidenceBand || "UNRATED";
        const oddsVal = selection.decimalOdds ?? selection.bestDecimalOdds;

        body.append(
            cardRow("Pick", formatEnum(selection.marketName || selection.marketCode) + " - " + formatEnum(selection.predictedValue)),
            cardRow("Probability", probValue ? percent(probValue) : "--"),
            cardRow("Confidence", formatEnum(confidenceVal), `confidence ${confidenceVal.toLowerCase().replace('_', '-')}`),
            cardRow("Odds", oddsVal ? decimal(oddsVal) : "No odds")
        );

        const edgeVal = selection.probabilityEdge;
        if (edgeVal) {
            body.appendChild(cardRow("Edge", percent(edgeVal), edgeVal > 0 ? "expected-value positive" : ""));
        }

        const action = document.createElement("div");
        action.className = "prediction-card-action";
        action.textContent = "View details";

        card.append(header, body, action);

        card.addEventListener("click", () => {
            const url = `/fixture-details.html?matchId=${encodeURIComponent(selection.matchId)}&modelVersion=${encodeURIComponent(lastResponse.modelVersion)}&recommended=${encodeURIComponent(selection.marketCode)}&recommendedMarketCode=${encodeURIComponent(selection.marketCode)}&runId=${encodeURIComponent(lastResponse.requestId)}` + (selection.selectionId ? `&selectionId=${encodeURIComponent(selection.selectionId)}` : '');
            window.location.href = url;
        });

        grid.appendChild(card);
    });

    return grid;
}

function fixtureIndicatorRow(selection) {
    const indicator = lastResponse?.fixtureIndicators?.[selection.selectionId];
    if (!indicator) return null;

    const items = [];
    if (indicator.h2hAvailable && indicator.h2hMatchCount) {
        items.push([
            "H2H",
            `${indicator.h2hMatchCount} completed head-to-head ${indicator.h2hMatchCount === 1 ? 'match' : 'matches'} available in local data.`
        ]);
    }
    if (indicator.homeLeaguePosition && indicator.awayLeaguePosition) {
        items.push([
            `${ordinal(indicator.homeLeaguePosition)} vs ${ordinal(indicator.awayLeaguePosition)}`,
            `League positions calculated from local completed matches${indicator.leagueTableTeamCount ? ` across ${indicator.leagueTableTeamCount} teams` : ''}.`
        ]);
    }
    if (indicator.partialSeasonData) {
        items.push([
            "Partial Season",
            `The model used incomplete season coverage${indicator.partialSeasonCoverage ? ` (${indicator.partialSeasonCoverage})` : ''}.`
        ]);
    }
    if (indicator.homeRecentFormPercentage !== null && indicator.homeRecentFormPercentage !== undefined) {
        items.push([
            `H Form ${indicator.homeRecentFormPercentage}%`,
            `Home form points percentage from ${indicator.homeRecentFormSampleSize} completed local ${indicator.homeRecentFormSampleSize === 1 ? 'match' : 'matches'} before kickoff.`
        ]);
    }
    if (indicator.awayRecentFormPercentage !== null && indicator.awayRecentFormPercentage !== undefined) {
        items.push([
            `A Form ${indicator.awayRecentFormPercentage}%`,
            `Away form points percentage from ${indicator.awayRecentFormSampleSize} completed local ${indicator.awayRecentFormSampleSize === 1 ? 'match' : 'matches'} before kickoff.`
        ]);
    }
    if (items.length === 0) return null;

    const row = document.createElement("div");
    row.className = "pe-fixture-indicators";
    row.setAttribute("aria-label", "Fixture data indicators");
    items.forEach(([label, tooltip]) => {
        const chip = document.createElement("span");
        chip.className = label === "Partial Season" ? "pe-fixture-indicator warning" : "pe-fixture-indicator";
        chip.textContent = label;
        chip.title = tooltip;
        chip.setAttribute("aria-label", `${label}. ${tooltip}`);
        row.appendChild(chip);
    });
    return row;
}

function ordinal(value) {
    const number = Number(value);
    const mod100 = number % 100;
    if (mod100 >= 11 && mod100 <= 13) return `${number}th`;
    if (number % 10 === 1) return `${number}st`;
    if (number % 10 === 2) return `${number}nd`;
    if (number % 10 === 3) return `${number}rd`;
    return `${number}th`;
}

function cardRow(label, value, valueClass = "") {
    const row = document.createElement("div");
    row.className = "prediction-card-row";
    
    const l = document.createElement("span");
    l.className = "prediction-card-label";
    l.textContent = label;

    const v = document.createElement("span");
    v.className = "prediction-card-value " + valueClass;
    v.textContent = value;
    v.title = value;

    row.append(l, v);
    return row;
}

function putOptionalNumber(request, name) {
    const raw = form.elements[name]?.value;
    if (raw !== undefined && raw !== null && raw !== "") {
        request[name] = Number(raw);
    }
}

function putOptionalString(request, name) {
    const raw = form.elements[name]?.value;
    if (raw) {
        request[name] = raw;
    }
}

function cell(value) {
    const element = document.createElement("td");
    element.textContent = value === null || value === undefined || value === "" ? "-" : String(value);
    return element;
}

function probabilityCell(value, rawValue) {
    const label = rawValue && rawValue !== value
        ? `${percent(value)} calibrated from ${percent(rawValue)}`
        : percent(value);
    const element = cell(label);
    element.className = "probability";
    return element;
}

function confidenceCell(value) {
    const normalized = String(value || "UNRATED").toLowerCase().replace("_", "-");
    const element = cell(value || "UNRATED");
    element.className = `confidence ${normalized}`;
    return element;
}

function tuningCell(selection) {
    const adjustment = selection.tuningAdjustment ? signedDecimal(selection.tuningAdjustment) : "-";
    const element = cell(adjustment);
    element.className = Number(selection.tuningAdjustment || 0) !== 0 ? "expected-value positive" : "expected-value";
    return element;
}

function oddsCell(selection) {
    const decimalOdds = selection.decimalOdds ?? selection.bestDecimalOdds;
    const impliedProbability = selection.bookmakerImpliedProbability ?? selection.bestImpliedProbability;
    const odds = decimalOdds ? Number(decimalOdds).toFixed(2) : "-";
    const bookmaker = selection.bestOddsBookmaker ? ` ${selection.bestOddsBookmaker}` : "";
    const implied = impliedProbability ? `, implied ${percent(impliedProbability)}` : "";
    const element = cell(`${odds}${bookmaker}${implied}`);
    element.className = "odds";
    return element;
}

function expectedValueCell(value) {
    const element = cell(signedDecimal(value));
    element.className = Number(value || 0) > 0 ? "expected-value positive" : "expected-value";
    return element;
}

function valueCell(value) {
    const normalized = String(value || "NO_ODDS").toLowerCase().replace("_", "-");
    const element = cell(value || "NO_ODDS");
    element.className = `value-rating ${normalized}`;
    return element;
}

function percent(value) {
    if (value === null || value === undefined || value === "") {
        return "-";
    }
    return `${(Number(value) * 100).toFixed(2)}%`;
}

function signedDecimal(value) {
    if (value === null || value === undefined || value === "") {
        return "-";
    }
    const number = Number(value);
    return `${number > 0 ? "+" : ""}${number.toFixed(4)}`;
}

function decimal(value) {
    if (value === null || value === undefined || value === "") {
        return "-";
    }
    return Number(value).toFixed(4);
}

function formatDateTime(value) {
    if (!value) {
        return "-";
    }
    return new Intl.DateTimeFormat(undefined, {
        year: "numeric",
        month: "short",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit"
    }).format(new Date(value));
}

function toIsoDate(date) {
    return date.toISOString().slice(0, 10);
}

function inclusiveDateRangeDays(from, to) {
    const fromDate = new Date(`${from}T00:00:00Z`);
    const toDate = new Date(`${to}T00:00:00Z`);
    return Math.floor((toDate - fromDate) / 86_400_000) + 1;
}

async function readError(response) {
    const text = await response.text();
    if (!text) {
        return `Request failed with HTTP ${response.status}.`;
    }
    try {
        const payload = JSON.parse(text);
        return payload.message || payload.error || text;
    } catch {
        return text;
    }
}

function filenameFromHeader(header) {
    if (!header) {
        return null;
    }
    const match = header.match(/filename="?([^"]+)"?/i);
    return match ? match[1] : null;
}

function showToast(message) {
    toast.textContent = message;
    toast.classList.remove("hidden");
    window.clearTimeout(showToast.timeoutId);
    showToast.timeoutId = window.setTimeout(() => {
        toast.classList.add("hidden");
    }, 4500);
}

function formatKickoffTime(isoString, compactDate) {
    if (!isoString) return "Time TBC";
    const d = new Date(isoString);
    if (isNaN(d.getTime())) return "Time TBC";
    
    const timeStr = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    const now = new Date();
    const isToday = d.toDateString() === now.toDateString();
    
    if (isToday && !compactDate) return timeStr;
    const monthDay = d.toLocaleDateString([], { month: 'short', day: 'numeric' });
    return `${monthDay}, ${timeStr}`;
}

function formatStatusLabel(status, liveMinute) {
    return window.PitchEdge.formatLiveBadge(status, liveMinute);
}

function getPredBadgeClass(status) {
    if (status === "Predictions ready") return "badge-positive";
    if (status === "Insufficient data" || status === "Low confidence") return "badge-warning";
    return "badge-neutral";
}

function openFixtureModalDrawer(f) {
    const modalOverlay = document.getElementById("fixtureModal");
    const modalTitle = document.getElementById("modalTitle");
    if (!modalOverlay || !modalTitle) return;
    
    modalOverlay.classList.remove("hidden");
    modalTitle.textContent = `${f.homeTeam} vs ${f.awayTeam}`;
    
    const modalBody = modalOverlay.querySelector(".modal-body");
    if (modalBody) {
        let scoreHtml = '';
        if (f.homeScore !== null && f.homeScore !== undefined) {
            const badgeText = window.PitchEdge.formatLiveBadge(f.status, f.liveMinute);
            const isLiveMatch = (f.status === "LIVE" || f.status === "IN_PLAY" || f.status === "HALF_TIME");
            const colorStyle = isLiveMatch ? "color:#ef4444;" : "color:var(--text);";
            scoreHtml = `<div style="font-size:18px; font-weight:bold; ${colorStyle} margin: 8px 0;">${f.homeTeam} ${f.homeScore} - ${f.awayScore} ${f.awayTeam} (${badgeText})</div>`;
        }

        modalBody.innerHTML = `
            <div style="display:flex; flex-direction:column; gap:12px; text-align:left; padding: 8px;">
                <div><strong style="font-size:11px; color:var(--muted);">LEAGUE</strong><br>${f.leagueName}</div>
                <div><strong style="font-size:11px; color:var(--muted);">KICKOFF TIME</strong><br>${formatKickoffTime(f.kickoffTime, true)}</div>
                ${f.venue ? `<div><strong style="font-size:11px; color:var(--muted);">VENUE</strong><br>${f.venue}</div>` : ''}
                <div><strong style="font-size:11px; color:var(--muted);">STATUS</strong><br>${formatStatusLabel(f.status, f.liveMinute)}</div>
                ${scoreHtml}
                <div style="display:flex; gap:8px; margin-top:4px;">
                    <span class="badge ${getPredBadgeClass(f.predictionStatus)}">${f.predictionStatus}</span>
                    <span class="badge badge-neutral">${f.oddsProvider || 'No odds'}</span>
                </div>
                ${f.lastRefreshedTime ? `<div style="font-size:11px; color:var(--muted); margin-top:8px;">Last refreshed: ${formatDateTime(f.lastRefreshedTime)}</div>` : ''}
                
                <div style="margin-top:16px; border-top: 1px solid var(--line); padding-top: 16px;">
                    ${f.hasPredictions 
                        ? `<a href="fixture-details.html?matchId=${f.matchId}&modelVersion=${lastResponse ? lastResponse.modelVersion : 'v1'}${lastResponse ? '&runId=' + lastResponse.requestId : ''}" class="button primary" style="display:inline-block; text-decoration:none;">View full prediction details</a>`
                        : `<p class="notice" style="margin:0;">No predictions have been generated for this fixture yet.</p>`
                    }
                </div>
            </div>
        `;
    }
}

// --- UI Helpers for Market Ranges & Form Restoring ---
const toggleHelpBtn = document.getElementById("toggleHelpBtn");
const settingsHelp = document.getElementById("settingsHelp");

if (toggleHelpBtn && settingsHelp) {
    toggleHelpBtn.addEventListener("click", () => {
        const isHidden = settingsHelp.classList.contains("hidden");
        settingsHelp.classList.toggle("hidden");
        toggleHelpBtn.setAttribute("aria-expanded", !isHidden);
    });
}

const addMarketRangeBtn = document.getElementById("addMarketRangeBtn");
const resetMarketRangeBtn = document.getElementById("resetMarketRangeBtn");
const marketRangesContainer = document.getElementById("marketRangesContainer");

if (addMarketRangeBtn) addMarketRangeBtn.addEventListener("click", () => addMarketRangeRow());
if (resetMarketRangeBtn) resetMarketRangeBtn.addEventListener("click", () => {
    if (marketRangesContainer) marketRangesContainer.innerHTML = '';
});

function addMarketRangeRow(code = '', min = '', max = '') {
    const row = document.createElement("div");
    row.className = "market-range-row";
    row.style.display = "flex";
    row.style.gap = "8px";
    row.style.marginBottom = "8px";
    
    const select = document.createElement("select");
    select.className = "market-range-select";
    Array.from(document.querySelectorAll('input[name="marketCodes"]')).forEach(cb => {
        const option = document.createElement("option");
        option.value = cb.value;
        option.textContent = cb.parentElement.textContent.trim();
        select.appendChild(option);
    });
    if (code) select.value = code;
    
    const minInput = document.createElement("input");
    minInput.type = "number";
    minInput.min = "0";
    minInput.max = "100";
    minInput.step = "1";
    minInput.placeholder = "Min %";
    minInput.className = "market-range-min";
    if (min !== '') minInput.value = min * 100;
    
    const maxInput = document.createElement("input");
    maxInput.type = "number";
    maxInput.min = "0";
    maxInput.max = "100";
    maxInput.step = "1";
    maxInput.placeholder = "Max %";
    maxInput.className = "market-range-max";
    if (max !== '') maxInput.value = max * 100;
    
    const removeBtn = document.createElement("button");
    removeBtn.type = "button";
    removeBtn.className = "secondary compact";
    removeBtn.textContent = "X";
    removeBtn.addEventListener("click", () => row.remove());
    
    row.append(select, minInput, maxInput, removeBtn);
    marketRangesContainer.appendChild(row);
}

function getMarketRanges() {
    const ranges = {};
    if (!marketRangesContainer) return null;
    Array.from(marketRangesContainer.children).forEach(row => {
        const code = row.querySelector(".market-range-select").value;
        const minVal = row.querySelector(".market-range-min").value;
        const maxVal = row.querySelector(".market-range-max").value;
        
        if (minVal !== '' || maxVal !== '') {
            ranges[code] = {};
            if (minVal !== '') ranges[code].min = Number(minVal) / 100;
            if (maxVal !== '') ranges[code].max = Number(maxVal) / 100;
        }
    });
    return Object.keys(ranges).length > 0 ? ranges : null;
}

function renderMarketRanges(rangesMap) {
    if (!marketRangesContainer) return;
    marketRangesContainer.innerHTML = '';
    if (!rangesMap) return;
    Object.entries(rangesMap).forEach(([code, range]) => {
        addMarketRangeRow(code, range.min ?? '', range.max ?? '');
    });
}

function restoreForm(request) {
    if (!request) return;

    setChecked("leagueCodes", false);
    setChecked("marketCodes", false);
    selectedLeagueCodes = new Set(
        (request.leagueCodes || []).filter(code => leagueCatalog.length === 0
            || leagueCatalog.some(league => league.leagueCode === code && isLeagueSelectable(league)))
    );
    renderLeagueSelector();
    (request.leagueCodes || []).forEach(code => {
        const cb = document.querySelector(`input[name="leagueCodes"][value="${code}"]`);
        if (cb) cb.checked = true;
    });
    (request.marketCodes || []).forEach(code => {
        const cb = document.querySelector(`input[name="marketCodes"][value="${code}"]`);
        if (cb) cb.checked = true;
    });

    if (request.fixtureDateFrom) fixtureDateFrom.value = request.fixtureDateFrom;
    if (request.fixtureDateTo) fixtureDateTo.value = request.fixtureDateTo;
    if (request.strategy) form.elements.strategy.value = request.strategy;
    if (request.numberOfBatches) form.elements.numberOfBatches.value = request.numberOfBatches;
    if (request.minimumSelections) form.elements.minimumSelections.value = request.minimumSelections;
    if (request.maximumSelections) form.elements.maximumSelections.value = request.maximumSelections;

    form.elements.minimumModelProbability.value = request.minimumModelProbability !== null && request.minimumModelProbability !== undefined ? request.minimumModelProbability * 100 : "";
    form.elements.maximumModelProbability.value = request.maximumModelProbability !== null && request.maximumModelProbability !== undefined ? request.maximumModelProbability * 100 : "";
    form.elements.minimumConfidence.value = request.minimumConfidence || "";
    form.elements.minimumDataQuality.value = request.minimumDataQuality !== null && request.minimumDataQuality !== undefined ? request.minimumDataQuality * 100 : "";
    form.elements.minimumExpectedValue.value = request.minimumExpectedValue ?? "";
    form.elements.minimumProbabilityEdge.value = request.minimumProbabilityEdge ?? "";
    form.elements.rankingMode.value = request.rankingMode || "";

    renderMarketRanges(request.marketProbabilityRanges);
}

function putOptionalProbability(request, name) {
    const raw = form.elements[name]?.value;
    if (raw !== undefined && raw !== null && raw !== "") {
        request[name] = Number(raw) / 100;
    }
}

async function loadRunFromUrl() {
    const params = new URLSearchParams(window.location.search);
    const runId = params.get('runId');
    if (!runId) return;
    window.location.replace(`/prediction-results.html?runId=${encodeURIComponent(runId)}`);
    return;

    let navType = 'navigate';
    if (performance.getEntriesByType) {
        const navEntries = performance.getEntriesByType("navigation");
        if (navEntries.length > 0) {
            navType = navEntries[0].type;
        }
    } else if (performance.navigation) {
        if (performance.navigation.type === 1) navType = 'reload';
        if (performance.navigation.type === 2) navType = 'back_forward';
    }

    try {
        const response = await fetch(`/api/v1/predictions/runs/${runId}`, {
            headers: { "Accept": "application/json" }
        });
        if (!response.ok) return;
        const payload = await response.json();
        
        const savedDate = payload.generatedAt ? payload.generatedAt.split('T')[0] : "";
        const today = toIsoDate(new Date());
        
        if (navType === 'reload') {
            if (savedDate && savedDate !== today) {
                clearResults(false);
                const oldRunLabel = document.getElementById('oldRunLabel');
                if (oldRunLabel) {
                    oldRunLabel.classList.remove('hidden');
                    oldRunLabel.innerHTML = `Previous prediction run from ${savedDate} is available. <a href="?runId=${runId}" style="margin-left:8px; font-weight:bold; color:var(--brand); text-decoration:none;">Restore previous run</a>`;
                }
                return;
            }
        }

        lastRequest = payload.input;
        lastResponse = payload;
        
        restoreForm(payload.input);
        renderResponse(payload);
        
        const resultsBtn = document.querySelector('[aria-controls="responsePanel"]');
        const resultsPanel = document.getElementById('responsePanel');
        if (resultsBtn && resultsPanel) {
            resultsBtn.setAttribute('aria-expanded', 'true');
            resultsPanel.classList.remove('hidden');
        }

        const oldRunLabel = document.getElementById('oldRunLabel');
        if (oldRunLabel) {
            if (savedDate && savedDate !== today) {
                oldRunLabel.classList.remove('hidden');
                oldRunLabel.textContent = `Viewing saved run generated ${savedDate} for ${payload.input.fixtureDateFrom} to ${payload.input.fixtureDateTo}`;
            } else {
                oldRunLabel.classList.add('hidden');
            }
        }
    } catch (e) {
        console.error("Failed to load run:", e);
    }
}

const clearResultsBtn = document.getElementById('clearResultsBtn');
if (clearResultsBtn) {
    clearResultsBtn.addEventListener('click', () => {
        if (confirm("Clear the currently displayed prediction results? This will not delete saved runs from the database.")) {
            clearResults(true);
        }
    });
}

function clearResults(userInitiated) {
    const summaryGrid = document.getElementById('summaryGrid');
    if (summaryGrid) summaryGrid.innerHTML = '';
    const warningsSection = document.getElementById('warningsSection');
    if (warningsSection) warningsSection.classList.add('hidden');
    const batchList = document.getElementById('batchList');
    if (batchList) batchList.innerHTML = '';
    const modelBadge = document.getElementById('modelBadge');
    if (modelBadge) modelBadge.textContent = 'No model';
    
    const resultsBtn = document.querySelector('[aria-controls="responsePanel"]');
    const resultsPanel = document.getElementById('responsePanel');
    if (resultsBtn && resultsPanel) {
        resultsBtn.setAttribute('aria-expanded', 'false');
        resultsPanel.classList.add('hidden');
    }
    
    lastResponse = null;
    lastRequest = null;
    if (searchFilter) searchFilter.value = '';
    if (leagueFilter) leagueFilter.value = '';
    if (marketFilter) marketFilter.value = '';
    if (sortFilter) sortFilter.value = '';

    const url = new URL(window.location);
    url.searchParams.delete('runId');
    window.history.replaceState({runId: null}, '', url);

    initializeDates();
    updateTopSummary();

    const sumRunStatus = document.getElementById('sumRunStatus');
    if (sumRunStatus) sumRunStatus.textContent = 'None';
    
    const oldRunLabel = document.getElementById('oldRunLabel');
    if (oldRunLabel) {
        if (userInitiated) {
            oldRunLabel.classList.remove('hidden');
            oldRunLabel.innerHTML = `Previous saved runs can still be opened from Recent Prediction Runs.`;
        } else {
            oldRunLabel.classList.add('hidden');
        }
    }
}

window.addEventListener('popstate', loadRunFromUrl);
document.addEventListener('DOMContentLoaded', loadRunFromUrl);

// --- Fixture Browser Logic ---
const fixtureBrowserDate = document.getElementById('fixtureBrowserDate');
const fixturePrevBtn = document.getElementById('fixturePrevBtn');
const fixtureNextBtn = document.getElementById('fixtureNextBtn');
const fixtureTodayBtn = document.getElementById('fixtureTodayBtn');
const fixtureBrowserSearch = document.getElementById('fixtureBrowserSearch');
const fixtureBrowserLeague = document.getElementById('fixtureBrowserLeague');
const fixtureBrowserStatus = document.getElementById('fixtureBrowserStatus');
const fixtureBrowserList = document.getElementById('fixtureBrowserList');
const fixtureBrowserLoading = document.getElementById('fixtureBrowserLoading');
const fixtureBrowserEmpty = document.getElementById('fixtureBrowserEmpty');

let currentFixtures = [];

function initFixtureBrowser() {
    if (!fixtureBrowserDate) return;
    fixtureBrowserDate.value = toIsoDate(new Date());
    
    const btnYest = document.getElementById('fixtureYesterdayBtn');
    const btnToday = document.getElementById('fixtureTodayBtn');
    const btnTom = document.getElementById('fixtureTomorrowBtn');

    const updateDateCarouselBtns = (targetDateStr) => {
        const todayStr = toIsoDate(new Date());
        const yestD = new Date(); yestD.setDate(yestD.getDate() - 1); const yestStr = toIsoDate(yestD);
        const tomD = new Date(); tomD.setDate(tomD.getDate() + 1); const tomStr = toIsoDate(tomD);

        [btnYest, btnToday, btnTom].forEach(b => {
            if (!b) return;
            b.style.background = 'transparent';
            b.style.color = 'var(--text)';
            b.classList.remove('active');
        });

        let activeBtn = null;
        if (targetDateStr === yestStr) activeBtn = btnYest;
        else if (targetDateStr === todayStr) activeBtn = btnToday;
        else if (targetDateStr === tomStr) activeBtn = btnTom;

        if (activeBtn) {
            activeBtn.classList.add('active');
            activeBtn.style.background = 'var(--brand)';
            activeBtn.style.color = '#fff';
        }
    };

    if (fixturePrevBtn) fixturePrevBtn.addEventListener('click', () => adjustFixtureDate(-1));
    if (fixtureNextBtn) fixtureNextBtn.addEventListener('click', () => adjustFixtureDate(1));
    if (btnToday) btnToday.addEventListener('click', () => {
        const todayStr = toIsoDate(new Date());
        fixtureBrowserDate.value = todayStr;
        updateDateCarouselBtns(todayStr);
        loadFixtures();
    });
    if (btnYest) btnYest.addEventListener('click', () => {
        const d = new Date(); d.setDate(d.getDate() - 1);
        const str = toIsoDate(d);
        fixtureBrowserDate.value = str;
        updateDateCarouselBtns(str);
        loadFixtures();
    });
    if (btnTom) btnTom.addEventListener('click', () => {
        const d = new Date(); d.setDate(d.getDate() + 1);
        const str = toIsoDate(d);
        fixtureBrowserDate.value = str;
        updateDateCarouselBtns(str);
        loadFixtures();
    });
    
    fixtureBrowserDate.addEventListener('change', () => {
        updateDateCarouselBtns(fixtureBrowserDate.value);
        loadFixtures();
    });
    
    const fixtureBrowserGroup = document.getElementById('fixtureBrowserGroup');
    [fixtureBrowserSearch, fixtureBrowserLeague, fixtureBrowserStatus, fixtureBrowserGroup].forEach(el => {
        if (el) el.addEventListener('change', renderFixtureBrowser);
        if (el && el === fixtureBrowserSearch) el.addEventListener('input', renderFixtureBrowser);
    });
    
    loadFixtures();
    setInterval(loadFixtures, 180000);
}

function adjustFixtureDate(days) {
    const d = new Date(fixtureBrowserDate.value);
    d.setDate(d.getDate() + days);
    fixtureBrowserDate.value = toIsoDate(d);
    fixtureBrowserDate.dispatchEvent(new Event('change'));
}

async function loadFixtures() {
    const date = fixtureBrowserDate.value;
    if (!date) return;
    
    fixtureBrowserLoading.classList.remove('hidden');
    fixtureBrowserEmpty.classList.add('hidden');
    fixtureBrowserList.innerHTML = '';
    
    try {
        const res = await fetch(`/api/v1/fixtures?date=${date}`);
        if (!res.ok) throw new Error('Failed');
        currentFixtures = await res.json();
        
        // Update league filter options
        const leagues = new Set(currentFixtures.map(f => f.leagueCode));
        const currentLeague = fixtureBrowserLeague.value;
        fixtureBrowserLeague.innerHTML = '<option value="">All Leagues</option>';
        Array.from(leagues).sort().forEach(l => {
            const opt = document.createElement('option');
            opt.value = l;
            opt.textContent = formatEnum(l);
            fixtureBrowserLeague.appendChild(opt);
        });
        fixtureBrowserLeague.value = currentLeague;
        
        renderFixtureBrowser();
    } catch (e) {
        fixtureBrowserEmpty.textContent = 'Error loading fixtures.';
        fixtureBrowserEmpty.classList.remove('hidden');
    } finally {
        fixtureBrowserLoading.classList.add('hidden');
    }
}

function renderFixtureBrowser() {
    if (!fixtureBrowserSearch) return;
    const search = fixtureBrowserSearch.value.toLowerCase();
    const league = fixtureBrowserLeague.value;
    const statusFilter = fixtureBrowserStatus.value;
    const groupMode = document.getElementById('fixtureBrowserGroup')?.value || "LEAGUE";
    
    let filtered = currentFixtures.filter(f => {
        if (search && !(f.homeTeam || "").toLowerCase().includes(search) && !(f.awayTeam || "").toLowerCase().includes(search)) return false;
        if (league && f.leagueCode !== league) return false;
        if (statusFilter) {
            if (statusFilter === "PRED_READY" && !f.hasPredictions) return false;
            if (statusFilter === "NO_PRED" && f.hasPredictions) return false;
            if (statusFilter === "LIVE" && f.status !== "LIVE" && f.status !== "IN_PLAY" && f.status !== "HALF_TIME") return false;
            if (statusFilter === "FINISHED" && f.status !== "FINISHED") return false;
            if (statusFilter === "SCHEDULED" && f.status !== "SCHEDULED") return false;
            if (statusFilter === "ODDS_AVAIL" && !f.hasOdds) return false;
        }
        return true;
    });
    
    filtered.sort((a, b) => new Date(a.kickoffTime || 0) - new Date(b.kickoffTime || 0));
    
    fixtureBrowserList.innerHTML = '';
    if (filtered.length === 0) {
        fixtureBrowserEmpty.textContent = 'No fixtures match filters.';
        fixtureBrowserEmpty.classList.remove('hidden');
        return;
    }
    fixtureBrowserEmpty.classList.add('hidden');

    const freshnessEl = document.getElementById('fixtureFreshness');
    if (freshnessEl) {
        let maxRef = null;
        currentFixtures.forEach(f => {
            if (f.lastRefreshedTime) {
                const dt = new Date(f.lastRefreshedTime);
                if (!maxRef || dt > maxRef) maxRef = dt;
            }
        });
        if (maxRef) {
            const diffMins = Math.floor((new Date() - maxRef) / 60000);
            if (diffMins <= 1) {
                freshnessEl.textContent = 'Live score sync: scores checked just now. Auto-updates every 3 minutes.';
            } else if (diffMins < 60) {
                freshnessEl.textContent = `Live score sync: scores checked ${diffMins} mins ago. Auto-updates every 3 minutes.`;
            } else {
                freshnessEl.textContent = `Live score sync: scores last checked ${maxRef.toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'})}. Auto-updates every 3 minutes.`;
            }
        } else {
            freshnessEl.textContent = 'Live score sync pending. Updates run automatically every 3 minutes.';
        }
    }
    
    if (groupMode === "NONE") {
        const grid = document.createElement('div');
        grid.className = 'fixture-browser-grid';
        renderFixtureCards(filtered, grid);
        fixtureBrowserList.appendChild(grid);
    } else {
        const groups = new Map();
        filtered.forEach(f => {
            let key = "";
            if (groupMode === "TIME") {
                key = formatKickoffTime(f.kickoffTime, false);
            } else if (groupMode === "LEAGUE") {
                key = f.leagueName || f.leagueCode;
            }
            if (!groups.has(key)) groups.set(key, []);
            groups.get(key).push(f);
        });

        groups.forEach((items, keyName) => {
            const groupBlock = document.createElement('div');
            groupBlock.className = 'fixture-group';
            
            const groupHead = document.createElement('div');
            groupHead.className = 'fixture-group-title';
            groupHead.textContent = keyName;
            
            const grid = document.createElement('div');
            grid.className = 'fixture-browser-grid';
            renderFixtureCards(items, grid);
            
            groupBlock.appendChild(groupHead);
            groupBlock.appendChild(grid);
            fixtureBrowserList.appendChild(groupBlock);
        });
    }
}

function renderFixtureCards(items, container) {
    const P = window.PitchEdge;
    items.forEach(f => {
        const card = document.createElement('div');
        card.className = `pe-fixture-card fixture-card ${f.status === 'FINISHED' ? 'finished' : ''}`;
        
        const scoreDisplay = P.formatScore(f);

        const badgeText = P.formatLiveBadge(f.status, f.liveMinute);
        const isLiveMatch = (f.status === "LIVE" || f.status === "IN_PLAY" || f.status === "HALF_TIME");
        const badgeClass = isLiveMatch ? "badge badge-live" : (f.status === "FINISHED" ? "badge badge-neutral" : "badge");
        const statusBadge = `<span class="${badgeClass}">${P.escapeHtml(badgeText)}</span>`;

        card.innerHTML = `
            <div class="fixture-card-header">
                <span>${P.escapeHtml(f.leagueName || formatEnum(f.leagueCode))}</span>
                <span>${P.escapeHtml(formatKickoffTime(f.kickoffTime, false))}</span>
            </div>
            <div class="pe-fixture-teams">
                <div class="pe-team-block">
                    ${P.teamMark(f.homeTeam, f.homeTeamBadgeUrl || f.homeTeamLogoUrl || f.homeBadgeUrl)}
                    <span class="pe-team-name">${P.escapeHtml(f.homeTeam)}</span>
                </div>
                <div class="pe-score-pill">${P.escapeHtml(scoreDisplay)}</div>
                <div class="pe-team-block away">
                    <span class="pe-team-name">${P.escapeHtml(f.awayTeam)}</span>
                    ${P.teamMark(f.awayTeam, f.awayTeamBadgeUrl || f.awayTeamLogoUrl || f.awayBadgeUrl)}
                </div>
            </div>
            <div class="fixture-card-status" style="align-items:center; gap:6px; flex-wrap:wrap; margin-top:4px;">
                ${statusBadge}
                <span class="badge ${getPredBadgeClass(f.predictionStatus)}">${P.escapeHtml(f.predictionStatus)}</span>
                <span class="badge badge-neutral">${P.escapeHtml(f.oddsProvider || 'No odds')}</span>
            </div>
            <div class="pe-fixture-insight">${P.escapeHtml(f.hasPredictions ? 'Prediction ready for this fixture.' : 'No prediction available yet.')}</div>
        `;
        
        card.addEventListener('click', () => openFixtureModalDrawer(f));
        container.appendChild(card);
    });
}

// Initialize components that depend on DOM elements declared as const
document.addEventListener('DOMContentLoaded', () => {
    restoreSectionState();
    initFixtureBrowser();
    updateTopSummary();
});
