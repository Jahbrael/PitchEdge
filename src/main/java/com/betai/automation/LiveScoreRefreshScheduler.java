package com.betai.automation;

import com.betai.api.dto.FixtureScoreRefreshSummary;
import com.betai.integration.thesportsdb.TheSportsDbProperties;
import com.betai.service.FixtureScoreRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "bet-ai.integrations.thesportsdb",
        name = "live-scores-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LiveScoreRefreshScheduler {

    private final FixtureScoreRefreshService fixtureScoreRefreshService;
    private final TheSportsDbProperties theSportsDbProperties;
    private final Clock clock;

    @Scheduled(
            fixedDelayString = "${bet-ai.integrations.thesportsdb.live-scores-refresh-fixed-delay-ms:180000}",
            initialDelayString = "${bet-ai.integrations.thesportsdb.live-scores-refresh-initial-delay-ms:60000}"
    )
    public void refreshTodayScores() {
        if (!theSportsDbProperties.enabled() || !StringUtils.hasText(theSportsDbProperties.apiKey())) {
            log.debug("Skipping live score refresh because TheSportsDB is disabled or not configured.");
            return;
        }

        LocalDate today = LocalDate.now(clock);
        try {
            FixtureScoreRefreshSummary summary = fixtureScoreRefreshService.refreshLiveScores(today);
            log.info(
                    "Automatic live score refresh completed for {}: checked={}, updated={}, unchanged={}, failed={}, reason={}",
                    today,
                    summary.fixturesChecked(),
                    summary.fixturesUpdated(),
                    summary.fixturesUnchanged(),
                    summary.failedFixtures(),
                    summary.safeFailureReason()
            );
        } catch (Exception exception) {
            log.warn("Automatic live score refresh failed for {}: {}", today, exception.getMessage());
        }
    }
}
