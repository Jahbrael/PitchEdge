document.addEventListener('DOMContentLoaded', () => {
    const P = window.PitchEdge;
    const dateInput = document.getElementById('fixtureDate');
    const searchInput = document.getElementById('teamSearch');
    const leagueFilter = document.getElementById('leagueFilter');
    const statusFilter = document.getElementById('statusFilter');
    const groupFilter = document.getElementById('groupFilter');
    const list = document.getElementById('fixtureList');
    const summary = document.getElementById('scoreRefreshSummary');
    let fixtures = [];
    let selectedChip = '';

    dateInput.value = P.isoDate(new Date());
    document.querySelectorAll('[data-day-offset]').forEach(button => {
        button.addEventListener('click', () => {
            const date = new Date();
            date.setDate(date.getDate() + Number(button.dataset.dayOffset || 0));
            dateInput.value = P.isoDate(date);
            loadFixtures();
        });
    });
    document.querySelectorAll('#fixtureChips .pe-chip').forEach(chip => {
        chip.addEventListener('click', () => {
            document.querySelectorAll('#fixtureChips .pe-chip').forEach(item => item.classList.remove('active'));
            chip.classList.add('active');
            selectedChip = chip.dataset.market || '';
            renderFixtures();
        });
    });
    [dateInput, leagueFilter, statusFilter, groupFilter].forEach(input => input.addEventListener('change', loadOrRender));
    searchInput.addEventListener('input', renderFixtures);
    loadFixtures();
    setInterval(loadFixtures, 180000);

    function loadOrRender(event) {
        if (event.target === dateInput) loadFixtures();
        else renderFixtures();
    }

    async function loadFixtures() {
        list.innerHTML = P.loadingState(4);
        try {
            fixtures = await P.apiJson(`/api/v1/platform/fixtures?date=${encodeURIComponent(dateInput.value)}`);
            hydrateLeagueFilter();
            renderScoreSyncSummary();
            renderFixtures();
        } catch (error) {
            list.innerHTML = P.emptyState('Could not load fixtures', safeMessage(error));
        }
    }

    function renderScoreSyncSummary() {
        if (!summary) return;
        const latest = fixtures
            .map(fixture => fixture.lastRefreshedTime ? new Date(fixture.lastRefreshedTime) : null)
            .filter(date => date && !Number.isNaN(date.getTime()))
            .sort((a, b) => b - a)[0];
        if (!latest) {
            summary.textContent = 'Live score sync pending. Updates run automatically every 3 minutes.';
            summary.className = 'pe-badge accent pe-badge-wrap';
            return;
        }
        summary.textContent = `Live score sync: scores last checked ${P.dateTime(latest.toISOString())}. Auto-updates every 3 minutes.`;
        summary.className = 'pe-badge good pe-badge-wrap';
    }

    function hydrateLeagueFilter() {
        const current = leagueFilter.value;
        const leagues = [...new Set(fixtures.map(f => f.leagueCode).filter(Boolean))].sort();
        leagueFilter.innerHTML = '<option value="">All leagues</option>';
        leagues.forEach(code => {
            const option = document.createElement('option');
            option.value = code;
            option.textContent = P.label(code);
            leagueFilter.appendChild(option);
        });
        if (leagues.includes(current)) leagueFilter.value = current;
    }

    function renderFixtures() {
        const search = searchInput.value.trim().toLowerCase();
        const league = leagueFilter.value;
        const status = statusFilter.value;
        let filtered = fixtures.filter(fixture => {
            if (search && !(fixture.homeTeam || '').toLowerCase().includes(search) && !(fixture.awayTeam || '').toLowerCase().includes(search)) return false;
            if (league && fixture.leagueCode !== league) return false;
            if (selectedChip === 'hasPredictions' && !fixture.hasPredictions) return false;
            if (selectedChip === 'hasOdds' && !fixture.hasOdds) return false;
            if (selectedChip === 'live' && !isLive(fixture)) return false;
            if (status === 'PRED_READY' && !fixture.hasPredictions) return false;
            if (status === 'NO_PRED' && fixture.hasPredictions) return false;
            if (status === 'ODDS_AVAIL' && !fixture.hasOdds) return false;
            if (status === 'LIVE' && !isLive(fixture)) return false;
            if (status === 'FINISHED' && fixture.status !== 'FINISHED') return false;
            if (status === 'SCHEDULED' && fixture.status !== 'SCHEDULED') return false;
            return true;
        });
        filtered.sort((a, b) => new Date(a.kickoffTime || 0) - new Date(b.kickoffTime || 0));

        if (!filtered.length) {
            list.innerHTML = P.emptyState('No fixtures found', 'No fixtures match the selected date and filters.');
            return;
        }

        const groupMode = groupFilter.value;
        if (groupMode === 'NONE') {
            list.innerHTML = `<div class="pe-grid-3">${filtered.map(fixtureCard).join('')}</div>`;
            return;
        }

        const groups = new Map();
        filtered.forEach(fixture => {
            const key = groupMode === 'TIME'
                ? P.dateTime(fixture.kickoffTime)
                : (fixture.leagueName || P.label(fixture.leagueCode));
            if (!groups.has(key)) groups.set(key, []);
            groups.get(key).push(fixture);
        });
        list.innerHTML = [...groups.entries()].map(([groupName, items]) => `
            <section class="pe-card">
                <div class="pe-section-header"><h2>${P.escapeHtml(groupName)}</h2><span class="pe-badge">${items.length} fixtures</span></div>
                <div class="pe-grid-3">${items.map(fixtureCard).join('')}</div>
            </section>
        `).join('');
    }

    function fixtureCard(fixture) {
        const score = P.formatScore(fixture);
        const details = fixture.hasPredictions
            ? `/fixture-details.html?matchId=${encodeURIComponent(fixture.matchId)}&modelVersion=v1`
            : `/fixture-details.html?matchId=${encodeURIComponent(fixture.matchId)}&modelVersion=v1`;
        return `
            <article class="pe-fixture-card">
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
                <div class="pe-meta-line" style="margin-top:12px">
                    <span class="pe-badge ${isLive(fixture) ? 'bad' : 'accent'}">${P.escapeHtml(statusLabel(fixture))}</span>
                    <span class="pe-badge ${fixture.hasPredictions ? 'good' : ''}">${P.escapeHtml(fixture.predictionStatus)}</span>
                    <span class="pe-badge ${fixture.hasOdds ? 'accent' : ''}">${P.escapeHtml(fixture.oddsProvider)}</span>
                </div>
                <div class="pe-fixture-insight">${P.escapeHtml(fixture.hasPredictions ? 'Prediction ready. Open details for the qualified market view.' : 'No prediction is available yet for this fixture.')}</div>
                <div class="pe-card-actions">
                    <a class="pe-btn secondary compact" href="${details}">Prediction</a>
                    <a class="pe-btn secondary compact" href="${details}#stats">Match Stats</a>
                </div>
            </article>`;
    }

    function isLive(fixture) {
        return ['LIVE', 'IN_PLAY', 'HALF_TIME'].includes(fixture.status);
    }

    function statusLabel(fixture) {
        return P.formatLiveBadge(fixture.status, fixture.liveMinute);
    }

    function safeMessage(error) {
        return error?.message && error.message.length < 180 ? error.message : 'The fixture request failed.';
    }
});
