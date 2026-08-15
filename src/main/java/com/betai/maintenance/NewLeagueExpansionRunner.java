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
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "bet-ai.maintenance.new-league-expansion", name = "enabled", havingValue = "true")
public class NewLeagueExpansionRunner implements ApplicationRunner {

    private static final Set<LeagueCode> TARGET_LEAGUES = EnumSet.of(
            LeagueCode.UEFA_CHAMPIONS_LEAGUE,
            LeagueCode.UEFA_EUROPA_LEAGUE,
            LeagueCode.UEFA_EUROPA_CONFERENCE_LEAGUE,
            LeagueCode.FA_CUP,
            LeagueCode.EFL_CUP,
            LeagueCode.COPA_DEL_REY,
            LeagueCode.COPPA_ITALIA,
            LeagueCode.DFB_POKAL,
            LeagueCode.COUPE_DE_FRANCE,
            LeagueCode.DANISH_SUPERLIGA,
            LeagueCode.SWISS_SUPER_LEAGUE,
            LeagueCode.AUSTRIAN_BUNDESLIGA,
            LeagueCode.POLISH_EKSTRAKLASA,
            LeagueCode.CZECH_FIRST_LEAGUE,
            LeagueCode.CROATIAN_FOOTBALL_LEAGUE,
            LeagueCode.SERBIAN_SUPERLIGA,
            LeagueCode.ROMANIAN_LIGA_I,
            LeagueCode.GREEK_SUPER_LEAGUE,
            LeagueCode.UKRAINIAN_PREMIER_LEAGUE,
            LeagueCode.SLOVAK_FIRST_LEAGUE,
            LeagueCode.LIGA_MX,
            LeagueCode.MLS,
            LeagueCode.USL_CHAMPIONSHIP,
            LeagueCode.ARGENTINE_PRIMERA_DIVISION,
            LeagueCode.COPA_LIBERTADORES,
            LeagueCode.COPA_SUDAMERICANA,
            LeagueCode.BRAZILIAN_SERIE_A,
            LeagueCode.BRAZILIAN_SERIE_C,
            LeagueCode.CHILEAN_PRIMERA_DIVISION,
            LeagueCode.COLOMBIAN_PRIMERA_A,
            LeagueCode.PERUVIAN_LIGA_1,
            LeagueCode.URUGUAYAN_PRIMERA_DIVISION,
            LeagueCode.PARAGUAYAN_PRIMERA_DIVISION,
            LeagueCode.ECUADORIAN_SERIE_A,
            LeagueCode.SAUDI_PRO_LEAGUE,
            LeagueCode.UAE_PRO_LEAGUE,
            LeagueCode.QATAR_STARS_LEAGUE,
            LeagueCode.J1_LEAGUE,
            LeagueCode.J2_LEAGUE,
            LeagueCode.A_LEAGUE_MEN,
            LeagueCode.THAI_LEAGUE_1,
            LeagueCode.INDIAN_SUPER_LEAGUE,
            LeagueCode.INDONESIAN_LIGA_1,
            LeagueCode.UZBEKISTAN_SUPER_LEAGUE,
            LeagueCode.EGYPTIAN_PREMIER_LEAGUE,
            LeagueCode.SOUTH_AFRICAN_PREMIER_DIVISION,
            LeagueCode.MOROCCAN_BOTOLA_PRO,
            LeagueCode.TUNISIAN_LIGUE_1,
            LeagueCode.CAF_CHAMPIONS_LEAGUE,
            LeagueCode.NIGERIAN_PREMIER_FOOTBALL_LEAGUE
    );

    private final FullPipelineOrchestrationService fullPipelineOrchestrationService;
    private final LeagueRepository leagueRepository;
    private final MatchRepository matchRepository;
    private final ConfigurableApplicationContext applicationContext;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;
        try {
            LocalDate today = LocalDate.now(clock);
            FullPipelineRequest request = new FullPipelineRequest(
                    TARGET_LEAGUES,
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

            log.info("Starting one-shot new-league expansion for {} leagues.", TARGET_LEAGUES.size());
            FullPipelineResponse response = fullPipelineOrchestrationService.runPipeline(request);
            response.steps().forEach(step -> log.info(
                    "New-league expansion step={} status={} summary={} failure={}",
                    step.step(),
                    step.status(),
                    step.summary(),
                    step.failureReason()
            ));
            log.info("New-league expansion pipelineRunId={} status={}.", response.pipelineRunId(), response.status());

            int enabledLeagues = enableImportedTargetLeagues();
            log.info("Enabled {} imported target leagues for user-facing prediction selection.", enabledLeagues);
        } catch (Exception exception) {
            exitCode = 1;
            log.error("New-league expansion failed: {}", exception.getMessage(), exception);
        } finally {
            int finalExitCode = exitCode;
            SpringApplication.exit(applicationContext, () -> finalExitCode);
        }
    }

    private int enableImportedTargetLeagues() {
        int enabled = 0;
        for (LeagueCode code : TARGET_LEAGUES) {
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
        return TARGET_LEAGUES.stream().collect(Collectors.toUnmodifiableSet());
    }
}
