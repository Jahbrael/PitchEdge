document.addEventListener('DOMContentLoaded', () => {
    const P = window.PitchEdge;
    const list = document.getElementById('leagueList');
    const search = document.getElementById('leagueSearch');
    const historyFilter = document.getElementById('historyFilter');
    let leagues = [];

    search.addEventListener('input', render);
    historyFilter.addEventListener('change', render);
    loadLeagues();

    async function loadLeagues() {
        try {
            leagues = await P.apiJson('/api/v1/platform/leagues');
            render();
        } catch (error) {
            list.innerHTML = P.emptyState('Could not load leagues', safeMessage(error));
        }
    }

    function render() {
        const q = search.value.trim().toLowerCase();
        const history = historyFilter.value;
        const filtered = leagues.filter(league => {
            const text = `${league.name} ${league.country} ${league.leagueCode}`.toLowerCase();
            if (q && !text.includes(q)) return false;
            if (history && league.historyStatus !== history) return false;
            return true;
        });
        if (!filtered.length) {
            list.innerHTML = P.emptyState('No leagues match your filters', 'Try a different league search or history status.');
            return;
        }
        list.innerHTML = filtered.map(card).join('');
    }

    function card(league) {
        const importStatus = importStatusLabel(league);
        const statusLabel = league.importStatus === 'IMPORT_PENDING' ? importStatus : P.label(league.historyStatus);
        const seasonRows = (league.seasonBreakdowns || []).map(season => `
            <tr>
                <td>${P.escapeHtml(season.seasonLabel)}</td>
                <td>${P.escapeHtml(season.matches)}</td>
                <td>${P.escapeHtml(season.finishedMatches)}</td>
                <td>${P.escapeHtml(season.scheduledMatches)}</td>
                <td>${P.escapeHtml(season.firstMatchDate || '--')}</td>
                <td>${P.escapeHtml(season.lastMatchDate || '--')}</td>
            </tr>
        `).join('');
        return `
            <article class="pe-card pe-league-card">
                <div class="pe-section-header">
                    <div>
                        <div class="pe-team-block">
                            ${P.teamMark(league.name, league.leagueBadgeUrl || league.leagueLogoUrl || league.leaguePosterUrl)}
                            <h2>${P.escapeHtml(league.name)}</h2>
                        </div>
                        <p>${P.escapeHtml(league.country)} - ${P.escapeHtml(league.currentSeason || 'Season coverage available')}</p>
                    </div>
                    <span class="pe-badge ${league.historyStatus === 'COMPLETE' ? 'good' : league.historyStatus === 'PARTIAL' ? 'warn' : ''}">${P.escapeHtml(statusLabel)}</span>
                </div>
                <div class="pe-metric-grid">
                    ${P.metric('Seasons available', `${league.importedSeasonLabels.length}/${league.requiredHistoryUnits}`)}
                    ${P.metric('Matches tracked', league.matches)}
                    ${P.metric('Finished fixtures', league.finishedMatches)}
                    ${P.metric('Scheduled fixtures', league.scheduledMatches)}
                    ${P.metric('Import status', importStatus)}
                    ${P.metric('Prediction access', league.predictionSelectable ? 'Ready' : 'Not ready yet')}
                    ${P.metric('Data coverage', league.importStatus === 'IMPORT_PENDING' ? 'Awaiting import' : P.label(league.historyStatus))}
                    ${P.metric('History model', P.label(league.historyPolicy))}
                </div>
                <details style="margin-top:16px">
                    <summary class="pe-btn secondary compact">Season History</summary>
                    <div style="overflow-x:auto;margin-top:12px">
                        <table>
                            <thead><tr><th>Season</th><th>Matches</th><th>Finished</th><th>Scheduled</th><th>First</th><th>Last</th></tr></thead>
                            <tbody>${seasonRows || `<tr><td colspan="6">No imported season history found.</td></tr>`}</tbody>
                        </table>
                    </div>
                </details>
            </article>`;
    }

    function importStatusLabel(league) {
        if (league.importStatus === 'IMPORT_PENDING' || Number(league.matches || 0) === 0) {
            return 'Import pending';
        }
        if (league.importStatus === 'IMPORTED') {
            return 'Imported';
        }
        return P.label(league.importStatus || league.historyStatus || 'PENDING');
    }

    function safeMessage(error) {
        return error?.message && error.message.length < 180 ? error.message : 'The league request failed.';
    }
});
