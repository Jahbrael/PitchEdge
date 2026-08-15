package com.betai.frontend;

import com.betai.domain.market.MarketCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class PitchEdgeStaticAssetsTest {

    private static final Path STATIC_ROOT = Path.of("src/main/resources/static");
    private static final Path SOURCE_ROOT = Path.of("src/main/java");
    private static final List<String> USER_ASSETS = List.of(
            "index.html",
            "dashboard.js",
            "fixtures.html",
            "fixtures.js",
            "predictions.html",
            "predictions.js",
            "predictions.css",
            "prediction-results.html",
            "prediction-results.js",
            "prediction-results.css",
            "fixture-details.html",
            "fixture-details.js",
            "fixture-details.css",
            "history.html",
            "history.js",
            "value-picks.html",
            "value-picks.js",
            "machine.html",
            "machine.js",
            "leagues.html",
            "leagues.js",
            "model-performance.html",
            "model-performance.js",
            "theme.css",
            "theme.js"
    );

    @Test
    void appShellUsesPitchEdgeIdentityAndMatchdayTokens() throws IOException {
        String themeJs = read("theme.js");
        String themeCss = read("theme.css");

        assertThat(themeJs).contains("PitchEdge", "Football predictions with evidence, probability, and market edge.");
        assertThat(themeJs).contains("Home", "Fixtures", "Predictions", "Machine", "Value Picks", "Leagues");
        assertThat(themeJs).doesNotContain("Operations", "pe-admin-link", "Admin</span>");
        assertThat(themeCss).contains("#f6f2e8", "#ffffff", "#0b5d3b", "#c89432", "#087a55");
        assertThat(themeCss).contains("--top-nav-height", "--premium-soft", "background: var(--brand-dark)");
        assertThat(themeCss).doesNotContain("#5630c8", "#ec005d", "linear-gradient(90deg, #43298f");
    }

    @Test
    void userFacingAssetsDoNotCallFullAutomationOrExternalRefreshEndpoints() throws IOException {
        String combined = combinedUserAssets();

        assertThat(combined)
                .doesNotContain("/api/v1/admin/pipeline/run")
                .doesNotContain("/api/v1/admin/refresh/daily")
                .doesNotContain("/api/v1/admin/odds/pre-match/refresh")
                .doesNotContain("/api/v1/admin/predictions/generate")
                .doesNotContain("/api/v1/admin/integrations/thesportsdb/league-season/import")
                .doesNotContain("/api/v1/admin/automation/runNow")
                .doesNotContain("/api/v1/fixtures/scores/refresh")
                .doesNotContain("Refresh Scores")
                .doesNotContain("Refresh scores");
    }

    @Test
    void predictionResultsRequiresExplicitRunIdAndDoesNotShowLatestCachedRun() throws IOException {
        String script = read("prediction-results.js");

        assertThat(script).contains("No prediction run selected");
        assertThat(script).doesNotContain("betai_latest_run_id");
        assertThat(script).doesNotContain("localStorage.getItem");
    }

    @Test
    void fixtureDetailsDoesNotFabricateRecommendedMarketProbability() throws IOException {
        String script = read("fixture-details.js");

        assertThat(script).contains("Recommended by this run, but the detailed market calculation was not available");
        assertThat(script).doesNotContain("0.75");
        assertThat(script).doesNotContain("0.68");
    }

    @Test
    void normalUserShellDoesNotExposeAdminOrOperationsNavigation() throws IOException {
        String themeJs = read("theme.js");
        String combined = combinedUserAssets();

        assertThat(themeJs).doesNotContain("Operations", "pe-admin-link", "/admin/dashboard.html");
        assertThat(combined)
                .doesNotContain("href=\"/admin/dashboard.html\"")
                .doesNotContain("Operations")
                .doesNotContain("admin-link");
    }

    @Test
    void homePageUsesFriendlyWordingAndNoProviderInternals() throws IOException {
        String dashboardJs = read("dashboard.js");
        String indexHtml = read("index.html");

        assertThat(indexHtml).contains("Today at a Glance", "Today’s Best Picks", "Matches to Watch", "Explore PitchEdge", "Recent Activity");
        assertThat(indexHtml).doesNotContain("Ready to Review", "Data Status", "id=\"dataStatusList\"", "id=\"dataHealthList\"");
        assertThat(dashboardJs).contains("Fixtures Today", "Live Fixtures", "High Confidence", "Value Picks", "View Details");
        assertThat(dashboardJs).doesNotContain("Score Status", "Scores status", "Match stats", "Need predictions", "Odds coverage", "Predictions ready", "Ready to review", "renderHealth", "Football data", "Latest local score refresh recorded", "Dashboard unavailable");

        assertThat(dashboardJs)
                .doesNotContain("TheSportsDB local import")
                .doesNotContain("SharpAPI local odds snapshots")
                .doesNotContain("No local score refresh recorded")
                .doesNotContain("SharpAPI odds");
    }

    @Test
    void userPagesUseProductLanguageAndHideProviderInternals() throws IOException {
        String combined = combinedUserAssets();

        assertThat(combined)
                .doesNotContain("TheSportsDB local import")
                .doesNotContain("SharpAPI local odds snapshots")
                .doesNotContain("SharpAPI odds")
                .doesNotContain("Source Health")
                .doesNotContain("raw snapshot")
                .doesNotContain("Local odds")
                .doesNotContain("Source</span>")
                .doesNotContain("Run ID")
                .doesNotContain("run ID");

        assertThat(read("value-picks.js"))
                .contains("Model probability", "Implied probability", "Bookmaker", "No slip selections");
        assertThat(read("leagues.js"))
                .contains("Seasons available", "Matches tracked", "Data coverage")
                .doesNotContain("sourceUsed");
        assertThat(read("history.js")).contains("Create Prediction Run", "Saved batches are private");
    }

    @Test
    void userCardsUseSharedPolishedComponentsAndSafeArtworkFallbacks() throws IOException {
        String themeJs = read("theme.js");
        String dashboardJs = read("dashboard.js");
        String fixtureDetailsJs = read("fixture-details.js");
        String predictionResultsJs = read("prediction-results.js");
        String predictionResultsCss = read("prediction-results.css");

        assertThat(themeJs)
                .contains("function teamMark", "function artworkUrl", "bindArtworkFallbacks", "/api/v1/artwork/proxy", "loading=\"lazy\"", "decoding=\"async\"", "pe-team-initials", "alt=\"\"", "addEventListener('error'")
                .doesNotContain(" badge\"")
                .doesNotContain("onerror=")
                .doesNotContain("onload=");
        assertThat(read("theme.css")).contains(".pe-team-mark.has-image .pe-team-initials", "object-fit: contain");
        assertThat(dashboardJs).contains("P.teamMark", "pe-fixture-teams", "pe-score-pill", "homeTeamLogoUrl", "awayTeamLogoUrl");
        assertThat(fixtureDetailsJs).contains("homeTeamBadgeUrl", "homeTeamLogoUrl", "awayTeamBadgeUrl", "awayTeamLogoUrl", "teamMark");
        assertThat(predictionResultsJs).contains("prediction-result-card", "Model probability", "Value edge");
        assertThat(predictionResultsCss)
                .contains(".card-pick-box", "background: var(--brand-soft)", ".card-pick-team")
                .doesNotContain("rgba(86, 48, 200, 0.10)")
                .doesNotContain("background: rgba(8, 11, 24, 0.46)");
    }

    @Test
    void migrationAndEntitiesDeclareLocalArtworkStorage() throws IOException {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V32__add_artwork_fields.sql"));
        String team = Files.readString(SOURCE_ROOT.resolve("com/betai/domain/team/Team.java"));
        String league = Files.readString(SOURCE_ROOT.resolve("com/betai/domain/league/League.java"));

        assertThat(migration).contains(
                "badge_url",
                "logo_url",
                "banner_url",
                "equipment_url",
                "fanart_url",
                "poster_url",
                "trophy_url"
        );
        assertThat(team).contains("badgeUrl", "logoUrl", "bannerUrl", "equipmentUrl", "fanartUrl");
        assertThat(league).contains("badgeUrl", "logoUrl", "bannerUrl", "posterUrl", "trophyUrl", "fanartUrl");
    }

    @Test
    void verifiedLeagueMigrationAddsImportPendingTheSportsDbCatalogEntries() throws IOException {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V33__add_verified_thesportsdb_leagues.sql"));

        assertThat(migration).contains(
                "UEFA_CHAMPIONS_LEAGUE",
                "UEFA Europa Conference League",
                "NIGERIAN_PREMIER_FOOTBALL_LEAGUE",
                "external_source_mappings",
                "'THESPORTSDB'",
                "'LEAGUE'",
                "'RESOLVED'",
                "false"
        );
        assertThat(migration).contains("4480", "5071", "4827");
    }

    @Test
    void predictionBuilderUsesLocalLeagueCatalogAndImportPendingState() throws IOException {
        String html = read("predictions.html");
        String script = read("predictions.js");

        assertThat(html).contains("leagueSearch", "leagueChoiceGrid", "leagueSelectionCount");
        assertThat(script).contains(
                "/api/v1/platform/leagues",
                "predictionSelectable",
                "Import pending",
                "selectedLeagueCodes",
                "leagueGroup"
        );
        assertThat(script).doesNotContain("thesportsdb.com", "api/v2/json");
    }

    @Test
    void predictionBuilderListsEveryEnabledBackendMarket() throws IOException {
        String html = read("predictions.html");
        String script = read("predictions.js");
        String theme = read("theme.js");
        List<String> listedMarketCodes = Pattern.compile("name=\\\"marketCodes\\\" value=\\\"([A-Z0-9_]+)\\\"")
                .matcher(html)
                .results()
                .map(match -> match.group(1))
                .toList();

        for (MarketCode marketCode : MarketCode.values()) {
            String checkboxValue = "value=\"" + marketCode.name() + "\"";
            if (marketCode.isEnabled()) {
                assertThat(html).contains(checkboxValue);
            } else {
                assertThat(html).doesNotContain(checkboxValue);
            }
        }
        assertThat(listedMarketCodes)
                .hasSize(74)
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(MarketCode.values())
                        .filter(MarketCode::isEnabled)
                        .map(Enum::name)
                        .toList());
        assertThat(html).contains("marketSearch", "marketVisibleCount", "74 markets");
        assertThat(script).contains("initializeMarketSearch", "filterMarkets");
        assertThat(theme).contains(".replace(/(\\d)_5(?=_|$)/g, '$1.5')");
    }

    @Test
    void userApiResponsesExposeLocalArtworkWithoutFrontendTheSportsDbCalls() throws IOException {
        String fixtureResponse = Files.readString(SOURCE_ROOT.resolve("com/betai/api/dto/FixtureBrowserResponse.java"));
        String selectionResponse = Files.readString(SOURCE_ROOT.resolve("com/betai/api/dto/PredictionSelectionResponse.java"));
        String detailsResponse = Files.readString(SOURCE_ROOT.resolve("com/betai/api/dto/details/FixturePredictionDetailsResponse.java"));
        String platformController = Files.readString(SOURCE_ROOT.resolve("com/betai/api/PlatformController.java"));
        String combined = combinedUserAssets();

        assertThat(fixtureResponse).contains(
                "homeTeamBadgeUrl", "homeTeamLogoUrl", "awayTeamBadgeUrl", "awayTeamLogoUrl", "leagueBadgeUrl", "leagueLogoUrl"
        );
        assertThat(selectionResponse).contains(
                "homeTeamBadgeUrl", "homeTeamLogoUrl", "awayTeamBadgeUrl", "awayTeamLogoUrl", "leagueBadgeUrl", "leagueLogoUrl"
        );
        assertThat(detailsResponse).contains(
                "homeTeamBadgeUrl", "homeTeamLogoUrl", "awayTeamBadgeUrl", "awayTeamLogoUrl", "leagueBadgeUrl", "leagueLogoUrl"
        );
        assertThat(platformController).contains("league.getBadgeUrl()", "league.getLogoUrl()", "league.getPosterUrl()");
        assertThat(Files.readString(SOURCE_ROOT.resolve("com/betai/security/RateLimitingFilter.java")))
                .contains("path.startsWith(\"/api/v1/artwork/\")");
        assertThat(combined)
                .doesNotContain("thesportsdb.com")
                .doesNotContain("api/v2/json")
                .doesNotContain("THESPORTSDB_API_KEY");
    }

    @Test
    void fixtureDetailsKeepsTopLevelTabsAndAddsRichMatchStatsSections() throws IOException {
        String html = read("fixture-details.html");
        String script = read("fixture-details.js");
        String styles = read("fixture-details.css");
        String detailsResponse = Files.readString(SOURCE_ROOT.resolve("com/betai/api/dto/details/FixturePredictionDetailsResponse.java"));

        assertThat(html).contains(
                "Prediction & Market Probabilities",
                "Match Stats & Supporting Evidence",
                "id=\"vsBadge\"",
                "id=\"matchStatsRoot\""
        );
        assertThat(script).contains(
                "Overview",
                "Ranking",
                "Last Matches",
                "Pre-Match Stats",
                "Trends",
                "H2H",
                "Live Match Stats",
                "renderLiveStatsSection",
                "liveStatRow",
                "classList.add('has-score')",
                "No head-to-head matches found in the local database.",
                "Ranking data is not available for this competition yet.",
                "Last 5 all",
                "data-trend-filter",
                "H2H matches",
                "Home wins",
                "Draws",
                "Away wins",
                "BTTS",
                "Over 2.5",
                "Average goals",
                "Over 1.5",
                "Over 3.5",
                "Under 3.5",
                "Under 4.5",
                "Home scored",
                "Away scored",
                "Under 2.5",
                "No clean sheet",
                "occurrence.hits}/${occurrence.sampleSize"
        );
        assertThat(html)
                .doesNotContain("Sample Size", "dataCompleteness", "Model Info", "modelVersionDisplay");
        assertThat(script)
                .contains("userFacingMarketExplanation", "userFacingTrendValue", "calibrated using", "settled selections")
                .doesNotContain("overviewMetric('Data quality'")
                .doesNotContain("stats.sampleLabel")
                .doesNotContain("${rate.percent} — ${rate.count}/${rate.sampleSize}")
                .doesNotContain("${stat.underPercent} — ${stat.underCount}/${stat.sampleSize}")
                .doesNotContain("${stat.overPercent} — ${stat.overCount}/${stat.sampleSize}");
        assertThat(styles).contains(
                ".match-stats-overview",
                ".live-match-stats-panel",
                ".live-stat-board",
                ".live-stat-row",
                ".vs-badge.has-score",
                ".stats-subtabs",
                ".stats-ranking-table",
                ".last-matches-grid",
                ".over-under-list",
                ".h2h-streak-grid",
                ".form-badge.win",
                ".score-badge",
                ".stats-empty-state"
        );
        assertThat(detailsResponse).contains(
                "RankingDto",
                "PreMatchStatsDto",
                "LiveMatchStatsDto",
                "LiveStatRowDto",
                "homeLast5Home",
                "awayLast5Away",
                "TrendDto",
                "MatchPreviewDto"
        );
        assertThat(script).doesNotContain("thesportsdb.com", "api/v2/json", "sharpapi.com", "SHARPAPI", "THESPORTSDB_API_KEY");
    }

    @Test
    void adminPageRemainsSeparateAndShowsTechnicalDiagnostics() throws IOException {
        String adminHtml = Files.readString(STATIC_ROOT.resolve("admin/dashboard.html"));
        String adminJs = Files.readString(STATIC_ROOT.resolve("admin/dashboard.js"));
        String adminCss = Files.readString(STATIC_ROOT.resolve("admin/dashboard.css"));

        assertThat(adminHtml).contains(
                "Operational Dashboard", "Admin Access", "Source Health", "Data Status",
                "Automation Progress", "automationProgressBar", "automationCurrentStep",
                "automationStartTime", "automationLastUpdateTime", "automationCompletionTime"
        );
        assertThat(adminJs).contains(
                "X-BETAI-ADMIN-KEY", "QUARANTINED", "DEGRADED", "renderDataStatus",
                "/api/v1/admin/automation/progress", "progress.progressPercentage",
                "progress.completedSteps", "progress.totalSteps", "Fully Completed",
                "progress.errorMessage", "step.failureReason"
        );
        assertThat(adminCss).contains(".automation-progress-bar", ".automation-progress-details", ".automation-progress-error");
    }

    @Test
    void predictionFixtureCardsRenderOnlyBackendSuppliedIndicators() throws IOException {
        String builderScript = read("predictions.js");
        String resultsScript = read("prediction-results.js");
        String themeCss = read("theme.css");
        String predictionResponse = Files.readString(SOURCE_ROOT.resolve("com/betai/api/dto/PredictionResponse.java"));

        assertThat(predictionResponse).contains("fixtureIndicators", "PredictionFixtureIndicatorsResponse");
        assertThat(builderScript).contains(
                "lastResponse?.fixtureIndicators?.[selection.selectionId]",
                "H2H", "Partial Season", "H Form", "A Form",
                "homeLeaguePosition", "awayLeaguePosition"
        );
        assertThat(resultsScript).contains(
                "runData.fixtureIndicators || {}", "fixtureIndicatorMarkup",
                "homeRecentFormPercentage", "awayRecentFormPercentage"
        );
        assertThat(builderScript + resultsScript).doesNotContain("Math.random", "fakeForm", "fakeH2h");
        assertThat(themeCss).contains(".pe-fixture-indicators", ".pe-fixture-indicator.warning", "flex-wrap: wrap");
    }

    @Test
    void adminApisRemainProtectedByAdminRole() throws IOException {
        String securityConfig = Files.readString(SOURCE_ROOT.resolve("com/betai/config/SecurityConfig.java"));

        assertThat(securityConfig).contains(".requestMatchers(\"/api/v1/admin/**\").hasRole(\"ADMIN\")");
    }

    @Test
    void homePageUsesBalancedLayoutAndFullWidthLiveAndFinishedGrid() throws IOException {
        String indexHtml = read("index.html");
        String themeCss = read("theme.css");

        assertThat(indexHtml).contains("Today’s Best Picks", "Recent Activity", "Matches to Watch", "Explore PitchEdge");
        assertThat(indexHtml).doesNotContain("Ready to Review", "id=\"valuePicksList\"", "Data Status");
        assertThat(indexHtml).contains("class=\"pe-grid-2\" id=\"fixturePreviewList\"");
        assertThat(indexHtml).contains(
                "pe-home-feature-grid",
                "pe-home-main-column",
                "pe-home-side-panel",
                "pe-machine-filter-panel",
                "pe-machine-filter-card",
                "pe-machine-filter-sections",
                "pe-machine-filter-group",
                "pe-machine-filter-footer",
                "market=OVER_2_5_GOALS",
                "boolean=ODDS"
        );
        assertThat(themeCss).contains(
                "pe-home-hero",
                "pe-home-tool-grid",
                "pe-home-feature-grid",
                "pe-home-main-column",
                "pe-machine-filter-summary",
                "pe-machine-filter-grid",
                "pe-machine-filter-card",
                "pe-machine-filter-sections",
                "pe-filter-chip-list",
                "pe-machine-filter-footer",
                "body.pe-home-page",
                "background: #ffffff;",
                "#fixturePreviewList",
                "minmax(min(100%, 420px), 1fr)",
                "grid-template-columns: minmax(0, 1fr) minmax(64px, auto) minmax(0, 1fr);"
        );
        assertThat(themeCss).doesNotContain("radial-gradient(circle, rgba(236, 0, 93");
    }

    @Test
    void homePageMeetsCleanSaaSProductRequirements() throws IOException {
        String indexHtml = read("index.html");
        String dashboardJs = read("dashboard.js");
        String themeCss = read("theme.css");

        // 1. Homepage renders successfully
        assertThat(indexHtml).contains("Matchday Analysis", "Today at a Glance", "Today’s Best Picks", "Matches to Watch", "Explore PitchEdge");

        // 2. Solid racing-green navigation replaces the former decorative gradient.
        assertThat(themeCss).contains(".pe-app-header", "background: var(--brand-dark)");

        // 3. Removed Today at a Glance cards do not appear
        assertThat(indexHtml).doesNotContain("Score Status", "Match Stats", "Need Predictions", "Odds Coverage", "Prediction Ready");
        assertThat(dashboardJs).doesNotContain("Score Status", "Scores status", "Match stats", "Need predictions", "Odds coverage", "Predictions ready", "Ready to review");

        // 4. Homepage CTAs link to correct routes
        assertThat(indexHtml).contains("href=\"/predictions.html\"", "href=\"/fixtures.html\"", "href=\"/value-picks.html\"", "href=\"/history.html\"");

        // 5. Explore PitchEdge cards link correctly
        assertThat(indexHtml).contains("href=\"/machine.html\"", "href=\"/model-performance.html\"", "href=\"/leagues.html\"");

        // 6. How PitchEdge Works section renders
        assertThat(indexHtml).contains("id=\"howItWorks\"", "How PitchEdge Works", "Predictions are generated from local football data", "Match Center shows supporting evidence", "Responsible Analysis Note");

        // 7. Footer renders
        assertThat(indexHtml).contains("class=\"pe-home-footer\"", "Product", "Analysis", "Information", "Responsible Use");

        // 8. Recent Activity is compact and handles empty state
        assertThat(indexHtml).contains("class=\"pe-run-list\"", "View History");
        assertThat(dashboardJs).contains("compact-row", "No recent prediction runs yet.", "Generate predictions to create a run that can be reopened here.");

        // 9. Matches to Watch renders with logos/fallbacks
        assertThat(dashboardJs).contains("P.teamMark", "homeTeamLogoUrl", "awayTeamLogoUrl", "No matches to watch right now.");

        // 10. No admin/provider/internal wording appears on homepage
        assertThat(indexHtml + dashboardJs).doesNotContain("Source health", "TheSportsDB import", "SharpAPI snapshots", "Pipeline", "Automation", "Raw snapshots", "API key", "Local import", "Backend run ID");

        // 11. No frontend calls to TheSportsDB or SharpAPI
        assertThat(indexHtml + dashboardJs).doesNotContain("thesportsdb.com", "sharpapi.com");

        // 12. No secrets are exposed
        assertThat(indexHtml + dashboardJs).doesNotContain("THESPORTSDB_API_KEY", "SHARPAPI_KEY");

        // 13. No horizontal overflow & clean styling
        assertThat(themeCss).doesNotContain("linear-gradient(135deg, #0f172a");
    }


    private static String combinedUserAssets() throws IOException {
        StringBuilder builder = new StringBuilder();
        for (String asset : USER_ASSETS) {
            builder.append(read(asset)).append('\n');
        }
        return builder.toString();
    }

    private static String read(String asset) throws IOException {
        return Files.readString(STATIC_ROOT.resolve(asset));
    }
}
