package com.betai.service;

import com.betai.config.SharpApiProperties;
import com.betai.config.ApiFootballProperties;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.source.RenderMode;
import com.betai.domain.source.SourceTarget;
import com.betai.domain.source.SourceType;
import com.betai.repository.LeagueRepository;
import com.betai.repository.MarketDefinitionRepository;
import com.betai.repository.SourceTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ReferenceDataInitializer implements ApplicationRunner {

    private static final String EUROPEAN_CORE_SEASON = "2025/2026";
    private static final String ACTIVE_CALENDAR_SEASON = "2026";
    private static final String FOOTBALL_DATA_USER_AGENT = "BetAIResearchBot/0.1 (+local-development)";
    private static final String FOOTBALL_DATA_MAIN_FIXTURES_URL = "https://www.football-data.co.uk/fixtures.csv";
    private static final String FOOTBALL_DATA_NEW_FIXTURES_URL = "https://www.football-data.co.uk/new_league_fixtures.csv";
    private static final String FOOTBALL_DATA_2025_2026_BASE_URL = "https://www.football-data.co.uk/mmz4281/2526/";
    private static final String FOOTBALL_DATA_WORLD_CUP_2026_WORKBOOK_URL = "https://www.football-data.co.uk/WorldCup2026.xlsx";
    private static final String THE_STATS_API_WORLD_CUP_2026_FIXTURES_URL = "https://www.thestatsapi.com/world-cup/data/fixtures.json";
    private static final String OPENFOOTBALL_WORLD_CUP_2026_JSON_URL = "https://raw.githubusercontent.com/openfootball/worldcup.json/master/2026/worldcup.json";
    private static final String THESPORTSDB_EVENTS_SEASON_URL = "https://www.thesportsdb.com/api/v1/json/123/eventsseason.php?id=%s&s=%s";
    private static final String THESPORTSDB_NEXT_EVENTS_URL = "https://www.thesportsdb.com/api/v1/json/123/eventsnextleague.php?id=%s";
    private static final String API_FOOTBALL_FIXTURES_URL = "{apiFootballBaseUrl}/fixtures?league=%s&season=%s&timezone={apiFootballTimezone}";
    private static final String SHARPAPI_ODDS_URL = "{sharpApiBaseUrl}/odds?league=%s&limit=200";
    private static final String SGODDS_DATA_PAGE_URL = "https://sgodds.com/football/data";
    private static final String ALLSVENSKAN_FULL_FIXTURES_URL = "https://allsvenskanfotboll.se/spelschema";
    private static final String ELITESERIEN_FULL_FIXTURES_URL = "https://www.eliteserien.no/terminliste";
    private static final List<String> THESPORTSDB_BACKFILL_SEASONS = List.of("2025", "2024");
    private static final Map<LeagueCode, String> EXTRA_EXPANSION_SEASONS = Map.ofEntries(
            Map.entry(LeagueCode.RUSSIAN_FOOTBALL_PREMIER_LEAGUE, "2026/2027"),
            Map.entry(LeagueCode.ITALIAN_SERIE_B, "2025/2026"),
            Map.entry(LeagueCode.SCOTTISH_CHAMPIONSHIP, "2026/2027"),
            Map.entry(LeagueCode.ENGLISH_LEAGUE_1, "2026/2027"),
            Map.entry(LeagueCode.ENGLISH_LEAGUE_2, "2026/2027"),
            Map.entry(LeagueCode.ITALIAN_SERIE_C_GIRONE_C, "2025/2026"),
            Map.entry(LeagueCode.GERMAN_2_BUNDESLIGA, "2026/2027"),
            Map.entry(LeagueCode.SPANISH_LA_LIGA_2, "2026/2027"),
            Map.entry(LeagueCode.FRENCH_LIGUE_2, "2026/2027"),
            Map.entry(LeagueCode.SWEDISH_SUPERETTAN, "2026"),
            Map.entry(LeagueCode.NORWEGIAN_1_DIVISJON, "2026"),
            Map.entry(LeagueCode.WELSH_PREMIER_LEAGUE, "2026/2027"),
            Map.entry(LeagueCode.UEFA_NATIONS_LEAGUE, "2026/2027"),
            Map.entry(LeagueCode.AFRICAN_CUP_OF_NATIONS, "2025"),
            Map.entry(LeagueCode.COPA_ARGENTINA, "2026"),
            Map.entry(LeagueCode.FIFA_CLUB_WORLD_CUP, "2025"),
            Map.entry(LeagueCode.SUPERCOPPA_ITALIANA, "2025"),
            Map.entry(LeagueCode.TACA_DE_LIGA, "2025/2026"),
            Map.entry(LeagueCode.TACA_DE_PORTUGAL, "2025/2026"),
            Map.entry(LeagueCode.SUPERCOPA_DE_ESPANA, "2025/2026"),
            Map.entry(LeagueCode.UEFA_SUPER_CUP, "2026"),
            Map.entry(LeagueCode.VENEZUELA_PRIMERA_DIVISION, "2026"),
            Map.entry(LeagueCode.AMERICAN_NWSL, "2026"),
            Map.entry(LeagueCode.INTERNATIONAL_FRIENDLIES, "2026"),
            Map.entry(LeagueCode.UEFA_EUROPEAN_UNDER_21_CHAMPIONSHIP, "2025/2026"),
            Map.entry(LeagueCode.CLUB_FRIENDLIES, "2026"),
            Map.entry(LeagueCode.FA_COMMUNITY_SHIELD, "2026"),
            Map.entry(LeagueCode.ENGLISH_NATIONAL_LEAGUE, "2025/2026"),
            Map.entry(LeagueCode.ARGENTINIAN_PRIMERA_B_NACIONAL, "2026"),
            Map.entry(LeagueCode.ALBANIAN_SUPERLIGA, "2025/2026"),
            Map.entry(LeagueCode.ANDORRAN_1A_DIVISIO, "2025/2026"),
            Map.entry(LeagueCode.ARMENIAN_PREMIER_LEAGUE, "2025/2026"),
            Map.entry(LeagueCode.AUSTRALIA_ACT_NPL, "2026"),
            Map.entry(LeagueCode.BELARUS_VYSCHA_LIGA, "2026"),
            Map.entry(LeagueCode.BELGIAN_CHALLENGER_PRO_LEAGUE, "2026/2027"),
            Map.entry(LeagueCode.BOSNIAN_PREMIER_LIGA, "2025/2026"),
            Map.entry(LeagueCode.BULGARIAN_FIRST_LEAGUE, "2026/2027"),
            Map.entry(LeagueCode.CHINA_LEAGUE_ONE, "2026"),
            Map.entry(LeagueCode.CYPRIOT_FIRST_DIVISION, "2025/2026"),
            Map.entry(LeagueCode.DANISH_2ND_DIVISION, "2026/2027"),
            Map.entry(LeagueCode.FAROE_ISLANDS_PREMIER_LEAGUE, "2026"),
            Map.entry(LeagueCode.FRENCH_LIGUE_3, "2026/2027"),
            Map.entry(LeagueCode.GEORGIAN_EROVNULI_LIGA, "2026"),
            Map.entry(LeagueCode.GERMANY_LIGA_3, "2025/2026"),
            Map.entry(LeagueCode.GREEK_SUPER_LEAGUE_2, "2025/2026"),
            Map.entry(LeagueCode.DUTCH_EERSTE_DIVISIE, "2026/2027"),
            Map.entry(LeagueCode.ISRAELI_PREMIER_LEAGUE, "2026/2027"),
            Map.entry(LeagueCode.ITALY_SERIE_D_GIRONE_D, "2025/2026"),
            Map.entry(LeagueCode.ENGLISH_NORTHERN_PREMIER_LEAGUE_PREMIER_DIVISION, "2025/2026"),
            Map.entry(LeagueCode.ENGLISH_ISTHMIAN_LEAGUE_PREMIER_DIVISION, "2025/2026"),
            Map.entry(LeagueCode.ENGLISH_SOUTHERN_PREMIER_LEAGUE_SOUTH_DIVISION, "2025/2026"),
            Map.entry(LeagueCode.MACEDONIAN_FIRST_LEAGUE, "2026/2027"),
            Map.entry(LeagueCode.MALTESE_PREMIER_LEAGUE, "2026/2027"),
            Map.entry(LeagueCode.MEXICAN_LIGA_DE_EXPANSION_MX, "2025/2026"),
            Map.entry(LeagueCode.MOLDOVAN_NATIONAL_DIVISION, "2026/2027"),
            Map.entry(LeagueCode.MONTENEGRIN_FIRST_LEAGUE, "2026/2027"),
            Map.entry(LeagueCode.MOROCCAN_BOTOLA_2, "2025/2026"),
            Map.entry(LeagueCode.NORTHERN_IRISH_PREMIERSHIP, "2026/2027"),
            Map.entry(LeagueCode.POLISH_I_LIGA, "2026/2027"),
            Map.entry(LeagueCode.PORTUGUESE_LIGAPRO, "2026/2027"),
            Map.entry(LeagueCode.ROMANIAN_LIGA_II, "2025/2026"),
            Map.entry(LeagueCode.RUSSIAN_FOOTBALL_NATIONAL_LEAGUE, "2026/2027"),
            Map.entry(LeagueCode.SAN_MARINO_CAMPIONATO, "2025/2026"),
            Map.entry(LeagueCode.SCOTTISH_LEAGUE_1, "2026/2027"),
            Map.entry(LeagueCode.SCOTTISH_LEAGUE_2, "2026/2027"),
            Map.entry(LeagueCode.SWEDISH_DIVISION_1_NORTH, "2026"),
            Map.entry(LeagueCode.TURKISH_1_LIG, "2025/2026"),
            Map.entry(LeagueCode.UKRAINIAN_FIRST_LEAGUE, "2025/2026"),
            Map.entry(LeagueCode.TURKISH_2_LIG, "2025/2026"),
            Map.entry(LeagueCode.ENGLISH_NATIONAL_LEAGUE_NORTH, "2025/2026"),
            Map.entry(LeagueCode.ENGLISH_NATIONAL_LEAGUE_SOUTH, "2025/2026"),
            Map.entry(LeagueCode.DANISH_1ST_DIVISION, "2026/2027"),
            Map.entry(LeagueCode.BOLIVIAN_PRIMERA_DIVISION, "2026"),
            Map.entry(LeagueCode.HUNGARIAN_NB_I, "2026/2027"),
            Map.entry(LeagueCode.SLOVENIAN_1_SNL, "2026/2027"),
            Map.entry(LeagueCode.AZERBAIJANI_PREMIER_LEAGUE, "2025/2026"),
            Map.entry(LeagueCode.LUXEMBOURG_NATIONAL_DIVISION, "2025/2026"),
            Map.entry(LeagueCode.GERMAN_REGIONALLIGA_NORD, "2025/2026"),
            Map.entry(LeagueCode.SWISS_CHALLENGE_LEAGUE, "2026/2027"),
            Map.entry(LeagueCode.AFC_CHAMPIONS_LEAGUE_ELITE, "2026/2027"),
            Map.entry(LeagueCode.CONCACAF_CHAMPIONS_CUP, "2026"),
            Map.entry(LeagueCode.SCOTTISH_FA_CUP, "2025/2026"),
            Map.entry(LeagueCode.COPA_DO_BRASIL, "2026"),
            Map.entry(LeagueCode.CONCACAF_CENTRAL_AMERICAN_CUP, "2026"),
            Map.entry(LeagueCode.IRANIAN_AZADEGAN_LEAGUE, "2025/2026"),
            Map.entry(LeagueCode.IRANIAN_PERSIAN_GULF_PRO_LEAGUE, "2025/2026"),
            Map.entry(LeagueCode.THAI_LEAGUE_2, "2025/2026"),
            Map.entry(LeagueCode.KENYAN_PREMIER_LEAGUE, "2025/2026"),
            Map.entry(LeagueCode.GERMAN_REGIONALLIGA_WEST, "2025/2026"),
            Map.entry(LeagueCode.GERMAN_REGIONALLIGA_SUDWEST, "2025/2026"),
            Map.entry(LeagueCode.GERMAN_REGIONALLIGA_BAYERN, "2025/2026"),
            Map.entry(LeagueCode.GERMAN_REGIONALLIGA_NORDOST, "2025/2026"),
            Map.entry(LeagueCode.ALGERIAN_LIGUE_1, "2025/2026"),
            Map.entry(LeagueCode.SENEGAL_LIGUE_1, "2025/2026"),
            Map.entry(LeagueCode.NORTHERN_IRISH_CHAMPIONSHIP, "2026/2027"),
            Map.entry(LeagueCode.SVENSKA_CUPEN, "2026/2027"),
            Map.entry(LeagueCode.ITALY_SERIE_D_GIRONE_B, "2025/2026"),
            Map.entry(LeagueCode.ITALY_SERIE_D_GIRONE_C, "2025/2026"),
            Map.entry(LeagueCode.ITALY_SERIE_D_GIRONE_E, "2025/2026"),
            Map.entry(LeagueCode.ITALY_SERIE_D_GIRONE_F, "2025/2026")
    );

    private final LeagueRepository leagueRepository;
    private final MarketDefinitionRepository marketDefinitionRepository;
    private final SourceTargetRepository sourceTargetRepository;
    private final SharpApiProperties sharpApiProperties;
    private final ApiFootballProperties apiFootballProperties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedLeagues();
        seedMarkets();
        seedTheSportsDbSourceTargets();
        seedSharpApiSourceTargets();
    }

    private void seedLeagues() {
        for (LeagueCode code : LeagueCode.values()) {
            League league = leagueRepository.findByCode(code).orElseGet(League::new);
            boolean created = league.getId() == null;
            league.setCode(code)
                    .setName(code.getDisplayName())
                    .setCountry(code.getCountry())
                    .setTier(code.getTier())
                    .setCurrentSeason(currentSeasonFor(code));
            if (created && !defaultImportEnabledFor(code)) {
                league.setScrapeEnabled(false);
            }
            leagueRepository.save(league);
        }
    }

    private void seedMarkets() {
        for (MarketCode code : MarketCode.values()) {
            MarketDefinition definition = marketDefinitionRepository.findByCode(code).orElseGet(MarketDefinition::new);
            definition.setCode(code)
                    .setDisplayName(code.getDisplayName())
                    .setMarketType(code.getMarketType())
                    .setMarketFamily(code.getMarketType())
                    .setDirection(code.getDirection())
                    .setSelectionValue(code.getSelectionValue())
                    .setThreshold(code.getThreshold())
                    .setPeriod(code.getPeriod())
                    .setTeamScope(code.getTeamScope())
                    .setTargetType(code.getTargetType())
                    .setRequiresTeamData(code.isRequiresTeamData())
                    .setRequiresPlayerData(code.isRequiresPlayerData())
                    .setRequiresHalfTimeData(code.isRequiresHalfTimeData())
                    .setRequiresEventData(code.isRequiresEventData())
                    .setRequiresOdds(code.isRequiresOdds())
                    .setEnabled(code.isEnabled())
                    .setActive(code.isEnabled())
                    .setMinimumSampleSize(code.getMinimumSampleSize())
                    .setSettlementDescription(code.getSettlementDescription());
            marketDefinitionRepository.save(definition);
        }
    }

    private String currentSeasonFor(LeagueCode code) {
        String extraExpansionSeason = EXTRA_EXPANSION_SEASONS.get(code);
        if (extraExpansionSeason != null) {
            return extraExpansionSeason;
        }
        return switch (code) {
            case PREMIER_LEAGUE,
                 LA_LIGA,
                 SERIE_A,
                 BUNDESLIGA,
                 LIGUE_1,
                 CHAMPIONSHIP,
                 EREDIVISIE,
                 PRIMEIRA_LIGA,
                 BELGIAN_PRO_LEAGUE,
                 SCOTTISH_PREMIERSHIP,
                 SUPER_LIG -> EUROPEAN_CORE_SEASON;
            case UEFA_CHAMPIONS_LEAGUE,
                 UEFA_EUROPA_LEAGUE,
                 UEFA_EUROPA_CONFERENCE_LEAGUE,
                 EFL_CUP,
                 COPPA_ITALIA,
                 DFB_POKAL,
                 DANISH_SUPERLIGA,
                 SWISS_SUPER_LEAGUE,
                 AUSTRIAN_BUNDESLIGA,
                 POLISH_EKSTRAKLASA,
                 CZECH_FIRST_LEAGUE,
                 CROATIAN_FOOTBALL_LEAGUE,
                 SERBIAN_SUPERLIGA,
                 ROMANIAN_LIGA_I,
                 UKRAINIAN_PREMIER_LEAGUE,
                 SLOVAK_FIRST_LEAGUE,
                 LIGA_MX -> "2026/2027";
            case FA_CUP,
                 COPA_DEL_REY,
                 COUPE_DE_FRANCE,
                 GREEK_SUPER_LEAGUE,
                 SAUDI_PRO_LEAGUE,
                 UAE_PRO_LEAGUE,
                 QATAR_STARS_LEAGUE,
                 A_LEAGUE_MEN,
                 THAI_LEAGUE_1,
                 INDIAN_SUPER_LEAGUE,
                 INDONESIAN_LIGA_1,
                 EGYPTIAN_PREMIER_LEAGUE,
                 SOUTH_AFRICAN_PREMIER_DIVISION,
                 MOROCCAN_BOTOLA_PRO,
                 TUNISIAN_LIGUE_1,
                 CAF_CHAMPIONS_LEAGUE,
                 NIGERIAN_PREMIER_FOOTBALL_LEAGUE -> "2025/2026";
            case ALLSVENSKAN,
                 ELITESERIEN,
                 FIFA_WORLD_CUP_2026,
                 VEIKKAUSLIIGA,
                 LEAGUE_OF_IRELAND_PREMIER_DIVISION,
                 LEAGUE_OF_IRELAND_FIRST_DIVISION,
                 BESTA_DEILD,
                 MEISTRILIIGA,
                 TOPLYGA,
                 LATVIAN_VIRSLIGA,
                 KAZAKHSTAN_PREMIER_LEAGUE,
                 CHINESE_SUPER_LEAGUE,
                 K_LEAGUE_1,
                 K_LEAGUE_2,
                 CANADIAN_PREMIER_LEAGUE,
                 BRAZILIAN_SERIE_B,
                 BRAZILIAN_SERIE_D,
                 MLS,
                 USL_CHAMPIONSHIP,
                 ARGENTINE_PRIMERA_DIVISION,
                 COPA_LIBERTADORES,
                 COPA_SUDAMERICANA,
                 BRAZILIAN_SERIE_A,
                 BRAZILIAN_SERIE_C,
                 CHILEAN_PRIMERA_DIVISION,
                 COLOMBIAN_PRIMERA_A,
                 PERUVIAN_LIGA_1,
                 URUGUAYAN_PRIMERA_DIVISION,
                 PARAGUAYAN_PRIMERA_DIVISION,
                 ECUADORIAN_SERIE_A,
                 J2_LEAGUE,
                 UZBEKISTAN_SUPER_LEAGUE -> ACTIVE_CALENDAR_SEASON;
            case J1_LEAGUE -> "2027";
            default -> ACTIVE_CALENDAR_SEASON;
        };
    }

    private boolean defaultImportEnabledFor(LeagueCode code) {
        if (EXTRA_EXPANSION_SEASONS.containsKey(code)) {
            return false;
        }
        return switch (code) {
            case UEFA_CHAMPIONS_LEAGUE,
                 UEFA_EUROPA_LEAGUE,
                 UEFA_EUROPA_CONFERENCE_LEAGUE,
                 FA_CUP,
                 EFL_CUP,
                 COPA_DEL_REY,
                 COPPA_ITALIA,
                 DFB_POKAL,
                 COUPE_DE_FRANCE,
                 DANISH_SUPERLIGA,
                 SWISS_SUPER_LEAGUE,
                 AUSTRIAN_BUNDESLIGA,
                 POLISH_EKSTRAKLASA,
                 CZECH_FIRST_LEAGUE,
                 CROATIAN_FOOTBALL_LEAGUE,
                 SERBIAN_SUPERLIGA,
                 ROMANIAN_LIGA_I,
                 GREEK_SUPER_LEAGUE,
                 UKRAINIAN_PREMIER_LEAGUE,
                 SLOVAK_FIRST_LEAGUE,
                 LIGA_MX,
                 MLS,
                 USL_CHAMPIONSHIP,
                 ARGENTINE_PRIMERA_DIVISION,
                 COPA_LIBERTADORES,
                 COPA_SUDAMERICANA,
                 BRAZILIAN_SERIE_A,
                 BRAZILIAN_SERIE_C,
                 CHILEAN_PRIMERA_DIVISION,
                 COLOMBIAN_PRIMERA_A,
                 PERUVIAN_LIGA_1,
                 URUGUAYAN_PRIMERA_DIVISION,
                 PARAGUAYAN_PRIMERA_DIVISION,
                 ECUADORIAN_SERIE_A,
                 SAUDI_PRO_LEAGUE,
                 UAE_PRO_LEAGUE,
                 QATAR_STARS_LEAGUE,
                 J1_LEAGUE,
                 J2_LEAGUE,
                 A_LEAGUE_MEN,
                 THAI_LEAGUE_1,
                 INDIAN_SUPER_LEAGUE,
                 INDONESIAN_LIGA_1,
                 UZBEKISTAN_SUPER_LEAGUE,
                 EGYPTIAN_PREMIER_LEAGUE,
                 SOUTH_AFRICAN_PREMIER_DIVISION,
                 MOROCCAN_BOTOLA_PRO,
                 TUNISIAN_LIGUE_1,
                 CAF_CHAMPIONS_LEAGUE,
                 NIGERIAN_PREMIER_FOOTBALL_LEAGUE -> false;
            default -> true;
        };
    }

    private void seedActiveLeagueSourceTargets() {
        List<ActiveLeagueSource> activeSources = List.of(
                new ActiveLeagueSource(
                        LeagueCode.VEIKKAUSLIIGA,
                        "Finland",
                        "Veikkausliiga",
                        "https://www.football-data.co.uk/new/FIN.csv",
                        "Football-Data 2026 Veikkausliiga CSV"
                ),
                new ActiveLeagueSource(
                        LeagueCode.LEAGUE_OF_IRELAND_PREMIER_DIVISION,
                        "Ireland",
                        "Premier Division",
                        "https://www.football-data.co.uk/new/IRL.csv",
                        "Football-Data 2026 League of Ireland Premier Division CSV"
                ),
                new ActiveLeagueSource(
                        LeagueCode.CHINESE_SUPER_LEAGUE,
                        "China",
                        "Super League",
                        "https://www.football-data.co.uk/new/CHN.csv",
                        "Football-Data 2026 Chinese Super League CSV"
                ),
                new ActiveLeagueSource(
                        LeagueCode.ALLSVENSKAN,
                        "Sweden",
                        "Allsvenskan",
                        "https://www.football-data.co.uk/new/SWE.csv",
                        "Football-Data 2026 Allsvenskan CSV"
                ),
                new ActiveLeagueSource(
                        LeagueCode.ELITESERIEN,
                        "Norway",
                        "Eliteserien",
                        "https://www.football-data.co.uk/new/NOR.csv",
                        "Football-Data 2026 Eliteserien CSV"
                )
        );

        for (ActiveLeagueSource activeSource : activeSources) {
            League league = leagueRepository.findByCode(activeSource.leagueCode())
                    .orElseThrow(() -> new IllegalStateException("League was not seeded: " + activeSource.leagueCode()));
            upsertFootballDataSource(
                    league,
                    SourceType.RESULTS,
                    activeSource.resultsSourceName(),
                    activeSource.resultsUrl(),
                    activeSource.country(),
                    activeSource.leagueName()
            );
            upsertFootballDataSource(
                    league,
                    SourceType.FIXTURES,
                    "Football-Data Latest Fixtures " + league.getName() + " CSV",
                    FOOTBALL_DATA_NEW_FIXTURES_URL,
                    activeSource.country(),
                    activeSource.leagueName()
            );
            upsertFootballDataExtraOddsSource(
                    league,
                    activeSource.country(),
                    activeSource.leagueName()
            );
            upsertFootballDataExtraHistoricalOddsSource(
                    league,
                    activeSource.country(),
                    activeSource.leagueName(),
                    activeSource.resultsUrl()
            );
            upsertOfficialFixtureFallbackSource(league);
        }
    }

    private void seedWorldCupSourceTargets() {
        League league = leagueRepository.findByCode(LeagueCode.FIFA_WORLD_CUP_2026)
                .orElseThrow(() -> new IllegalStateException("League was not seeded: " + LeagueCode.FIFA_WORLD_CUP_2026));
        upsertWorldCupSource(
                league,
                SourceType.FIXTURES,
                "TheStatsAPI FIFA World Cup 2026 Fixtures JSON",
                THE_STATS_API_WORLD_CUP_2026_FIXTURES_URL,
                "{\"format\":\"world-cup-2026-fixtures-json\"}",
                new BigDecimal("82.00"),
                5
        );
        upsertWorldCupSource(
                league,
                SourceType.RESULTS,
                "Openfootball FIFA World Cup 2026 Results JSON",
                OPENFOOTBALL_WORLD_CUP_2026_JSON_URL,
                "{\"format\":\"openfootball-world-cup-json\"}",
                new BigDecimal("78.00"),
                8
        );
        upsertWorldCupSource(
                league,
                SourceType.RESULTS,
                "Football-Data World Cup Historical Results Workbook",
                FOOTBALL_DATA_WORLD_CUP_2026_WORKBOOK_URL,
                "{\"format\":\"football-data-world-cup-workbook\"}",
                new BigDecimal("90.00"),
                10
        );
        upsertWorldCupSource(
                league,
                SourceType.ODDS_REFERENCE,
                "Football-Data World Cup Historical Odds Workbook",
                FOOTBALL_DATA_WORLD_CUP_2026_WORKBOOK_URL,
                "{\"format\":\"football-data-world-cup-workbook\","
                        + "\"bookmakerPrefixes\":\"B365,PINNY,BETFAIR_EXCH,MAX,AVG\"}",
                new BigDecimal("90.00"),
                12
        );
        upsertWorldCupSource(
                league,
                SourceType.CARDS,
                "Football-Data World Cup Historical Cards Workbook",
                FOOTBALL_DATA_WORLD_CUP_2026_WORKBOOK_URL,
                "{\"format\":\"football-data-world-cup-workbook\"}",
                new BigDecimal("88.00"),
                14
        );
        upsertWorldCupSource(
                league,
                SourceType.CORNERS,
                "Football-Data World Cup Historical Corners Workbook",
                FOOTBALL_DATA_WORLD_CUP_2026_WORKBOOK_URL,
                "{\"format\":\"football-data-world-cup-workbook\"}",
                new BigDecimal("88.00"),
                14
        );
    }

    private void upsertWorldCupSource(
            League league,
            SourceType sourceType,
            String name,
            String url,
            String selectorsJson,
            BigDecimal reliabilityScore,
            int fallbackPriority
    ) {
        SourceTarget target = sourceTargetRepository
                .findByLeague_CodeAndSourceTypeAndName(league.getCode(), sourceType, name)
                .orElseGet(SourceTarget::new);
        boolean created = target.getId() == null;

        target.setLeague(league)
                .setSourceType(sourceType)
                .setName(name)
                .setUrlTemplate(url)
                .setSourceSeasonToken(null)
                .setTargetSeasonLabel(league.getCurrentSeason())
                .setRenderMode(RenderMode.STATIC_HTML)
                .setRobotsTxtRequired(true)
                .setUserAgent(FOOTBALL_DATA_USER_AGENT)
                .setRateLimitPerMinute(6)
                .setTimeoutMs(20000)
                .setReliabilityScore(reliabilityScore)
                .setFallbackPriority(fallbackPriority)
                .setSelectorsJson(selectorsJson);
        if (created) {
            target.setActive(true);
        }
        sourceTargetRepository.save(target);
    }

    private void seedTheSportsDbSourceTargets() {
        List<TheSportsDbLeagueSource> sources = List.of(
                new TheSportsDbLeagueSource(LeagueCode.FIFA_WORLD_CUP_2026, "4429"),
                new TheSportsDbLeagueSource(LeagueCode.PREMIER_LEAGUE, "4328"),
                new TheSportsDbLeagueSource(LeagueCode.CHAMPIONSHIP, "4329"),
                new TheSportsDbLeagueSource(LeagueCode.SCOTTISH_PREMIERSHIP, "4330"),
                new TheSportsDbLeagueSource(LeagueCode.BUNDESLIGA, "4331"),
                new TheSportsDbLeagueSource(LeagueCode.SERIE_A, "4332"),
                new TheSportsDbLeagueSource(LeagueCode.LIGUE_1, "4334"),
                new TheSportsDbLeagueSource(LeagueCode.LA_LIGA, "4335"),
                new TheSportsDbLeagueSource(LeagueCode.EREDIVISIE, "4337"),
                new TheSportsDbLeagueSource(LeagueCode.BELGIAN_PRO_LEAGUE, "4338"),
                new TheSportsDbLeagueSource(LeagueCode.SUPER_LIG, "4339"),
                new TheSportsDbLeagueSource(LeagueCode.PRIMEIRA_LIGA, "4344"),
                new TheSportsDbLeagueSource(LeagueCode.ALLSVENSKAN, "4347"),
                new TheSportsDbLeagueSource(LeagueCode.ELITESERIEN, "4358"),
                new TheSportsDbLeagueSource(LeagueCode.CHINESE_SUPER_LEAGUE, "4359"),
                new TheSportsDbLeagueSource(LeagueCode.VEIKKAUSLIIGA, "4636"),
                new TheSportsDbLeagueSource(LeagueCode.LEAGUE_OF_IRELAND_PREMIER_DIVISION, "4643"),
                new TheSportsDbLeagueSource(LeagueCode.LEAGUE_OF_IRELAND_FIRST_DIVISION, "4757"),
                new TheSportsDbLeagueSource(LeagueCode.BESTA_DEILD, "4642"),
                new TheSportsDbLeagueSource(LeagueCode.MEISTRILIIGA, "4634"),
                new TheSportsDbLeagueSource(LeagueCode.TOPLYGA, "4651"),
                new TheSportsDbLeagueSource(LeagueCode.LATVIAN_VIRSLIGA, "4650"),
                new TheSportsDbLeagueSource(LeagueCode.KAZAKHSTAN_PREMIER_LEAGUE, "4649"),
                new TheSportsDbLeagueSource(LeagueCode.K_LEAGUE_1, "4689"),
                new TheSportsDbLeagueSource(LeagueCode.K_LEAGUE_2, "4822"),
                new TheSportsDbLeagueSource(LeagueCode.CANADIAN_PREMIER_LEAGUE, "4820"),
                new TheSportsDbLeagueSource(LeagueCode.BRAZILIAN_SERIE_B, "4404"),
                new TheSportsDbLeagueSource(LeagueCode.BRAZILIAN_SERIE_D, "5079"),
                new TheSportsDbLeagueSource(LeagueCode.UEFA_CHAMPIONS_LEAGUE, "4480"),
                new TheSportsDbLeagueSource(LeagueCode.UEFA_EUROPA_LEAGUE, "4481"),
                new TheSportsDbLeagueSource(LeagueCode.UEFA_EUROPA_CONFERENCE_LEAGUE, "5071"),
                new TheSportsDbLeagueSource(LeagueCode.FA_CUP, "4482"),
                new TheSportsDbLeagueSource(LeagueCode.EFL_CUP, "4570"),
                new TheSportsDbLeagueSource(LeagueCode.COPA_DEL_REY, "4483"),
                new TheSportsDbLeagueSource(LeagueCode.COPPA_ITALIA, "4506"),
                new TheSportsDbLeagueSource(LeagueCode.DFB_POKAL, "4485"),
                new TheSportsDbLeagueSource(LeagueCode.COUPE_DE_FRANCE, "4484"),
                new TheSportsDbLeagueSource(LeagueCode.DANISH_SUPERLIGA, "4340"),
                new TheSportsDbLeagueSource(LeagueCode.SWISS_SUPER_LEAGUE, "4675"),
                new TheSportsDbLeagueSource(LeagueCode.AUSTRIAN_BUNDESLIGA, "4621"),
                new TheSportsDbLeagueSource(LeagueCode.POLISH_EKSTRAKLASA, "4422"),
                new TheSportsDbLeagueSource(LeagueCode.CZECH_FIRST_LEAGUE, "4631"),
                new TheSportsDbLeagueSource(LeagueCode.CROATIAN_FOOTBALL_LEAGUE, "4629"),
                new TheSportsDbLeagueSource(LeagueCode.SERBIAN_SUPERLIGA, "4671"),
                new TheSportsDbLeagueSource(LeagueCode.ROMANIAN_LIGA_I, "4691"),
                new TheSportsDbLeagueSource(LeagueCode.GREEK_SUPER_LEAGUE, "4336"),
                new TheSportsDbLeagueSource(LeagueCode.UKRAINIAN_PREMIER_LEAGUE, "4354"),
                new TheSportsDbLeagueSource(LeagueCode.SLOVAK_FIRST_LEAGUE, "4672"),
                new TheSportsDbLeagueSource(LeagueCode.LIGA_MX, "4350"),
                new TheSportsDbLeagueSource(LeagueCode.MLS, "4346"),
                new TheSportsDbLeagueSource(LeagueCode.USL_CHAMPIONSHIP, "4684"),
                new TheSportsDbLeagueSource(LeagueCode.ARGENTINE_PRIMERA_DIVISION, "4406"),
                new TheSportsDbLeagueSource(LeagueCode.COPA_LIBERTADORES, "4501"),
                new TheSportsDbLeagueSource(LeagueCode.COPA_SUDAMERICANA, "4724"),
                new TheSportsDbLeagueSource(LeagueCode.BRAZILIAN_SERIE_A, "4351"),
                new TheSportsDbLeagueSource(LeagueCode.BRAZILIAN_SERIE_C, "4625"),
                new TheSportsDbLeagueSource(LeagueCode.CHILEAN_PRIMERA_DIVISION, "4627"),
                new TheSportsDbLeagueSource(LeagueCode.COLOMBIAN_PRIMERA_A, "4497"),
                new TheSportsDbLeagueSource(LeagueCode.PERUVIAN_LIGA_1, "4688"),
                new TheSportsDbLeagueSource(LeagueCode.URUGUAYAN_PRIMERA_DIVISION, "4432"),
                new TheSportsDbLeagueSource(LeagueCode.PARAGUAYAN_PRIMERA_DIVISION, "4687"),
                new TheSportsDbLeagueSource(LeagueCode.ECUADORIAN_SERIE_A, "4686"),
                new TheSportsDbLeagueSource(LeagueCode.SAUDI_PRO_LEAGUE, "4668"),
                new TheSportsDbLeagueSource(LeagueCode.UAE_PRO_LEAGUE, "4678"),
                new TheSportsDbLeagueSource(LeagueCode.QATAR_STARS_LEAGUE, "4663"),
                new TheSportsDbLeagueSource(LeagueCode.J1_LEAGUE, "4633"),
                new TheSportsDbLeagueSource(LeagueCode.J2_LEAGUE, "4824"),
                new TheSportsDbLeagueSource(LeagueCode.A_LEAGUE_MEN, "4356"),
                new TheSportsDbLeagueSource(LeagueCode.THAI_LEAGUE_1, "4743"),
                new TheSportsDbLeagueSource(LeagueCode.INDIAN_SUPER_LEAGUE, "4791"),
                new TheSportsDbLeagueSource(LeagueCode.INDONESIAN_LIGA_1, "4790"),
                new TheSportsDbLeagueSource(LeagueCode.UZBEKISTAN_SUPER_LEAGUE, "4794"),
                new TheSportsDbLeagueSource(LeagueCode.EGYPTIAN_PREMIER_LEAGUE, "4829"),
                new TheSportsDbLeagueSource(LeagueCode.SOUTH_AFRICAN_PREMIER_DIVISION, "4802"),
                new TheSportsDbLeagueSource(LeagueCode.MOROCCAN_BOTOLA_PRO, "4520"),
                new TheSportsDbLeagueSource(LeagueCode.TUNISIAN_LIGUE_1, "4828"),
                new TheSportsDbLeagueSource(LeagueCode.CAF_CHAMPIONS_LEAGUE, "4720"),
                new TheSportsDbLeagueSource(LeagueCode.NIGERIAN_PREMIER_FOOTBALL_LEAGUE, "4827"),
                new TheSportsDbLeagueSource(LeagueCode.RUSSIAN_FOOTBALL_PREMIER_LEAGUE, "4355"),
                new TheSportsDbLeagueSource(LeagueCode.ITALIAN_SERIE_B, "4394"),
                new TheSportsDbLeagueSource(LeagueCode.SCOTTISH_CHAMPIONSHIP, "4395"),
                new TheSportsDbLeagueSource(LeagueCode.ENGLISH_LEAGUE_1, "4396"),
                new TheSportsDbLeagueSource(LeagueCode.ENGLISH_LEAGUE_2, "4397"),
                new TheSportsDbLeagueSource(LeagueCode.ITALIAN_SERIE_C_GIRONE_C, "4398"),
                new TheSportsDbLeagueSource(LeagueCode.GERMAN_2_BUNDESLIGA, "4399"),
                new TheSportsDbLeagueSource(LeagueCode.SPANISH_LA_LIGA_2, "4400"),
                new TheSportsDbLeagueSource(LeagueCode.FRENCH_LIGUE_2, "4401"),
                new TheSportsDbLeagueSource(LeagueCode.SWEDISH_SUPERETTAN, "4403"),
                new TheSportsDbLeagueSource(LeagueCode.NORWEGIAN_1_DIVISJON, "4457"),
                new TheSportsDbLeagueSource(LeagueCode.WELSH_PREMIER_LEAGUE, "4472"),
                new TheSportsDbLeagueSource(LeagueCode.UEFA_NATIONS_LEAGUE, "4490"),
                new TheSportsDbLeagueSource(LeagueCode.AFRICAN_CUP_OF_NATIONS, "4496"),
                new TheSportsDbLeagueSource(LeagueCode.COPA_ARGENTINA, "4500"),
                new TheSportsDbLeagueSource(LeagueCode.FIFA_CLUB_WORLD_CUP, "4503"),
                new TheSportsDbLeagueSource(LeagueCode.SUPERCOPPA_ITALIANA, "4507"),
                new TheSportsDbLeagueSource(LeagueCode.TACA_DE_LIGA, "4509"),
                new TheSportsDbLeagueSource(LeagueCode.TACA_DE_PORTUGAL, "4510"),
                new TheSportsDbLeagueSource(LeagueCode.SUPERCOPA_DE_ESPANA, "4511"),
                new TheSportsDbLeagueSource(LeagueCode.UEFA_SUPER_CUP, "4512"),
                new TheSportsDbLeagueSource(LeagueCode.VENEZUELA_PRIMERA_DIVISION, "4513"),
                new TheSportsDbLeagueSource(LeagueCode.AMERICAN_NWSL, "4521"),
                new TheSportsDbLeagueSource(LeagueCode.INTERNATIONAL_FRIENDLIES, "4562"),
                new TheSportsDbLeagueSource(LeagueCode.UEFA_EUROPEAN_UNDER_21_CHAMPIONSHIP, "4566"),
                new TheSportsDbLeagueSource(LeagueCode.CLUB_FRIENDLIES, "4569"),
                new TheSportsDbLeagueSource(LeagueCode.FA_COMMUNITY_SHIELD, "4571"),
                new TheSportsDbLeagueSource(LeagueCode.ENGLISH_NATIONAL_LEAGUE, "4590"),
                new TheSportsDbLeagueSource(LeagueCode.ARGENTINIAN_PRIMERA_B_NACIONAL, "4616"),
                new TheSportsDbLeagueSource(LeagueCode.ALBANIAN_SUPERLIGA, "4617"),
                new TheSportsDbLeagueSource(LeagueCode.ANDORRAN_1A_DIVISIO, "4618"),
                new TheSportsDbLeagueSource(LeagueCode.ARMENIAN_PREMIER_LEAGUE, "4619"),
                new TheSportsDbLeagueSource(LeagueCode.AUSTRALIA_ACT_NPL, "4620"),
                new TheSportsDbLeagueSource(LeagueCode.BELARUS_VYSCHA_LIGA, "4622"),
                new TheSportsDbLeagueSource(LeagueCode.BELGIAN_CHALLENGER_PRO_LEAGUE, "4623"),
                new TheSportsDbLeagueSource(LeagueCode.BOSNIAN_PREMIER_LIGA, "4624"),
                new TheSportsDbLeagueSource(LeagueCode.BULGARIAN_FIRST_LEAGUE, "4626"),
                new TheSportsDbLeagueSource(LeagueCode.CHINA_LEAGUE_ONE, "4628"),
                new TheSportsDbLeagueSource(LeagueCode.CYPRIOT_FIRST_DIVISION, "4630"),
                new TheSportsDbLeagueSource(LeagueCode.DANISH_2ND_DIVISION, "4632"),
                new TheSportsDbLeagueSource(LeagueCode.FAROE_ISLANDS_PREMIER_LEAGUE, "4635"),
                new TheSportsDbLeagueSource(LeagueCode.FRENCH_LIGUE_3, "4637"),
                new TheSportsDbLeagueSource(LeagueCode.GEORGIAN_EROVNULI_LIGA, "4638"),
                new TheSportsDbLeagueSource(LeagueCode.GERMANY_LIGA_3, "4639"),
                new TheSportsDbLeagueSource(LeagueCode.GREEK_SUPER_LEAGUE_2, "4640"),
                new TheSportsDbLeagueSource(LeagueCode.DUTCH_EERSTE_DIVISIE, "4641"),
                new TheSportsDbLeagueSource(LeagueCode.ISRAELI_PREMIER_LEAGUE, "4644"),
                new TheSportsDbLeagueSource(LeagueCode.ITALY_SERIE_D_GIRONE_D, "4645"),
                new TheSportsDbLeagueSource(LeagueCode.ENGLISH_NORTHERN_PREMIER_LEAGUE_PREMIER_DIVISION, "4646"),
                new TheSportsDbLeagueSource(LeagueCode.ENGLISH_ISTHMIAN_LEAGUE_PREMIER_DIVISION, "4647"),
                new TheSportsDbLeagueSource(LeagueCode.ENGLISH_SOUTHERN_PREMIER_LEAGUE_SOUTH_DIVISION, "4648"),
                new TheSportsDbLeagueSource(LeagueCode.MACEDONIAN_FIRST_LEAGUE, "4652"),
                new TheSportsDbLeagueSource(LeagueCode.MALTESE_PREMIER_LEAGUE, "4653"),
                new TheSportsDbLeagueSource(LeagueCode.MEXICAN_LIGA_DE_EXPANSION_MX, "4654"),
                new TheSportsDbLeagueSource(LeagueCode.MOLDOVAN_NATIONAL_DIVISION, "4655"),
                new TheSportsDbLeagueSource(LeagueCode.MONTENEGRIN_FIRST_LEAGUE, "4656"),
                new TheSportsDbLeagueSource(LeagueCode.MOROCCAN_BOTOLA_2, "4657"),
                new TheSportsDbLeagueSource(LeagueCode.NORTHERN_IRISH_PREMIERSHIP, "4659"),
                new TheSportsDbLeagueSource(LeagueCode.POLISH_I_LIGA, "4661"),
                new TheSportsDbLeagueSource(LeagueCode.PORTUGUESE_LIGAPRO, "4662"),
                new TheSportsDbLeagueSource(LeagueCode.ROMANIAN_LIGA_II, "4665"),
                new TheSportsDbLeagueSource(LeagueCode.RUSSIAN_FOOTBALL_NATIONAL_LEAGUE, "4666"),
                new TheSportsDbLeagueSource(LeagueCode.SAN_MARINO_CAMPIONATO, "4667"),
                new TheSportsDbLeagueSource(LeagueCode.SCOTTISH_LEAGUE_1, "4669"),
                new TheSportsDbLeagueSource(LeagueCode.SCOTTISH_LEAGUE_2, "4670"),
                new TheSportsDbLeagueSource(LeagueCode.SWEDISH_DIVISION_1_NORTH, "4674"),
                new TheSportsDbLeagueSource(LeagueCode.TURKISH_1_LIG, "4676"),
                new TheSportsDbLeagueSource(LeagueCode.UKRAINIAN_FIRST_LEAGUE, "4677"),
                new TheSportsDbLeagueSource(LeagueCode.TURKISH_2_LIG, "4679"),
                new TheSportsDbLeagueSource(LeagueCode.ENGLISH_NATIONAL_LEAGUE_NORTH, "4681"),
                new TheSportsDbLeagueSource(LeagueCode.ENGLISH_NATIONAL_LEAGUE_SOUTH, "4682"),
                new TheSportsDbLeagueSource(LeagueCode.DANISH_1ST_DIVISION, "4683"),
                new TheSportsDbLeagueSource(LeagueCode.BOLIVIAN_PRIMERA_DIVISION, "4685"),
                new TheSportsDbLeagueSource(LeagueCode.HUNGARIAN_NB_I, "4690"),
                new TheSportsDbLeagueSource(LeagueCode.SLOVENIAN_1_SNL, "4692"),
                new TheSportsDbLeagueSource(LeagueCode.AZERBAIJANI_PREMIER_LEAGUE, "4693"),
                new TheSportsDbLeagueSource(LeagueCode.LUXEMBOURG_NATIONAL_DIVISION, "4694"),
                new TheSportsDbLeagueSource(LeagueCode.GERMAN_REGIONALLIGA_NORD, "4695"),
                new TheSportsDbLeagueSource(LeagueCode.SWISS_CHALLENGE_LEAGUE, "4713"),
                new TheSportsDbLeagueSource(LeagueCode.AFC_CHAMPIONS_LEAGUE_ELITE, "4719"),
                new TheSportsDbLeagueSource(LeagueCode.CONCACAF_CHAMPIONS_CUP, "4721"),
                new TheSportsDbLeagueSource(LeagueCode.SCOTTISH_FA_CUP, "4723"),
                new TheSportsDbLeagueSource(LeagueCode.COPA_DO_BRASIL, "4725"),
                new TheSportsDbLeagueSource(LeagueCode.CONCACAF_CENTRAL_AMERICAN_CUP, "4739"),
                new TheSportsDbLeagueSource(LeagueCode.IRANIAN_AZADEGAN_LEAGUE, "4741"),
                new TheSportsDbLeagueSource(LeagueCode.IRANIAN_PERSIAN_GULF_PRO_LEAGUE, "4742"),
                new TheSportsDbLeagueSource(LeagueCode.THAI_LEAGUE_2, "4744"),
                new TheSportsDbLeagueSource(LeagueCode.KENYAN_PREMIER_LEAGUE, "4745"),
                new TheSportsDbLeagueSource(LeagueCode.GERMAN_REGIONALLIGA_WEST, "4746"),
                new TheSportsDbLeagueSource(LeagueCode.GERMAN_REGIONALLIGA_SUDWEST, "4747"),
                new TheSportsDbLeagueSource(LeagueCode.GERMAN_REGIONALLIGA_BAYERN, "4748"),
                new TheSportsDbLeagueSource(LeagueCode.GERMAN_REGIONALLIGA_NORDOST, "4749"),
                new TheSportsDbLeagueSource(LeagueCode.ALGERIAN_LIGUE_1, "4753"),
                new TheSportsDbLeagueSource(LeagueCode.SENEGAL_LIGUE_1, "4754"),
                new TheSportsDbLeagueSource(LeagueCode.NORTHERN_IRISH_CHAMPIONSHIP, "4755"),
                new TheSportsDbLeagueSource(LeagueCode.SVENSKA_CUPEN, "4756"),
                new TheSportsDbLeagueSource(LeagueCode.ITALY_SERIE_D_GIRONE_B, "4778"),
                new TheSportsDbLeagueSource(LeagueCode.ITALY_SERIE_D_GIRONE_C, "4779"),
                new TheSportsDbLeagueSource(LeagueCode.ITALY_SERIE_D_GIRONE_E, "4780"),
                new TheSportsDbLeagueSource(LeagueCode.ITALY_SERIE_D_GIRONE_F, "4782")
        );

        for (TheSportsDbLeagueSource source : sources) {
            League league = leagueRepository.findByCode(source.leagueCode())
                    .orElseThrow(() -> new IllegalStateException("League was not seeded: " + source.leagueCode()));
            upsertTheSportsDbSeasonSource(league, SourceType.RESULTS, source.leagueId(), league.getCurrentSeason(), 35);
            upsertTheSportsDbSeasonSource(league, SourceType.FIXTURES, source.leagueId(), league.getCurrentSeason(), 36);
            upsertTheSportsDbNextEventsSource(league, source.leagueId());
            List<String> backfillSeasons = calculateBackfillSeasons(league.getCurrentSeason(), 2);
            for (String backfillSeason : backfillSeasons) {
                upsertTheSportsDbSeasonSource(league, SourceType.RESULTS, source.leagueId(), backfillSeason, 65);
            }
        }
    }

    private List<String> calculateBackfillSeasons(String currentSeason, int count) {
        java.util.List<String> backfills = new java.util.ArrayList<>();
        if (currentSeason.contains("/")) {
            String[] parts = currentSeason.split("/");
            int start = Integer.parseInt(parts[0]);
            int end = Integer.parseInt(parts[1]);
            for (int i = 1; i <= count; i++) {
                backfills.add((start - i) + "/" + (end - i));
            }
        } else {
            int year = Integer.parseInt(currentSeason);
            for (int i = 1; i <= count; i++) {
                backfills.add(String.valueOf(year - i));
            }
        }
        return backfills;
    }

    private void upsertTheSportsDbSeasonSource(
            League league,
            SourceType sourceType,
            String leagueId,
            String season,
            int fallbackPriority
    ) {
        String name = "TheSportsDB " + season + " " + league.getName() + " Events JSON " + sourceType.name();
        SourceTarget target = sourceTargetRepository
                .findByLeague_CodeAndSourceTypeAndName(league.getCode(), sourceType, name)
                .orElseGet(SourceTarget::new);

        String urlSeason = season.replace("/", "-");

        target.setLeague(league)
                .setSourceType(sourceType)
                .setName(name)
                .setUrlTemplate(String.format(THESPORTSDB_EVENTS_SEASON_URL, leagueId, urlSeason))
                .setSourceSeasonToken(season)
                .setTargetSeasonLabel(season)
                .setRenderMode(RenderMode.STATIC_HTML)
                .setRobotsTxtRequired(false)
                .setUserAgent(FOOTBALL_DATA_USER_AGENT)
                .setRateLimitPerMinute(6)
                .setTimeoutMs(15000)
                .setReliabilityScore(new BigDecimal("72.00"))
                .setFallbackPriority(fallbackPriority)
                .setSystemDisabled(false)
                .setActive(true)
                .setSelectorsJson("{\"format\":\"thesportsdb-events-json\","
                        + "\"leagueId\":\"" + leagueId + "\","
                        + "\"season\":\"" + urlSeason + "\","
                        + "\"coverageMode\":\"season\"}");
        sourceTargetRepository.save(target);
    }

    private void upsertTheSportsDbNextEventsSource(League league, String leagueId) {
        String name = "TheSportsDB Next " + league.getName() + " Events JSON FIXTURES";
        SourceTarget target = sourceTargetRepository
                .findByLeague_CodeAndSourceTypeAndName(league.getCode(), SourceType.FIXTURES, name)
                .orElseGet(SourceTarget::new);

        target.setLeague(league)
                .setSourceType(SourceType.FIXTURES)
                .setName(name)
                .setUrlTemplate(String.format(THESPORTSDB_NEXT_EVENTS_URL, leagueId))
                .setSourceSeasonToken(null)
                .setTargetSeasonLabel(league.getCurrentSeason())
                .setRenderMode(RenderMode.STATIC_HTML)
                .setRobotsTxtRequired(false)
                .setUserAgent(FOOTBALL_DATA_USER_AGENT)
                .setRateLimitPerMinute(6)
                .setTimeoutMs(15000)
                .setReliabilityScore(new BigDecimal("65.00"))
                .setFallbackPriority(75)
                .setSystemDisabled(false)
                .setActive(true)
                .setSelectorsJson("{\"format\":\"thesportsdb-events-json\","
                        + "\"leagueId\":\"" + leagueId + "\","
                        + "\"season\":\"" + league.getCurrentSeason() + "\","
                        + "\"coverageMode\":\"next-events\"}");
        sourceTargetRepository.save(target);
    }

    private void seedSgoddsSourceTargets() {
        League league = leagueRepository.findByCode(LeagueCode.K_LEAGUE_1)
                .orElseThrow(() -> new IllegalStateException("League was not seeded: " + LeagueCode.K_LEAGUE_1));
        upsertSgoddsResultsSource(league);
        upsertSgoddsOddsSource(league);
    }

    private void seedApiFootballSourceTargets() {
        List<ApiFootballLeagueSource> sources = List.of(
                new ApiFootballLeagueSource(LeagueCode.BELGIAN_PRO_LEAGUE, "144"),
                new ApiFootballLeagueSource(LeagueCode.VEIKKAUSLIIGA, "244"),
                new ApiFootballLeagueSource(LeagueCode.LEAGUE_OF_IRELAND_PREMIER_DIVISION, "357"),
                new ApiFootballLeagueSource(LeagueCode.BESTA_DEILD, "164"),
                new ApiFootballLeagueSource(LeagueCode.MEISTRILIIGA, "329"),
                new ApiFootballLeagueSource(LeagueCode.TOPLYGA, "362"),
                new ApiFootballLeagueSource(LeagueCode.LATVIAN_VIRSLIGA, "365"),
                new ApiFootballLeagueSource(LeagueCode.KAZAKHSTAN_PREMIER_LEAGUE, "389"),
                new ApiFootballLeagueSource(LeagueCode.CHINESE_SUPER_LEAGUE, "169"),
                new ApiFootballLeagueSource(LeagueCode.K_LEAGUE_1, "292"),
                new ApiFootballLeagueSource(LeagueCode.K_LEAGUE_2, "293"),
                new ApiFootballLeagueSource(LeagueCode.CANADIAN_PREMIER_LEAGUE, "252"),
                new ApiFootballLeagueSource(LeagueCode.BRAZILIAN_SERIE_B, "72"),
                new ApiFootballLeagueSource(LeagueCode.BRAZILIAN_SERIE_D, "76")
        );

        for (ApiFootballLeagueSource source : sources) {
            League league = leagueRepository.findByCode(source.leagueCode())
                    .orElseThrow(() -> new IllegalStateException("League was not seeded: " + source.leagueCode()));
            upsertApiFootballMatchDataSource(league, source.leagueId());
        }
    }

    private void upsertApiFootballMatchDataSource(League league, String leagueId) {
        String sourceSeason = apiFootballSeasonFor(league);
        String name = "API-Football " + sourceSeason + " " + league.getName() + " Match Data JSON";
        SourceTarget target = sourceTargetRepository
                .findByLeague_CodeAndSourceTypeAndName(league.getCode(), SourceType.MATCH_DATA, name)
                .orElseGet(SourceTarget::new);

        target.setLeague(league)
                .setSourceType(SourceType.MATCH_DATA)
                .setName(name)
                .setUrlTemplate(String.format(API_FOOTBALL_FIXTURES_URL, leagueId, sourceSeason))
                .setSourceSeasonToken(sourceSeason)
                .setTargetSeasonLabel(league.getCurrentSeason())
                .setRenderMode(RenderMode.STATIC_HTML)
                .setRobotsTxtRequired(false)
                .setUserAgent(FOOTBALL_DATA_USER_AGENT)
                .setRateLimitPerMinute(6)
                .setTimeoutMs(20000)
                .setReliabilityScore(new BigDecimal("86.00"))
                .setFallbackPriority(12)
                .setSystemDisabled(false)
                .setActive(apiFootballProperties.enabled()
                        && org.springframework.util.StringUtils.hasText(apiFootballProperties.apiKey()))
                .setSelectorsJson("{\"format\":\"api-football-fixtures-json\","
                        + "\"provider\":\"api-football\","
                        + "\"leagueId\":\"" + leagueId + "\","
                        + "\"season\":\"" + sourceSeason + "\","
                        + "\"coverageMode\":\"league-season-fixtures\"}");
        sourceTargetRepository.save(target);
    }

    private String apiFootballSeasonFor(League league) {
        String currentSeason = league.getCurrentSeason();
        if (currentSeason != null && currentSeason.contains("/")) {
            return currentSeason.substring(0, currentSeason.indexOf('/'));
        }
        return currentSeason;
    }

    private void seedSharpApiSourceTargets() {
        // Disable existing The Odds API targets
        sourceTargetRepository.findAll().stream()
                .filter(t -> t.getName().contains("The Odds API"))
                .forEach(t -> {
                    t.setActive(false);
                    t.setSystemDisabled(true);
                    sourceTargetRepository.save(t);
                });

        List<com.betai.domain.source.SharpApiLeagueSource> sources = List.of(
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.PREMIER_LEAGUE, "england_-_premier_league"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.CHAMPIONSHIP, "england_-_championship"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.LA_LIGA, "spain_-_la_liga"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.SERIE_A, "italy_-_serie_a"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.BUNDESLIGA, "germany_-_bundesliga"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.LIGUE_1, "france_-_ligue_1"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.EREDIVISIE, "netherlands_-_eredivisie"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.PRIMEIRA_LIGA, "primeira_liga"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.BELGIAN_PRO_LEAGUE, "first_division_a"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.SCOTTISH_PREMIERSHIP, "scotland_-_premiership"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.SUPER_LIG, "super_lig"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.ALLSVENSKAN, "sweden_-_allsvenskan"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.ELITESERIEN, "norway_-_eliteserien"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.VEIKKAUSLIIGA, "finland_-_veikkausliiga"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.LEAGUE_OF_IRELAND_PREMIER_DIVISION, "ireland_premier_division"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.LEAGUE_OF_IRELAND_FIRST_DIVISION, "ireland_-_division_1"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.BESTA_DEILD, "besta_deild"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.MEISTRILIIGA, "estonia_-_premium_liiga"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.TOPLYGA, "lithuania_-_top_liga"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.LATVIAN_VIRSLIGA, "latvia_-_virsliga"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.KAZAKHSTAN_PREMIER_LEAGUE, "kazakhstan_-_premier_league"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.CHINESE_SUPER_LEAGUE, "china_-_super_league"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.K_LEAGUE_1, "republic_of_korea_-_k_league_1"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.K_LEAGUE_2, "korea_-_k2_league"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.CANADIAN_PREMIER_LEAGUE, "canada_-_canadian_premier_league"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.BRAZILIAN_SERIE_B, "brazil_-_serie_b"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.BRAZILIAN_SERIE_D, "brazil_-_serie_d"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.FIFA_WORLD_CUP_2026, "fifa_-_world_cup"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.UEFA_CHAMPIONS_LEAGUE, "uefa_-_champions_league"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.UEFA_EUROPA_LEAGUE, "uefa_-_europa_league"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.UEFA_EUROPA_CONFERENCE_LEAGUE, "uefa_-_europa_conference_league"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.FA_CUP, "england_-_fa_cup"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.EFL_CUP, "england_-_efl_cup"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.DFB_POKAL, "germany_-_dfb_pokal"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.SWISS_SUPER_LEAGUE, "switzerland_-_super_league"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.MLS, "usa_-_major_league_soccer"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.USL_CHAMPIONSHIP, "usa_-_usl_championship"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.ARGENTINE_PRIMERA_DIVISION, "argentina_-_primera_division"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.COPA_LIBERTADORES, "conmebol_-_copa_libertadores"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.COPA_SUDAMERICANA, "conmebol_-_copa_sudamericana"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.BRAZILIAN_SERIE_A, "brazil_-_serie_a"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.BRAZILIAN_SERIE_C, "brazil_-_serie_c"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.CHILEAN_PRIMERA_DIVISION, "chile_-_primera_division"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.URUGUAYAN_PRIMERA_DIVISION, "uruguay_-_primera_division"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.ECUADORIAN_SERIE_A, "ecuador_-_serie_a"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.SAUDI_PRO_LEAGUE, "saudi_arabia_-_saudi_league"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.MOROCCAN_BOTOLA_PRO, "morocco_-_botola_pro"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.RUSSIAN_FOOTBALL_PREMIER_LEAGUE, "russia_-_premier_league"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.ITALIAN_SERIE_B, "italy_-_serie_b"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.ENGLISH_LEAGUE_1, "england_-_league_1"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.ENGLISH_LEAGUE_2, "england_-_league_2"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.GERMAN_2_BUNDESLIGA, "germany_-_bundesliga_2"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.SWEDISH_SUPERETTAN, "sweden_-_superettan"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.UEFA_NATIONS_LEAGUE, "uefa_-_nations_league"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.COPA_ARGENTINA, "argentina_-_copa_argentina"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.UEFA_SUPER_CUP, "uefa_-_super_cup"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.VENEZUELA_PRIMERA_DIVISION, "venezuela_-_primera_division"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.AMERICAN_NWSL, "usa_-_nwsl"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.FA_COMMUNITY_SHIELD, "england_-_community_shield"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.ARGENTINIAN_PRIMERA_B_NACIONAL, "argentina_-_primera_nacional"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.CHINA_LEAGUE_ONE, "china_-_league_one"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.BOLIVIAN_PRIMERA_DIVISION, "bolivia_-_primera_division"),
                new com.betai.domain.source.SharpApiLeagueSource(LeagueCode.COPA_DO_BRASIL, "brazil_-_copa_do_brasil")
        );

        for (com.betai.domain.source.SharpApiLeagueSource source : sources) {
            League league = leagueRepository.findByCode(source.leagueCode())
                    .orElseThrow(() -> new IllegalStateException("League was not seeded: " + source.leagueCode()));
            upsertSharpApiSource(league, source.sportKey());
        }
    }

    private void upsertSharpApiSource(League league, String sportKey) {
        String name = "SharpAPI Upcoming Odds " + league.getName() + " JSON";
        SourceTarget target = sourceTargetRepository
                .findByLeague_CodeAndSourceTypeAndName(league.getCode(), SourceType.ODDS_REFERENCE, name)
                .orElseGet(SourceTarget::new);

        target.setLeague(league)
                .setSourceType(SourceType.ODDS_REFERENCE)
                .setName(name)
                .setUrlTemplate(String.format(SHARPAPI_ODDS_URL, sportKey))
                .setSourceSeasonToken(null)
                .setTargetSeasonLabel(league.getCurrentSeason())
                .setRenderMode(RenderMode.STATIC_HTML)
                .setRobotsTxtRequired(false)
                .setUserAgent(FOOTBALL_DATA_USER_AGENT)
                .setRateLimitPerMinute(6)
                .setTimeoutMs(20000)
                .setReliabilityScore(new BigDecimal("92.00"))
                .setFallbackPriority(5)
                .setActive(sharpApiProperties.enabled()
                        && org.springframework.util.StringUtils.hasText(sharpApiProperties.apiKey()))
                .setSelectorsJson("{\"format\":\"sharpapi-odds-json\","
                        + "\"sportKey\":\"" + sportKey + "\","
                        + "\"includeOneXTwo\":true,"
                        + "\"includeOverUnder25\":true}");
        sourceTargetRepository.save(target);
    }

    private void upsertSgoddsResultsSource(League league) {
        String name = "Sgodds Results K League CSV";
        SourceTarget target = sourceTargetRepository
                .findByLeague_CodeAndSourceTypeAndName(league.getCode(), SourceType.RESULTS, name)
                .orElseGet(SourceTarget::new);

        target.setLeague(league)
                .setSourceType(SourceType.RESULTS)
                .setName(name)
                .setUrlTemplate(SGODDS_DATA_PAGE_URL)
                .setSourceSeasonToken(league.getCurrentSeason())
                .setTargetSeasonLabel(league.getCurrentSeason())
                .setRenderMode(RenderMode.STATIC_HTML)
                .setRobotsTxtRequired(true)
                .setUserAgent(FOOTBALL_DATA_USER_AGENT)
                .setRateLimitPerMinute(6)
                .setTimeoutMs(15000)
                .setReliabilityScore(new BigDecimal("72.00"))
                .setFallbackPriority(35)
                .setActive(true)
                .setSystemDisabled(false)
                .setHealthNote("Uses the Sgodds data page and resolves the current K League CSV download link at scrape time.")
                .setConsecutiveFailures(0)
                .setLastFailureAt(null)
                .setLastFailureReason(null)
                .setSelectorsJson("{\"format\":\"sgodds-results-csv\","
                        + "\"leagueName\":\"K League\","
                        + "\"downloadPageFormat\":\"sgodds-league-download-page\"}");
        sourceTargetRepository.save(target);
    }

    private void upsertSgoddsOddsSource(League league) {
        String name = "Sgodds Opening Odds K League CSV";
        SourceTarget target = sourceTargetRepository
                .findByLeague_CodeAndSourceTypeAndName(league.getCode(), SourceType.ODDS_REFERENCE, name)
                .orElseGet(SourceTarget::new);

        target.setLeague(league)
                .setSourceType(SourceType.ODDS_REFERENCE)
                .setName(name)
                .setUrlTemplate(SGODDS_DATA_PAGE_URL)
                .setSourceSeasonToken(league.getCurrentSeason())
                .setTargetSeasonLabel(league.getCurrentSeason())
                .setRenderMode(RenderMode.STATIC_HTML)
                .setRobotsTxtRequired(true)
                .setUserAgent(FOOTBALL_DATA_USER_AGENT)
                .setRateLimitPerMinute(6)
                .setTimeoutMs(15000)
                .setReliabilityScore(new BigDecimal("70.00"))
                .setFallbackPriority(40)
                .setActive(true)
                .setSystemDisabled(false)
                .setHealthNote("Uses the Sgodds data page and resolves the current K League CSV download link at scrape time.")
                .setConsecutiveFailures(0)
                .setLastFailureAt(null)
                .setLastFailureReason(null)
                .setSelectorsJson("{\"format\":\"sgodds-opening-odds-csv\","
                        + "\"leagueName\":\"K League\","
                        + "\"downloadPageFormat\":\"sgodds-league-download-page\","
                        + "\"bookmakerCode\":\"SGODDS\","
                        + "\"bookmakerName\":\"Sgodds Opening Odds\","
                        + "\"includeOneXTwo\":true,"
                        + "\"includeOverUnder25\":true}");
        sourceTargetRepository.save(target);
    }

    private void upsertOfficialFixtureFallbackSource(League league) {
        if (league.getCode() == LeagueCode.ALLSVENSKAN) {
            upsertHtmlFixtureSource(
                    league,
                    "AllsvenskanFotboll Full 2026 Fixture Schedule",
                    ALLSVENSKAN_FULL_FIXTURES_URL,
                    "{\"format\":\"official-allsvenskan-fixtures-html\"}"
            );
        }
        if (league.getCode() == LeagueCode.ELITESERIEN) {
            upsertHtmlFixtureSource(
                    league,
                    "Eliteserien Official Full 2026 Fixture Schedule",
                    ELITESERIEN_FULL_FIXTURES_URL,
                    "{\"format\":\"official-eliteserien-fixtures-html\"}"
            );
        }
    }

    private void upsertHtmlFixtureSource(League league, String name, String url, String selectorsJson) {
        SourceTarget target = sourceTargetRepository
                .findByLeague_CodeAndSourceTypeAndName(league.getCode(), SourceType.FIXTURES, name)
                .orElseGet(SourceTarget::new);
        boolean created = target.getId() == null;

        target.setLeague(league)
                .setSourceType(SourceType.FIXTURES)
                .setName(name)
                .setUrlTemplate(url)
                .setSourceSeasonToken(null)
                .setTargetSeasonLabel(league.getCurrentSeason())
                .setRenderMode(RenderMode.STATIC_HTML)
                .setRobotsTxtRequired(true)
                .setUserAgent(FOOTBALL_DATA_USER_AGENT)
                .setRateLimitPerMinute(6)
                .setTimeoutMs(15000)
                .setReliabilityScore(new BigDecimal("80.00"))
                .setFallbackPriority(10)
                .setSelectorsJson(selectorsJson);
        if (created) {
            target.setActive(true);
        }
        sourceTargetRepository.save(target);
    }

    private void seedMainLeagueOddsSourceTargets() {
        mainLeagueSources().forEach(source -> {
            upsertFootballDataMainResultsSource(source.leagueCode(), source.divisionCode());
            upsertFootballDataMainFixtureSource(source.leagueCode(), source.divisionCode());
            upsertFootballDataMainOddsSource(source.leagueCode(), source.divisionCode());
            upsertFootballDataMainHistoricalOddsSource(source.leagueCode(), source.divisionCode());
        });
    }

    private List<MainLeagueSource> mainLeagueSources() {
        return List.of(
                new MainLeagueSource(LeagueCode.PREMIER_LEAGUE, "E0"),
                new MainLeagueSource(LeagueCode.CHAMPIONSHIP, "E1"),
                new MainLeagueSource(LeagueCode.LA_LIGA, "SP1"),
                new MainLeagueSource(LeagueCode.SERIE_A, "I1"),
                new MainLeagueSource(LeagueCode.BUNDESLIGA, "D1"),
                new MainLeagueSource(LeagueCode.LIGUE_1, "F1"),
                new MainLeagueSource(LeagueCode.EREDIVISIE, "N1"),
                new MainLeagueSource(LeagueCode.PRIMEIRA_LIGA, "P1"),
                new MainLeagueSource(LeagueCode.BELGIAN_PRO_LEAGUE, "B1"),
                new MainLeagueSource(LeagueCode.SCOTTISH_PREMIERSHIP, "SC0"),
                new MainLeagueSource(LeagueCode.SUPER_LIG, "T1")
        );
    }

    private void upsertFootballDataMainResultsSource(LeagueCode leagueCode, String divisionCode) {
        League league = leagueRepository.findByCode(leagueCode)
                .orElseThrow(() -> new IllegalStateException("League was not seeded: " + leagueCode));
        String name = "Football-Data Historical Results " + league.getName() + " CSV";
        SourceTarget target = sourceTargetRepository
                .findByLeague_CodeAndSourceTypeAndName(league.getCode(), SourceType.RESULTS, name)
                .orElseGet(SourceTarget::new);
        boolean created = target.getId() == null;

        target.setLeague(league)
                .setSourceType(SourceType.RESULTS)
                .setName(name)
                .setUrlTemplate(FOOTBALL_DATA_2025_2026_BASE_URL + divisionCode + ".csv")
                .setSourceSeasonToken(null)
                .setTargetSeasonLabel(league.getCurrentSeason())
                .setRenderMode(RenderMode.STATIC_HTML)
                .setRobotsTxtRequired(true)
                .setUserAgent(FOOTBALL_DATA_USER_AGENT)
                .setRateLimitPerMinute(6)
                .setTimeoutMs(15000)
                .setReliabilityScore(new BigDecimal("90.00"))
                .setFallbackPriority(20)
                .setSelectorsJson("{\"format\":\"football-data-historical-results-csv\","
                        + "\"divisionCode\":\"" + divisionCode + "\"}");
        if (created) {
            target.setActive(true);
        }
        sourceTargetRepository.save(target);
    }

    private void upsertFootballDataMainFixtureSource(LeagueCode leagueCode, String divisionCode) {
        League league = leagueRepository.findByCode(leagueCode)
                .orElseThrow(() -> new IllegalStateException("League was not seeded: " + leagueCode));
        String name = "Football-Data Latest Fixtures " + league.getName() + " CSV";
        SourceTarget target = sourceTargetRepository
                .findByLeague_CodeAndSourceTypeAndName(league.getCode(), SourceType.FIXTURES, name)
                .orElseGet(SourceTarget::new);
        boolean created = target.getId() == null;

        target.setLeague(league)
                .setSourceType(SourceType.FIXTURES)
                .setName(name)
                .setUrlTemplate(FOOTBALL_DATA_MAIN_FIXTURES_URL)
                .setSourceSeasonToken(null)
                .setTargetSeasonLabel(league.getCurrentSeason())
                .setRenderMode(RenderMode.STATIC_HTML)
                .setRobotsTxtRequired(true)
                .setUserAgent(FOOTBALL_DATA_USER_AGENT)
                .setRateLimitPerMinute(6)
                .setTimeoutMs(15000)
                .setReliabilityScore(new BigDecimal("85.00"))
                .setFallbackPriority(25)
                .setSelectorsJson("{\"format\":\"football-data-latest-fixtures-csv\","
                        + "\"divisionCode\":\"" + divisionCode + "\"}");
        if (created) {
            target.setActive(true);
        }
        sourceTargetRepository.save(target);
    }

    private void upsertFootballDataMainOddsSource(LeagueCode leagueCode, String divisionCode) {
        League league = leagueRepository.findByCode(leagueCode)
                .orElseThrow(() -> new IllegalStateException("League was not seeded: " + leagueCode));
        String name = "Football-Data Latest Odds " + league.getName() + " CSV";
        SourceTarget target = sourceTargetRepository
                .findByLeague_CodeAndSourceTypeAndName(league.getCode(), SourceType.ODDS_REFERENCE, name)
                .orElseGet(SourceTarget::new);
        boolean created = target.getId() == null;

        target.setLeague(league)
                .setSourceType(SourceType.ODDS_REFERENCE)
                .setName(name)
                .setUrlTemplate(FOOTBALL_DATA_MAIN_FIXTURES_URL)
                .setSourceSeasonToken(null)
                .setTargetSeasonLabel(league.getCurrentSeason())
                .setRenderMode(RenderMode.STATIC_HTML)
                .setRobotsTxtRequired(true)
                .setUserAgent(FOOTBALL_DATA_USER_AGENT)
                .setRateLimitPerMinute(6)
                .setTimeoutMs(15000)
                .setReliabilityScore(new BigDecimal("85.00"))
                .setFallbackPriority(30)
                .setSelectorsJson("{\"format\":\"football-data-latest-fixtures-odds-csv\","
                        + "\"divisionCode\":\"" + divisionCode + "\","
                        + "\"bookmakerPrefixes\":\"B365,PS,Max,Avg,BFE\","
                        + "\"over25Prefixes\":\"B365,P,Max,Avg,BFE\","
                        + "\"under25Prefixes\":\"B365,P,Max,Avg,BFE\","
                        + "\"includeOver25\":true,"
                        + "\"includeUnder25\":true}");
        if (created) {
            target.setActive(true);
        }
        sourceTargetRepository.save(target);
    }

    private void upsertFootballDataMainHistoricalOddsSource(LeagueCode leagueCode, String divisionCode) {
        League league = leagueRepository.findByCode(leagueCode)
                .orElseThrow(() -> new IllegalStateException("League was not seeded: " + leagueCode));
        String name = "Football-Data Historical Odds " + league.getName() + " CSV";
        SourceTarget target = sourceTargetRepository
                .findByLeague_CodeAndSourceTypeAndName(league.getCode(), SourceType.ODDS_REFERENCE, name)
                .orElseGet(SourceTarget::new);
        boolean created = target.getId() == null;

        target.setLeague(league)
                .setSourceType(SourceType.ODDS_REFERENCE)
                .setName(name)
                .setUrlTemplate(FOOTBALL_DATA_2025_2026_BASE_URL + divisionCode + ".csv")
                .setSourceSeasonToken(null)
                .setTargetSeasonLabel(league.getCurrentSeason())
                .setRenderMode(RenderMode.STATIC_HTML)
                .setRobotsTxtRequired(true)
                .setUserAgent(FOOTBALL_DATA_USER_AGENT)
                .setRateLimitPerMinute(6)
                .setTimeoutMs(15000)
                .setReliabilityScore(new BigDecimal("90.00"))
                .setFallbackPriority(15)
                .setSelectorsJson("{\"format\":\"football-data-historical-odds-csv\","
                        + "\"divisionCode\":\"" + divisionCode + "\","
                        + "\"bookmakerPrefixes\":\"B365,PS,Max,Avg,BFE\","
                        + "\"over25Prefixes\":\"B365,P,Max,Avg,BFE\","
                        + "\"under25Prefixes\":\"B365,P,Max,Avg,BFE\","
                        + "\"oddsColumnMode\":\"CLOSING\","
                        + "\"includeOver25\":true,"
                        + "\"includeUnder25\":true}");
        if (created) {
            target.setActive(true);
        }
        sourceTargetRepository.save(target);
    }

    private void upsertFootballDataExtraOddsSource(League league, String country, String leagueName) {
        String name = "Football-Data Latest Odds " + league.getName() + " CSV";
        SourceTarget target = sourceTargetRepository
                .findByLeague_CodeAndSourceTypeAndName(league.getCode(), SourceType.ODDS_REFERENCE, name)
                .orElseGet(SourceTarget::new);
        boolean created = target.getId() == null;

        target.setLeague(league)
                .setSourceType(SourceType.ODDS_REFERENCE)
                .setName(name)
                .setUrlTemplate(FOOTBALL_DATA_NEW_FIXTURES_URL)
                .setSourceSeasonToken(null)
                .setTargetSeasonLabel(league.getCurrentSeason())
                .setRenderMode(RenderMode.STATIC_HTML)
                .setRobotsTxtRequired(true)
                .setUserAgent(FOOTBALL_DATA_USER_AGENT)
                .setRateLimitPerMinute(6)
                .setTimeoutMs(15000)
                .setReliabilityScore(new BigDecimal("85.00"))
                .setFallbackPriority(30)
                .setSelectorsJson("{\"format\":\"football-data-extra-fixtures-odds-csv\","
                        + "\"country\":\"" + country + "\","
                        + "\"leagueName\":\"" + leagueName + "\","
                        + "\"bookmakerPrefixes\":\"B365,PS,Max,Avg,BFE\","
                        + "\"includeOver25\":false,"
                        + "\"includeUnder25\":false}");
        if (created) {
            target.setActive(true);
        }
        sourceTargetRepository.save(target);
    }

    private void upsertFootballDataExtraHistoricalOddsSource(League league, String country, String leagueName, String resultsUrl) {
        String name = "Football-Data Historical Odds " + league.getName() + " CSV";
        SourceTarget target = sourceTargetRepository
                .findByLeague_CodeAndSourceTypeAndName(league.getCode(), SourceType.ODDS_REFERENCE, name)
                .orElseGet(SourceTarget::new);
        boolean created = target.getId() == null;

        target.setLeague(league)
                .setSourceType(SourceType.ODDS_REFERENCE)
                .setName(name)
                .setUrlTemplate(resultsUrl)
                .setSourceSeasonToken(league.getCurrentSeason())
                .setTargetSeasonLabel(league.getCurrentSeason())
                .setRenderMode(RenderMode.STATIC_HTML)
                .setRobotsTxtRequired(true)
                .setUserAgent(FOOTBALL_DATA_USER_AGENT)
                .setRateLimitPerMinute(6)
                .setTimeoutMs(15000)
                .setReliabilityScore(new BigDecimal("90.00"))
                .setFallbackPriority(15)
                .setSelectorsJson("{\"format\":\"football-data-extra-historical-odds-csv\","
                        + "\"country\":\"" + country + "\","
                        + "\"leagueName\":\"" + leagueName + "\","
                        + "\"season\":\"" + league.getCurrentSeason() + "\","
                        + "\"bookmakerPrefixes\":\"B365,PS,Max,Avg,BFE\","
                        + "\"oddsColumnMode\":\"CLOSING\","
                        + "\"includeOver25\":false,"
                        + "\"includeUnder25\":false}");
        if (created) {
            target.setActive(true);
        }
        sourceTargetRepository.save(target);
    }

    private void upsertFootballDataSource(
            League league,
            SourceType sourceType,
            String name,
            String url,
            String country,
            String leagueName
    ) {
        SourceTarget target = sourceTargetRepository
                .findByLeague_CodeAndSourceTypeAndName(league.getCode(), sourceType, name)
                .orElseGet(SourceTarget::new);
        boolean created = target.getId() == null;

        target.setLeague(league)
                .setSourceType(sourceType)
                .setName(name)
                .setUrlTemplate(url)
                .setSourceSeasonToken(sourceType == SourceType.RESULTS ? league.getCurrentSeason() : null)
                .setTargetSeasonLabel(league.getCurrentSeason())
                .setRenderMode(RenderMode.STATIC_HTML)
                .setRobotsTxtRequired(true)
                .setUserAgent(FOOTBALL_DATA_USER_AGENT)
                .setRateLimitPerMinute(6)
                .setTimeoutMs(15000)
                .setReliabilityScore(new BigDecimal("85.00"))
                .setFallbackPriority(sourceType == SourceType.RESULTS ? 20 : 25)
                .setSelectorsJson(extraLeagueSelectors(country, leagueName, sourceType == SourceType.RESULTS ? league.getCurrentSeason() : null));
        if (created) {
            target.setActive(true);
        }
        sourceTargetRepository.save(target);
    }

    private String extraLeagueSelectors(String country, String leagueName, String season) {
        String seasonJson = season == null ? "" : ",\"season\":\"" + season + "\"";
        return "{\"format\":\"football-data-extra-league-csv\",\"country\":\""
                + country
                + "\",\"leagueName\":\""
                + leagueName
                + "\""
                + seasonJson
                + "}";
    }

    private record ActiveLeagueSource(
            LeagueCode leagueCode,
            String country,
            String leagueName,
            String resultsUrl,
            String resultsSourceName
    ) {
    }

    private record MainLeagueSource(LeagueCode leagueCode, String divisionCode) {
    }

    private record TheSportsDbLeagueSource(LeagueCode leagueCode, String leagueId) {
    }

    private record ApiFootballLeagueSource(LeagueCode leagueCode, String leagueId) {
    }

    private record TheOddsApiLeagueSource(LeagueCode leagueCode, String sportKey) {
    }
}
