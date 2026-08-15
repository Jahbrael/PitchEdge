document.addEventListener('DOMContentLoaded', () => {
    const P = window.PitchEdge;
    const fromDate = document.getElementById('fromDate');
    const toDate = document.getElementById('toDate');
    const searchInput = document.getElementById('searchInput');
    const leagueFilter = document.getElementById('leagueFilter');
    const marketFilter = document.getElementById('marketFilter');
    const sortFilter = document.getElementById('sortFilter');
    const list = document.getElementById('valuePicksList');
    const valueCount = document.getElementById('valueCount');
    const valueSummary = document.getElementById('valueSummary');
    const slipSummary = document.getElementById('slipSummary');
    const slipList = document.getElementById('slipList');
    let picks = [];
    let slip = [];

    const today = new Date();
    const twoWeeks = new Date();
    twoWeeks.setDate(today.getDate() + 14);
    fromDate.value = P.isoDate(today);
    toDate.value = P.isoDate(twoWeeks);

    [fromDate, toDate].forEach(input => input.addEventListener('change', loadPicks));
    [searchInput, leagueFilter, marketFilter, sortFilter].forEach(input => {
        input.addEventListener(input === searchInput ? 'input' : 'change', renderPicks);
    });
    document.getElementById('clearSlipBtn').addEventListener('click', () => {
        slip = [];
        renderSlip();
    });

    loadPicks();

    async function loadPicks() {
        list.innerHTML = P.loadingState(4);
        try {
            picks = await P.apiJson(`/api/v1/platform/value-picks?from=${encodeURIComponent(fromDate.value)}&to=${encodeURIComponent(toDate.value)}`);
            hydrateFilters();
            renderSummary();
            renderPicks();
            renderSlip();
        } catch (error) {
            list.innerHTML = P.emptyState('Could not load value picks', safeMessage(error));
        }
    }

    function renderSummary() {
        if (!valueSummary) return;
        const edges = picks.map(pick => Number(pick.probabilityEdge ?? pick.valueEdge)).filter(Number.isFinite);
        const oddsCount = picks.filter(pick => pick.decimalOdds || pick.bestDecimalOdds).length;
        const highConfidence = picks.filter(pick => ['HIGH', 'VERY_HIGH'].includes(pick.confidenceBand)).length;
        const avgEdge = edges.length ? edges.reduce((sum, edge) => sum + edge, 0) / edges.length : null;
        const bestEdge = edges.length ? Math.max(...edges) : null;
        valueSummary.innerHTML = [
            P.metric('Value picks', picks.length),
            P.metric('Average edge', P.signedPercent(avgEdge)),
            P.metric('Best edge', P.signedPercent(bestEdge)),
            P.metric('Odds coverage', `${oddsCount}/${picks.length || 0}`),
            P.metric('High confidence', highConfidence)
        ].join('');
    }

    function hydrateFilters() {
        const currentLeague = leagueFilter.value;
        const currentMarket = marketFilter.value;
        leagueFilter.innerHTML = '<option value="">All leagues</option>';
        marketFilter.innerHTML = '<option value="">All markets</option>';
        [...new Set(picks.map(p => p.leagueCode).filter(Boolean))].sort().forEach(code => {
            const option = document.createElement('option');
            option.value = code;
            option.textContent = P.label(code);
            leagueFilter.appendChild(option);
        });
        [...new Set(picks.map(p => p.marketCode || p.marketName).filter(Boolean))].sort().forEach(code => {
            const option = document.createElement('option');
            option.value = code;
            option.textContent = P.label(code);
            marketFilter.appendChild(option);
        });
        leagueFilter.value = currentLeague;
        marketFilter.value = currentMarket;
    }

    function renderPicks() {
        const q = searchInput.value.trim().toLowerCase();
        const league = leagueFilter.value;
        const market = marketFilter.value;
        const sort = sortFilter.value;
        let filtered = picks.filter(pick => {
            const text = `${pick.fixture || ''} ${pick.teamOrPlayer || ''}`.toLowerCase();
            if (q && !text.includes(q)) return false;
            if (league && pick.leagueCode !== league) return false;
            if (market && pick.marketCode !== market && pick.marketName !== market) return false;
            return true;
        });
        filtered.sort((a, b) => {
            if (sort === 'probability') return (P.selectionProbability(b) || 0) - (P.selectionProbability(a) || 0);
            if (sort === 'time') return new Date(a.kickoffAt || 0) - new Date(b.kickoffAt || 0);
            return (b.probabilityEdge || b.valueEdge || 0) - (a.probabilityEdge || a.valueEdge || 0);
        });
        valueCount.textContent = `${filtered.length} pick${filtered.length === 1 ? '' : 's'}`;
        if (!filtered.length) {
            list.innerHTML = P.emptyState('No value picks found', 'No selections match these filters with odds and positive edge. Try widening the edge, league, or market filters.');
            return;
        }
        list.innerHTML = filtered.map(valueCard).join('');
        list.querySelectorAll('[data-add-selection]').forEach(button => {
            button.addEventListener('click', () => {
                const id = button.dataset.addSelection;
                const pick = picks.find(item => item.selectionId === id);
                if (pick && !slip.some(item => item.selectionId === id)) {
                    slip.push(pick);
                    renderSlip();
                }
            });
        });
    }

    function valueCard(pick) {
        const probability = P.selectionProbability(pick);
        const odds = pick.decimalOdds ?? pick.bestDecimalOdds;
        const implied = pick.bookmakerImpliedProbability ?? pick.bestImpliedProbability;
        const edge = pick.probabilityEdge ?? pick.valueEdge;
        return `
            <article class="pe-prediction-card">
                <div class="pe-meta-line">
                    <span>${P.escapeHtml(P.label(pick.leagueCode))}</span>
                    <span>${P.escapeHtml(P.dateTime(pick.kickoffAt))}</span>
                </div>
                <h3>${P.escapeHtml(pick.fixture || 'Fixture')}</h3>
                <div class="pe-grid-3">
                    <div><span class="pe-badge accent">${P.escapeHtml(P.label(pick.marketCode || pick.marketName))}</span></div>
                    <div><span class="pe-badge value">${P.escapeHtml(P.signedPercent(edge))} edge</span></div>
                    <div><span class="pe-badge ${P.confidenceClass(pick.confidenceBand)}">${P.escapeHtml(P.label(pick.confidenceBand || 'UNRATED'))}</span></div>
                </div>
                <div class="pe-metric-grid" style="margin-top:12px">
                    ${P.metric('Model probability', P.percent(probability))}
                    ${P.metric('Odds', P.decimal(odds))}
                    ${P.metric('Implied probability', P.percent(implied))}
                    ${P.metric('Bookmaker', pick.bestOddsBookmaker || 'Odds available')}
                </div>
                <div class="pe-card-actions">
                    <button type="button" class="pe-btn secondary compact" data-add-selection="${P.escapeHtml(pick.selectionId)}">Add to Slip</button>
                    <a class="pe-btn secondary compact" href="${P.detailsUrl(pick, null, pick.modelVersion)}">Details</a>
                </div>
            </article>`;
    }

    function renderSlip() {
        const probabilities = slip.map(P.selectionProbability).filter(v => v !== null && v !== undefined);
        const odds = slip.map(item => item.decimalOdds ?? item.bestDecimalOdds).filter(v => v !== null && v !== undefined);
        const combinedProbability = probabilities.reduce((acc, value) => acc * Number(value), probabilities.length ? 1 : 0);
        const combinedOdds = odds.reduce((acc, value) => acc * Number(value), odds.length ? 1 : 0);
        const averageProbability = probabilities.length
            ? probabilities.reduce((acc, value) => acc + Number(value), 0) / probabilities.length
            : null;
        const sameLeagueWarning = new Set(slip.map(item => item.leagueCode)).size < slip.length && slip.length > 1;
        const tooManyWarning = slip.length > 5;
        slipSummary.innerHTML = [
            ['Selections', slip.length],
            ['Average model probability', P.percent(averageProbability)],
            ['Combined probability', P.percent(combinedProbability)],
            ['Combined odds', odds.length === slip.length ? P.decimal(combinedOdds) : 'Partial odds'],
            ['Odds coverage', `${odds.length}/${slip.length}`],
            ['Risk level', slip.length > 4 ? 'High' : slip.length > 2 ? 'Medium' : 'Low']
        ].map(([label, value]) => `<div class="pe-card"><span class="pe-badge">${P.escapeHtml(label)}</span><h3>${P.escapeHtml(value)}</h3></div>`).join('');
        if (!slip.length) {
            slipList.innerHTML = P.emptyState(
                'No slip selections',
                'Add value picks to compare combined probability, combined odds, odds coverage, and risk. Combined probability assumes selections are independent.'
            );
            return;
        }
        slipList.innerHTML = `
            ${(sameLeagueWarning || tooManyWarning) ? `<div class="pe-card"><span class="pe-badge warn">Warnings</span><p>${sameLeagueWarning ? 'Selections share the same league. ' : ''}${tooManyWarning ? 'Too many legs can raise risk quickly.' : ''}</p></div>` : ''}
            ${slip.map(item => `<div class="pe-card"><strong>${P.escapeHtml(item.fixture)}</strong><p>${P.escapeHtml(P.label(item.marketCode))} - ${P.escapeHtml(item.teamOrPlayer || item.predictedValue || '')}</p></div>`).join('')}
        `;
    }

    function safeMessage(error) {
        return error?.message && error.message.length < 180 ? error.message : 'The value-pick request failed.';
    }
});
