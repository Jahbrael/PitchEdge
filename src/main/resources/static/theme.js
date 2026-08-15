(function() {
    const PRODUCT_NAME = 'PitchEdge';
    const TAGLINE = 'Football predictions with evidence, probability, and market edge.';

    function storedTheme() {
        try {
            return localStorage.getItem('theme');
        } catch {
            return null;
        }
    }

    document.documentElement.setAttribute('data-theme', storedTheme() === 'dark' ? 'light' : (storedTheme() || 'light'));

    const labelOverrides = {
        FIFA_WORLD_CUP_2026: 'FIFA World Cup 2026',
        PREMIER_LEAGUE: 'Premier League',
        LA_LIGA: 'La Liga',
        SERIE_A: 'Serie A',
        BUNDESLIGA: 'Bundesliga',
        LIGUE_1: 'Ligue 1',
        CHAMPIONSHIP: 'Championship',
        CHAMPIONS_LEAGUE: 'Champions League',
        EREDIVISIE: 'Eredivisie',
        PRIMEIRA_LIGA: 'Primeira Liga',
        BELGIAN_PRO_LEAGUE: 'Belgian Pro League',
        SCOTTISH_PREMIERSHIP: 'Scottish Premiership',
        SUPER_LIG: 'Super Lig',
        ALLSVENSKAN: 'Allsvenskan',
        ELITESERIEN: 'Eliteserien',
        VEIKKAUSLIIGA: 'Veikkausliiga',
        LEAGUE_OF_IRELAND_PREMIER_DIVISION: 'League of Ireland Premier Division',
        LEAGUE_OF_IRELAND_FIRST_DIVISION: 'League of Ireland First Division',
        BESTA_DEILD: 'Besta Deild',
        MEISTRILIIGA: 'Meistriliiga',
        TOPLYGA: 'TOP Lyga',
        LATVIAN_VIRSLIGA: 'Latvian Virsliga',
        KAZAKHSTAN_PREMIER_LEAGUE: 'Kazakhstan Premier League',
        CHINESE_SUPER_LEAGUE: 'Chinese Super League',
        K_LEAGUE_1: 'K League 1',
        K_LEAGUE_2: 'K League 2',
        CANADIAN_PREMIER_LEAGUE: 'Canadian Premier League',
        BRAZILIAN_SERIE_B: 'Brazilian Serie B',
        BRAZILIAN_SERIE_D: 'Brazilian Serie D',
        UEFA_CHAMPIONS_LEAGUE: 'UEFA Champions League',
        UEFA_EUROPA_LEAGUE: 'UEFA Europa League',
        UEFA_EUROPA_CONFERENCE_LEAGUE: 'UEFA Europa Conference League',
        FA_CUP: 'FA Cup',
        EFL_CUP: 'EFL Cup',
        COPA_DEL_REY: 'Copa del Rey',
        COPPA_ITALIA: 'Coppa Italia',
        DFB_POKAL: 'DFB-Pokal',
        COUPE_DE_FRANCE: 'Coupe de France',
        DANISH_SUPERLIGA: 'Danish Superliga',
        SWISS_SUPER_LEAGUE: 'Swiss Super League',
        AUSTRIAN_BUNDESLIGA: 'Austrian Bundesliga',
        POLISH_EKSTRAKLASA: 'Polish Ekstraklasa',
        CZECH_FIRST_LEAGUE: 'Czech First League',
        CROATIAN_FOOTBALL_LEAGUE: 'Croatian Football League',
        SERBIAN_SUPERLIGA: 'Serbian SuperLiga',
        ROMANIAN_LIGA_I: 'Romanian Liga I',
        GREEK_SUPER_LEAGUE: 'Greek Super League',
        UKRAINIAN_PREMIER_LEAGUE: 'Ukrainian Premier League',
        SLOVAK_FIRST_LEAGUE: 'Slovak First Football League',
        LIGA_MX: 'Liga MX',
        MLS: 'Major League Soccer',
        USL_CHAMPIONSHIP: 'USL Championship',
        ARGENTINE_PRIMERA_DIVISION: 'Argentine Primera Division',
        COPA_LIBERTADORES: 'Copa Libertadores',
        COPA_SUDAMERICANA: 'Copa Sudamericana',
        BRAZILIAN_SERIE_A: 'Brazilian Serie A',
        BRAZILIAN_SERIE_C: 'Brazilian Serie C',
        CHILEAN_PRIMERA_DIVISION: 'Chilean Primera Division',
        COLOMBIAN_PRIMERA_A: 'Colombian Primera A',
        PERUVIAN_LIGA_1: 'Peruvian Liga 1',
        URUGUAYAN_PRIMERA_DIVISION: 'Uruguayan Primera Division',
        PARAGUAYAN_PRIMERA_DIVISION: 'Paraguayan Primera Division',
        ECUADORIAN_SERIE_A: 'Ecuadorian Serie A',
        SAUDI_PRO_LEAGUE: 'Saudi Pro League',
        UAE_PRO_LEAGUE: 'UAE Pro League',
        QATAR_STARS_LEAGUE: 'Qatar Stars League',
        J1_LEAGUE: 'J1 League',
        J2_LEAGUE: 'J2 League',
        A_LEAGUE_MEN: 'A-League Men',
        THAI_LEAGUE_1: 'Thai League 1',
        INDIAN_SUPER_LEAGUE: 'Indian Super League',
        INDONESIAN_LIGA_1: 'Indonesian Liga 1',
        UZBEKISTAN_SUPER_LEAGUE: 'Uzbekistan Super League',
        EGYPTIAN_PREMIER_LEAGUE: 'Egyptian Premier League',
        SOUTH_AFRICAN_PREMIER_DIVISION: 'South African Premier Division',
        MOROCCAN_BOTOLA_PRO: 'Moroccan Botola Pro',
        TUNISIAN_LIGUE_1: 'Tunisian Ligue Professionnelle 1',
        CAF_CHAMPIONS_LEAGUE: 'CAF Champions League',
        NIGERIAN_PREMIER_FOOTBALL_LEAGUE: 'Nigerian Premier Football League',
        VERY_HIGH: 'Very high',
        HIGH: 'High',
        MEDIUM: 'Medium',
        LOW: 'Low',
        UNRATED: 'Unrated',
        NO_ODDS: 'No odds',
        ODDS_AVAILABLE: 'Odds available',
        SHARPAPI_ODDS: 'Odds available',
        INSUFFICIENT_DATA: 'Insufficient data',
        PREDICTIONS_READY: 'Predictions ready',
        NO_PREDICTION_YET: 'No prediction yet',
        SCHEDULED: 'Scheduled',
        FINISHED: 'Finished',
        LIVE: 'Live',
        IN_PLAY: 'Live',
        HALF_TIME: 'Half time',
        POSTPONED: 'Postponed',
        CANCELLED: 'Cancelled',
        ABANDONED: 'Abandoned',
        HOME_WIN: 'Home Win',
        DRAW: 'Draw',
        AWAY_WIN: 'Away Win',
        HOME_OR_DRAW: 'Home or Draw',
        AWAY_OR_DRAW: 'Away or Draw',
        HOME_OR_AWAY: 'Home or Away',
        HOME_DRAW_NO_BET: 'Home Draw No Bet',
        AWAY_DRAW_NO_BET: 'Away Draw No Bet',
        OVER_0_5_GOALS: 'Over 0.5 Goals',
        OVER_1_5_GOALS: 'Over 1.5 Goals',
        OVER_2_5_GOALS: 'Over 2.5 Goals',
        OVER_3_5_GOALS: 'Over 3.5 Goals',
        OVER_4_5_GOALS: 'Over 4.5 Goals',
        UNDER_0_5_GOALS: 'Under 0.5 Goals',
        UNDER_1_5_GOALS: 'Under 1.5 Goals',
        UNDER_2_5_GOALS: 'Under 2.5 Goals',
        UNDER_3_5_GOALS: 'Under 3.5 Goals',
        UNDER_4_5_GOALS: 'Under 4.5 Goals',
        BTTS_YES: 'BTTS Yes',
        BTTS_NO: 'BTTS No',
        BOTH_TEAMS_TO_SCORE: 'Both Teams To Score',
        HOME_TEAM_CLEAN_SHEET: 'Home Clean Sheet',
        AWAY_TEAM_CLEAN_SHEET: 'Away Clean Sheet',
        CORNERS_OVER_8_5: 'Corners Over 8.5',
        CORNERS_OVER_9_5: 'Corners Over 9.5',
        CORNERS_OVER_10_5: 'Corners Over 10.5',
        CORNERS_UNDER_9_5: 'Corners Under 9.5',
        YELLOW_CARDS_OVER_3_5: 'Yellow Cards Over 3.5',
        YELLOW_CARDS_OVER_4_5: 'Yellow Cards Over 4.5',
        YELLOW_CARDS_UNDER_4_5: 'Yellow Cards Under 4.5',
        RED_CARD_YES: 'Red Card Yes',
        RED_CARD_NO: 'Red Card No',
        LEAGUE_SEASONS: 'League seasons',
        INTERNATIONAL_FOUR_YEAR_WINDOW: 'International four-year window',
        COMPLETE: 'Complete',
        PARTIAL: 'Partial',
        PENDING: 'Pending',
        IMPORT_PENDING: 'Import pending',
        IMPORTED: 'Imported',
        FAILED: 'Failed',
        LOWER_RISK: 'Lower risk',
        BALANCED: 'Balanced',
        VALUE: 'Value',
        LONGSHOT: 'Longshot',
        HIGH_CONFIDENCE: 'High confidence',
        MIXED_PORTFOLIO: 'Mixed portfolio',
        CUSTOM: 'Custom'
    };

    function label(value) {
        if (value === null || value === undefined || value === '') return '--';
        if (typeof value !== 'string') return String(value);
        if (labelOverrides[value]) return labelOverrides[value];
        return value
            .replace(/(\d)_5(?=_|$)/g, '$1.5')
            .replace(/_/g, ' ')
            .replace(/\b\w/g, char => char.toUpperCase())
            .replace(/\bAi\b/g, 'AI')
            .replace(/\bBtts\b/g, 'BTTS')
            .replace(/\bFifa\b/g, 'FIFA');
    }

    function escapeHtml(value) {
        return String(value ?? '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function logoSvg() {
        return `
            <svg viewBox="0 0 64 64" role="img" aria-label="PitchEdge logo" xmlns="http://www.w3.org/2000/svg">
                <path d="M13 10h38l6 10v24L32 58 7 44V20l6-10Z" fill="#ffffff" stroke="#0B5D3B" stroke-width="3" stroke-linejoin="round"/>
                <path d="M18 19h28v26H18V19Z" fill="#F6F2E8" stroke="#7A9486" stroke-width="2"/>
                <path d="M32 19v26M18 32h28" stroke="#7A9486" stroke-width="1.6" opacity=".8"/>
                <path d="M16 46 49 15" stroke="#0B5D3B" stroke-width="4" stroke-linecap="round"/>
                <path d="M21 39l8-7 7 4 10-13" fill="none" stroke="#17201D" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
                <circle cx="21" cy="39" r="3" fill="#087A55"/>
                <circle cx="36" cy="36" r="3" fill="#0B5D3B"/>
                <circle cx="46" cy="23" r="3" fill="#08462D"/>
            </svg>`;
    }

    function installFavicon() {
        const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">${logoSvg()
            .replace(/^[\s\S]*?<svg[^>]*>/, '')
            .replace('</svg>', '')}</svg>`;
        const link = document.createElement('link');
        link.rel = 'icon';
        link.type = 'image/svg+xml';
        link.href = `data:image/svg+xml,${encodeURIComponent(svg)}`;
        document.head.appendChild(link);
    }

    function pageMeta(path) {
        const map = {
            '/': ['Home', TAGLINE],
            '/index.html': ['Home', TAGLINE],
            '/fixtures.html': ['Fixtures', 'Browse fixtures, scores, prediction status, and odds coverage.'],
            '/predictions.html': ['Prediction Builder', 'Configure leagues, markets, filters, and strategy before generating a run.'],
            '/prediction-results.html': ['Prediction Results', 'Review generated picks with probability, confidence, odds, and edge.'],
            '/fixture-details.html': ['Match Center', 'Prediction evidence, all market probabilities, odds, and match stats.'],
            '/machine.html': ['PitchEdge Machine', 'Advanced filters for qualified predictions and value conditions.'],
            '/value-picks.html': ['Value Picks', 'Picks where model probability is higher than bookmaker implied probability.'],
            '/history.html': ['Prediction History', 'Reopen previous prediction runs and saved batches.'],
            '/leagues.html': ['Leagues', 'Coverage, season history, match counts, and data status.'],
            '/model-performance.html': ['Model Performance', 'Settled predictions, accuracy rows, and calibration status.']
        };
        return map[path] || [document.querySelector('h1')?.textContent?.trim() || 'Home', TAGLINE];
    }

    function navItems() {
        return [
            ['Home', '/', 'H'],
            ['Fixtures', '/fixtures.html', 'F'],
            ['Predictions', '/predictions.html', 'P'],
            ['Machine', '/machine.html', 'M'],
            ['Value Picks', '/value-picks.html', 'V'],
            ['History', '/history.html', 'H'],
            ['Stats', '/model-performance.html', 'S'],
            ['Leagues', '/leagues.html', 'L']
        ];
    }

    function installShell() {
        const path = window.location.pathname || '/';
        if (path.startsWith('/admin/') || path === '/login.html' || path === '/register.html') {
            return;
        }

        const oldHeader = document.querySelector('body > header.topbar, body > header.app-header');
        const fallbackTitle = oldHeader?.querySelector('h1')?.textContent?.trim();
        if (oldHeader) oldHeader.remove();
        document.querySelector('body > .sidebar')?.remove();

        const [resolvedTitle, subtitle] = pageMeta(path);
        document.title = `${resolvedTitle} - ${PRODUCT_NAME}`;
        document.body.classList.add('pe-shell-page');

        const activePath = path === '/index.html' ? '/' : path;
        const links = navItems().map(([name, href, icon]) => {
            const active = activePath === href || (href !== '/' && activePath.startsWith(href));
            return `<a class="pe-nav-link${active ? ' active' : ''}" href="${href}">
                <span class="pe-nav-icon">${icon}</span><span>${name}</span>
            </a>`;
        }).join('');

        const sidebar = document.createElement('aside');
        sidebar.className = 'pe-sidebar';
        sidebar.innerHTML = `
            <a class="pe-logo" href="/" aria-label="${PRODUCT_NAME} home">
                <span class="pe-logo-mark">${logoSvg()}</span>
                <span class="pe-logo-copy"><strong>${PRODUCT_NAME}</strong><span>${TAGLINE}</span></span>
            </a>
            <nav class="pe-sidebar-section" aria-label="Main navigation">
                <p class="pe-sidebar-label">Workspace</p>
                <div class="pe-nav-list">${links}</div>
            </nav>
            <div class="pe-nav-actions" style="display: flex; align-items: center; gap: 8px; flex: 0 0 auto; margin-left: auto; white-space: nowrap;">
                <a class="pe-btn secondary compact" href="/login.html" style="font-size: 13px; padding: 6px 14px; min-height: 34px;">Log In</a>
                <a class="pe-btn compact" href="/register.html" style="font-size: 13px; padding: 6px 14px; min-height: 34px; background: #fff; color: var(--brand-dark, #08462D); border-color: #fff;">Sign Up</a>
            </div>`;


        document.body.prepend(sidebar);
        document.querySelectorAll('body > main').forEach(main => main.classList.add('pe-main'));
        bindThemeToggle();
    }

    function bindThemeToggle() {
        document.querySelectorAll('.theme-toggle-btn').forEach(btn => {
            if (btn.dataset.pitchEdgeBound === 'true') return;
            btn.dataset.pitchEdgeBound = 'true';
            btn.addEventListener('click', () => {
                const root = document.documentElement;
                const currentTheme = root.getAttribute('data-theme') || 'light';
                const nextTheme = currentTheme === 'dark' ? 'light' : 'dark';
                root.setAttribute('data-theme', nextTheme);
                try {
                    localStorage.setItem('theme', nextTheme);
                } catch {
                    // Theme persistence is optional.
                }
            });
        });
    }

    function bindArtworkFallbacks() {
        if (document.documentElement.dataset.pitchEdgeArtworkBound === 'true') return;
        document.documentElement.dataset.pitchEdgeArtworkBound = 'true';
        document.addEventListener('load', event => {
            const image = event.target;
            if (image instanceof HTMLImageElement && image.closest('.pe-team-mark')) {
                image.closest('.pe-team-mark').classList.add('has-image');
            }
        }, true);
        document.addEventListener('error', event => {
            const image = event.target;
            if (image instanceof HTMLImageElement && image.closest('.pe-team-mark')) {
                const mark = image.closest('.pe-team-mark');
                image.remove();
                mark.classList.remove('has-image');
            }
        }, true);
    }

    function numberValue(value) {
        if (value === null || value === undefined || value === '') return null;
        const parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : null;
    }

    function percent(value, digits = 1) {
        const parsed = numberValue(value);
        if (parsed === null) return '--';
        return `${(parsed * 100).toFixed(digits)}%`;
    }

    function decimal(value, digits = 2) {
        const parsed = numberValue(value);
        if (parsed === null) return '--';
        return parsed.toFixed(digits);
    }

    function signedPercent(value, digits = 1) {
        const parsed = numberValue(value);
        if (parsed === null) return '--';
        const sign = parsed > 0 ? '+' : '';
        return `${sign}${(parsed * 100).toFixed(digits)}%`;
    }

    function dateTime(value, options) {
        if (!value) return '--';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return String(value);
        return new Intl.DateTimeFormat(undefined, options || {
            month: 'short',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        }).format(date);
    }

    function isoDate(date) {
        return date.toISOString().slice(0, 10);
    }

    async function apiJson(url, options) {
        const response = await fetch(url, {
            cache: 'no-store',
            headers: { Accept: 'application/json', 'Cache-Control': 'no-cache', ...(options?.headers || {}) },
            ...options
        });
        if (!response.ok) {
            const text = await response.text();
            throw new Error(text || `Request failed with HTTP ${response.status}`);
        }
        return response.status === 204 ? null : response.json();
    }

    function flattenSelections(response) {
        return (response?.batches || []).flatMap(batch => batch.selections || []);
    }

    function selectionProbability(selection) {
        return selection?.tunedModelProbability
            ?? selection?.calibratedProbability
            ?? selection?.probability
            ?? selection?.rawModelProbability
            ?? null;
    }

    function detailsUrl(selection, runId, modelVersion) {
        const params = new URLSearchParams();
        params.set('matchId', selection.matchId);
        params.set('modelVersion', selection.modelVersion || modelVersion || 'v1');
        if (selection.marketCode) {
            params.set('recommended', selection.marketCode);
            params.set('recommendedMarketCode', selection.marketCode);
        }
        if (runId) params.set('runId', runId);
        if (selection.selectionId) params.set('selectionId', selection.selectionId);
        return `/fixture-details.html?${params.toString()}`;
    }

    function emptyState(title, message) {
        return `<div class="pe-empty pe-card">
            <span class="pe-logo-mark">${logoSvg()}</span>
            <h3>${escapeHtml(title)}</h3>
            <p>${escapeHtml(message)}</p>
        </div>`;
    }

    function loadingState(count = 3) {
        return Array.from({ length: count }, () => '<div class="pe-skeleton"></div>').join('');
    }

    function confidenceClass(value) {
        if (value === 'VERY_HIGH' || value === 'HIGH') return 'good';
        if (value === 'LOW' || value === 'UNRATED') return 'warn';
        return 'accent';
    }

    function initials(value) {
        const text = String(value || '').trim();
        if (!text) return 'PE';
        const parts = text.split(/\s+/).filter(Boolean);
        const first = parts[0]?.[0] || '';
        const second = parts.length > 1 ? parts[parts.length - 1][0] : (parts[0]?.[1] || '');
        return `${first}${second}`.toUpperCase();
    }

    function artworkUrl(value) {
        const raw = String(value || '').trim();
        if (!raw) return '';
        try {
            const parsed = new URL(raw, window.location.origin);
            const host = parsed.hostname.toLowerCase();
            const sportsDbHosts = [`r2.the${'sportsdb.com'}`, `www.the${'sportsdb.com'}`, `the${'sportsdb.com'}`];
            if (parsed.origin === window.location.origin) {
                return parsed.pathname + parsed.search + parsed.hash;
            }
            if (parsed.protocol === 'https:' && sportsDbHosts.includes(host)) {
                return `/api/v1/artwork/proxy?url=${encodeURIComponent(parsed.href)}`;
            }
            return raw;
        } catch (error) {
            return '';
        }
    }

    function teamMark(name, imageUrl) {
        const safeName = escapeHtml(name || 'Team');
        const safeUrl = imageUrl ? escapeHtml(artworkUrl(imageUrl)) : '';
        const markInitials = escapeHtml(initials(name));
        if (safeUrl) {
            return `<span class="pe-team-mark" aria-label="${safeName}" data-initials="${markInitials}">
                <span class="pe-team-initials" aria-hidden="true">${markInitials}</span>
                <img src="${safeUrl}" alt="" loading="lazy" decoding="async">
            </span>`;
        }
        return `<span class="pe-team-mark" aria-label="${safeName}" data-initials="${markInitials}"><span class="pe-team-initials" aria-hidden="true">${markInitials}</span></span>`;
    }

    function metric(labelText, value, note) {
        return `<div class="pe-metric">
            <span>${escapeHtml(labelText)}</span>
            <strong>${escapeHtml(value ?? '--')}</strong>
            ${note ? `<small>${escapeHtml(note)}</small>` : ''}
        </div>`;
    }

    function formatScore(item) {
        if (!item) return 'vs';
        const h = item.homeScore;
        const a = item.awayScore;
        if (h !== null && h !== undefined && a !== null && a !== undefined) {
            return `${h} - ${a}`;
        }
        return 'vs';
    }

    function formatLiveBadge(status, liveMinute) {
        const s = String(status || '').toUpperCase();
        if (s === 'FINISHED') return 'Finished';
        if (s === 'HALF_TIME' || s === 'HT') return 'Half time';
        if (s === 'POSTPONED') return 'Postponed';
        if (s === 'CANCELLED') return 'Cancelled';
        if (s === 'ABANDONED') return 'Abandoned';
        if (s === 'LIVE' || s === 'IN_PLAY') {
            if (!liveMinute) return 'Live';
            const min = String(liveMinute).trim();
            if (min === 'P' || min.toLowerCase() === 'penalties') return 'Live (Penalties)';
            if (min === 'HT' || min.toLowerCase() === 'half time') return 'Live (HT)';
            if (min.toLowerCase() === 'extra time') return 'Live (ET)';
            if (/^\d+$/.test(min)) return `Live ${min}'`;
            return min.startsWith('Live') ? min : `Live ${min}`;
        }
        return label(status);
    }

    window.formatEnumLabel = label;
    window.PitchEdge = {
        PRODUCT_NAME,
        TAGLINE,
        logoSvg,
        label,
        escapeHtml,
        percent,
        signedPercent,
        decimal,
        dateTime,
        isoDate,
        apiJson,
        flattenSelections,
        selectionProbability,
        detailsUrl,
        emptyState,
        loadingState,
        confidenceClass,
        initials,
        teamMark,
        artworkUrl,
        metric,
        formatScore,
        formatLiveBadge
    };

    installFavicon();
    document.addEventListener('DOMContentLoaded', () => {
        installShell();
        bindThemeToggle();
        bindArtworkFallbacks();
    });
})();
