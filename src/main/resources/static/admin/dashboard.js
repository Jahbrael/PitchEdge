const adminKeyInput = document.getElementById("adminKey");
const saveKeyButton = document.getElementById("saveKey");
const refreshButton = document.getElementById("refreshDashboard");
const runAutomationBtn = document.getElementById("runAutomationBtn");
const runAutomationModal = document.getElementById("runAutomationModal");
const cancelAutomationBtn = document.getElementById("cancelAutomationBtn");
const confirmAutomationBtn = document.getElementById("confirmAutomationBtn");
const statusPill = document.getElementById("systemStatus");

const totalsGrid = document.getElementById("totalsGrid");
const leagueRows = document.getElementById("leagueRows");
const sourceRows = document.getElementById("sourceRows");
const runRows = document.getElementById("runRows");
const dataStatusGrid = document.getElementById("dataStatusGrid");
const dataStatusSummary = document.getElementById("dataStatusSummary");
const automationProgressStatus = document.getElementById("automationProgressStatus");
const automationProgressBar = document.getElementById("automationProgressBar");
const automationProgressTrack = automationProgressBar?.parentElement;
const automationProgressPercent = document.getElementById("automationProgressPercent");
const automationProgressCount = document.getElementById("automationProgressCount");
const automationCurrentStep = document.getElementById("automationCurrentStep");
const automationStartTime = document.getElementById("automationStartTime");
const automationLastUpdateTime = document.getElementById("automationLastUpdateTime");
const automationCompletionTime = document.getElementById("automationCompletionTime");
const automationProgressError = document.getElementById("automationProgressError");

const alertsSection = document.getElementById("alertsSection");
const alertsList = document.getElementById("alertsList");
const alertsSummary = document.getElementById("alertsSummary");

const toast = document.getElementById("toast");

const detailDrawer = document.getElementById("detailDrawer");
const drawerTitle = document.getElementById("drawerTitle");
const drawerBody = document.getElementById("drawerBody");
const drawerCloseBtn = document.getElementById("drawerCloseBtn");
const drawerOverlay = document.getElementById("drawerOverlay");

const ADMIN_KEY_STORAGE = "betai.adminKey";

// State
let dashboardData = {
    runs: [],
    leagues: [],
    sources: [],
    totals: {}
};

// Pagination for runs
let currentRunPage = 1;
const RUNS_PER_PAGE = 20;
let automationProgressPollId = null;

// Initialize
adminKeyInput.value = sessionStorage.getItem(ADMIN_KEY_STORAGE) || "";

saveKeyButton.addEventListener("click", () => {
    sessionStorage.setItem(ADMIN_KEY_STORAGE, adminKeyInput.value.trim());
    showToast("Admin key saved for this browser session.");
    loadDashboard();
});

adminKeyInput.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
        sessionStorage.setItem(ADMIN_KEY_STORAGE, adminKeyInput.value.trim());
        loadDashboard();
    }
});

refreshButton.addEventListener("click", loadDashboard);

runAutomationBtn.addEventListener("click", () => {
    runAutomationModal.classList.remove("hidden");
});

cancelAutomationBtn.addEventListener("click", () => {
    runAutomationModal.classList.add("hidden");
});

confirmAutomationBtn.addEventListener("click", async () => {
    runAutomationModal.classList.add("hidden");
    const originalText = runAutomationBtn.textContent;
    runAutomationBtn.disabled = true;
    runAutomationBtn.textContent = "Starting...";
    let accepted = false;

    try {
        let csrfToken = "";
        const cookies = document.cookie.split(';');
        for (let cookie of cookies) {
            const [name, value] = cookie.trim().split('=');
            if (name === 'XSRF-TOKEN') {
                csrfToken = value;
                break;
            }
        }
        
        if (!csrfToken) {
            const csrfResponse = await fetch("/api/v1/auth/csrf");
            if (csrfResponse.ok) {
                const csrfData = await csrfResponse.json();
                csrfToken = csrfData.token;
            }
        }

        const response = await fetch("/api/v1/admin/automation/runNow", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "X-BETAI-ADMIN-KEY": adminKeyInput.value.trim(),
                "X-XSRF-TOKEN": csrfToken
            }
        });
        const data = await response.json();

        if (response.ok) {
            accepted = true;
            showToast(`Automation started: ${data.message}`);
            await loadAutomationProgress();
        } else {
            showToast(`Start Failed: ${data.message || response.statusText}`);
        }
    } catch (err) {
        showToast(`Start Error: ${err.message}`);
    } finally {
        if (!accepted) {
            runAutomationBtn.disabled = false;
        }
        runAutomationBtn.textContent = originalText;
    }
});

// Setup collapsible sections
document.querySelectorAll('.collapsible .section-header').forEach(header => {
    const sectionId = header.closest('.section').id;
    const isCollapsed = localStorage.getItem(`betai.collapse.${sectionId}`) !== 'false';
    
    if (isCollapsed) {
        header.closest('.section').classList.add('collapsed');
        header.setAttribute('aria-expanded', 'false');
    } else {
        header.closest('.section').classList.remove('collapsed');
        header.setAttribute('aria-expanded', 'true');
    }

    header.addEventListener('click', () => toggleSection(header));
    header.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            toggleSection(header);
        }
    });
});

function toggleSection(header) {
    const section = header.closest('.section');
    const sectionId = section.id;
    const isCollapsed = section.classList.contains('collapsed');
    
    if (isCollapsed) {
        section.classList.remove('collapsed');
        header.setAttribute('aria-expanded', 'true');
        localStorage.setItem(`betai.collapse.${sectionId}`, 'false');
    } else {
        section.classList.add('collapsed');
        header.setAttribute('aria-expanded', 'false');
        localStorage.setItem(`betai.collapse.${sectionId}`, 'true');
    }
}

// Drawer logic
function openDrawer(title, contentHtml) {
    drawerTitle.textContent = title;
    drawerBody.innerHTML = contentHtml;
    detailDrawer.setAttribute('aria-hidden', 'false');
    drawerCloseBtn.focus();
}

function closeDrawer() {
    detailDrawer.setAttribute('aria-hidden', 'true');
}

drawerCloseBtn.addEventListener('click', closeDrawer);
drawerOverlay.addEventListener('click', closeDrawer);
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && detailDrawer.getAttribute('aria-hidden') === 'false') {
        closeDrawer();
    }
});

if (adminKeyInput.value.trim()) {
    loadDashboard();
}

async function loadDashboard() {
    const adminKey = adminKeyInput.value.trim();
    if (!adminKey) return;

    refreshButton.disabled = true;
    try {
        const response = await fetch("/api/v1/admin/dashboard/overview", {
            headers: { "Accept": "application/json", "X-BETAI-ADMIN-KEY": adminKey }
        });

        if (!response.ok) throw new Error(`HTTP ${response.status}`);

        const overview = await response.json();
        dashboardData.runs = overview.recentRuns || [];
        dashboardData.leagues = overview.leagues || [];
        dashboardData.sources = overview.sources || [];
        dashboardData.totals = overview.totals || {};
        
        renderStatus(overview.status);
        renderAlerts(overview.alerts || []);
        renderTotals(dashboardData.totals);
        renderDataStatus(overview);
        updateLeagueFilters();
        updateSourceFilters();
        updateRunFilters();
        void loadAutomationProgress();
        
        showToast(`Dashboard loaded at ${formatDateTime(overview.generatedAt)}.`);
    } catch (error) {
        showToast(error.message);
    } finally {
        refreshButton.disabled = false;
    }
}

async function loadAutomationProgress() {
    const adminKey = adminKeyInput.value.trim();
    if (!adminKey || !automationProgressBar) return;

    try {
        const response = await fetch("/api/v1/admin/automation/progress", {
            headers: { "Accept": "application/json", "X-BETAI-ADMIN-KEY": adminKey }
        });
        if (!response.ok) throw new Error(`Automation progress unavailable (HTTP ${response.status})`);
        const progress = await response.json();
        renderAutomationProgress(progress);
        if (progress.status === "RUNNING") {
            scheduleAutomationProgressPoll();
        } else {
            stopAutomationProgressPoll();
            runAutomationBtn.disabled = false;
        }
    } catch (error) {
        stopAutomationProgressPoll();
        if (automationProgressError) {
            automationProgressError.textContent = error.message;
            automationProgressError.classList.remove("hidden");
        }
    }
}

function scheduleAutomationProgressPoll() {
    stopAutomationProgressPoll();
    automationProgressPollId = window.setTimeout(loadAutomationProgress, 2500);
}

function stopAutomationProgressPoll() {
    if (automationProgressPollId !== null) {
        window.clearTimeout(automationProgressPollId);
        automationProgressPollId = null;
    }
}

function renderAutomationProgress(progress) {
    const percentage = Math.max(0, Math.min(100, Number(progress.progressPercentage || 0)));
    const status = progress.status || "NOT_STARTED";
    const statusLabel = progress.statusLabel || statusLabelForProgress(status);
    const totalSteps = Number(progress.totalSteps || 0);
    const completedSteps = Number(progress.completedSteps || 0);

    automationProgressStatus.textContent = statusLabel;
    automationProgressStatus.className = `tag automation-progress-status ${status.toLowerCase()}`;
    automationProgressBar.style.width = `${percentage}%`;
    automationProgressBar.classList.toggle("running", status === "RUNNING");
    automationProgressTrack?.setAttribute("aria-valuenow", String(percentage));
    automationProgressPercent.textContent = `${percentage}%`;
    automationProgressCount.textContent = `${completedSteps} of ${totalSteps} steps completed`;
    automationCurrentStep.textContent = progress.currentStep ? formatEnum(progress.currentStep) : "—";
    automationStartTime.textContent = formatDateTime(progress.startTime);
    automationLastUpdateTime.textContent = formatDateTime(progress.lastUpdateTime);
    automationCompletionTime.textContent = formatDateTime(progress.completionTime);
    runAutomationBtn.disabled = status === "RUNNING";

    const failedSteps = (progress.steps || []).filter(step => step.status === "FAILED");
    if (progress.errorMessage || failedSteps.length > 0) {
        const details = failedSteps.map(step => {
            const reason = step.failureReason || step.summary || "Step failed.";
            return `<li><strong>${escapeHtml(formatEnum(step.step))}:</strong> ${escapeHtml(reason)}</li>`;
        }).join("");
        automationProgressError.innerHTML = `
            <strong>Automation error</strong>
            ${progress.errorMessage ? `<p>${escapeHtml(progress.errorMessage)}</p>` : ""}
            ${details ? `<ul>${details}</ul>` : ""}
        `;
        automationProgressError.classList.remove("hidden");
    } else {
        automationProgressError.replaceChildren();
        automationProgressError.classList.add("hidden");
    }
}

function statusLabelForProgress(status) {
    if (status === "RUNNING") return "Running";
    if (status === "COMPLETED") return "Fully Completed";
    if (status === "PARTIAL_SUCCESS") return "Partial Success";
    if (status === "FAILED") return "Failed";
    return "Not Started";
}

// Render Functions
function renderStatus(status) {
    statusPill.textContent = status || "UNKNOWN";
    statusPill.className = `status-pill ${(status || "UNKNOWN").toLowerCase()}`;
}

function renderAlerts(alerts) {
    alertsList.replaceChildren();
    
    if (alerts.length === 0) {
        alertsSection.classList.add("hidden");
        return;
    }
    
    alertsSection.classList.remove("hidden");
    alertsSummary.innerHTML = `<span class="tag critical">${alerts.length} Alerts</span>`;
    
    alerts.forEach((alertText) => {
        const card = document.createElement("div");
        
        let type = "info";
        if (alertText.toLowerCase().includes("failed") || alertText.toLowerCase().includes("quarantined")) {
            type = "critical";
        } else if (alertText.toLowerCase().includes("low") || alertText.toLowerCase().includes("partial")) {
            type = "warn";
        }
        
        card.className = `alert-card ${type}`;
        
        // Parse alert title and body
        let title = "System Alert";
        let body = alertText;
        
        const isPartial = alertText.includes("failed/partial for");
        const isFailed = alertText.includes("failed for");

        if (isPartial) {
            title = "Pipeline Warning";
        } else if (isFailed) {
            title = "Pipeline Failure";
        } else if (alertText.includes("quarantined")) {
            title = "Source Quarantined";
        } else if (alertText.includes("too little imported history") || alertText.includes("low scheduled fixture")) {
            title = "Low Coverage";
        }

        if ((isFailed || isPartial) && alertText.includes(": ")) {
            const parts = alertText.split(": ");
            const prefix = parts[0];
            const reason = parts.slice(1).join(": ");
            
            const matchString = isPartial ? " run failed/partial for " : " run failed for ";
            const forIndex = prefix.indexOf(matchString);
            
            if (forIndex !== -1) {
                const stage = prefix.substring(0, forIndex).trim();
                const leaguesStr = prefix.substring(forIndex + matchString.length);
                const leagues = leaguesStr.split(",");
                if (leagues.length > 3) {
                    const truncated = `${leagues[0]}, ${leagues[1]}, ${leagues[2]} and ${leagues.length - 3} more`;
                    body = `${stage}${matchString}${truncated}: ${reason}`;
                }
            }
        }
        
        card.innerHTML = `<h3>${title}</h3><p>${body}</p>`;
        
        // Expand relevant section when alert clicked
        card.addEventListener('click', () => {
            if (title.includes("Pipeline")) {
                document.getElementById('runsSection').classList.remove('collapsed');
                document.querySelector('#runsSection .section-header').setAttribute('aria-expanded', 'true');
                localStorage.setItem(`betai.collapse.runsSection`, 'false');
                document.getElementById('runsSection').scrollIntoView({ behavior: 'smooth' });
            } else if (title.includes("Coverage")) {
                document.getElementById('leaguesSection').classList.remove('collapsed');
                document.querySelector('#leaguesSection .section-header').setAttribute('aria-expanded', 'true');
                localStorage.setItem(`betai.collapse.leaguesSection`, 'false');
                document.getElementById('leaguesSection').scrollIntoView({ behavior: 'smooth' });
            } else if (title.includes("Source")) {
                document.getElementById('sourcesSection').classList.remove('collapsed');
                document.querySelector('#sourcesSection .section-header').setAttribute('aria-expanded', 'true');
                localStorage.setItem(`betai.collapse.sourcesSection`, 'false');
                document.getElementById('sourcesSection').scrollIntoView({ behavior: 'smooth' });
            }
        });
        
        alertsList.appendChild(card);
    });
}

function renderTotals(totals) {
    totalsGrid.replaceChildren();
    [
        ["Active Leagues", totals.activeLeagues],
        ["Active Sources", totals.activeSourceTargets],
        ["Matches", totals.matches],
        ["Predictions", totals.predictionSelections],
        ["Failures", totals.failedAutomationRuns]
    ].forEach(([label, value]) => {
        const metric = document.createElement("div");
        metric.className = "metric";
        metric.innerHTML = `<span>${label}</span><strong>${number(value)}</strong>`;
        totalsGrid.appendChild(metric);
    });
}

function renderDataStatus(overview) {
    if (!dataStatusGrid) return;
    dataStatusGrid.replaceChildren();

    const totals = overview?.totals || {};
    const leagues = overview?.leagues || [];
    const runs = overview?.recentRuns || [];
    const completeLeagues = leagues.filter(l => ["OK", "COMPLETE"].includes(l.historyStatus || l.dataCoverageStatus)).length;
    const attentionLeagues = leagues.filter(l => {
        const status = l.historyStatus || l.dataCoverageStatus || "";
        return status.includes("LOW") || ["PARTIAL", "FAILED", "PENDING"].includes(status);
    }).length;
    const latestRun = runs.find(r => r.stage === "PIPELINE")
        || runs.find(r => String(r.stage || "").includes("THESPORTSDB"))
        || runs[0];

    if (dataStatusSummary) {
        dataStatusSummary.innerHTML = `
            <span class="tag ${overview?.status === "OK" ? "success" : "warn"}">${overview?.status || "UNKNOWN"}</span>
            <span class="tag">${number(leagues.length)} leagues</span>
        `;
    }

    [
        ["Football data", `${number(completeLeagues)}/${number(leagues.length)} complete`, attentionLeagues > 0 ? `${number(attentionLeagues)} leagues need attention` : "League coverage OK"],
        ["Latest refresh", latestRun?.status || "NEVER_RUN", latestRun ? `${latestRun.stage} • ${formatDateTime(latestRun.finishedAt || latestRun.startedAt)}` : "No admin run recorded"],
        ["Scores", `${number(totals.finishedMatches)} finished`, `${number(totals.scheduledMatches)} scheduled`],
        ["Odds", `${number(totals.oddsSnapshots)} snapshots`, `${number(totals.pricedSelections)} priced selections`],
        ["Predictions", `${number(totals.predictionSelections)} selections`, `${number(totals.positiveValueSelections)} positive edge`],
        ["Automation", `${number(totals.automationRuns)} runs`, `${number(totals.failedAutomationRuns)} failed`]
    ].forEach(([label, value, note]) => {
        const metric = document.createElement("div");
        metric.className = "metric";

        const labelEl = document.createElement("span");
        labelEl.textContent = label;
        const valueEl = document.createElement("strong");
        valueEl.textContent = value;
        const noteEl = document.createElement("small");
        noteEl.textContent = note;

        metric.append(labelEl, valueEl, noteEl);
        dataStatusGrid.appendChild(metric);
    });
}

// ----------------- LEAGUES -----------------
const lgSearch = document.getElementById("leagueSearch");
const lgCoverage = document.getElementById("leagueCoverageFilter");

[lgSearch, lgCoverage].forEach(el => el.addEventListener("input", updateLeagueFilters));
document.getElementById("leagueFilterResetBtn").addEventListener("click", () => {
    lgSearch.value = ""; lgCoverage.value = "ALL"; updateLeagueFilters();
});

function updateLeagueFilters() {
    const s = lgSearch.value.toLowerCase();
    const c = lgCoverage.value;
    
    let filtered = dashboardData.leagues.filter(l => {
        if (c !== "ALL") {
            if (c === "OK" && !["OK", "COMPLETE"].includes(l.dataCoverageStatus)) return false;
            if (c !== "OK" && l.dataCoverageStatus !== c) return false;
        }
        if (s && !l.name.toLowerCase().includes(s) && !l.leagueCode.toLowerCase().includes(s)) return false;
        return true;
    });
    
    // Update summary
    const complete = dashboardData.leagues.filter(l => ["OK", "COMPLETE"].includes(l.dataCoverageStatus)).length;
    const warning = dashboardData.leagues.filter(l => l.dataCoverageStatus && l.dataCoverageStatus.includes("LOW")).length;
    
    document.getElementById("leagueSummary").innerHTML = `
        <span class="tag success">${complete} Complete</span>
        ${warning > 0 ? `<span class="tag warn">${warning} Warning</span>` : ''}
        <span class="tag">${dashboardData.leagues.length} Total</span>
    `;
    
    renderLeagues(filtered);
}

function renderLeagues(leagues) {
    leagueRows.replaceChildren();
    if (leagues.length === 0) {
        document.getElementById("leagueEmptyState").classList.remove("hidden");
    } else {
        document.getElementById("leagueEmptyState").classList.add("hidden");
        leagues.forEach(l => {
            const row = document.createElement("tr");
            row.style.cursor = "pointer";
            row.innerHTML = `
                <td><strong>${l.leagueCode}</strong></td>
                <td>${l.historyPolicy || "-"}</td>
                <td>${number(l.importedSeasonCount)}/${number(l.requiredSeasonCount)}</td>
                <td>${number(l.matches)}</td>
                <td>${number(l.scheduledMatches)}</td>
                <td>${number(l.finishedMatches)}</td>
                <td><span class="tag ${coverageStatusClass(l.historyStatus || l.dataCoverageStatus)}">${l.historyStatus || l.dataCoverageStatus}</span></td>
                <td><span class="tag ${String(l.latestRefreshStatus || '').toLowerCase()}">${l.latestRefreshStatus || '-'}</span></td>
                <td><button class="action-btn">View</button></td>
            `;
            row.addEventListener("click", () => showLeagueDetails(l));
            leagueRows.appendChild(row);
        });
    }
}

function showLeagueDetails(l) {
    const html = `
        <div class="detail-row"><span class="detail-label">Name</span><span class="detail-value">${l.name} (${l.leagueCode})</span></div>
        <div class="detail-row"><span class="detail-label">Current Season</span><span class="detail-value">${l.currentSeason}</span></div>
        <div class="detail-row"><span class="detail-label">Teams</span><span class="detail-value">${number(l.teams)}</span></div>
        <div class="detail-row"><span class="detail-label">Sources</span><span class="detail-value">${l.activeSourceTargets} Active / ${l.sourceTargets} Total</span></div>
        <div class="detail-row"><span class="detail-label">Coverage Status</span><span class="detail-value"><span class="tag ${coverageStatusClass(l.historyStatus || l.dataCoverageStatus)}">${l.historyStatus || l.dataCoverageStatus}</span> ${l.dataCoverageMessage || ''}</span></div>
        <div class="detail-row"><span class="detail-label">Latest Refresh Time</span><span class="detail-value">${formatDateTime(l.latestRefreshAt)}</span></div>
        <div class="detail-row"><span class="detail-label">Seasons Breakdown</span><span class="detail-value">${seasonSummaryHtml(l.seasonBreakdowns || [])}</span></div>
    `;
    openDrawer(`League: ${l.leagueCode}`, html);
}

function seasonSummaryHtml(seasons) {
    if (!seasons.length) return "-";
    return seasons.map(s => `<div><strong>${s.seasonLabel}</strong>: ${number(s.matches)} matches (${number(s.finishedMatches)} Fin / ${number(s.scheduledMatches)} Sch)</div>`).join("");
}

// ----------------- SOURCES -----------------
const srcSearch = document.getElementById("sourceSearch");
const srcActive = document.getElementById("sourceActiveFilter");
const srcProv = document.getElementById("sourceProviderFilter");
const srcHealth = document.getElementById("sourceHealthFilter");

[srcSearch, srcActive, srcProv, srcHealth].forEach(el => el.addEventListener("input", updateSourceFilters));
document.getElementById("sourceFilterResetBtn").addEventListener("click", () => {
    srcSearch.value = ""; srcActive.value = "ACTIVE_ONLY"; srcProv.value = "ALL"; srcHealth.value = "ALL"; updateSourceFilters();
});

function updateSourceFilters() {
    const s = srcSearch.value.toLowerCase();
    const a = srcActive.value;
    const p = srcProv.value;
    const h = srcHealth.value;
    
    let filtered = dashboardData.sources.filter(src => {
        if (a === "ACTIVE_ONLY" && !src.active) return false;
        if (p !== "ALL" && src.sourceType !== p) return false;
        const status = sourceHealthStatus(src);
        if (h !== "ALL" && status !== h) return false;
        if (s && !src.name.toLowerCase().includes(s) && !src.leagueCode.toLowerCase().includes(s)) return false;
        return true;
    });
    
    // Sort active sharp API and sports db first, inactive last
    filtered.sort((x, y) => (y.active === x.active) ? 0 : x.active ? -1 : 1);
    
    // Update summary
    const activeCount = dashboardData.sources.filter(src => src.active).length;
    const quarCount = dashboardData.sources.filter(src => sourceHealthStatus(src) === "QUARANTINED").length;
    
    document.getElementById("sourceSummary").innerHTML = `
        <span class="tag success">${activeCount} Active</span>
        ${quarCount > 0 ? `<span class="tag critical">${quarCount} Quarantined</span>` : ''}
    `;
    
    renderSources(filtered);
}

function renderSources(sources) {
    sourceRows.replaceChildren();
    if (sources.length === 0) {
        document.getElementById("sourceEmptyState").classList.remove("hidden");
    } else {
        document.getElementById("sourceEmptyState").classList.add("hidden");
        sources.forEach(src => {
            const status = sourceHealthStatus(src);
            const row = document.createElement("tr");
            row.style.cursor = "pointer";
            row.innerHTML = `
                <td><strong>${src.sourceType}</strong></td>
                <td>${src.leagueCode}</td>
                <td>${src.active ? '<span class="tag success">Active</span>' : '<span class="tag inactive">Historical</span>'}</td>
                <td><span class="tag ${sourceHealthClass(src)}">${status}</span></td>
                <td>${src.reliabilityScore || 0}</td>
                <td>${src.consecutiveFailures || 0}</td>
                <td>${formatDateTime(src.lastSuccessAt || src.lastFailureAt)}</td>
                <td><button class="action-btn">View</button></td>
            `;
            row.addEventListener("click", () => showSourceDetails(src, status));
            sourceRows.appendChild(row);
        });
    }
}

function showSourceDetails(src, status) {
    const html = `
        <div class="detail-row"><span class="detail-label">Source Name</span><span class="detail-value">${src.name}</span></div>
        <div class="detail-row"><span class="detail-label">Provider</span><span class="detail-value">${src.sourceType}</span></div>
        <div class="detail-row"><span class="detail-label">League</span><span class="detail-value">${src.leagueCode}</span></div>
        <div class="detail-row"><span class="detail-label">Status</span><span class="detail-value"><span class="tag ${sourceHealthClass(src)}">${status}</span> ${src.active ? '(Active)' : '(Historical)'}</span></div>
        <div class="detail-row"><span class="detail-label">Failures & Reliability</span><span class="detail-value">${src.consecutiveFailures} consecutive failures, Reliability Score: ${src.reliabilityScore}</span></div>
        <div class="detail-row"><span class="detail-label">Last Success</span><span class="detail-value">${formatDateTime(src.lastSuccessAt)}</span></div>
        <div class="detail-row"><span class="detail-label">Last Failure</span><span class="detail-value">${formatDateTime(src.lastFailureAt)}</span></div>
        ${src.quarantinedUntil ? `<div class="detail-row"><span class="detail-label">Quarantined Until</span><span class="detail-value">${formatDateTime(src.quarantinedUntil)}</span></div>` : ''}
    `;
    openDrawer(`Source: ${src.sourceType}`, html);
}

function sourceHealthStatus(source) {
    if (source.systemDisabled) return "DISABLED";
    if (source.quarantinedUntil && new Date(source.quarantinedUntil).getTime() > Date.now()) return "QUARANTINED";
    if (source.consecutiveFailures >= 3) return "DEGRADED";
    return "OK";
}

function sourceHealthClass(source) {
    const status = sourceHealthStatus(source);
    if (status === "OK") return "success";
    if (status === "DISABLED" || status === "QUARANTINED") return "failed";
    return "degraded";
}

// ----------------- RUNS -----------------
const runSearch = document.getElementById("runLeagueSearch");
const runStatus = document.getElementById("runStatusFilter");
const runStage = document.getElementById("runStageFilter");

[runSearch, runStatus, runStage].forEach(el => el.addEventListener("input", () => { currentRunPage = 1; updateRunFilters(); }));
document.getElementById("runsFilterResetBtn").addEventListener("click", () => {
    runSearch.value = ""; runStatus.value = "ALL"; runStage.value = "ALL"; currentRunPage = 1; updateRunFilters();
});

function updateRunFilters() {
    // Populate dynamic stages if empty
    if (runStage.options.length === 1 && dashboardData.runs.length > 0) {
        const stages = [...new Set(dashboardData.runs.map(r => r.stage))];
        stages.forEach(st => {
            const opt = document.createElement("option");
            opt.value = st; opt.textContent = st;
            runStage.appendChild(opt);
        });
    }

    const s = runSearch.value.toLowerCase();
    const stt = runStatus.value;
    const stg = runStage.value;
    
    let filtered = dashboardData.runs.filter(r => {
        if (stt !== "ALL" && r.status !== stt) return false;
        if (stg !== "ALL" && r.stage !== stg) return false;
        if (s && !r.leagueCode.toLowerCase().includes(s)) return false;
        return true;
    });
    
    // Update summary
    const failures = dashboardData.runs.filter(r => r.status === "FAILED").length;
    const partials = dashboardData.runs.filter(r => r.status === "PARTIAL_SUCCESS").length;
    
    document.getElementById("runsSummary").innerHTML = `
        ${failures > 0 ? `<span class="tag critical">${failures} Failed</span>` : ''}
        ${partials > 0 ? `<span class="tag warn">${partials} Partial</span>` : ''}
        <span class="tag">${dashboardData.runs.length} Total</span>
    `;
    
    renderRuns(filtered);
}

function renderRuns(filteredRuns) {
    const totalPages = Math.ceil(filteredRuns.length / RUNS_PER_PAGE) || 1;
    if (currentRunPage > totalPages) currentRunPage = totalPages;
    
    const startIndex = (currentRunPage - 1) * RUNS_PER_PAGE;
    const paginated = filteredRuns.slice(startIndex, startIndex + RUNS_PER_PAGE);
    
    runRows.replaceChildren();
    
    if (paginated.length === 0) {
        document.getElementById("runsEmptyState").classList.remove("hidden");
        document.getElementById("runsPagination").classList.add("hidden");
    } else {
        document.getElementById("runsEmptyState").classList.add("hidden");
        document.getElementById("runsPagination").classList.remove("hidden");
        
        paginated.forEach(r => {
            const row = document.createElement("tr");
            row.style.cursor = "pointer";
            row.innerHTML = `
                <td><span class="tag ${String(r.status).toLowerCase()}">${r.status}</span></td>
                <td><strong>${r.stage}</strong></td>
                <td>${r.leagueCode}</td>
                <td>${formatDateTime(r.startedAt)}</td>
                <td>${formatDuration(r.durationMs)}</td>
                <td>${r.attempts || 1}</td>
                <td><button class="action-btn">View</button></td>
            `;
            row.addEventListener("click", () => showRunDetails(r));
            runRows.appendChild(row);
        });
        
        document.getElementById("runsPageInfo").textContent = `Page ${currentRunPage} of ${totalPages}`;
        document.getElementById("runsPrevBtn").disabled = currentRunPage === 1;
        document.getElementById("runsNextBtn").disabled = currentRunPage === totalPages;
        
        document.getElementById("runsPrevBtn").onclick = () => { if (currentRunPage > 1) { currentRunPage--; renderRuns(filteredRuns); } };
        document.getElementById("runsNextBtn").onclick = () => { if (currentRunPage < totalPages) { currentRunPage++; renderRuns(filteredRuns); } };
    }
}

function showRunDetails(r) {
    let html = `
        <div class="detail-row"><span class="detail-label">Run ID</span><span class="detail-value" style="font-family: monospace; font-size: 12px;">${r.runId}</span></div>
        <div class="detail-row"><span class="detail-label">Stage & League</span><span class="detail-value">${r.stage} - ${r.leagueCode}</span></div>
        <div class="detail-row"><span class="detail-label">Status</span><span class="detail-value"><span class="tag ${String(r.status).toLowerCase()}">${r.status}</span></span></div>
        <div class="detail-row"><span class="detail-label">Timeline</span><span class="detail-value">Started: ${formatDateTime(r.startedAt)}<br>Finished: ${formatDateTime(r.finishedAt)}<br>Duration: ${formatDuration(r.durationMs)}</span></div>
        <div class="detail-row"><span class="detail-label">Attempts</span><span class="detail-value">${r.attempts || 1}</span></div>
        <div class="detail-row"><span class="detail-label">Summary</span><span class="detail-value">${r.summary || 'None'}</span></div>
    `;
    
    if (r.failureReason) {
        html += `<div class="detail-row"><span class="detail-label">Failure Reason</span><span class="detail-value" style="color: var(--bad);">${r.failureReason}</span></div>`;
    }
    
    openDrawer(`Run Details`, html);
}

// Utils
function coverageStatusClass(status) {
    switch (status) {
        case "OK": case "COMPLETE": return "success";
        case "LOW_HISTORY": case "LOW_FIXTURE_COVERAGE": case "LOW_HISTORY_AND_FIXTURES": case "PARTIAL": return "warn";
        case "NO_ACTIVE_SOURCES": case "FAILED": return "critical";
        case "INACTIVE": case "NOT_SCRAPING": case "PENDING": return "inactive";
        default: return "";
    }
}

function formatEnum(value) {
    if (!value) return "—";
    return String(value)
        .toLowerCase()
        .split("_")
        .filter(Boolean)
        .map(word => word.charAt(0).toUpperCase() + word.slice(1))
        .join(" ");
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function formatDateTime(value) {
    if (!value) return "-";
    return new Intl.DateTimeFormat(undefined, {
        month: "short", day: "2-digit", hour: "2-digit", minute: "2-digit"
    }).format(new Date(value));
}

function formatDuration(value) {
    if (value == null) return "-";
    if (value < 1000) return `${value} ms`;
    return `${(value / 1000).toFixed(2)} s`;
}

function number(value) {
    return new Intl.NumberFormat().format(value || 0);
}

function showToast(message) {
    toast.textContent = message;
    toast.classList.remove("hidden");
    window.clearTimeout(showToast.timeoutId);
    showToast.timeoutId = window.setTimeout(() => toast.classList.add("hidden"), 4500);
}
