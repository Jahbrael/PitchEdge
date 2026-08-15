document.addEventListener('DOMContentLoaded', () => {
    const P = window.PitchEdge;
    const stats = document.getElementById('dashboardStats');
    const bestPicks = document.getElementById('bestPicksList');
    const fixtures = document.getElementById('fixturePreviewList');
    const runs = document.getElementById('recentRunsList');

    loadHome();
    setInterval(loadHome, 180000);

    async function loadHome() {
        try {
            const today = P.isoDate(new Date());
            const data = await P.apiJson(`/api/v1/platform/dashboard?date=${encodeURIComponent(today)}`);
            const dateEl = document.getElementById('homeDate');
            if (dateEl) {
                dateEl.textContent = new Date(`${data.date}T00:00:00`).toLocaleDateString(undefined, {
                    weekday: 'long',
                    month: 'long',
                    day: 'numeric',
                    year: 'numeric'
                });
            }
            renderStats(data.metrics || {});
            renderPredictionList(bestPicks, data.bestPicks, 'No qualified picks available yet.');
            renderFixtures(data.fixtures || []);
            renderRuns(data.recentRuns || []);
        } catch (error) {
            if (stats) stats.innerHTML = P.emptyState('Today’s overview unavailable', safeMessage(error));
            if (bestPicks) bestPicks.innerHTML = '';
            if (fixtures) fixtures.innerHTML = '';
            if (runs) runs.innerHTML = '';
        }
    }

    function renderStats(metrics) {
        if (!stats) return;
        const rows = [
            ['Fixtures Today', metrics.totalFixturesToday ?? 0, 'Matches on today’s slate'],
            ['Live Fixtures', metrics.liveFixtures ?? 0, 'Currently in play'],
            ['High Confidence', metrics.highConfidencePicks ?? 0, 'Very high confidence picks'],
            ['Value Picks', metrics.valuePicks ?? 0, 'Model probability beats implied']
        ];
        stats.innerHTML = rows.map(([label, value, note]) => `
            <article class="pe-card pe-stat">
                <span>${P.escapeHtml(label)}</span>
                <strong>${P.escapeHtml(value)}</strong>
                <small>${P.escapeHtml(note)}</small>
            </article>
        `).join('');
    }

    function renderPredictionList(container, selections, emptyText) {
        if (!container) return;
        if (!selections || selections.length === 0) {
            container.innerHTML = P.emptyState('Nothing to show yet', emptyText);
            return;
        }
        container.innerHTML = selections.slice(0, 5).map(selection => predictionCard(selection)).join('');
    }

    function predictionCard(selection) {
        const probability = P.selectionProbability(selection);
        const edge = selection.probabilityEdge ?? selection.valueEdge;
        const odds = selection.decimalOdds ?? selection.bestDecimalOdds;
        const confidence = selection.confidenceBand || 'UNRATED';
        return `
            <article class="pe-prediction-card compact">
                <div class="pe-meta-line">
                    <span class="pe-league-tag">${P.escapeHtml(P.label(selection.leagueCode))}</span>
                    <span class="pe-time-tag">${P.escapeHtml(P.dateTime(selection.kickoffAt))}</span>
                </div>
                <h3 class="pe-card-title">${P.escapeHtml(selection.fixture || selection.match || 'Fixture')}</h3>
                <div class="pe-badge-row">
                    <span class="pe-badge accent">${P.escapeHtml(P.label(selection.marketCode || selection.marketName))}</span>
                    <span class="pe-badge ${P.confidenceClass(confidence)}">${P.escapeHtml(P.label(confidence))}</span>
                    <span class="pe-badge ${edge > 0 ? 'value' : ''}">${P.escapeHtml(edge > 0 ? `Edge ${P.signedPercent(edge)}` : 'No edge')}</span>
                </div>
                <div class="pe-compact-metrics">
                    <div class="pe-metric-item">
                        <span>Pick</span>
                        <strong>${P.escapeHtml(selection.teamOrPlayer || P.label(selection.predictedValue))}</strong>
                    </div>
                    <div class="pe-metric-item">
                        <span>Model probability</span>
                        <strong>${P.escapeHtml(P.percent(probability))}</strong>
                    </div>
                    <div class="pe-metric-item">
                        <span>Odds</span>
                        <strong>${P.escapeHtml(odds ? P.decimal(odds) : 'No odds')}</strong>
                    </div>
                </div>
                <div class="pe-card-actions">
                    <a class="pe-btn secondary compact" href="${P.detailsUrl(selection, null, selection.modelVersion)}">View Details</a>
                </div>
            </article>`;
    }

    function renderFixtures(items) {
        if (!fixtures) return;
        const getPriority = (status) => {
            const s = String(status || '').toUpperCase();
            if (['LIVE', 'IN_PLAY', 'HALF_TIME', 'HT', 'PAUSED'].includes(s)) return 1;
            if (['SCHEDULED', 'TIME_TBC'].includes(s)) return 2;
            return 3;
        };
        const display = [...items].sort((a, b) => {
            const pA = getPriority(a.status);
            const pB = getPriority(b.status);
            if (pA !== pB) return pA - pB;
            return new Date(a.kickoffTime || 0) - new Date(b.kickoffTime || 0);
        }).slice(0, 8);
        if (display.length === 0) {
            fixtures.innerHTML = P.emptyState('No matches to watch right now.', 'No fixtures are currently in play or scheduled for today.');
            return;
        }
        fixtures.innerHTML = display.map(fixture => {
            const score = P.formatScore(fixture);
            return `
                <article class="pe-fixture-card compact">
                    <div class="pe-meta-line">
                        <span>${P.escapeHtml(fixture.leagueName || P.label(fixture.leagueCode))}</span>
                        <span>${P.escapeHtml(P.dateTime(fixture.kickoffTime))}</span>
                    </div>
                    <div class="pe-fixture-teams">
                        <div class="pe-team-block">
                            ${P.teamMark(fixture.homeTeam, fixture.homeTeamBadgeUrl || fixture.homeTeamLogoUrl || fixture.homeBadgeUrl)}
                            <span class="pe-team-name">${P.escapeHtml(fixture.homeTeam)}</span>
                        </div>
                        <div class="pe-score-pill">${P.escapeHtml(score)}</div>
                        <div class="pe-team-block away">
                            <span class="pe-team-name">${P.escapeHtml(fixture.awayTeam)}</span>
                            ${P.teamMark(fixture.awayTeam, fixture.awayTeamBadgeUrl || fixture.awayTeamLogoUrl || fixture.awayBadgeUrl)}
                        </div>
                    </div>
                    <div class="pe-meta-line" style="margin-top:10px">
                        <span class="pe-badge ${fixture.status === 'LIVE' || fixture.status === 'IN_PLAY' ? 'bad' : 'accent'}">${P.escapeHtml(P.formatLiveBadge(fixture.status, fixture.liveMinute))}</span>
                        <span class="pe-badge ${fixture.hasPredictions ? 'good' : ''}">${P.escapeHtml(fixture.predictionStatus || (fixture.hasPredictions ? 'Prediction ready' : 'Unrated'))}</span>
                        <span class="pe-badge ${fixture.hasOdds ? 'accent' : ''}">${P.escapeHtml(fixture.oddsProvider || (fixture.hasOdds ? 'Odds available' : 'No odds'))}</span>
                    </div>
                    <div class="pe-card-actions" style="margin-top:10px">
                        <a class="pe-btn secondary compact" href="/fixture-details.html?matchId=${encodeURIComponent(fixture.matchId)}&modelVersion=v1">Prediction</a>
                        <a class="pe-btn secondary compact" href="/fixture-details.html?matchId=${encodeURIComponent(fixture.matchId)}&modelVersion=v1">Match Stats</a>
                    </div>
                </article>`;
        }).join('');
    }

    function renderRuns(items) {
        if (!runs) return;
        if (!items || !items.length) {
            runs.innerHTML = P.emptyState('No recent prediction runs yet.', 'Generate predictions to create a run that can be reopened here.');
            return;
        }
        runs.innerHTML = items.slice(0, 5).map(run => `
            <article class="pe-run-card compact-row">
                <div class="pe-run-main">
                    <div class="pe-meta-line">
                        <span>${P.escapeHtml(P.dateTime(run.generatedAt))}</span>
                        <span class="pe-badge accent">${P.escapeHtml(P.label(run.input?.strategy || 'BALANCED'))}</span>
                    </div>
                    <h4>${P.escapeHtml(run.returnedSelections || run.selectionsReturned || 0)} picks</h4>
                    <p class="pe-run-dates">Fixtures: ${P.escapeHtml(run.input?.fixtureDateFrom || '--')} to ${P.escapeHtml(run.input?.fixtureDateTo || '--')}</p>
                </div>
                <div class="pe-run-action">
                    <a class="pe-btn secondary compact" href="/prediction-results.html?runId=${encodeURIComponent(run.requestId)}">Open Run</a>
                </div>
            </article>
        `).join('');
    }

    function safeMessage(error) {
        return error?.message && error.message.length < 180 ? error.message : 'The home page request failed. Try reloading the page.';
    }
});
