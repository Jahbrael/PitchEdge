package com.betai.maintenance;

import com.betai.api.dto.FullPipelineRequest;
import com.betai.api.dto.FullPipelineResponse;
import com.betai.domain.feature.SeasonSelectionMode;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.MatchStatus;
import com.betai.repository.LeagueRepository;
import com.betai.repository.MatchRepository;
import com.betai.service.FullPipelineOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "bet-ai.maintenance.extra-league-expansion", name = "enabled", havingValue = "true")
public class ExtraLeagueExpansionRunner implements ApplicationRunner {

    private static final int BATCH_SIZE = 20;
    private static final List<LeagueCode> TARGET_LEAGUES = List.of(
            LeagueCode.RUSSIAN_FOOTBALL_PREMIER_LEAGUE,
            LeagueCode.ITALIAN_SERIE_B,
            LeagueCode.SCOTTISH_CHAMPIONSHIP,
            LeagueCode.ENGLISH_LEAGUE_1,
            LeagueCode.ENGLISH_LEAGUE_2,
            LeagueCode.ITALIAN_SERIE_C_GIRONE_C,
            LeagueCode.GERMAN_2_BUNDESLIGA,
            LeagueCode.SPANISH_LA_LIGA_2,
            LeagueCode.FRENCH_LIGUE_2,
            LeagueCode.SWEDISH_SUPERETTAN,
            LeagueCode.NORWEGIAN_1_DIVISJON,
            LeagueCode.WELSH_PREMIER_LEAGUE,
            LeagueCode.UEFA_NATIONS_LEAGUE,
            LeagueCode.AFRICAN_CUP_OF_NATIONS,
            LeagueCode.COPA_ARGENTINA,
            LeagueCode.FIFA_CLUB_WORLD_CUP,
            LeagueCode.SUPERCOPPA_ITALIANA,
            LeagueCode.TACA_DE_LIGA,
            LeagueCode.TACA_DE_PORTUGAL,
            LeagueCode.SUPERCOPA_DE_ESPANA,
            LeagueCode.UEFA_SUPER_CUP,
            LeagueCode.VENEZUELA_PRIMERA_DIVISION,
            LeagueCode.AMERICAN_NWSL,
            LeagueCode.INTERNATIONAL_FRIENDLIES,
            LeagueCode.UEFA_EUROPEAN_UNDER_21_CHAMPIONSHIP,
            LeagueCode.CLUB_FRIENDLIES,
            LeagueCode.FA_COMMUNITY_SHIELD,
            LeagueCode.ENGLISH_NATIONAL_LEAGUE,
            LeagueCode.ARGENTINIAN_PRIMERA_B_NACIONAL,
            LeagueCode.ALBANIAN_SUPERLIGA,
            LeagueCode.ANDORRAN_1A_DIVISIO,
            LeagueCode.ARMENIAN_PREMIER_LEAGUE,
            LeagueCode.AUSTRALIA_ACT_NPL,
            LeagueCode.BELARUS_VYSCHA_LIGA,
            LeagueCode.BELGIAN_CHALLENGER_PRO_LEAGUE,
            LeagueCode.BOSNIAN_PREMIER_LIGA,
            LeagueCode.BULGARIAN_FIRST_LEAGUE,
            LeagueCode.CHINA_LEAGUE_ONE,
            LeagueCode.CYPRIOT_FIRST_DIVISION,
            LeagueCode.DANISH_2ND_DIVISION,
            LeagueCode.FAROE_ISLANDS_PREMIER_LEAGUE,
            LeagueCode.FRENCH_LIGUE_3,
            LeagueCode.GEORGIAN_EROVNULI_LIGA,
            LeagueCode.GERMANY_LIGA_3,
            LeagueCode.GREEK_SUPER_LEAGUE_2,
            LeagueCode.DUTCH_EERSTE_DIVISIE,
            LeagueCode.ISRAELI_PREMIER_LEAGUE,
            LeagueCode.ITALY_SERIE_D_GIRONE_D,
            LeagueCode.ENGLISH_NORTHERN_PREMIER_LEAGUE_PREMIER_DIVISION,
            LeagueCode.ENGLISH_ISTHMIAN_LEAGUE_PREMIER_DIVISION,
            LeagueCode.ENGLISH_SOUTHERN_PREMIER_LEAGUE_SOUTH_DIVISION,
            LeagueCode.MACEDONIAN_FIRST_LEAGUE,
            LeagueCode.MALTESE_PREMIER_LEAGUE,
            LeagueCode.MEXICAN_LIGA_DE_EXPANSION_MX,
            LeagueCode.MOLDOVAN_NATIONAL_DIVISION,
            LeagueCode.MONTENEGRIN_FIRST_LEAGUE,
            LeagueCode.MOROCCAN_BOTOLA_2,
            LeagueCode.NORTHERN_IRISH_PREMIERSHIP,
            LeagueCode.POLISH_I_LIGA,
            LeagueCode.PORTUGUESE_LIGAPRO,
            LeagueCode.ROMANIAN_LIGA_II,
            LeagueCode.RUSSIAN_FOOTBALL_NATIONAL_LEAGUE,
            LeagueCode.SAN_MARINO_CAMPIONATO,
            LeagueCode.SCOTTISH_LEAGUE_1,
            LeagueCode.SCOTTISH_LEAGUE_2,
            LeagueCode.SWEDISH_DIVISION_1_NORTH,
            LeagueCode.TURKISH_1_LIG,
            LeagueCode.UKRAINIAN_FIRST_LEAGUE,
            LeagueCode.TURKISH_2_LIG,
            LeagueCode.ENGLISH_NATIONAL_LEAGUE_NORTH,
            LeagueCode.ENGLISH_NATIONAL_LEAGUE_SOUTH,
            LeagueCode.DANISH_1ST_DIVISION,
            LeagueCode.BOLIVIAN_PRIMERA_DIVISION,
            LeagueCode.HUNGARIAN_NB_I,
            LeagueCode.SLOVENIAN_1_SNL,
            LeagueCode.AZERBAIJANI_PREMIER_LEAGUE,
            LeagueCode.LUXEMBOURG_NATIONAL_DIVISION,
            LeagueCode.GERMAN_REGIONALLIGA_NORD,
            LeagueCode.SWISS_CHALLENGE_LEAGUE,
            LeagueCode.AFC_CHAMPIONS_LEAGUE_ELITE,
            LeagueCode.CONCACAF_CHAMPIONS_CUP,
            LeagueCode.SCOTTISH_FA_CUP,
            LeagueCode.COPA_DO_BRASIL,
            LeagueCode.CONCACAF_CENTRAL_AMERICAN_CUP,
            LeagueCode.IRANIAN_AZADEGAN_LEAGUE,
            LeagueCode.IRANIAN_PERSIAN_GULF_PRO_LEAGUE,
            LeagueCode.THAI_LEAGUE_2,
            LeagueCode.KENYAN_PREMIER_LEAGUE,
            LeagueCode.GERMAN_REGIONALLIGA_WEST,
            LeagueCode.GERMAN_REGIONALLIGA_SUDWEST,
            LeagueCode.GERMAN_REGIONALLIGA_BAYERN,
            LeagueCode.GERMAN_REGIONALLIGA_NORDOST,
            LeagueCode.ALGERIAN_LIGUE_1,
            LeagueCode.SENEGAL_LIGUE_1,
            LeagueCode.NORTHERN_IRISH_CHAMPIONSHIP,
            LeagueCode.SVENSKA_CUPEN,
            LeagueCode.ITALY_SERIE_D_GIRONE_B,
            LeagueCode.ITALY_SERIE_D_GIRONE_C,
            LeagueCode.ITALY_SERIE_D_GIRONE_E,
            LeagueCode.ITALY_SERIE_D_GIRONE_F
    );

    private final FullPipelineOrchestrationService fullPipelineOrchestrationService;
    private final LeagueRepository leagueRepository;
    private final MatchRepository matchRepository;
    private final ConfigurableApplicationContext applicationContext;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;
        List<LeagueCode> successfulLeagues = new ArrayList<>();
        List<LeagueCode> failedLeagues = new ArrayList<>();
        try {
            List<LeagueCode> pendingLeagues = pendingTargets();
            log.info(
                    "Starting one-shot extra-league expansion for {} pending leagues out of {} configured targets.",
                    pendingLeagues.size(),
                    TARGET_LEAGUES.size()
            );
            if (pendingLeagues.isEmpty()) {
                log.info("No pending extra-league expansion targets remain.");
                return;
            }

            LocalDate today = LocalDate.now(clock);
            for (int start = 0; start < pendingLeagues.size(); start += BATCH_SIZE) {
                List<LeagueCode> batch = pendingLeagues.subList(start, Math.min(start + BATCH_SIZE, pendingLeagues.size()));
                Set<LeagueCode> batchSet = batch.stream().collect(Collectors.toCollection(() -> EnumSet.noneOf(LeagueCode.class)));
                int batchNumber = (start / BATCH_SIZE) + 1;
                log.info("Starting extra-league expansion batch {} with {} leagues: {}.", batchNumber, batchSet.size(), batchSet);
                try {
                    FullPipelineResponse response = fullPipelineOrchestrationService.runPipeline(request(today, batchSet));
                    response.steps().forEach(step -> log.info(
                            "Extra-league expansion batch={} step={} status={} summary={} failure={}",
                            batchNumber,
                            step.step(),
                            step.status(),
                            step.summary(),
                            step.failureReason()
                    ));
                    log.info(
                            "Extra-league expansion batch={} pipelineRunId={} status={}.",
                            batchNumber,
                            response.pipelineRunId(),
                            response.status()
                    );
                    if ("FAILED".equals(response.status())) {
                        failedLeagues.addAll(batch);
                    } else {
                        successfulLeagues.addAll(batch);
                    }
                    int enabledLeagues = enableImportedTargetLeagues(batchSet);
                    log.info("Enabled {} imported leagues after batch {}.", enabledLeagues, batchNumber);
                } catch (RuntimeException exception) {
                    failedLeagues.addAll(batch);
                    log.error(
                            "Extra-league expansion batch {} failed for {}: {}",
                            batchNumber,
                            batchSet,
                            exception.getMessage(),
                            exception
                    );
                }
            }
            if (successfulLeagues.isEmpty() && !failedLeagues.isEmpty()) {
                exitCode = 1;
            }
            log.info(
                    "Extra-league expansion finished. successfulLeagues={}, failedLeagues={}.",
                    successfulLeagues,
                    failedLeagues
            );
        } catch (RuntimeException exception) {
            exitCode = 1;
            log.error("Extra-league expansion failed before batch execution: {}", exception.getMessage(), exception);
        } finally {
            int finalExitCode = exitCode;
            SpringApplication.exit(applicationContext, () -> finalExitCode);
        }
    }

    private FullPipelineRequest request(LocalDate today, Set<LeagueCode> leagueCodes) {
        return new FullPipelineRequest(
                leagueCodes,
                today,
                today,
                today.plusDays(14),
                today.minusDays(3),
                today.minusDays(1),
                today.minusDays(365),
                today.minusDays(1),
                EnumSet.of(MatchStatus.SCHEDULED),
                null,
                false,
                false,
                false,
                null,
                false,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                30,
                true,
                true,
                true,
                false,
                true,
                3,
                SeasonSelectionMode.CURRENT_AND_RECENT_COMPLETE,
                null
        );
    }

    private List<LeagueCode> pendingTargets() {
        return TARGET_LEAGUES.stream()
                .filter(code -> !completedAndEnabled(code))
                .toList();
    }

    private boolean completedAndEnabled(LeagueCode code) {
        League league = leagueRepository.findByCode(code).orElse(null);
        return league != null && league.isScrapeEnabled() && matchRepository.countByLeague_Code(code) > 0;
    }

    private int enableImportedTargetLeagues(Set<LeagueCode> batch) {
        int enabled = 0;
        for (LeagueCode code : batch) {
            long matchCount = matchRepository.countByLeague_Code(code);
            if (matchCount <= 0) {
                continue;
            }
            League league = leagueRepository.findByCode(code).orElse(null);
            if (league == null || league.isScrapeEnabled()) {
                continue;
            }
            league.setScrapeEnabled(true);
            leagueRepository.save(league);
            enabled++;
        }
        return enabled;
    }

    public static Set<LeagueCode> targetLeagues() {
        return Set.copyOf(TARGET_LEAGUES);
    }
}
