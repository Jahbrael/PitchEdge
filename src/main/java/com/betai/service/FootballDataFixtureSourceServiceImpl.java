package com.betai.service;

import com.betai.api.dto.FootballDataFixtureSourceRegistrationRequest;
import com.betai.api.dto.FootballDataFixtureSourceRegistrationResponse;
import com.betai.api.dto.SourceTargetResponse;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.source.RenderMode;
import com.betai.domain.source.SourceTarget;
import com.betai.domain.source.SourceType;
import com.betai.exception.ReferenceDataNotFoundException;
import com.betai.repository.LeagueRepository;
import com.betai.repository.SourceTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FootballDataFixtureSourceServiceImpl implements FootballDataFixtureSourceService {

    private static final String LATEST_FIXTURES_URL = "https://www.football-data.co.uk/fixtures.csv";
    private static final String NEW_LEAGUE_FIXTURES_URL = "https://www.football-data.co.uk/new_league_fixtures.csv";
    private static final String USER_AGENT = "BetAIResearchBot/0.1 (+local-development)";

    private final LeagueRepository leagueRepository;
    private final SourceTargetRepository sourceTargetRepository;
    private final Clock clock;

    @Override
    @Transactional
    public FootballDataFixtureSourceRegistrationResponse registerLatestFixtureSources(
            FootballDataFixtureSourceRegistrationRequest request
    ) {
        List<SourceTargetResponse> targets = List.of(
                upsert(request, LeagueCode.PREMIER_LEAGUE, "E0"),
                upsert(request, LeagueCode.CHAMPIONSHIP, "E1"),
                upsert(request, LeagueCode.LA_LIGA, "SP1"),
                upsert(request, LeagueCode.SERIE_A, "I1"),
                upsert(request, LeagueCode.BUNDESLIGA, "D1"),
                upsert(request, LeagueCode.LIGUE_1, "F1"),
                upsert(request, LeagueCode.EREDIVISIE, "N1"),
                upsert(request, LeagueCode.PRIMEIRA_LIGA, "P1"),
                upsert(request, LeagueCode.BELGIAN_PRO_LEAGUE, "B1"),
                upsert(request, LeagueCode.SCOTTISH_PREMIERSHIP, "SC0"),
                upsert(request, LeagueCode.SUPER_LIG, "T1"),
                upsertExtraLeagueFixtures(LeagueCode.VEIKKAUSLIIGA, "Finland", "Veikkausliiga"),
                upsertExtraLeagueFixtures(LeagueCode.LEAGUE_OF_IRELAND_PREMIER_DIVISION, "Ireland", "Premier Division"),
                upsertExtraLeagueFixtures(LeagueCode.CHINESE_SUPER_LEAGUE, "China", "Super League"),
                upsertExtraLeagueFixtures(LeagueCode.ALLSVENSKAN, "Sweden", "Allsvenskan"),
                upsertExtraLeagueFixtures(LeagueCode.ELITESERIEN, "Norway", "Eliteserien")
        );
        return new FootballDataFixtureSourceRegistrationResponse(
                UUID.randomUUID(),
                OffsetDateTime.now(clock),
                LATEST_FIXTURES_URL,
                targets
        );
    }

    private SourceTargetResponse upsert(
            FootballDataFixtureSourceRegistrationRequest request,
            LeagueCode leagueCode,
            String divisionCode
    ) {
        League league = leagueRepository.findByCode(leagueCode)
                .orElseThrow(() -> new ReferenceDataNotFoundException("League is not configured: " + leagueCode + "."));
        String name = "Football-Data Latest Fixtures " + league.getName() + " CSV";
        SourceTarget target = sourceTargetRepository
                .findByLeague_CodeAndSourceTypeAndName(leagueCode, SourceType.FIXTURES, name)
                .orElseGet(SourceTarget::new);

        target.setLeague(league)
                .setSourceType(SourceType.FIXTURES)
                .setName(name)
                .setUrlTemplate(LATEST_FIXTURES_URL)
                .setSourceSeasonToken(null)
                .setTargetSeasonLabel(request.targetSeasonLabel().trim())
                .setRenderMode(RenderMode.STATIC_HTML)
                .setActive(request.active() == null || request.active())
                .setRobotsTxtRequired(request.robotsTxtRequired() == null || request.robotsTxtRequired())
                .setUserAgent(USER_AGENT)
                .setRateLimitPerMinute(request.rateLimitPerMinute() == null ? 6 : request.rateLimitPerMinute())
                .setTimeoutMs(request.timeoutMs() == null ? 10000 : request.timeoutMs())
                .setReliabilityScore(new BigDecimal("85.00"))
                .setSelectorsJson("{\"format\":\"football-data-latest-fixtures-csv\",\"divisionCode\":\"" + divisionCode + "\"}");

        return SourceTargetResponse.from(sourceTargetRepository.save(target));
    }

    private SourceTargetResponse upsertExtraLeagueFixtures(
            LeagueCode leagueCode,
            String country,
            String leagueName
    ) {
        League league = leagueRepository.findByCode(leagueCode)
                .orElseThrow(() -> new ReferenceDataNotFoundException("League is not configured: " + leagueCode + "."));
        String name = "Football-Data Latest Fixtures " + league.getName() + " CSV";
        SourceTarget target = sourceTargetRepository
                .findByLeague_CodeAndSourceTypeAndName(leagueCode, SourceType.FIXTURES, name)
                .orElseGet(SourceTarget::new);

        target.setLeague(league)
                .setSourceType(SourceType.FIXTURES)
                .setName(name)
                .setUrlTemplate(NEW_LEAGUE_FIXTURES_URL)
                .setSourceSeasonToken(null)
                .setTargetSeasonLabel(league.getCurrentSeason())
                .setRenderMode(RenderMode.STATIC_HTML)
                .setActive(true)
                .setRobotsTxtRequired(true)
                .setUserAgent(USER_AGENT)
                .setRateLimitPerMinute(6)
                .setTimeoutMs(10000)
                .setReliabilityScore(new BigDecimal("85.00"))
                .setSelectorsJson("{\"format\":\"football-data-extra-league-csv\",\"country\":\""
                        + country
                        + "\",\"leagueName\":\""
                        + leagueName
                        + "\"}");

        return SourceTargetResponse.from(sourceTargetRepository.save(target));
    }
}
