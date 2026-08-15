document.addEventListener('DOMContentLoaded', () => {
    const P = window.PitchEdge;
    const stats = document.getElementById('performanceStats');
    const accuracyList = document.getElementById('accuracyList');
    loadPerformance();

    async function loadPerformance() {
        try {
            const data = await P.apiJson('/api/v1/platform/performance');
            renderStats(data);
            renderAccuracy(data);
        } catch (error) {
            stats.innerHTML = P.emptyState('Could not load performance', safeMessage(error));
            accuracyList.innerHTML = '';
        }
    }

    function renderStats(data) {
        const winRate = data.wonPredictions + data.lostPredictions > 0
            ? data.wonPredictions / (data.wonPredictions + data.lostPredictions)
            : null;
        stats.innerHTML = [
            ['Prediction accuracy', P.percent(winRate), 'Won / non-void settled picks'],
            ['Settled predictions', data.settledPredictions, 'Completed picks with outcomes'],
            ['Odds coverage', data.pricedSelections, 'Settled picks with odds'],
            ['Value picks tracked', data.positiveValueSelections, 'Selections with positive edge']
        ].map(([label, value, note]) => `
            <article class="pe-card pe-stat">
                <span>${P.escapeHtml(label)}</span>
                <strong>${P.escapeHtml(value)}</strong>
                <small>${P.escapeHtml(note)}</small>
            </article>
        `).join('');
    }

    function renderAccuracy(data) {
        if (data.emptyStateMessage) {
            accuracyList.innerHTML = P.emptyState('Not enough settled predictions yet', data.emptyStateMessage);
            return;
        }
        if (!data.accuracyRows || data.accuracyRows.length === 0) {
            accuracyList.innerHTML = P.emptyState('No accuracy rows found', 'Run settlement after predictions finish to populate model performance.');
            return;
        }
        accuracyList.innerHTML = data.accuracyRows.map(row => `
            <article class="pe-card pe-performance-card">
                <div class="pe-meta-line">
                    <span>${P.escapeHtml(P.label(row.leagueCode))}</span>
                    <span>${P.escapeHtml(row.accuracyDate || '--')}</span>
                </div>
                <h3>${P.escapeHtml(row.marketName || P.label(row.marketCode))}</h3>
                <div class="pe-metric-grid">
                    ${P.metric('Win rate', P.percent(row.winRate))}
                    ${P.metric('Settled picks', row.settledSelections)}
                    ${P.metric('Won', row.wonCount)}
                    ${P.metric('Lost', row.lostCount)}
                    ${P.metric('Forecast quality', P.decimal(row.brierScore, 4), 'Lower is better')}
                    ${P.metric('Calibration gap', P.decimal(row.calibrationError, 4), 'Lower is better')}
                </div>
            </article>
        `).join('');
    }

    function safeMessage(error) {
        return error?.message && error.message.length < 180 ? error.message : 'The performance request failed.';
    }
});
