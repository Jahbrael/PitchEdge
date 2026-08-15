document.addEventListener('DOMContentLoaded', () => {
    const P = window.PitchEdge;
    const runList = document.getElementById('runHistoryList');
    const savedList = document.getElementById('savedBatchList');
    loadRuns();
    renderSignedOutSavedBatches();

    async function loadRuns() {
        try {
            const runs = await P.apiJson('/api/v1/platform/runs/recent');
            if (!runs.length) {
                runList.innerHTML = `
                    ${P.emptyState('No prediction runs found', 'Generate predictions to create a run that can be reopened here with its picks, filters, date range, and strategy.')}
                    <a class="pe-btn secondary compact" href="/predictions.html">Create Prediction Run</a>
                `;
                return;
            }
            runList.innerHTML = runs.map(run => {
                const selections = P.flattenSelections(run);
                const leagues = new Set(selections.map(item => item.leagueCode).filter(Boolean));
                const markets = new Set(selections.map(item => item.marketCode).filter(Boolean));
                return `
                    <article class="pe-card">
                        <div class="pe-meta-line">
                            <span>${P.escapeHtml(P.dateTime(run.generatedAt))}</span>
                            <span>${P.escapeHtml(P.label(run.input?.strategy || 'BALANCED'))}</span>
                        </div>
                        <h3>${P.escapeHtml(run.returnedSelections || run.selectionsReturned || selections.length || 0)} recommended picks</h3>
                        <div class="pe-grid-3">
                            <div><span>Fixtures</span><strong>${P.escapeHtml(run.fixturesConsidered || 0)}</strong></div>
                            <div><span>Leagues</span><strong>${leagues.size}</strong></div>
                            <div><span>Markets</span><strong>${markets.size}</strong></div>
                        </div>
                        <p style="color:var(--text-muted)">${P.escapeHtml(run.input?.fixtureDateFrom || '--')} to ${P.escapeHtml(run.input?.fixtureDateTo || '--')}</p>
                        <a class="pe-btn secondary compact" href="/prediction-results.html?runId=${encodeURIComponent(run.requestId)}">Open Run</a>
                    </article>`;
            }).join('');
        } catch (error) {
            runList.innerHTML = P.emptyState('Could not load recent runs', safeMessage(error));
        }
    }

    function renderSignedOutSavedBatches() {
        savedList.innerHTML = `
            ${P.emptyState('Sign in to view saved batches', 'Saved batches are private. Recent runs are generated prediction sessions; saved batches are personal collections you keep for later.')}
            <a class="pe-btn secondary compact" href="/login.html">Sign In</a>
        `;
    }

    function safeMessage(error) {
        return error?.message && error.message.length < 180 ? error.message : 'The history request failed.';
    }
});
