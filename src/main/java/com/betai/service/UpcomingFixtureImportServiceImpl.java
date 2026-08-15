package com.betai.service;

import com.betai.api.dto.UpcomingFixtureImportItem;
import com.betai.api.dto.UpcomingFixtureImportRequest;
import com.betai.api.dto.UpcomingFixtureImportResponse;
import com.betai.api.dto.UpcomingFixtureResponse;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.Match;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.team.Team;
import com.betai.domain.team.TeamAlias;
import com.betai.exception.InvalidRequestException;
import com.betai.exception.ReferenceDataNotFoundException;
import com.betai.repository.LeagueRepository;
import com.betai.repository.MatchRepository;
import com.betai.repository.TeamAliasRepository;
import com.betai.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpcomingFixtureImportServiceImpl implements UpcomingFixtureImportService {

    private static final String SOURCE_NAME = "manual-fixture-import";

    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final TeamAliasRepository teamAliasRepository;
    private final MatchRepository matchRepository;
    private final Clock clock;

    @Override
    @Transactional
    public UpcomingFixtureImportResponse importFixtures(UpcomingFixtureImportRequest request) {
        League league = leagueRepository.findByCode(request.leagueCode())
                .orElseThrow(() -> new ReferenceDataNotFoundException("League is not configured: " + request.leagueCode() + "."));
        String seasonLabel = normalizeSeasonLabel(request.seasonLabel());
        ImportCounters counters = new ImportCounters();
        List<UpcomingFixtureResponse> importedFixtures = new ArrayList<>();

        for (UpcomingFixtureImportItem item : request.fixtures()) {
            ImportedFixture imported = importFixture(league, seasonLabel, item);
            if (imported.created()) {
                counters.created++;
            } else {
                counters.updated++;
            }
            importedFixtures.add(UpcomingFixtureResponse.from(imported.match()));
        }

        return new UpcomingFixtureImportResponse(
                UUID.randomUUID(),
                OffsetDateTime.now(clock),
                league.getCode().name(),
                seasonLabel,
                counters.created,
                counters.updated,
                List.copyOf(importedFixtures)
        );
    }

    private ImportedFixture importFixture(League league, String seasonLabel, UpcomingFixtureImportItem item) {
        String homeTeamName = normalizedRequiredText(item.homeTeam(), "homeTeam");
        String awayTeamName = normalizedRequiredText(item.awayTeam(), "awayTeam");
        if (normalizeKey(homeTeamName).equals(normalizeKey(awayTeamName))) {
            throw new InvalidRequestException("homeTeam and awayTeam cannot refer to the same team.");
        }

        Team homeTeam = resolveTeam(league, homeTeamName);
        Team awayTeam = resolveTeam(league, awayTeamName);
        OffsetDateTime kickoffAt = item.matchDate()
                .atTime(item.kickoffTime())
                .atZone(zoneFor(league.getCode()))
                .toOffsetDateTime();
        String sourceFixtureKey = sourceFixtureKey(league.getCode(), seasonLabel, item, homeTeamName, awayTeamName);

        Optional<Match> bySourceKey = matchRepository.findByLeague_CodeAndSourceFixtureKeySafely(league.getCode(), sourceFixtureKey);
        Optional<Match> byFixtureIdentity = matchRepository.findByLeague_CodeAndHomeTeam_IdAndAwayTeam_IdAndKickoffAtSafely(
                league.getCode(),
                homeTeam.getId(),
                awayTeam.getId(),
                kickoffAt
        );
        Match match = bySourceKey.or(() -> byFixtureIdentity).orElseGet(Match::new);
        boolean created = match.getId() == null;

        match.setLeague(league)
                .setHomeTeam(homeTeam)
                .setAwayTeam(awayTeam)
                .setMatchDate(item.matchDate())
                .setKickoffAt(kickoffAt)
                .setStatus(MatchStatus.SCHEDULED)
                .setHomeScore(null)
                .setAwayScore(null)
                .setSeasonLabel(seasonLabel)
                .setRoundLabel(truncate(blankToNull(item.roundLabel()), 64))
                .setVenue(truncate(blankToNull(item.venue()), 160))
                .setSourceFixtureKey(sourceFixtureKey);

        return new ImportedFixture(matchRepository.save(match), created);
    }

    private Team resolveTeam(League league, String sourceTeamName) {
        String aliasNormalized = normalizeKey(sourceTeamName);
        Optional<TeamAlias> alias = teamAliasRepository.findByLeague_CodeAndAliasNormalized(league.getCode(), aliasNormalized);
        if (alias.isPresent()) {
            return alias.get().getTeam();
        }

        Optional<Team> canonicalMatch = teamRepository.findByLeague_CodeAndCanonicalNameIgnoreCaseSafely(league.getCode(), sourceTeamName);
        if (canonicalMatch.isPresent()) {
            Team team = canonicalMatch.get();
            saveAlias(league, team, sourceTeamName, aliasNormalized);
            return team;
        }

        Team team = teamRepository.save(new Team()
                .setLeague(league)
                .setCanonicalName(truncate(sourceTeamName, 160))
                .setShortName(truncate(sourceTeamName, 80))
                .setCountry(league.getCountry())
                .setExternalKey(truncate("MANUAL:" + league.getCode().name() + ":" + aliasNormalized, 160))
                .setActive(true));
        saveAlias(league, team, sourceTeamName, aliasNormalized);
        return team;
    }

    private void saveAlias(League league, Team team, String sourceTeamName, String aliasNormalized) {
        teamAliasRepository.findByLeague_CodeAndAliasNormalized(league.getCode(), aliasNormalized)
                .orElseGet(() -> teamAliasRepository.save(new TeamAlias()
                        .setLeague(league)
                        .setTeam(team)
                        .setAlias(truncate(sourceTeamName, 160))
                        .setAliasNormalized(truncate(aliasNormalized, 180))
                        .setSourceName(SOURCE_NAME)));
    }

    private String sourceFixtureKey(
            LeagueCode leagueCode,
            String seasonLabel,
            UpcomingFixtureImportItem item,
            String homeTeamName,
            String awayTeamName
    ) {
        if (StringUtils.hasText(item.sourceFixtureKey())) {
            return truncate(item.sourceFixtureKey().trim(), 180);
        }
        String key = "MANUAL:" + leagueCode.name()
                + ":" + seasonLabel
                + ":" + item.matchDate()
                + ":" + normalizeKey(homeTeamName)
                + ":" + normalizeKey(awayTeamName);
        return truncate(key, 180);
    }

    private String normalizeSeasonLabel(String seasonLabel) {
        String normalized = normalizedRequiredText(seasonLabel, "seasonLabel");
        if (normalized.length() > 32) {
            throw new InvalidRequestException("seasonLabel cannot exceed 32 characters.");
        }
        return normalized;
    }

    private String normalizedRequiredText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new InvalidRequestException(fieldName + " is required.");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeKey(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace("&", " and ")
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return StringUtils.hasText(normalized) ? normalized : "unknown";
    }

    private ZoneId zoneFor(LeagueCode leagueCode) {
        return switch (leagueCode) {
            case PREMIER_LEAGUE -> ZoneId.of("Europe/London");
            case CHAMPIONSHIP -> ZoneId.of("Europe/London");
            case LA_LIGA -> ZoneId.of("Europe/Madrid");
            case SERIE_A -> ZoneId.of("Europe/Rome");
            case BUNDESLIGA -> ZoneId.of("Europe/Berlin");
            case LIGUE_1 -> ZoneId.of("Europe/Paris");
            case EREDIVISIE -> ZoneId.of("Europe/Amsterdam");
            case PRIMEIRA_LIGA -> ZoneId.of("Europe/Lisbon");
            case BELGIAN_PRO_LEAGUE -> ZoneId.of("Europe/Brussels");
            case SCOTTISH_PREMIERSHIP -> ZoneId.of("Europe/London");
            case SUPER_LIG -> ZoneId.of("Europe/Istanbul");
            case ALLSVENSKAN -> ZoneId.of("Europe/Stockholm");
            case ELITESERIEN -> ZoneId.of("Europe/Oslo");
            case FIFA_WORLD_CUP_2026 -> ZoneId.of("UTC");
            case VEIKKAUSLIIGA -> ZoneId.of("Europe/Helsinki");
            case LEAGUE_OF_IRELAND_PREMIER_DIVISION,
                 LEAGUE_OF_IRELAND_FIRST_DIVISION -> ZoneId.of("Europe/Dublin");
            case BESTA_DEILD -> ZoneId.of("Atlantic/Reykjavik");
            case MEISTRILIIGA -> ZoneId.of("Europe/Tallinn");
            case TOPLYGA -> ZoneId.of("Europe/Vilnius");
            case LATVIAN_VIRSLIGA -> ZoneId.of("Europe/Riga");
            case KAZAKHSTAN_PREMIER_LEAGUE -> ZoneId.of("Asia/Almaty");
            case CHINESE_SUPER_LEAGUE -> ZoneId.of("Asia/Shanghai");
            case K_LEAGUE_1,
                 K_LEAGUE_2 -> ZoneId.of("Asia/Seoul");
            case CANADIAN_PREMIER_LEAGUE -> ZoneId.of("America/Toronto");
            case BRAZILIAN_SERIE_B,
                 BRAZILIAN_SERIE_D -> ZoneId.of("America/Sao_Paulo");
            default -> ZoneId.of("UTC");
        };
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record ImportedFixture(Match match, boolean created) {
    }

    private static final class ImportCounters {
        private int created;
        private int updated;
    }
}
