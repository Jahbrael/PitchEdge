document.addEventListener('DOMContentLoaded', () => {
    const displayLabel = (value) => window.PitchEdge ? window.PitchEdge.label(value) : window.formatEnumLabel(value);
    const escapeHtml = (value) => window.PitchEdge ? window.PitchEdge.escapeHtml(value) : String(value ?? '');
    const readableText = (value) => String(value ?? '').replace(/\b[A-Z][A-Z0-9]+(?:_[A-Z0-9]+)+\b/g, token => displayLabel(token));
    const safeReadableText = (value) => escapeHtml(readableText(value));
    const params = new URLSearchParams(window.location.search);
    const matchId = params.get('matchId');
    const modelVersion = params.get('modelVersion');
    const recommendedMarketCode = params.get('recommended') || params.get('recommendedMarketCode') || '';

    if (!matchId || !modelVersion) {
        showError('Missing required parameters: matchId and modelVersion.');
        return;
    }

    const selectionId = params.get('selectionId');
    const runId = params.get('runId');
    if (runId) {
        const backLink = document.getElementById('backToPredictionsBtn');
        if (backLink) {
            backLink.href = `/prediction-results.html?runId=${runId}`;
        }
    }

    let allMarkets = [];
    let unavailableMarketsList = [];

    // Filter controls
    const searchInput = document.getElementById('marketSearch');
    const categorySelect = document.getElementById('categoryFilter');
    const sortSelect = document.getElementById('sortFilter');
    const qualifiedToggle = document.getElementById('qualifiedOnlyToggle');
    if (qualifiedToggle) qualifiedToggle.checked = false;

    // Tab switching
    const tabPredBtn = document.getElementById('tabPredictionsBtn');
    const tabStatsBtn = document.getElementById('tabStatsBtn');
    const tabPredContent = document.getElementById('tabContentPredictions');
    const tabStatsContent = document.getElementById('tabContentStats');

    if (tabPredBtn && tabStatsBtn) {
        tabPredBtn.addEventListener('click', () => {
            tabPredBtn.classList.add('active');
            tabPredBtn.style.background = 'var(--brand)';
            tabPredBtn.style.color = '#fff';
            tabStatsBtn.classList.remove('active');
            tabStatsBtn.style.background = 'var(--panel)';
            tabStatsBtn.style.color = 'var(--text)';
            tabPredContent.classList.remove('hidden');
            tabStatsContent.classList.add('hidden');
        });
        tabStatsBtn.addEventListener('click', () => {
            tabStatsBtn.classList.add('active');
            tabStatsBtn.style.background = 'var(--brand)';
            tabStatsBtn.style.color = '#fff';
            tabPredBtn.classList.remove('active');
            tabPredBtn.style.background = 'var(--panel)';
            tabPredBtn.style.color = 'var(--text)';
            tabStatsContent.classList.remove('hidden');
            tabPredContent.classList.add('hidden');
        });
    }

    fetchFixtureDetails(matchId, modelVersion, recommendedMarketCode);

    const updateView = () => renderMarkets(allMarkets);

    searchInput.addEventListener('input', updateView);
    categorySelect.addEventListener('change', updateView);
    sortSelect.addEventListener('change', updateView);
    qualifiedToggle.addEventListener('change', updateView);

    async function fetchFixtureDetails(id, version, recMarketCode) {
        try {
            const url = new URL(`/api/v1/predictions/fixtures/${id}/details`, window.location.origin);
            url.searchParams.append('modelVersion', version);
            if (recMarketCode) {
                url.searchParams.append('recommendedMarketCode', recMarketCode);
            }
            if (runId) {
                url.searchParams.append('runId', runId);
            }
            if (selectionId) {
                url.searchParams.append('selectionId', selectionId);
            }

            const response = await fetch(url.toString());
            if (!response.ok) throw new Error('Failed to fetch fixture details');
            
            const data = await response.json();
            
            document.getElementById('loadingState').classList.add('hidden');
            document.getElementById('contentState').classList.remove('hidden');

            allMarkets = data.markets || [];
            unavailableMarketsList = data.unavailableMarkets || [];
            
            const recCode = recMarketCode || params.get('recommended') || params.get('recommendedMarketCode') || (data.predictionSummary ? data.predictionSummary.recommendedMarketCode : '');
            if (recCode && recCode !== '--') {
                let recMarket = allMarkets.find(m => m.marketCode === recCode);
                if (!recMarket) {
                    const unavail = unavailableMarketsList.find(m => m.marketCode === recCode);
                    recMarket = {
                        marketCode: recCode,
                        marketName: unavail ? (unavail.marketName || window.formatEnumLabel(recCode)) : window.formatEnumLabel(recCode),
                        category: unavail ? (unavail.category || 'RECOMMENDED') : 'RECOMMENDED',
                        probability: null,
                        confidence: data.predictionSummary?.confidenceLevel || 'UNRATED',
                        qualified: true,
                        available: false,
                        bookmakerOdds: unavail?.bookmakerOdds || null,
                        modelEdge: unavail?.modelEdge || null,
                        dataWarning: 'Recommended by this run, but the detailed market calculation was not available for this view.',
                        explanation: data.predictionSummary?.reasonQualified || 'Recommended market from the selected prediction run.'
                    };
                    allMarkets.unshift(recMarket);
                    unavailableMarketsList = unavailableMarketsList.filter(m => m.marketCode !== recCode);
                } else {
                    recMarket.qualified = true; // Ensure recommended pick is always qualified
                }
            }

            populateHeader(data.fixture, data.predictionSummary, allMarkets, recCode);
            populateCategoryFilter(allMarkets);
            
            renderMarkets(allMarkets);
            renderUnavailableMarkets(unavailableMarketsList);
            renderSupportingStats(data);
            if (window.location.hash === '#stats' && tabStatsBtn) {
                tabStatsBtn.click();
            }
        } catch (error) {
            console.error(error);
            showError('Failed to load fixture details.');
        }
    }

    function showError(message) {
        document.getElementById('loadingState').classList.add('hidden');
        const errorState = document.getElementById('errorState');
        errorState.classList.remove('hidden');
        errorState.querySelector('p').textContent = message;
    }

    function populateHeader(fixture, summary, markets, recommendedCode) {
        document.getElementById('homeTeamName').textContent = fixture.homeTeam || 'Home';
        document.getElementById('awayTeamName').textContent = fixture.awayTeam || 'Away';
        const homeMark = document.getElementById('homeTeamMark');
        const awayMark = document.getElementById('awayTeamMark');
        if (homeMark) homeMark.innerHTML = window.PitchEdge.teamMark(fixture.homeTeam || 'Home', fixture.homeTeamBadgeUrl || fixture.homeTeamLogoUrl || fixture.homeBadgeUrl || fixture.homeLogoUrl);
        if (awayMark) awayMark.innerHTML = window.PitchEdge.teamMark(fixture.awayTeam || 'Away', fixture.awayTeamBadgeUrl || fixture.awayTeamLogoUrl || fixture.awayBadgeUrl || fixture.awayLogoUrl);
        document.getElementById('competitionName').textContent = displayLabel(fixture.competition || 'Unknown Comp');
        
        const date = new Date(fixture.kickoffTime);
        document.getElementById('kickoffTime').textContent = isNaN(date.getTime()) ? fixture.kickoffTime : date.toLocaleString(undefined, {
            weekday: 'short', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
        });
        
        document.getElementById('venueName').textContent = fixture.venue || 'Unknown Venue';

        const vsBadge = document.getElementById('vsBadge');
        if (vsBadge) {
            vsBadge.classList.remove('has-score');
            if (fixture.homeScore !== null && fixture.homeScore !== undefined && fixture.awayScore !== null && fixture.awayScore !== undefined) {
                vsBadge.textContent = `${fixture.homeScore} - ${fixture.awayScore}`;
                vsBadge.classList.add('has-score');
            } else {
                vsBadge.textContent = 'VS';
            }
        }
        const statusBadge = document.getElementById('matchStatusBadge');
        if (statusBadge && fixture.status) {
            statusBadge.style.display = 'inline-block';
            statusBadge.textContent = window.PitchEdge.formatLiveBadge(fixture.status, fixture.liveMinute);
            const isLiveMatch = (fixture.status === "LIVE" || fixture.status === "IN_PLAY" || fixture.status === "HALF_TIME");
            statusBadge.className = isLiveMatch ? "badge badge-live" : "badge badge-neutral";
        }

        // Summary
        document.getElementById('overallConfidence').textContent = window.formatEnumLabel(summary?.confidenceLevel || '--');

        // Recommended Pick
        let recName = recommendedCode || summary.recommendedMarketCode || '--';
        if (recName !== '--') {
            const recMarket = markets.find(m => m.marketCode === recName);
            if (recMarket && recMarket.marketName) {
                recName = recMarket.marketName;
            } else {
                recName = displayLabel(recName);
            }
        }
        document.getElementById('recommendedPick').textContent = recName;
    }

    function populateCategoryFilter(markets) {
        const categories = new Set(markets.map(m => m.category).filter(Boolean));
        const select = document.getElementById('categoryFilter');
        select.innerHTML = '<option value="ALL">All Categories</option>';
        categories.forEach(cat => {
            const opt = document.createElement('option');
            opt.value = cat;
            opt.textContent = formatCategory(cat);
            select.appendChild(opt);
        });
    }

    function formatCategory(cat) {
        return displayLabel(cat);
    }

    function renderMarkets(markets) {
        const container = document.getElementById('availableMarketsContainer');
        container.innerHTML = '';

        const searchText = searchInput.value.toLowerCase().trim();
        const category = categorySelect.value;
        const sort = sortSelect.value;
        const qualifiedOnly = qualifiedToggle.checked;

        let filtered = markets.filter(m => {
            if (qualifiedOnly && !m.qualified) return false;
            if (category !== 'ALL' && m.category !== category) return false;
            if (searchText && (!m.marketName || !m.marketName.toLowerCase().includes(searchText))) return false;
            return true;
        });

        filtered.sort((a, b) => {
            if (sort === 'PROBABILITY_DESC') {
                return (b.probability || 0) - (a.probability || 0);
            } else if (sort === 'EDGE_DESC') {
                return (b.modelEdge || 0) - (a.modelEdge || 0);
            } else if (sort === 'CATEGORY') {
                const catA = a.category || '';
                const catB = b.category || '';
                if (catA < catB) return -1;
                if (catA > catB) return 1;
                return (b.probability || 0) - (a.probability || 0);
            } else if (sort === 'CONFIDENCE_DESC') {
                const confOrder = { 'VERY_HIGH': 4, 'HIGH': 3, 'MEDIUM': 2, 'LOW': 1, 'UNRATED': 0 };
                const confA = confOrder[(a.confidence || '').toUpperCase()] ?? -1;
                const confB = confOrder[(b.confidence || '').toUpperCase()] ?? -1;
                if (confA !== confB) return confB - confA;
                return (b.probability || 0) - (a.probability || 0);
            }
            return 0;
        });

        if (filtered.length === 0) {
            if (markets.length === 0) {
                container.innerHTML = `<div class="empty-state" style="grid-column: 1 / -1; padding: 32px; text-align: center; color: var(--text-muted);"><p>No calculated markets were found for this fixture and prediction run.</p><p style="font-size: 12px; margin-top: 8px;">No completed market calculations were available for this view.</p></div>`;
            } else {
                container.innerHTML = `<div class="empty-state" style="grid-column: 1 / -1; padding: 32px; text-align: center; color: var(--text-muted);"><p>No markets match your current filters.</p></div>`;
            }
            return;
        }

        filtered.forEach(m => {
            const card = document.createElement('div');
            card.className = 'market-card';
            
            const probPercent = m.probability !== null && m.probability !== undefined ? (m.probability * 100).toFixed(1) + '%' : '--';
            const edgePercent = typeof m.modelEdge === 'number' ? (m.modelEdge * 100).toFixed(1) + '%' : '--';
            const odds = m.bookmakerOdds ? m.bookmakerOdds.toFixed(2) : 'No odds';
            
            const edgeClass = m.modelEdge > 0 ? 'edge-positive' : (m.modelEdge < 0 ? 'edge-negative' : '');
            const confClass = (m.confidence || '').toLowerCase();
            const explanation = userFacingMarketExplanation(m.explanation);
            
            card.innerHTML = `
                <div class="market-header">
                    <div>
                        <h3 class="market-title">${displayLabel(m.marketName || m.marketCode)}</h3>
                        <span class="market-category">${formatCategory(m.category || 'Unknown')}</span>
                    </div>
                    ${m.qualified ? '<div class="market-badges"><span class="badge-qualified">Qualified</span></div>' : ''}
                </div>
                
                <div class="market-main-stats">
                    <span class="market-prob">${probPercent}</span>
                    <span class="market-confidence ${confClass}">${displayLabel(m.confidence || 'No Rating')}</span>
                </div>
                
                <div class="market-odds-grid">
                    <div class="odds-item">
                        <span class="label">Bookie Odds</span>
                        <span class="val">${odds}</span>
                    </div>
                    <div class="odds-item">
                        <span class="label">Implied</span>
                        <span class="val">${m.bookmakerImpliedProbability ? (m.bookmakerImpliedProbability * 100).toFixed(1) + '%' : '--'}</span>
                    </div>
                    <div class="odds-item">
                        <span class="label">Edge</span>
                        <span class="val ${edgeClass}">${edgePercent}</span>
                    </div>
                </div>
                
                ${m.dataWarning ? `<div style="color: var(--bad); font-size: 13px; font-weight: 600; padding: 8px; background: var(--soft-red); border-radius: var(--radius-sm); margin-top: 8px;">! ${safeReadableText(m.dataWarning)}</div>` : ''}
                
                ${explanation ? `<div class="market-explanation">${safeReadableText(explanation)}</div>` : ''}
            `;
            container.appendChild(card);
        });
    }

    function userFacingMarketExplanation(explanation) {
        const value = String(explanation || '').trim();
        if (!value) return '';
        if (/calibrated using/i.test(value)) return '';
        if (/settled selections/i.test(value)) return '';
        if (/through\s+\d{4}-\d{2}-\d{2}/i.test(value)) return '';
        return value;
    }

    function renderUnavailableMarkets(markets) {
        const section = document.getElementById('unavailableSection');
        const list = document.getElementById('unavailableMarketsList');
        const badge = document.getElementById('unavailableCountBadge');
        
        if (badge) badge.textContent = markets ? markets.length : 0;

        if (!markets || markets.length === 0) {
            section.classList.add('hidden');
            return;
        }
        
        section.classList.remove('hidden');
        list.innerHTML = '';
        
        markets.forEach(m => {
            const div = document.createElement('div');
            div.className = 'unavailable-item';
            div.innerHTML = `
                <div class="unavailable-item-title">${displayLabel(m.marketName || m.marketCode)}</div>
                <div class="unavailable-item-reason">${safeReadableText(m.reason || 'Data unavailable')}</div>
            `;
            list.appendChild(div);
        });
    }

    function renderSupportingStats(data) {
        const root = document.getElementById('matchStatsRoot');
        if (!root) return;
        const fixture = data.fixture || {};
        root.innerHTML = `
            <div class="match-stats-header">
                <div>
                    <p class="pe-page-kicker">Stats generated from local database records</p>
                    <h3>Match Stats & Supporting Evidence</h3>
                    <p>${safeReadableText(data.note || 'Supporting statistics are generated from local match history.')}</p>
                </div>
                <span class="pe-badge accent">${escapeHtml(fixture.competition || 'Competition')}</span>
            </div>
            <div class="match-stats-overview">
                ${teamOverviewCard('Home', fixture.homeTeam, fixture.homeTeamBadgeUrl || fixture.homeTeamLogoUrl, data.homeForm, data.homeLast5)}
                <div class="match-preview-card">
                    <span class="pe-badge good">Match preview</span>
                    <p>${safeReadableText(data.matchPreview?.text || 'Not enough local match history to build a preview for this fixture yet.')}</p>
                    ${renderLimitations(data.matchPreview?.limitations || [])}
                </div>
                ${teamOverviewCard('Away', fixture.awayTeam, fixture.awayTeamBadgeUrl || fixture.awayTeamLogoUrl, data.awayForm, data.awayLast5)}
            </div>
            ${renderLiveStatsSection(data)}
            <div class="stats-subtabs" role="tablist" aria-label="Match stats sections">
                ${['Overview', 'Ranking', 'Last Matches', 'Pre-Match Stats', 'Trends', 'H2H'].map((label, index) => `<button type="button" class="stats-subtab ${index === 0 ? 'active' : ''}" data-stats-section="${label}">${label}</button>`).join('')}
            </div>
            <div class="stats-section-panel" data-stats-panel="Overview">${renderOverviewSection(data)}</div>
            <div class="stats-section-panel hidden" data-stats-panel="Ranking">${renderRankingSection(data)}</div>
            <div class="stats-section-panel hidden" data-stats-panel="Last Matches">${renderLastMatchesSection(data)}</div>
            <div class="stats-section-panel hidden" data-stats-panel="Pre-Match Stats">${renderPreMatchStatsSection(data)}</div>
            <div class="stats-section-panel hidden" data-stats-panel="Trends">${renderTrendsSection(data)}</div>
            <div class="stats-section-panel hidden" data-stats-panel="H2H">${renderH2hSection(data)}</div>
        `;
        root.querySelectorAll('.stats-subtab').forEach(button => {
            button.addEventListener('click', () => {
                root.querySelectorAll('.stats-subtab').forEach(tab => tab.classList.remove('active'));
                root.querySelectorAll('.stats-section-panel').forEach(panel => panel.classList.add('hidden'));
                button.classList.add('active');
                root.querySelector(`[data-stats-panel="${button.dataset.statsSection}"]`)?.classList.remove('hidden');
            });
        });
        root.querySelectorAll('[data-team-match-scope]').forEach(button => {
            button.addEventListener('click', () => {
                const panel = button.closest('.stats-panel-card');
                if (!panel) return;
                panel.querySelectorAll('[data-team-match-scope]').forEach(chip => chip.classList.remove('active'));
                panel.querySelectorAll('[data-match-list]').forEach(list => list.classList.add('hidden'));
                button.classList.add('active');
                panel.querySelector(`[data-match-list="${button.dataset.teamMatchScope}"]`)?.classList.remove('hidden');
            });
        });
        root.querySelectorAll('[data-trend-filter]').forEach(button => {
            button.addEventListener('click', () => {
                const filter = button.dataset.trendFilter;
                root.querySelectorAll('[data-trend-filter]').forEach(chip => chip.classList.remove('active'));
                button.classList.add('active');
                root.querySelectorAll('[data-trend-card]').forEach(card => {
                    card.classList.toggle('hidden', filter !== 'ALL' && card.dataset.trendCard !== filter);
                });
            });
        });
    }

    function teamOverviewCard(side, teamName, imageUrl, form, matches) {
        const sample = matches?.length || 0;
        return `<article class="team-overview-card">
            <div class="team-overview-title">
                ${window.PitchEdge.teamMark(teamName || side, imageUrl)}
                <div><span>${side}</span><strong>${escapeHtml(teamName || side)}</strong></div>
            </div>
            <div class="form-badge-row">${formBadges(form?.formString)}</div>
            <div class="mini-stat-grid">
                ${miniStat('Scored', sample ? form?.goalsScored : '--')}
                ${miniStat('Conceded', sample ? form?.goalsConceded : '--')}
                ${miniStat('Avg GF', sample ? form?.avgGoalsScored : '--')}
            </div>
        </article>`;
    }

    function renderLiveStatsSection(data) {
        const stats = data.liveStats;
        const fixture = data.fixture || {};
        const isLive = fixture.status === 'LIVE' || fixture.status === 'IN_PLAY' || fixture.status === 'HALF_TIME';
        if (!stats && !isLive) return '';
        if (!stats?.available && !isLive) return '';
        const refreshed = stats?.refreshedAt ? new Date(stats.refreshedAt) : null;
        const refreshedText = refreshed && !Number.isNaN(refreshed.getTime())
            ? `Updated ${refreshed.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })}`
            : 'Waiting for live refresh';
        if (!stats?.available || !stats.rows?.length) {
            return `<section class="live-match-stats-panel">
                <div class="stats-section-heading">
                    <div>
                        <h4>Live Match Stats</h4>
                        <p>${escapeHtml(stats?.statusLabel || window.PitchEdge.formatLiveBadge(fixture.status, fixture.liveMinute))}</p>
                    </div>
                    <span class="pe-badge neutral">${escapeHtml(refreshedText)}</span>
                </div>
                ${emptyStatsState(stats?.unavailableReason || 'Live match stats are not available yet.')}
            </section>`;
        }
        return `<section class="live-match-stats-panel">
            <div class="stats-section-heading">
                <div>
                    <h4>Live Match Stats</h4>
                    <p>${escapeHtml(stats.statusLabel || window.PitchEdge.formatLiveBadge(fixture.status, fixture.liveMinute))}</p>
                </div>
                <span class="pe-badge live">${escapeHtml(refreshedText)}</span>
            </div>
            <div class="live-stat-board">
                ${stats.rows.map(row => liveStatRow(row)).join('')}
            </div>
        </section>`;
    }

    function liveStatRow(row) {
        return `<div class="live-stat-row">
            <strong>${escapeHtml(row.homeValue || '--')}</strong>
            <span>${escapeHtml(row.label || displayLabel(row.code))}</span>
            <strong>${escapeHtml(row.awayValue || '--')}</strong>
        </div>`;
    }

    function renderOverviewSection(data) {
        const h = data.homeForm || {};
        const a = data.awayForm || {};
        return `<div class="stats-card-grid">
            ${overviewMetric('Home form', formBadges(h.formString), 'Recent form')}
            ${overviewMetric('Away form', formBadges(a.formString), 'Recent form')}
            ${overviewMetric('Home goals', data.homeLast5?.length ? `${valueOrDash(h.goalsScored)} scored / ${valueOrDash(h.goalsConceded)} conceded` : 'Unavailable', 'Recent form')}
            ${overviewMetric('Away goals', data.awayLast5?.length ? `${valueOrDash(a.goalsScored)} scored / ${valueOrDash(a.goalsConceded)} conceded` : 'Unavailable', 'Recent form')}
            ${overviewMetric('Venue', escapeHtml(data.fixture?.venue || 'Venue unavailable'), data.fixture?.status ? displayLabel(data.fixture.status) : 'Status unavailable')}
        </div>`;
    }

    function renderRankingSection(data) {
        const ranking = data.ranking;
        if (!ranking?.available || !ranking.rows?.length) {
            return emptyStatsState(ranking?.unavailableReason || 'Ranking data is not available for this competition yet.');
        }
        return `<div class="stats-section-heading">
            <div><h4>Ranking</h4><p>${escapeHtml(ranking.sourceLabel)} • ${escapeHtml(ranking.seasonLabel || 'Current season')}</p></div>
        </div>
        <div class="ranking-table-wrap">
            <table class="stats-ranking-table">
                <thead><tr><th>#</th><th>Team</th><th>MP</th><th>W</th><th>D</th><th>L</th><th>Goals</th><th>PTS</th><th>PPG</th><th>Last 5</th></tr></thead>
                <tbody>${ranking.rows.map(row => `<tr class="${row.currentFixtureTeam ? 'is-current' : ''}">
                    <td><span class="rank-pill">${row.position}</span></td>
                    <td class="ranking-team">${window.PitchEdge.teamMark(row.teamName, row.teamBadgeUrl || row.teamLogoUrl)}<span>${escapeHtml(row.teamName)}</span></td>
                    <td>${row.played}</td><td>${row.wins}</td><td>${row.draws}</td><td>${row.losses}</td>
                    <td>${row.goalsFor}:${row.goalsAgainst}</td><td><strong>${row.points}</strong></td><td>${row.pointsPerGame}</td>
                    <td><div class="form-badge-row compact">${(row.last5 || []).map(resultBadge).join('')}</div></td>
                </tr>`).join('')}</tbody>
            </table>
        </div>`;
    }

    function renderLastMatchesSection(data) {
        return `<div class="last-matches-grid">
            ${teamMatchesPanel(data.fixture?.homeTeam || 'Home team', data.fixture?.homeTeamBadgeUrl || data.fixture?.homeTeamLogoUrl, data.homeForm, data.homeLast5, data.homeLast5Home, 'Last 5 home')}
            ${teamMatchesPanel(data.fixture?.awayTeam || 'Away team', data.fixture?.awayTeamBadgeUrl || data.fixture?.awayTeamLogoUrl, data.awayForm, data.awayLast5, data.awayLast5Away, 'Last 5 away')}
        </div>`;
    }

    function teamMatchesPanel(teamName, imageUrl, form, matches, splitMatches, splitLabel) {
        const allMatches = matches || [];
        const split = splitMatches || [];
        if (!allMatches.length) return `<article class="stats-panel-card">${emptyStatsState(`No recent matches found for ${teamName}.`)}</article>`;
        return `<article class="stats-panel-card">
            <div class="team-overview-title">${window.PitchEdge.teamMark(teamName, imageUrl)}<div><span>Last 5 matches</span><strong>${escapeHtml(teamName)}</strong></div></div>
            <div class="stats-filter-chip-row">
                <button type="button" class="stats-filter-chip active" data-team-match-scope="all">Last 5 all</button>
                <button type="button" class="stats-filter-chip" data-team-match-scope="split">${escapeHtml(splitLabel)}</button>
            </div>
            <div class="mini-stat-grid">
                ${miniStat('W', allMatches.filter(m => m.result === 'W').length)}
                ${miniStat('D', allMatches.filter(m => m.result === 'D').length)}
                ${miniStat('L', allMatches.filter(m => m.result === 'L').length)}
                ${miniStat('Avg scored', form?.avgGoalsScored ?? '--')}
            </div>
            <div class="match-row-list" data-match-list="all">${allMatches.map(matchRow).join('')}</div>
            <div class="match-row-list hidden" data-match-list="split">${split.length ? split.map(matchRow).join('') : emptyStatsState(`${splitLabel} matches are not available yet.`)}</div>
        </article>`;
    }

    function renderPreMatchStatsSection(data) {
        const stats = data.preMatchStats;
        if (!stats?.overUnderGoals?.length) return emptyStatsState('Not enough match history to calculate this trend.');
        return `<div class="stats-section-heading"><div><h4>Pre-Match Stats</h4><p>Local match trends</p></div></div>
            <div class="over-under-list">${stats.overUnderGoals.map(row => `
                <article class="ou-row">
                    <h5>Over/Under ${escapeHtml(row.line)} Goals</h5>
                    ${ouTeam('Home team', row.home)}
                    ${ouTeam('Away team', row.away)}
                </article>
            `).join('')}</div>
            <div class="stats-card-grid">
                ${rateCard('Home BTTS Yes', stats.homeBttsYes)}
                ${rateCard('Away BTTS Yes', stats.awayBttsYes)}
                ${rateCard('Home clean sheets', stats.homeCleanSheets)}
                ${rateCard('Away clean sheets', stats.awayCleanSheets)}
                ${overviewMetric('Corners', stats.cornersAvailability, 'Local event/stat data')}
                ${overviewMetric('Cards', stats.cardsAvailability, 'Local event/stat data')}
            </div>`;
    }

    function renderTrendsSection(data) {
        const trends = data.trends || [];
        if (!trends.length) return emptyStatsState('Not enough match history to calculate this trend.');
        const categories = ['ALL', ...new Set(trends.map(t => t.category).filter(Boolean))];
        return `<div class="trend-filter-row">${categories.map((category, index) => `<button type="button" class="stats-filter-chip ${index === 0 ? 'active' : ''}" data-trend-filter="${escapeHtml(category)}">${displayLabel(category)}</button>`).join('')}</div>
            <div class="stats-card-grid">${trends.map(t => trendCard(t)).join('')}</div>`;
    }

    function trendCard(trend) {
        return `<article class="stats-panel-card" data-trend-card="${escapeHtml(trend.category || 'OTHER')}">
            <span>${escapeHtml(trend.label)}</span>
            <strong>${escapeHtml(userFacingTrendValue(trend.value))}</strong>
            <small>${displayLabel(trend.category)} • ${escapeHtml(trend.detail || '')}</small>
        </article>`;
    }

    function renderH2hSection(data) {
        const h2h = data.headToHead;
        if (!h2h?.matches?.length) return emptyStatsState('No head-to-head matches found in the local database.');
        return `<div class="stats-card-grid">
            ${overviewMetric('H2H matches', h2h.totalMatches, 'Local database')}
            ${overviewMetric('Home wins', h2h.homeWins, 'Current fixture home team')}
            ${overviewMetric('Draws', h2h.draws, 'Head-to-head')}
            ${overviewMetric('Away wins', h2h.awayWins, 'Current fixture away team')}
            ${overviewMetric('BTTS', h2h.bttsRate, 'Head-to-head trend')}
            ${overviewMetric('Over 2.5', h2h.over25Rate, 'Head-to-head trend')}
        </div>
        <div class="stats-card-grid h2h-streak-grid">
            ${overviewMetric('Average goals', h2h.avgGoals, 'Goals per H2H match')}
            ${h2hOccurrenceMetric('Over 1.5', h2h.over15, 'At least 2 total goals')}
            ${h2hOccurrenceMetric('Over 3.5', h2h.over35, 'At least 4 total goals')}
            ${h2hOccurrenceMetric('Under 3.5', h2h.under35, 'No more than 3 total goals')}
            ${h2hOccurrenceMetric('Under 4.5', h2h.under45, 'No more than 4 total goals')}
            ${h2hOccurrenceMetric('Home scored', h2h.homeScored, 'Current home team scored')}
            ${h2hOccurrenceMetric('Away scored', h2h.awayScored, 'Current away team scored')}
            ${h2hOccurrenceMetric('Under 2.5', h2h.under25, 'No more than 2 total goals')}
            ${h2hOccurrenceMetric('No clean sheet', h2h.noCleanSheet, 'Both teams scored')}
        </div>
        <div class="match-row-list h2h-list">${h2h.matches.map(m => `<div class="match-result-row">
            <span>${escapeHtml(m.date)} • ${escapeHtml(m.competition)}</span>
            <strong>${escapeHtml(m.homeTeam)} ${scoreBadge(m.score)} ${escapeHtml(m.awayTeam)}</strong>
            <span>${escapeHtml(displayLabel(m.winner))}</span>
        </div>`).join('')}</div>`;
    }

    function h2hOccurrenceMetric(label, occurrence, note) {
        if (!occurrence?.sampleSize) return '';
        return overviewMetric(label, `${occurrence.hits}/${occurrence.sampleSize}`, note);
    }

    function renderLimitations(limitations) {
        if (!limitations.length) return '';
        return `<ul class="stats-limitations">${limitations.map(item => `<li>${escapeHtml(item)}</li>`).join('')}</ul>`;
    }

    function overviewMetric(label, value, note) {
        return `<article class="stats-panel-card"><span>${escapeHtml(label)}</span><strong>${value}</strong><small>${escapeHtml(note || '')}</small></article>`;
    }

    function miniStat(label, value) {
        return `<div class="mini-stat"><span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong></div>`;
    }

    function rateCard(label, rate) {
        if (!rate || !rate.sampleSize) return overviewMetric(label, 'Unavailable', 'Not enough match history');
        return overviewMetric(label, rate.percent, 'Recent trend');
    }

    function ouTeam(label, stat) {
        if (!stat?.sampleSize) return `<div class="ou-team"><strong>${escapeHtml(label)}</strong><span>Unavailable</span></div>`;
        return `<div class="ou-team"><strong>${escapeHtml(label)}</strong><span>Under ${stat.underPercent}</span><span>Over ${stat.overPercent}</span></div>`;
    }

    function userFacingTrendValue(value) {
        const text = String(value || '').trim();
        const percentMatch = text.match(/\(([^()]*%)\)/);
        return percentMatch ? percentMatch[1] : text;
    }

    function matchRow(match) {
        return `<div class="match-result-row">
            <span>${escapeHtml(match.date)} • ${escapeHtml(match.competition)} • ${escapeHtml(displayLabel(match.homeOrAway))}</span>
            <strong>${escapeHtml(match.opponent)} ${scoreBadge(match.score)}</strong>
            ${resultBadge(match.result)}
        </div>`;
    }

    function scoreBadge(score) {
        return `<span class="score-badge">${escapeHtml(score)}</span>`;
    }

    function resultBadge(result) {
        const value = String(result || '').toUpperCase();
        const cls = value === 'W' ? 'win' : value === 'L' ? 'loss' : 'draw';
        return `<span class="form-badge ${cls}">${escapeHtml(value || '-')}</span>`;
    }

    function formBadges(formString) {
        if (!formString || formString === 'N/A') return '<span class="muted-inline">No form data</span>';
        return String(formString).split('-').map(resultBadge).join('');
    }

    function valueOrDash(value) {
        return value === null || value === undefined ? '--' : value;
    }

    function emptyStatsState(message) {
        return `<div class="stats-empty-state">${escapeHtml(message)}</div>`;
    }
});
