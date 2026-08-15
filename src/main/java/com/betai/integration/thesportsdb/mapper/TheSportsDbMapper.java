package com.betai.integration.thesportsdb.mapper;

import com.betai.domain.match.MatchStatus;
import com.betai.integration.thesportsdb.dto.TheSportsDbEventDto;
import com.betai.integration.thesportsdb.dto.TheSportsDbEventStatisticDto;
import com.betai.integration.thesportsdb.dto.TheSportsDbLeagueDto;
import com.betai.integration.thesportsdb.dto.TheSportsDbSeasonDto;
import com.betai.integration.thesportsdb.dto.TheSportsDbTeamDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class TheSportsDbMapper {

    private final ObjectMapper objectMapper;

    public List<TheSportsDbTeamDto> teams(String rawJson) {
        JsonNode root = root(rawJson);
        List<TheSportsDbTeamDto> teams = new ArrayList<>();
        for (JsonNode node : array(root, "list", "teams", "team", "data", "results")) {
            String externalId = text(node, "idTeam", "id", "teamId", "id_team");
            String name = text(node, "strTeam", "name", "teamName", "strTeamAlternate");
            if (!StringUtils.hasText(externalId) || !StringUtils.hasText(name)) {
                continue;
            }
            Set<String> aliases = new LinkedHashSet<>();
            addAlias(aliases, name);
            addAlias(aliases, text(node, "strAlternate", "alternateName", "strTeamAlternate"));
            addAlias(aliases, text(node, "strShort", "shortName", "abbr", "strTeamShort"));
            teams.add(new TheSportsDbTeamDto(
                    externalId,
                    name,
                    text(node, "strShort", "shortName", "abbr", "strTeamShort"),
                    text(node, "strCountry", "country", "strTeamCountry"),
                    List.copyOf(aliases),
                    text(node, "strBadge", "strTeamBadge", "badge", "badgeUrl"),
                    text(node, "strLogo", "strTeamLogo", "logo", "logoUrl"),
                    text(node, "strBanner", "strTeamBanner", "banner", "bannerUrl"),
                    text(node, "strEquipment", "equipment", "equipmentUrl"),
                    text(node, "strFanart1", "strTeamFanart1", "fanart", "fanartUrl")
            ));
        }
        return List.copyOf(teams);
    }

    public List<TheSportsDbLeagueDto> leagues(String rawJson) {
        JsonNode root = root(rawJson);
        List<TheSportsDbLeagueDto> leagues = new ArrayList<>();
        for (JsonNode node : array(root, "list", "leagues", "league", "lookup", "search", "all", "data", "results")) {
            String externalId = text(node, "idLeague", "id", "leagueId", "id_league");
            String name = text(node, "strLeague", "name", "leagueName");
            if (!StringUtils.hasText(externalId) || !StringUtils.hasText(name)) {
                continue;
            }
            leagues.add(new TheSportsDbLeagueDto(
                    externalId,
                    name,
                    text(node, "strCountry", "country", "strLeagueCountry"),
                    text(node, "strBadge", "strLeagueBadge", "badge", "badgeUrl"),
                    text(node, "strLogo", "strLeagueLogo", "logo", "logoUrl"),
                    text(node, "strBanner", "strLeagueBanner", "banner", "bannerUrl"),
                    text(node, "strPoster", "strLeaguePoster", "poster", "posterUrl"),
                    text(node, "strTrophy", "strLeagueTrophy", "trophy", "trophyUrl"),
                    text(node, "strFanart1", "strLeagueFanart1", "fanart", "fanartUrl")
            ));
        }
        return List.copyOf(leagues);
    }

    public List<TheSportsDbSeasonDto> seasons(String rawJson) {
        JsonNode root = root(rawJson);
        List<TheSportsDbSeasonDto> seasons = new ArrayList<>();
        for (JsonNode node : array(root, "list", "seasons", "season", "data", "results")) {
            String season = node.isTextual()
                    ? node.asText()
                    : text(node, "strSeason", "season", "name", "year");
            if (StringUtils.hasText(season)) {
                seasons.add(new TheSportsDbSeasonDto(season.trim()));
            }
        }
        return List.copyOf(seasons);
    }

    public List<TheSportsDbEventDto> events(String rawJson) {
        JsonNode root = root(rawJson);
        List<TheSportsDbEventDto> events = new ArrayList<>();
        for (JsonNode node : array(root, "events", "event", "schedule", "fixtures", "results", "data", "list", "lookup", "livescore")) {
            String externalEventId = text(node, "idEvent", "id", "eventId", "id_event");
            String homeTeamName = text(node, "strHomeTeam", "homeTeam", "home", "home_name");
            String awayTeamName = text(node, "strAwayTeam", "awayTeam", "away", "away_name");
            if (!StringUtils.hasText(externalEventId)
                    || !StringUtils.hasText(homeTeamName)
                    || !StringUtils.hasText(awayTeamName)) {
                continue;
            }
            String originalDate = text(node, "dateEvent", "date", "eventDate", "strDate");
            String originalTime = text(node, "strTimestamp", "strTime", "time", "eventTime", "kickoff");
            Optional<OffsetDateTime> kickoffAt = kickoffAt(node, originalDate, originalTime);
            events.add(new TheSportsDbEventDto(
                    externalEventId,
                    text(node, "idLeague", "leagueId", "id_league"),
                    text(node, "strSeason", "season"),
                    text(node, "idHomeTeam", "homeTeamId", "idHome", "home_id"),
                    text(node, "idAwayTeam", "awayTeamId", "idAway", "away_id"),
                    homeTeamName,
                    awayTeamName,
                    kickoffAt.orElse(null),
                    status(node, kickoffAt.orElse(null)),
                    integer(node, "intHomeScore", "homeScore", "home_score"),
                    integer(node, "intAwayScore", "awayScore", "away_score"),
                    text(node, "intRound", "strRound", "round", "roundLabel"),
                    text(node, "strVenue", "venue", "stadium"),
                    originalDate,
                    originalTime,
                    text(node, "strReferee", "referee"),
                    integer(node, "intHalfTimeHomeScore", "halfTimeHomeScore"),
                    integer(node, "intHalfTimeAwayScore", "halfTimeAwayScore"),
                    text(node, "strProgress", "strStatus", "status", "eventStatus"),
                    text(node, "strHomeTeamBadge", "homeTeamBadge", "homeBadgeUrl"),
                    text(node, "strAwayTeamBadge", "awayTeamBadge", "awayBadgeUrl"),
                    text(node, "strLeagueBadge", "leagueBadge", "leagueBadgeUrl"),
                    text(node, "strPoster", "poster", "posterUrl"),
                    text(node, "strThumb", "thumb", "thumbnailUrl")
            ));
        }
        return List.copyOf(events);
    }

    public List<TheSportsDbEventStatisticDto> eventStatistics(String rawJson, String homeTeamName, String awayTeamName) {
        JsonNode root = root(rawJson);
        List<TheSportsDbEventStatisticDto> statistics = new ArrayList<>();
        for (JsonNode node : array(root, "lookup", "eventstats", "statistics", "stats", "data", "results")) {
            String sourceName = text(node, "strStat", "statistic", "type", "name", "statName");
            if (!StringUtils.hasText(sourceName)) {
                continue;
            }
            String period = text(node, "period", "strPeriod");
            String homeValue = text(node, "intHome", "home", "homeValue", "home_value");
            String awayValue = text(node, "intAway", "away", "awayValue", "away_value");
            if (hasAny(node, "intHome", "home", "homeValue", "home_value")
                    || hasAny(node, "intAway", "away", "awayValue", "away_value")) {
                statistics.add(eventStatistic(homeTeamName, sourceName, homeValue, period));
                statistics.add(eventStatistic(awayTeamName, sourceName, awayValue, period));
                continue;
            }

            String teamName = text(node, "strTeam", "team", "teamName", "team_name");
            String value = text(node, "intStat", "value", "statValue", "stat_value");
            statistics.add(eventStatistic(teamName, sourceName, value, period));
        }
        return List.copyOf(statistics);
    }

    private JsonNode root(String rawJson) {
        try {
            return objectMapper.readTree(StringUtils.hasText(rawJson) ? rawJson : "{}");
        } catch (IOException exception) {
            throw new IllegalArgumentException("TheSportsDB JSON could not be parsed.", exception);
        }
    }

    private List<JsonNode> array(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode node = root.get(fieldName);
            if (node != null && node.isArray()) {
                List<JsonNode> values = new ArrayList<>();
                node.forEach(values::add);
                return values;
            }
        }
        if (root.isArray()) {
            List<JsonNode> values = new ArrayList<>();
            root.forEach(values::add);
            return values;
        }
        return List.of();
    }

    private String text(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull()) {
                String text = value.asText(null);
                if (StringUtils.hasText(text)) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    private Integer integer(JsonNode node, String... fieldNames) {
        String text = text(node, fieldNames);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean hasAny(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.has(fieldName)) {
                return true;
            }
        }
        return false;
    }

    private TheSportsDbEventStatisticDto eventStatistic(String teamName, String sourceName, String sourceValue, String period) {
        BigDecimal numeric = numeric(sourceValue);
        return new TheSportsDbEventStatisticDto(
                teamName,
                canonicalStatisticCode(sourceName),
                sourceName.trim(),
                numeric,
                numeric == null && StringUtils.hasText(sourceValue) ? sourceValue.trim() : null,
                StringUtils.hasText(period) ? period.trim() : "FULL_TIME",
                sourceName.trim()
        );
    }

    private BigDecimal numeric(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replace("%", "");
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String canonicalStatisticCode(String sourceName) {
        String normalized = normalizeStatName(sourceName);
        if (normalized.contains("expected_goals") || normalized.equals("xg")) {
            return "EXPECTED_GOALS";
        }
        if (normalized.contains("shots_on_target") || normalized.contains("shots_on_goal")) {
            return "SHOTS_ON_TARGET";
        }
        if (normalized.contains("shots_off_target")) {
            return "SHOTS_OFF_TARGET";
        }
        if (normalized.contains("shots")) {
            return "SHOTS";
        }
        if (normalized.contains("corner")) {
            return "CORNERS";
        }
        if (normalized.contains("yellow")) {
            return "YELLOW_CARDS";
        }
        if (normalized.contains("red")) {
            return "RED_CARDS";
        }
        if (normalized.contains("foul")) {
            return "FOULS";
        }
        if (normalized.contains("offside")) {
            return "OFFSIDES";
        }
        if (normalized.contains("save")) {
            return "SAVES";
        }
        if (normalized.contains("accurate_pass")) {
            return "ACCURATE_PASSES";
        }
        if (normalized.contains("pass_accuracy")) {
            return "PASS_ACCURACY";
        }
        if (normalized.contains("pass")) {
            return "PASSES";
        }
        if (normalized.contains("possession")) {
            return "POSSESSION";
        }
        return truncate("SOURCE_" + normalized.toUpperCase(Locale.ROOT), 64);
    }

    private String normalizeStatName(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replace("%", " percentage ")
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return StringUtils.hasText(normalized) ? normalized : "unknown";
    }

    private MatchStatus status(JsonNode node, OffsetDateTime kickoffAt) {
        String status = text(node, "strStatus", "status", "eventStatus", "strProgress");
        Integer homeScore = integer(node, "intHomeScore", "homeScore", "home_score");
        Integer awayScore = integer(node, "intAwayScore", "awayScore", "away_score");
        String normalized = status == null ? "" : status.toLowerCase();
        if (normalized.contains("postpon") || normalized.equals("ppd")) {
            return MatchStatus.POSTPONED;
        }
        if (normalized.contains("cancel") || normalized.equals("can")) {
            return MatchStatus.CANCELLED;
        }
        if (normalized.contains("abandon") || normalized.equals("abd")) {
            return MatchStatus.ABANDONED;
        }
        if (normalized.equals("ft")
                || normalized.startsWith("ft ")
                || normalized.startsWith("ft(")
                || normalized.equals("aet")
                || normalized.startsWith("aet ")
                || normalized.equals("final")
                || normalized.contains("finish")
                || normalized.contains("full time")
                || normalized.contains("ended")
                || normalized.contains("completed")
                || normalized.contains("after penalties")
                || normalized.contains("penalties ft")
                || normalized.contains("ft - penalties")
                || normalized.contains("penalties (ft)")
                || normalized.contains("penalties (after ft)")
                || normalized.contains("aet / penalties")
                || normalized.contains("aet (penalties)")
                || normalized.contains("ft pen")) {
            return MatchStatus.FINISHED;
        }
        if (kickoffAt != null && kickoffAt.isBefore(OffsetDateTime.now(ZoneOffset.UTC).minusHours(4))
                && homeScore != null && awayScore != null) {
            return MatchStatus.FINISHED;
        }
        if (normalized.contains("live")
                || normalized.contains("in play")
                || normalized.contains("in progress")
                || normalized.contains("playing")
                || normalized.contains("active")
                || normalized.contains("running")
                || normalized.equals("1h")
                || normalized.equals("2h")
                || normalized.contains("1st half")
                || normalized.contains("first half")
                || normalized.contains("2nd half")
                || normalized.contains("second half")
                || normalized.equals("ht")
                || normalized.contains("half time")
                || normalized.contains("halftime")
                || normalized.equals("break")
                || normalized.equals("et")
                || normalized.contains("extra time")
                || normalized.contains("et -")
                || normalized.contains("et ")
                || normalized.contains(" et")
                || normalized.contains("1st extra")
                || normalized.contains("2nd extra")
                || normalized.contains("penalties")
                || normalized.contains("penalty")
                || normalized.contains("shootout")
                || normalized.equals("p")
                || normalized.contains("paused")
                || normalized.contains("interrupted")
                || normalized.contains("var")
                || normalized.equals("ot")
                || normalized.contains("overtime")
                || normalized.matches("^[0-9]+'?\\+?[0-9]*'?$")) {
            return MatchStatus.LIVE;
        }
        if (normalized.equals("ns") || normalized.contains("not started") || normalized.contains("sched") || normalized.contains("tbc")) {
            return MatchStatus.SCHEDULED;
        }
        if (homeScore != null && awayScore != null) {
            return MatchStatus.FINISHED;
        }
        return MatchStatus.SCHEDULED;
    }

    private Optional<OffsetDateTime> kickoffAt(JsonNode node, String originalDate, String originalTime) {
        String timestamp = text(node, "strTimestamp", "timestamp", "dateTime", "datetime");
        Optional<OffsetDateTime> parsedTimestamp = parseTimestamp(timestamp);
        if (parsedTimestamp.isPresent()) {
            return parsedTimestamp;
        }
        if (!StringUtils.hasText(originalDate) || !StringUtils.hasText(originalTime)) {
            return Optional.empty();
        }
        return parseDateTime(originalDate, originalTime);
    }

    private Optional<OffsetDateTime> parseTimestamp(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Optional.of(Instant.parse(value).atOffset(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Optional.of(LocalDateTime.parse(value).atOffset(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    private Optional<OffsetDateTime> parseDateTime(String date, String time) {
        try {
            LocalDate parsedDate = LocalDate.parse(date.trim());
            String normalizedTime = time.trim();
            if (normalizedTime.endsWith("Z") || normalizedTime.contains("+")) {
                return parseTimestamp(parsedDate + "T" + normalizedTime);
            }
            if (normalizedTime.length() == 5) {
                normalizedTime = normalizedTime + ":00";
            }
            LocalTime parsedTime = LocalTime.parse(normalizedTime, DateTimeFormatter.ISO_LOCAL_TIME);
            return Optional.of(parsedDate.atTime(parsedTime).atOffset(ZoneOffset.UTC));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private void addAlias(Set<String> aliases, String value) {
        if (StringUtils.hasText(value)) {
            aliases.add(value.trim());
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
