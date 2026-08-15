document.addEventListener('DOMContentLoaded', () => {
    const P = window.PitchEdge;
    const fromDate = document.getElementById('fromDate');
    const toDate = document.getElementById('toDate');
    const controls = ['searchInput', 'leagueFilter', 'marketFilter', 'confidenceFilter', 'minProbability', 'maxProbability', 'booleanFilter']
        .map(id => document.getElementById(id));
    const leagueFilter = document.getElementById('leagueFilter');
    const marketFilter = document.getElementById('marketFilter');
    const results = document.getElementById('machineResults');
    const resultCount = document.getElementById('resultCount');
    let predictions = [];

    const today = new Date();
    const to = new Date();
    to.setDate(today.getDate() + 14);
    fromDate.value = P.isoDate(today);
    toDate.value = P.isoDate(to);
    [fromDate, toDate].forEach(input => input.addEventListener('change', loadPredictions));
    controls.forEach(input => input.addEventListener(input.type === 'search' || input.type === 'number' ? 'input' : 'change', render));
    loadPredictions();

    let urlParamsApplied = false;
    function applyUrlParams() {
        if (urlParamsApplied) return;
        urlParamsApplied = true;
        const params = new URLSearchParams(window.location.search);
        if (params.get('league')) leagueFilter.value = params.get('league');
        if (params.get('market')) marketFilter.value = params.get('market');
        if (params.get('confidence')) document.getElementById('confidenceFilter').value = params.get('confidence');
        if (params.get('min')) document.getElementById('minProbability').value = params.get('min');
        if (params.get('max')) document.getElementById('maxProbability').value = params.get('max');
        if (params.get('boolean')) document.getElementById('booleanFilter').value = params.get('boolean');
        if (params.get('search')) document.getElementById('searchInput').value = params.get('search');
    }

    async function loadPredictions() {
        results.innerHTML = P.loadingState(6);
        try {
            predictions = await P.apiJson(`/api/v1/platform/predictions?from=${encodeURIComponent(fromDate.value)}&to=${encodeURIComponent(toDate.value)}`);
            hydrateFilters();
            applyUrlParams();
            render();
        } catch (error) {
            results.innerHTML = P.emptyState('Could not load predictions', safeMessage(error));
        }
    }

    function hydrateFilters() {
        const currentLeague = leagueFilter.value;
        const currentMarket = marketFilter.value;
        leagueFilter.innerHTML = '<option value="">All leagues</option>';
        marketFilter.innerHTML = '<option value="">All markets</option>';
        [...new Set(predictions.map(p => p.leagueCode).filter(Boolean))].sort().forEach(code => {
            leagueFilter.insertAdjacentHTML('beforeend', `<option value="${P.escapeHtml(code)}">${P.escapeHtml(P.label(code))}</option>`);
        });
        [...new Set(predictions.map(p => p.marketCode || p.marketName).filter(Boolean))].sort().forEach(code => {
            marketFilter.insertAdjacentHTML('beforeend', `<option value="${P.escapeHtml(code)}">${P.escapeHtml(P.label(code))}</option>`);
        });
        leagueFilter.value = currentLeague;
        marketFilter.value = currentMarket;
    }

    function render() {
        const search = document.getElementById('searchInput').value.trim().toLowerCase();
        const league = leagueFilter.value;
        const market = marketFilter.value;
        const confidence = document.getElementById('confidenceFilter').value;
        const min = Number(document.getElementById('minProbability').value || 0) / 100;
        const maxRaw = document.getElementById('maxProbability').value;
        const max = maxRaw === '' ? 1 : Number(maxRaw) / 100;
        const bool = document.getElementById('booleanFilter').value;
        const filtered = predictions.filter(item => {
            const probability = Number(P.selectionProbability(item) || 0);
            const edge = item.probabilityEdge ?? item.valueEdge;
            const odds = item.decimalOdds ?? item.bestDecimalOdds;
            const text = `${item.fixture || ''} ${item.teamOrPlayer || ''}`.toLowerCase();
            if (search && !text.includes(search)) return false;
            if (league && item.leagueCode !== league) return false;
            if (market && item.marketCode !== market && item.marketName !== market) return false;
            if (confidence && item.confidenceBand !== confidence) return false;
            if (probability < min || probability > max) return false;
            if (bool === 'ODDS' && !odds) return false;
            if (bool === 'EDGE' && !(edge > 0)) return false;
            if (bool === 'HIGH_CONF' && !['HIGH', 'VERY_HIGH'].includes(item.confidenceBand)) return false;
            if (bool === 'LOW_RISK' && probability < 0.7) return false;
            return true;
        }).sort((a, b) => (P.selectionProbability(b) || 0) - (P.selectionProbability(a) || 0));

        resultCount.textContent = `${filtered.length} result${filtered.length === 1 ? '' : 's'}`;
        if (!filtered.length) {
            results.innerHTML = P.emptyState('No predictions match your filters', 'Adjust the Machine filters or generate new predictions for this date range.');
            return;
        }
        results.innerHTML = filtered.map(card).join('');
    }

    function card(item) {
        const probability = P.selectionProbability(item);
        const edge = item.probabilityEdge ?? item.valueEdge;
        const odds = item.decimalOdds ?? item.bestDecimalOdds;
        return `
            <article class="pe-prediction-card">
                <div class="pe-meta-line">
                    <span>${P.escapeHtml(P.label(item.leagueCode))}</span>
                    <span>${P.escapeHtml(P.dateTime(item.kickoffAt))}</span>
                </div>
                <h3>${P.escapeHtml(item.fixture || 'Fixture')}</h3>
                <div class="pe-grid-3">
                    <span class="pe-badge accent">${P.escapeHtml(P.label(item.marketCode || item.marketName))}</span>
                    <span class="pe-badge ${P.confidenceClass(item.confidenceBand)}">${P.escapeHtml(P.label(item.confidenceBand || 'UNRATED'))}</span>
                    <span class="pe-badge ${edge > 0 ? 'value' : ''}">${P.escapeHtml(edge > 0 ? P.signedPercent(edge) : 'No edge')}</span>
                </div>
                <div class="pe-metric-grid" style="margin-top:12px">
                    ${P.metric('Model probability', P.percent(probability))}
                    ${P.metric('Odds', odds ? P.decimal(odds) : 'No odds')}
                    ${P.metric('Pick', item.teamOrPlayer || item.predictedValue || '--')}
                    ${P.metric('Value edge', edge > 0 ? P.signedPercent(edge) : 'No edge')}
                </div>
                <div class="pe-card-actions">
                    <a class="pe-btn secondary compact" href="${P.detailsUrl(item, null, item.modelVersion)}">Details</a>
                </div>
            </article>`;
    }

    function safeMessage(error) {
        return error?.message && error.message.length < 180 ? error.message : 'The Machine request failed.';
    }
});
