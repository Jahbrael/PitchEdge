document.addEventListener('DOMContentLoaded', async () => {
    const P = window.PitchEdge;
    const params = new URLSearchParams(window.location.search);
    const runId = params.get('runId');

    const loadingState = document.getElementById('loadingState');
    const errorState = document.getElementById('errorState');
    const contentState = document.getElementById('contentState');
    const cardsGrid = document.getElementById('predictionCardsGrid');

    if (!runId) {
        showError('No prediction run selected', 'Return to the Prediction Builder to generate or open a prediction run.');
        return;
    }

    try {
        const response = await fetch(`/api/v1/predictions/runs/${encodeURIComponent(runId)}`, {
            headers: { Accept: 'application/json' }
        });
        if (response.status === 204) {
            showError('Prediction run not found', 'No prediction run exists for this saved reference.');
            return;
        }
        if (!response.ok) {
            showError('Prediction run not found', 'No prediction run exists for this saved reference.');
            return;
        }
        const runData = await response.json();
        showContent(runData);
    } catch (error) {
        showError('Prediction run unavailable', safeMessage(error));
    }

    function showContent(runData) {
        loadingState.classList.add('hidden');
        errorState.classList.add('hidden');
        contentState.classList.remove('hidden');

        const selections = P.flattenSelections(runData);
        populateSummary(runData, selections);
        setupFilters(
            selections,
            runData.modelVersion || runData.input?.modelVersion || 'v1',
            runData.fixtureIndicators || {}
        );
        setupExport(runData);
    }

    function showError(title, message) {
        loadingState.classList.add('hidden');
        contentState.classList.add('hidden');
        errorState.classList.remove('hidden');
        cardsGrid.innerHTML = '';
        document.getElementById('errorTitle').textContent = title;
        document.getElementById('errorMessage').textContent = message;
    }

    function populateSummary(data, selections) {
        document.getElementById('runMeta').textContent = `Saved run: ${shortReference(data.requestId || runId)}`;
        document.getElementById('statDateRange').textContent = data.input?.fixtureDateFrom && data.input?.fixtureDateTo
            ? `${data.input.fixtureDateFrom} to ${data.input.fixtureDateTo}`
            : 'Selected run';

        document.getElementById('statLeaguesCount').textContent = new Set(selections.map(s => s.leagueCode).filter(Boolean)).size;
        document.getElementById('statFixturesCount').textContent = new Set(selections.map(s => s.matchId).filter(Boolean)).size;
        document.getElementById('statPicksCount').textContent = selections.length;

        if (data.warnings && data.warnings.length > 0) {
            const warnSec = document.getElementById('warningsSection');
            const warnList = document.getElementById('warningsList');
            warnSec.classList.remove('hidden');
            warnList.innerHTML = data.warnings.map(warning => `<li>${P.escapeHtml(warning)}</li>`).join('');
        }
    }

    function setupFilters(selections, modelVersion, fixtureIndicators) {
        const searchInput = document.getElementById('searchFilter');
        const leagueSelect = document.getElementById('leagueFilter');
        const marketSelect = document.getElementById('marketFilter');
        const sortSelect = document.getElementById('sortFilter');
        const h2hSelect = document.getElementById('h2hFilter');
        const seasonSelect = document.getElementById('seasonFilter');

        Array.from(new Set(selections.map(s => s.leagueCode).filter(Boolean))).sort().forEach(code => {
            const opt = document.createElement('option');
            opt.value = code;
            opt.textContent = P.label(code);
            leagueSelect.appendChild(opt);
        });

        Array.from(new Set(selections.map(s => s.marketName || s.marketCode).filter(Boolean))).sort().forEach(market => {
            const opt = document.createElement('option');
            opt.value = market;
            opt.textContent = P.label(market);
            marketSelect.appendChild(opt);
        });

        let selectedChipMarket = '';
        document.querySelectorAll('#marketChipsContainer .chip-btn').forEach(button => {
            button.addEventListener('click', () => {
                document.querySelectorAll('#marketChipsContainer .chip-btn').forEach(item => {
                    item.classList.remove('active');
                    item.style.background = 'var(--panel)';
                    item.style.color = 'var(--text)';
                    item.style.border = '1px solid var(--line)';
                });
                button.classList.add('active');
                button.style.background = 'var(--brand)';
                button.style.color = '#fff';
                button.style.border = 'none';
                selectedChipMarket = button.dataset.market || '';
                render();
            });
        });

        [searchInput, leagueSelect, marketSelect, sortSelect, h2hSelect, seasonSelect].forEach(control => {
            if (control) {
                control.addEventListener(control === searchInput ? 'input' : 'change', render);
            }
        });

        render();

        function render() {
            const q = searchInput.value.trim().toLowerCase();
            const league = leagueSelect.value;
            const market = marketSelect.value;
            const sort = sortSelect.value;

            const h2h = h2hSelect ? h2hSelect.value : '';
            const season = seasonSelect ? seasonSelect.value : '';

            let filtered = selections.filter(selection => {
                const label = `${selection.fixture || ''} ${selection.match || ''} ${selection.teamOrPlayer || ''}`.toLowerCase();
                if (q && !label.includes(q)) return false;
                if (league && selection.leagueCode !== league) return false;
                if (market && selection.marketName !== market && selection.marketCode !== market) return false;
                if (selectedChipMarket && selection.marketCode !== selectedChipMarket && !(selection.marketCode || '').includes(selectedChipMarket)) return false;
                
                const indicator = fixtureIndicators?.[selection.selectionId];
                if (h2h === 'has_h2h') {
                    if (!indicator || !indicator.h2hAvailable || !indicator.h2hMatchCount) return false;
                }
                
                if (season === 'full_season') {
                    if (indicator && indicator.partialSeasonData) return false;
                }
                
                return true;
            });

            filtered.sort((a, b) => {
                if (sort === 'prob_desc') return (P.selectionProbability(b) || 0) - (P.selectionProbability(a) || 0);
                if (sort === 'edge_desc') return (b.probabilityEdge || b.valueEdge || 0) - (a.probabilityEdge || a.valueEdge || 0);
                return new Date(a.kickoffAt || 0) - new Date(b.kickoffAt || 0);
            });

            renderCards(filtered, modelVersion, fixtureIndicators);
        }
    }

    function renderCards(selections, modelVersion, fixtureIndicators) {
        const emptyState = document.getElementById('noMatchesState');
        cardsGrid.innerHTML = '';

        if (!selections || selections.length === 0) {
            emptyState.classList.remove('hidden');
            return;
        }
        emptyState.classList.add('hidden');

        cardsGrid.innerHTML = selections.map(selection => {
            const probability = P.selectionProbability(selection);
            const edge = selection.probabilityEdge ?? selection.valueEdge;
            const odds = selection.decimalOdds ?? selection.bestDecimalOdds;
            const confidence = selection.confidenceBand || 'UNRATED';
            const detailsUrl = P.detailsUrl(selection, runId, modelVersion);
            const indicatorMarkup = fixtureIndicatorMarkup(fixtureIndicators?.[selection.selectionId]);
            const fixtureParts = splitFixture(selection.fixture || selection.match);
            const fixtureMarkup = fixtureParts
                ? `<div class="pe-fixture-teams prediction-result-teams">
                        <div class="pe-team-block">${P.teamMark(fixtureParts.home, selection.homeTeamBadgeUrl || selection.homeTeamLogoUrl)}<span class="pe-team-name">${P.escapeHtml(fixtureParts.home)}</span></div>
                        <div class="pe-score-pill">vs</div>
                        <div class="pe-team-block away"><span class="pe-team-name">${P.escapeHtml(fixtureParts.away)}</span>${P.teamMark(fixtureParts.away, selection.awayTeamBadgeUrl || selection.awayTeamLogoUrl)}</div>
                    </div>`
                : `<div class="card-fixture">${P.escapeHtml(selection.fixture || selection.match || 'Fixture')}</div>`;
            return `
                <article class="pe-prediction-card prediction-result-card">
                    <div>
                        <div class="card-top">
                            <span class="card-league">${P.escapeHtml(P.label(selection.leagueCode || 'League'))}</span>
                            <span class="card-time">${P.escapeHtml(P.dateTime(selection.kickoffAt))}</span>
                        </div>
                        ${fixtureMarkup}
                        ${indicatorMarkup}
                        <div class="card-pick-box">
                            <div class="card-pick-label">Recommended market</div>
                            <div class="card-pick-name">${P.escapeHtml(P.label(selection.marketName || selection.marketCode))}</div>
                            ${selection.teamOrPlayer ? `<div class="card-pick-team">Pick: <strong>${P.escapeHtml(selection.teamOrPlayer)}</strong></div>` : ''}
                        </div>
                        <div class="pe-metric-grid prediction-result-metrics">
                            ${P.metric('Model probability', P.percent(probability))}
                            ${P.metric('Odds', odds ? P.decimal(odds) : 'No odds')}
                            ${P.metric('Value edge', edge === null || edge === undefined ? '--' : P.signedPercent(edge))}
                        </div>
                    </div>
                    <div class="card-footer">
                        <span class="badge ${P.confidenceClass(confidence)}">${P.escapeHtml(P.label(confidence))}</span>
                        <a href="${P.escapeHtml(detailsUrl)}" class="card-details-btn">View Details</a>
                    </div>
                </article>`;
        }).join('');
    }

    function fixtureIndicatorMarkup(indicator) {
        if (!indicator) return '';
        const items = [];
        if (indicator.h2hAvailable && indicator.h2hMatchCount) {
            items.push(chip(
                'H2H',
                `${indicator.h2hMatchCount} completed head-to-head ${indicator.h2hMatchCount === 1 ? 'match' : 'matches'} available in local data.`
            ));
        }
        if (indicator.homeLeaguePosition && indicator.awayLeaguePosition) {
            items.push(chip(
                `${ordinal(indicator.homeLeaguePosition)} vs ${ordinal(indicator.awayLeaguePosition)}`,
                `League positions calculated from local completed matches${indicator.leagueTableTeamCount ? ` across ${indicator.leagueTableTeamCount} teams` : ''}.`
            ));
        }
        if (indicator.partialSeasonData) {
            items.push(chip(
                'Partial Season',
                `The model used incomplete season coverage${indicator.partialSeasonCoverage ? ` (${indicator.partialSeasonCoverage})` : ''}.`,
                true
            ));
        }
        if (indicator.homeRecentFormPercentage !== null && indicator.homeRecentFormPercentage !== undefined) {
            items.push(chip(
                `H Form ${indicator.homeRecentFormPercentage}%`,
                `Home form points percentage from ${indicator.homeRecentFormSampleSize} completed local ${indicator.homeRecentFormSampleSize === 1 ? 'match' : 'matches'} before kickoff.`
            ));
        }
        if (indicator.awayRecentFormPercentage !== null && indicator.awayRecentFormPercentage !== undefined) {
            items.push(chip(
                `A Form ${indicator.awayRecentFormPercentage}%`,
                `Away form points percentage from ${indicator.awayRecentFormSampleSize} completed local ${indicator.awayRecentFormSampleSize === 1 ? 'match' : 'matches'} before kickoff.`
            ));
        }
        return items.length
            ? `<div class="pe-fixture-indicators" aria-label="Fixture data indicators">${items.join('')}</div>`
            : '';
    }

    function chip(label, tooltip, warning = false) {
        const safeLabel = P.escapeHtml(label);
        const safeTooltip = P.escapeHtml(tooltip);
        return `<span class="pe-fixture-indicator${warning ? ' warning' : ''}" title="${safeTooltip}" aria-label="${safeLabel}. ${safeTooltip}">${safeLabel}</span>`;
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

    function splitFixture(value) {
        if (!value) return null;
        const text = String(value);
        const match = text.match(/^(.+?)\s+(?:vs|v)\s+(.+)$/i);
        if (!match) return null;
        return {
            home: match[1].trim(),
            away: match[2].trim()
        };
    }

    function shortReference(value) {
        const text = String(value || '').trim();
        if (!text) return '--';
        return text.length > 14 ? `${text.slice(0, 8)}...${text.slice(-4)}` : text;
    }

    function setupExport(data) {
        const btn = document.getElementById('exportExcelBtn');
        if (!btn) return;
        btn.addEventListener('click', async () => {
            if (!data.input) return;
            try {
                btn.disabled = true;
                btn.textContent = 'Exporting...';
                const tokenPayload = await P.apiJson('/api/v1/auth/csrf');
                const res = await fetch('/api/v1/predictions/form/export', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-XSRF-TOKEN': tokenPayload.token
                    },
                    body: JSON.stringify(data.input)
                });
                if (!res.ok) throw new Error('Export failed');
                const blob = await res.blob();
                const url = window.URL.createObjectURL(blob);
                const link = document.createElement('a');
                link.href = url;
                link.download = `pitchedge_predictions_${data.requestId || 'export'}.xlsx`;
                document.body.appendChild(link);
                link.click();
                link.remove();
                window.URL.revokeObjectURL(url);
            } catch (error) {
                alert(safeMessage(error));
            } finally {
                btn.disabled = false;
                btn.textContent = 'Download Excel';
            }
        });
    }

    function safeMessage(error) {
        return error?.message && error.message.length < 180
            ? error.message
            : 'The prediction run request failed. Try reloading the page.';
    }
});
