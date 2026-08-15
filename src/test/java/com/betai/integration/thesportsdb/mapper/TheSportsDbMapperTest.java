package com.betai.integration.thesportsdb.mapper;

import com.betai.domain.match.MatchStatus;
import com.betai.integration.thesportsdb.dto.TheSportsDbEventDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TheSportsDbMapperTest {

    private final TheSportsDbMapper mapper = new TheSportsDbMapper(new ObjectMapper());

    @Test
    void mapsScheduleEventsWithoutInventingMissingScores() {
        String rawJson = """
                {
                  "events": [
                    {
                      "idEvent": "1001",
                      "idLeague": "4328",
                      "idHomeTeam": "10",
                      "idAwayTeam": "20",
                      "strHomeTeam": "Home FC",
                      "strAwayTeam": "Away FC",
                      "dateEvent": "2026-08-01",
                      "strTime": "15:00:00",
                      "strStatus": "Not Started"
                    }
                  ]
                }
                """;

        List<TheSportsDbEventDto> events = mapper.events(rawJson);

        assertThat(events).hasSize(1);
        TheSportsDbEventDto event = events.getFirst();
        assertThat(event.externalEventId()).isEqualTo("1001");
        assertThat(event.kickoffAt()).isEqualTo(OffsetDateTime.parse("2026-08-01T15:00:00Z"));
        assertThat(event.status()).isEqualTo(MatchStatus.SCHEDULED);
        assertThat(event.homeScore()).isNull();
        assertThat(event.awayScore()).isNull();
    }

    @Test
    void mapsFinishedEventsFromScoresAndTimestamp() {
        String rawJson = """
                {
                  "events": [
                    {
                      "idEvent": "1002",
                      "strHomeTeam": "Home FC",
                      "strAwayTeam": "Away FC",
                      "strTimestamp": "2026-08-01T16:30:00Z",
                      "intHomeScore": "2",
                      "intAwayScore": "1"
                    }
                  ]
                }
                """;

        List<TheSportsDbEventDto> events = mapper.events(rawJson);

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().status()).isEqualTo(MatchStatus.FINISHED);
        assertThat(events.getFirst().homeScore()).isEqualTo(2);
        assertThat(events.getFirst().awayScore()).isEqualTo(1);
        assertThat(events.getFirst().kickoffAt()).isEqualTo(OffsetDateTime.parse("2026-08-01T16:30:00Z"));
    }

    @Test
    void mapsExtraTimeAndPenaltiesToLiveStatusEvenWhenScoresPresent() {
        String rawJson = """
                {
                  "events": [
                    {
                      "idEvent": "1003",
                      "strHomeTeam": "Home FC",
                      "strAwayTeam": "Away FC",
                      "strStatus": "Extra Time",
                      "intHomeScore": "1",
                      "intAwayScore": "1"
                    },
                    {
                      "idEvent": "1004",
                      "strHomeTeam": "Home FC",
                      "strAwayTeam": "Away FC",
                      "strStatus": "105'",
                      "intHomeScore": "2",
                      "intAwayScore": "1"
                    },
                    {
                      "idEvent": "1005",
                      "strHomeTeam": "Home FC",
                      "strAwayTeam": "Away FC",
                      "strStatus": "Penalties",
                      "intHomeScore": "2",
                      "intAwayScore": "2"
                    }
                  ]
                }
                """;

        List<TheSportsDbEventDto> events = mapper.events(rawJson);

        assertThat(events).hasSize(3);
        assertThat(events.get(0).status()).isEqualTo(MatchStatus.LIVE);
        assertThat(events.get(1).status()).isEqualTo(MatchStatus.LIVE);
        assertThat(events.get(2).status()).isEqualTo(MatchStatus.LIVE);
    }

    @Test
    void mapsCompletedPenaltiesToFinishedStatusWhenKickoffIsOld() {
        String rawJson = """
                {
                  "events": [
                    {
                      "idEvent": "2001",
                      "strHomeTeam": "Home FC",
                      "strAwayTeam": "Away FC",
                      "strStatus": "Penalties",
                      "intHomeScore": "1",
                      "intAwayScore": "1",
                      "dateEvent": "2020-01-01",
                      "strTime": "15:00:00"
                    },
                    {
                      "idEvent": "2002",
                      "strHomeTeam": "Home FC",
                      "strAwayTeam": "Away FC",
                      "strStatus": "FT (Penalties)",
                      "intHomeScore": "2",
                      "intAwayScore": "2"
                    }
                  ]
                }
                """;

        List<TheSportsDbEventDto> events = mapper.events(rawJson);

        assertThat(events).hasSize(2);
        assertThat(events.get(0).status()).isEqualTo(MatchStatus.FINISHED);
        assertThat(events.get(1).status()).isEqualTo(MatchStatus.FINISHED);
    }

    @Test
    void mapsTeamAliasesAndSeasons() {
        String teamsJson = """
                {"teams":[{
                  "idTeam":"10",
                  "strTeam":"Bodo/Glimt",
                  "strAlternate":"Bodoe Glimt",
                  "strShort":"B/G",
                  "strBadge":"https://cdn.test/team-badge.png",
                  "strLogo":"https://cdn.test/team-logo.png",
                  "strBanner":"https://cdn.test/team-banner.png",
                  "strEquipment":"https://cdn.test/team-kit.png",
                  "strFanart1":"https://cdn.test/team-fanart.png"
                }]}
                """;
        String seasonsJson = """
                {"seasons":[{"strSeason":"2026"},{"season":"2025"}]}
                """;

        assertThat(mapper.teams(teamsJson).getFirst().aliases())
                .containsExactly("Bodo/Glimt", "Bodoe Glimt", "B/G");
        assertThat(mapper.teams(teamsJson).getFirst().strBadge()).isEqualTo("https://cdn.test/team-badge.png");
        assertThat(mapper.teams(teamsJson).getFirst().strLogo()).isEqualTo("https://cdn.test/team-logo.png");
        assertThat(mapper.teams(teamsJson).getFirst().strBanner()).isEqualTo("https://cdn.test/team-banner.png");
        assertThat(mapper.teams(teamsJson).getFirst().strEquipment()).isEqualTo("https://cdn.test/team-kit.png");
        assertThat(mapper.teams(teamsJson).getFirst().strFanart1()).isEqualTo("https://cdn.test/team-fanart.png");
        assertThat(mapper.seasons(seasonsJson))
                .extracting("season")
                .containsExactly("2026", "2025");
    }

    @Test
    void mapsLeagueAndEventArtworkFields() {
        String leaguesJson = """
                {"lookup":[{
                  "idLeague":"4328",
                  "strLeague":"Premier League",
                  "strCountry":"England",
                  "strBadge":"https://cdn.test/league-badge.png",
                  "strLogo":"https://cdn.test/league-logo.png",
                  "strBanner":"https://cdn.test/league-banner.png",
                  "strPoster":"https://cdn.test/league-poster.png",
                  "strTrophy":"https://cdn.test/league-trophy.png",
                  "strFanart1":"https://cdn.test/league-fanart.png"
                }]}
                """;
        var league = mapper.leagues(leaguesJson).getFirst();
        assertThat(league.externalLeagueId()).isEqualTo("4328");
        assertThat(league.strBadge()).isEqualTo("https://cdn.test/league-badge.png");
        assertThat(league.strLogo()).isEqualTo("https://cdn.test/league-logo.png");
        assertThat(league.strBanner()).isEqualTo("https://cdn.test/league-banner.png");
        assertThat(league.strPoster()).isEqualTo("https://cdn.test/league-poster.png");
        assertThat(league.strTrophy()).isEqualTo("https://cdn.test/league-trophy.png");
        assertThat(league.strFanart1()).isEqualTo("https://cdn.test/league-fanart.png");

        String eventJson = """
                {"events":[{
                  "idEvent":"1003",
                  "strHomeTeam":"Home FC",
                  "strAwayTeam":"Away FC",
                  "dateEvent":"2026-08-01",
                  "strTime":"15:00:00",
                  "strHomeTeamBadge":"https://cdn.test/home.png",
                  "strAwayTeamBadge":"https://cdn.test/away.png",
                  "strLeagueBadge":"https://cdn.test/league.png",
                  "strPoster":"https://cdn.test/poster.png",
                  "strThumb":"https://cdn.test/thumb.png"
                }]}
                """;
        var event = mapper.events(eventJson).getFirst();
        assertThat(event.strHomeTeamBadge()).isEqualTo("https://cdn.test/home.png");
        assertThat(event.strAwayTeamBadge()).isEqualTo("https://cdn.test/away.png");
        assertThat(event.strLeagueBadge()).isEqualTo("https://cdn.test/league.png");
        assertThat(event.strPoster()).isEqualTo("https://cdn.test/poster.png");
        assertThat(event.strThumb()).isEqualTo("https://cdn.test/thumb.png");
    }

    @Test
    void mapsPremiumV2ListResponses() {
        String teamsJson = """
                {"list":[{"idTeam":"10","strTeam":"Arsenal","strTeamShort":"ARS"}]}
                """;
        String seasonsJson = """
                {"list":[{"strSeason":"2025-2026"}]}
                """;

        assertThat(mapper.teams(teamsJson))
                .extracting("name")
                .containsExactly("Arsenal");
        assertThat(mapper.seasons(seasonsJson))
                .extracting("season")
                .containsExactly("2025-2026");
    }

    @Test
    void mapsUnknownStatisticsAndKeepsMissingValuesNull() {
        String rawJson = """
                {
                  "eventstats": [
                    {"strStat": "Corners", "intHome": "7", "intAway": "3"},
                    {"strStat": "Mystery Pressure", "intHome": "", "intAway": null}
                  ]
                }
                """;

        var statistics = mapper.eventStatistics(rawJson, "Home FC", "Away FC");

        assertThat(statistics).hasSize(4);
        assertThat(statistics)
                .filteredOn(stat -> stat.statisticName().equals("Corners"))
                .extracting("statisticCode")
                .containsOnly("CORNERS");
        assertThat(statistics)
                .filteredOn(stat -> stat.statisticName().equals("Mystery Pressure"))
                .extracting("statisticCode")
                .containsOnly("SOURCE_MYSTERY_PRESSURE");
        assertThat(statistics)
                .filteredOn(stat -> stat.statisticName().equals("Mystery Pressure"))
                .allSatisfy(stat -> assertThat(stat.numericValue()).isNull());
    }

    @Test
    void mapsPremiumV2LookupStatistics() {
        String rawJson = """
                {
                  "lookup": [
                    {"strStat":"Shots on Goal","intHome":"10","intAway":"3"},
                    {"strStat":"Corner Kicks","intHome":"6","intAway":"7"},
                    {"strStat":"Yellow Cards","intHome":"1","intAway":"2"},
                    {"strStat":"Red Cards","intHome":"0","intAway":"0"}
                  ]
                }
                """;

        var statistics = mapper.eventStatistics(rawJson, "Liverpool", "Bournemouth");

        assertThat(statistics).hasSize(8);
        assertThat(statistics).extracting("statisticCode")
                .contains("SHOTS_ON_TARGET", "CORNERS", "YELLOW_CARDS", "RED_CARDS");
    }

    @Test
    void mapsLivescoreAndLookupEventsWithZeroScores() {
        String livescoreJson = """
                {
                  "livescore": [
                    {
                      "idEvent": "2001",
                      "strHomeTeam": "Team A",
                      "strAwayTeam": "Team B",
                      "intHomeScore": "0",
                      "intAwayScore": "0",
                      "strStatus": "1H",
                      "strProgress": "25'"
                    }
                  ]
                }
                """;
        List<TheSportsDbEventDto> liveEvents = mapper.events(livescoreJson);
        assertThat(liveEvents).hasSize(1);
        assertThat(liveEvents.getFirst().status()).isEqualTo(MatchStatus.LIVE);
        assertThat(liveEvents.getFirst().homeScore()).isEqualTo(0);
        assertThat(liveEvents.getFirst().awayScore()).isEqualTo(0);

        String lookupJson = """
                {
                  "lookup": [
                    {
                      "idEvent": "2002",
                      "strHomeTeam": "Team C",
                      "strAwayTeam": "Team D",
                      "intHomeScore": "0",
                      "intAwayScore": "0",
                      "strStatus": "FT"
                    }
                  ]
                }
                """;
        List<TheSportsDbEventDto> lookupEvents = mapper.events(lookupJson);
        assertThat(lookupEvents).hasSize(1);
        assertThat(lookupEvents.getFirst().status()).isEqualTo(MatchStatus.FINISHED);
        assertThat(lookupEvents.getFirst().homeScore()).isEqualTo(0);
        assertThat(lookupEvents.getFirst().awayScore()).isEqualTo(0);
    }
}
