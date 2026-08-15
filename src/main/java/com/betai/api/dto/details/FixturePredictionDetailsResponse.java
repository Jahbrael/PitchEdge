package com.betai.api.dto.details;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record FixturePredictionDetailsResponse(
        FixtureDto fixture,
        PredictionSummaryDto predictionSummary,
        List<MarketPredictionDto> markets,
        List<UnavailableMarketDto> unavailableMarkets,
        List<TeamRecentMatchDto> homeLast5,
        List<TeamRecentMatchDto> awayLast5,
        List<TeamRecentMatchDto> homeLast5Home,
        List<TeamRecentMatchDto> awayLast5Away,
        TeamFormSummaryDto homeForm,
        TeamFormSummaryDto awayForm,
        HeadToHeadSummaryDto headToHead,
        MarketEvidenceDto marketEvidence,
        RankingDto ranking,
        PreMatchStatsDto preMatchStats,
        LiveMatchStatsDto liveStats,
        List<TrendDto> trends,
        MatchPreviewDto matchPreview,
        String note
) {
    public record FixtureDto(
            UUID id,
            String homeTeam,
            String homeTeamBadgeUrl,
            String homeTeamLogoUrl,
            String awayTeam,
            String awayTeamBadgeUrl,
            String awayTeamLogoUrl,
            String competition,
            String leagueBadgeUrl,
            String leagueLogoUrl,
            OffsetDateTime kickoffTime,
            String status,
            String venue,
            Integer homeScore,
            Integer awayScore,
            String liveMinute
    ) {}

    public record PredictionSummaryDto(
            OffsetDateTime generatedAt,
            String modelVersion,
            String recommendedMarketCode,
            Integer seasonsUsed,
            Integer sampleSize,
            String dataSource,
            String confidenceLevel,
            String reasonQualified
    ) {}

    public record MarketPredictionDto(
            String marketCode,
            String marketName,
            String category,
            BigDecimal probability,
            String confidence,
            boolean qualified,
            boolean available,
            String explanation,
            BigDecimal modelProbability,
            BigDecimal bookmakerOdds,
            BigDecimal bookmakerImpliedProbability,
            BigDecimal modelEdge,
            String dataWarning
    ) {}

    public record UnavailableMarketDto(
            String marketCode,
            String marketName,
            String category,
            boolean available,
            String reason
    ) {}

    public record TeamRecentMatchDto(
            String date,
            String opponent,
            String homeOrAway,
            String score,
            String result,
            int goalsFor,
            int goalsAgainst,
            String competition,
            Boolean cleanSheet,
            Boolean bothTeamsScored
    ) {}

    public record TeamFormSummaryDto(
            String formString,
            int goalsScored,
            int goalsConceded,
            double avgGoalsScored,
            double avgGoalsConceded,
            int cleanSheets,
            int failedToScoreCount,
            int bothTeamsScoredCount,
            int over15Count,
            int over25Count
    ) {}

    public record H2hMatchDto(
            String date,
            String competition,
            String homeTeam,
            String awayTeam,
            String score,
            String winner
    ) {}

    public record H2hOccurrenceDto(
            int hits,
            int sampleSize
    ) {}

    public record HeadToHeadSummaryDto(
            int totalMatches,
            int homeWins,
            int awayWins,
            int draws,
            double avgGoals,
            String bttsRate,
            String over15Rate,
            String over25Rate,
            H2hOccurrenceDto over15,
            H2hOccurrenceDto over35,
            H2hOccurrenceDto under35,
            H2hOccurrenceDto under45,
            H2hOccurrenceDto homeScored,
            H2hOccurrenceDto awayScored,
            H2hOccurrenceDto under25,
            H2hOccurrenceDto noCleanSheet,
            List<H2hMatchDto> matches
    ) {}

    public record MarketEvidenceDto(
            String marketCode,
            String homeOver15Rate,
            String awayOver15Rate,
            String combinedOver15Rate,
            String homeOver25Rate,
            String awayOver25Rate,
            String homeBttsRate,
            String awayBttsRate,
            String homeCleanSheetRate,
            String awayFailedToScoreRate,
            String cornersInfo,
            String cardsInfo
    ) {}

    public record RankingDto(
            boolean available,
            String sourceLabel,
            String seasonLabel,
            String unavailableReason,
            List<TeamStandingDto> rows
    ) {}

    public record TeamStandingDto(
            int position,
            UUID teamId,
            String teamName,
            String teamBadgeUrl,
            String teamLogoUrl,
            int played,
            int wins,
            int draws,
            int losses,
            int goalsFor,
            int goalsAgainst,
            int goalDifference,
            int points,
            double pointsPerGame,
            List<String> last5,
            boolean currentFixtureTeam
    ) {}

    public record PreMatchStatsDto(
            String sampleLabel,
            List<OverUnderComparisonDto> overUnderGoals,
            TeamRateDto homeBttsYes,
            TeamRateDto awayBttsYes,
            TeamRateDto homeCleanSheets,
            TeamRateDto awayCleanSheets,
            TeamRateDto homeFailedToScore,
            TeamRateDto awayFailedToScore,
            String cornersAvailability,
            String cardsAvailability
    ) {}

    public record OverUnderComparisonDto(
            String line,
            TeamOverUnderDto home,
            TeamOverUnderDto away
    ) {}

    public record TeamOverUnderDto(
            int sampleSize,
            int underCount,
            int overCount,
            String underPercent,
            String overPercent
    ) {}

    public record TeamRateDto(
            int sampleSize,
            int count,
            String percent
    ) {}

    public record TrendDto(
            String category,
            String label,
            String value,
            String detail,
            int sampleSize
    ) {}

    public record LiveMatchStatsDto(
            boolean available,
            String statusLabel,
            OffsetDateTime refreshedAt,
            String unavailableReason,
            List<LiveStatRowDto> rows
    ) {}

    public record LiveStatRowDto(
            String code,
            String label,
            String homeValue,
            String awayValue,
            String displayType
    ) {}

    public record MatchPreviewDto(
            String text,
            List<String> limitations
    ) {}
}
