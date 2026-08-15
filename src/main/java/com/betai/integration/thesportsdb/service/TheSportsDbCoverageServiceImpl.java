package com.betai.integration.thesportsdb.service;

import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.market.MarketType;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.source.CoverageLevel;
import com.betai.domain.source.LeagueSeasonCoverage;
import com.betai.domain.source.LeagueSeasonMarketAvailability;
import com.betai.exception.ReferenceDataNotFoundException;
import com.betai.integration.thesportsdb.TheSportsDbCoverageProperties;
import com.betai.repository.EventStatisticRepository;
import com.betai.repository.LeagueRepository;
import com.betai.repository.LeagueSeasonCoverageRepository;
import com.betai.repository.LeagueSeasonMarketAvailabilityRepository;
import com.betai.repository.MarketDefinitionRepository;
import com.betai.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TheSportsDbCoverageServiceImpl implements TheSportsDbCoverageService {

    private final LeagueRepository leagueRepository;
    private final MatchRepository matchRepository;
    private final EventStatisticRepository eventStatisticRepository;
    private final MarketDefinitionRepository marketDefinitionRepository;
    private final LeagueSeasonCoverageRepository coverageRepository;
    private final LeagueSeasonMarketAvailabilityRepository marketAvailabilityRepository;
    private final TheSportsDbCoverageProperties coverageProperties;
    private final Clock clock;

    @Override
    @Transactional
    public LeagueSeasonCoverage recalculate(LeagueCode leagueCode, String seasonLabel) {
        League league = leagueRepository.findByCode(leagueCode)
                .orElseThrow(() -> new ReferenceDataNotFoundException("League is not configured: " + leagueCode + "."));
        String resolvedSeason = StringUtils.hasText(seasonLabel) ? seasonLabel.trim() : league.getCurrentSeason();
        CoverageFacts facts = facts(leagueCode, resolvedSeason);
        OffsetDateTime verifiedAt = OffsetDateTime.now(clock);

        LeagueSeasonCoverage coverage = coverageRepository.findByLeague_CodeAndSeasonLabel(leagueCode, resolvedSeason)
                .orElseGet(LeagueSeasonCoverage::new);
        coverage.setLeague(league)
                .setSeasonLabel(resolvedSeason)
                .setHasFixtures(facts.fixtureCount > 0)
                .setHasResults(facts.completedCount > 0)
                .setHasTeamStatistics(facts.eventsWithStatistics > 0)
                .setHasEventStatistics(facts.eventsWithStatistics > 0)
                .setHasLineups(false)
                .setHasTimeline(false)
                .setHasPlayerStatistics(false)
                .setHasGoals(facts.completedCount > 0)
                .setHasAssists(false)
                .setHasCards(facts.cardsCount > 0)
                .setHasCorners(facts.cornersCount > 0)
                .setHasShots(facts.shotsCount > 0)
                .setHasShotsOnTarget(facts.shotsOnTargetCount > 0)
                .setHasPasses(facts.passesCount > 0)
                .setHasSaves(facts.savesCount > 0)
                .setHasXg(facts.xgCount > 0)
                .setCompletedEventsChecked(toInt(facts.completedCount))
                .setEventsWithStatistics(toInt(facts.eventsWithStatistics))
                .setEventsWithLineups(0)
                .setEventsWithTimeline(0)
                .setPlayersWithStatistics(0)
                .setCoveragePercentage(percentage(facts.eventsWithStatistics, facts.completedCount))
                .setStatisticsCoverageLevel(level(ratio(facts.eventsWithStatistics, facts.completedCount)))
                .setCornersCoverageLevel(level(ratio(facts.cornersCount, facts.completedCount)))
                .setCardsCoverageLevel(level(ratio(facts.cardsCount, facts.completedCount)))
                .setLastVerifiedAt(verifiedAt);
        LeagueSeasonCoverage saved = coverageRepository.save(coverage);
        updateMarketAvailability(league, resolvedSeason, saved, verifiedAt);
        return saved;
    }

    private CoverageFacts facts(LeagueCode leagueCode, String seasonLabel) {
        long fixtureCount = matchRepository.countByLeague_CodeAndSeasonLabel(leagueCode, seasonLabel);
        long completedCount = matchRepository.countByLeague_CodeAndSeasonLabelAndStatus(
                leagueCode,
                seasonLabel,
                MatchStatus.FINISHED
        );
        long yellowCards = eventStatisticRepository.countDistinctMatchesWithStatistic(leagueCode, seasonLabel, "YELLOW_CARDS");
        long redCards = eventStatisticRepository.countDistinctMatchesWithStatistic(leagueCode, seasonLabel, "RED_CARDS");
        return new CoverageFacts(
                fixtureCount,
                completedCount,
                eventStatisticRepository.countDistinctMatchesWithAnyStatistic(leagueCode, seasonLabel),
                eventStatisticRepository.countDistinctMatchesWithStatistic(leagueCode, seasonLabel, "CORNERS"),
                Math.max(yellowCards, redCards),
                eventStatisticRepository.countDistinctMatchesWithStatistic(leagueCode, seasonLabel, "SHOTS"),
                eventStatisticRepository.countDistinctMatchesWithStatistic(leagueCode, seasonLabel, "SHOTS_ON_TARGET"),
                eventStatisticRepository.countDistinctMatchesWithStatistic(leagueCode, seasonLabel, "PASSES"),
                eventStatisticRepository.countDistinctMatchesWithStatistic(leagueCode, seasonLabel, "SAVES"),
                eventStatisticRepository.countDistinctMatchesWithStatistic(leagueCode, seasonLabel, "EXPECTED_GOALS")
        );
    }

    private void updateMarketAvailability(
            League league,
            String seasonLabel,
            LeagueSeasonCoverage coverage,
            OffsetDateTime verifiedAt
    ) {
        List<MarketDefinition> markets = marketDefinitionRepository.findByEnabledTrueOrderByDisplayNameAsc();
        for (MarketDefinition market : markets) {
            AvailabilityDecision decision = availability(market, coverage);
            LeagueSeasonMarketAvailability availability = marketAvailabilityRepository
                    .findByLeague_CodeAndSeasonLabelAndMarketCode(league.getCode(), seasonLabel, market.getCode())
                    .orElseGet(LeagueSeasonMarketAvailability::new);
            availability.setLeague(league)
                    .setSeasonLabel(seasonLabel)
                    .setMarketCode(market.getCode())
                    .setAvailable(decision.available())
                    .setCoverageLevel(decision.coverageLevel())
                    .setReason(decision.reason())
                    .setLastVerifiedAt(verifiedAt);
            marketAvailabilityRepository.save(availability);
        }
    }

    private AvailabilityDecision availability(MarketDefinition market, LeagueSeasonCoverage coverage) {
        if (market.isRequiresPlayerData()) {
            return unavailable(CoverageLevel.UNAVAILABLE, "Player statistics and expected minutes are not available.");
        }
        if (market.isRequiresHalfTimeData()) {
            return unavailable(CoverageLevel.UNAVAILABLE, "Reliable period-specific scores are not available.");
        }
        if (market.isRequiresEventData()) {
            return unavailable(CoverageLevel.UNAVAILABLE, "Timeline/event-order coverage is not available.");
        }

        MarketType type = market.getMarketFamily();
        if (type == MarketType.TOTAL_CORNERS || type == MarketType.TEAM_CORNERS) {
            return requirement(coverage.getCornersCoverageLevel(), "Corner coverage is sparse or unavailable.");
        }
        if (type == MarketType.TOTAL_YELLOW_CARDS || type == MarketType.RED_CARD) {
            return requirement(coverage.getCardsCoverageLevel(), "Card coverage is sparse or unavailable.");
        }
        if (!coverage.isHasResults() || !coverage.isHasGoals()) {
            return unavailable(CoverageLevel.UNAVAILABLE, "Historical results and goal scores are unavailable.");
        }
        return new AvailabilityDecision(true, CoverageLevel.FULL, null);
    }

    private AvailabilityDecision requirement(CoverageLevel level, String unavailableReason) {
        if (level == CoverageLevel.FULL || level == CoverageLevel.PARTIAL) {
            return new AvailabilityDecision(true, level, null);
        }
        return unavailable(level, unavailableReason);
    }

    private AvailabilityDecision unavailable(CoverageLevel level, String reason) {
        return new AvailabilityDecision(false, level, reason);
    }

    private CoverageLevel level(BigDecimal ratio) {
        if (ratio.compareTo(fullThreshold()) >= 0) {
            return CoverageLevel.FULL;
        }
        if (ratio.compareTo(partialThreshold()) >= 0) {
            return CoverageLevel.PARTIAL;
        }
        if (ratio.signum() > 0) {
            return CoverageLevel.SPARSE;
        }
        return CoverageLevel.UNAVAILABLE;
    }

    private BigDecimal ratio(long numerator, long denominator) {
        if (denominator <= 0 || numerator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 8, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(long numerator, long denominator) {
        return ratio(numerator, denominator).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal fullThreshold() {
        return coverageProperties.fullThreshold() == null ? new BigDecimal("0.80") : coverageProperties.fullThreshold();
    }

    private BigDecimal partialThreshold() {
        return coverageProperties.partialThreshold() == null ? new BigDecimal("0.30") : coverageProperties.partialThreshold();
    }

    private int toInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private record CoverageFacts(
            long fixtureCount,
            long completedCount,
            long eventsWithStatistics,
            long cornersCount,
            long cardsCount,
            long shotsCount,
            long shotsOnTargetCount,
            long passesCount,
            long savesCount,
            long xgCount
    ) {
    }

    private record AvailabilityDecision(boolean available, CoverageLevel coverageLevel, String reason) {
    }
}
