package com.betai.service;

import com.betai.api.dto.DailyExtractionRequest;
import com.betai.api.dto.DailyExtractionResponse;
import com.betai.api.dto.ExtractionRunResponse;
import com.betai.api.dto.ExtractionValidationErrorResponse;
import com.betai.domain.extraction.ExtractionRun;
import com.betai.domain.extraction.ExtractionStatus;
import com.betai.domain.extraction.ExtractionValidationError;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.Match;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.snapshot.RawSnapshot;
import com.betai.domain.snapshot.ScrapeStatus;
import com.betai.domain.statistics.MatchStatistics;
import com.betai.domain.team.Team;
import com.betai.domain.team.TeamAlias;
import com.betai.exception.InvalidRequestException;
import com.betai.exception.ResourceNotFoundException;
import com.betai.repository.ExtractionRunRepository;
import com.betai.repository.ExtractionValidationErrorRepository;
import com.betai.repository.LeagueRepository;
import com.betai.repository.MatchRepository;
import com.betai.repository.MatchStatisticsRepository;
import com.betai.repository.RawSnapshotRepository;
import com.betai.repository.TeamAliasRepository;
import com.betai.repository.TeamRepository;
import com.betai.util.SnapshotPayloads;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FootballDataCsvExtractionService implements ExtractionService {

    private static final String SOURCE_NAME = "football-data.co.uk";
    private static final String FORMAT_WORLD_CUP_FIXTURES_JSON = "world-cup-2026-fixtures-json";
    private static final String FORMAT_OPENFOOTBALL_WORLD_CUP_JSON = "openfootball-world-cup-json";
    private static final String FORMAT_FOOTBALL_DATA_WORLD_CUP_WORKBOOK = "football-data-world-cup-workbook";
    private static final String FORMAT_THESPORTSDB_EVENTS_JSON = "thesportsdb-events-json";
    private static final String FORMAT_SGODDS_RESULTS_CSV = "sgodds-results-csv";
    private static final String FORMAT_API_FOOTBALL_FIXTURES_JSON = "api-football-fixtures-json";
    private static final LocalTime DEFAULT_KICKOFF_TIME = LocalTime.of(15, 0);
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("d/M/uu"),
            DateTimeFormatter.ofPattern("dd/MM/uu")
    );
    private static final List<DateTimeFormatter> TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("H:mm:ss"),
            DateTimeFormatter.ofPattern("HH:mm:ss")
    );
    private static final Pattern ELITESERIEN_FIXTURE_PATTERN = Pattern.compile(
            "#\\d+\\s+([^#]+?)\\s+-\\s+([^#]+?)\\s+(\\d{2}\\.\\d{2}\\.\\d{4})\\s+(\\d{1,2}:\\d{2})"
    );
    private static final Pattern ALLSVENSKAN_FIXTURE_PATTERN = Pattern.compile(
            "(\\d{1,2})\\s+([a-zåäö]{3,5}\\.?|juni|juli)\\s*(\\d{1,2}:\\d{2})\\s+(.+?)VS\\s*(.+?)(?=\\s+(?:\\d{1,2}\\s+(?:[a-zåäö]{3,5}\\.?|juni|juli)\\s*\\d{1,2}:\\d{2}|Omgång|Kommande|Resultat|$))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern OPENFOOTBALL_TIME_PATTERN = Pattern.compile(
            "(\\d{1,2}:\\d{2})\\s+UTC([+-]\\d{1,2})",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SGODDS_FULL_TIME_PATTERN = Pattern.compile(
            "FT\\s*:\\s*(\\d+)\\s*-\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    private final RawSnapshotRepository rawSnapshotRepository;
    private final ExtractionRunRepository extractionRunRepository;
    private final ExtractionValidationErrorRepository extractionValidationErrorRepository;
    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final TeamAliasRepository teamAliasRepository;
    private final MatchRepository matchRepository;
    private final MatchStatisticsRepository matchStatisticsRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    @Transactional
    public ExtractionRunResponse extractRawSnapshot(UUID rawSnapshotId, boolean forceReprocess) {
        if (!forceReprocess) {
            Optional<ExtractionRun> cached = extractionRunRepository
                    .findFirstByRawSnapshot_IdAndExtractionStatusOrderByStartedAtDesc(rawSnapshotId, ExtractionStatus.SUCCESS);
            if (cached.isPresent()) {
                return toResponse(cached.get(), true);
            }
        }

        RawSnapshot snapshot = rawSnapshotRepository.findById(rawSnapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("Raw snapshot not found: " + rawSnapshotId + "."));

        ExtractionRun run = extractionRunRepository.save(new ExtractionRun()
                .setRawSnapshot(snapshot)
                .setExtractionStatus(ExtractionStatus.RUNNING)
                .setStartedAt(OffsetDateTime.now(clock)));

        if (snapshot.getScrapeStatus() != ScrapeStatus.SUCCESS) {
            run.finish(
                    OffsetDateTime.now(clock),
                    ExtractionStatus.SKIPPED,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    "Snapshot scrape status is " + snapshot.getScrapeStatus() + "."
            );
            return toResponse(extractionRunRepository.save(run), false);
        }

        if (!isSupportedSnapshot(snapshot)) {
            run.finish(
                    OffsetDateTime.now(clock),
                    ExtractionStatus.SKIPPED,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    "Snapshot is not configured as a supported football extraction source."
            );
            return toResponse(extractionRunRepository.save(run), false);
        }

        try {
            ExtractionSummary summary = parseSnapshot(snapshot, run);
            ExtractionStatus finalStatus = finalStatus(summary);
            run.finish(
                    OffsetDateTime.now(clock),
                    finalStatus,
                    summary.rowsSeen(),
                    summary.rowsAccepted(),
                    summary.teamsUpserted(),
                    summary.matchesUpserted(),
                    summary.statsUpserted(),
                    summary.validationErrors(),
                    finalMessage(finalStatus)
            );
            return toResponse(extractionRunRepository.save(run), false);
        } catch (Exception exception) {
            run.finish(
                    OffsetDateTime.now(clock),
                    ExtractionStatus.FAILED,
                    run.getRowsSeen(),
                    run.getRowsAccepted(),
                    run.getTeamsUpserted(),
                    run.getMatchesUpserted(),
                    run.getStatsUpserted(),
                    run.getValidationErrorCount(),
                    truncate(exception.getMessage(), 1000)
            );
            return toResponse(extractionRunRepository.save(run), false);
        }
    }

    @Override
    @Transactional
    public DailyExtractionResponse extractDailySnapshots(DailyExtractionRequest request) {
        OffsetDateTime triggeredAt = OffsetDateTime.now(clock);
        LocalDate snapshotDate = request.snapshotDate() == null ? LocalDate.now(clock) : request.snapshotDate();
        List<League> leagues = resolveLeagues(request.leagueCodes());
        List<ExtractionRunResponse> runs = new ArrayList<>();

        for (League league : leagues) {
            List<RawSnapshot> snapshots = rawSnapshotRepository
                    .findByLeague_CodeAndSnapshotDateAndScrapeStatusOrderByCreatedAtAsc(
                            league.getCode(),
                            snapshotDate,
                            ScrapeStatus.SUCCESS
                    );
            for (RawSnapshot snapshot : snapshots) {
                runs.add(extractRawSnapshot(snapshot.getId(), request.forceReprocess()));
            }
        }

        return new DailyExtractionResponse(UUID.randomUUID(), triggeredAt, List.copyOf(runs));
    }

    private ExtractionSummary parseSnapshot(RawSnapshot snapshot, ExtractionRun run) throws IOException {
        if (!StringUtils.hasText(snapshot.getRawPayload())) {
            throw new InvalidRequestException("Raw snapshot payload is empty.");
        }

        if (isWorldCupFixturesJsonSnapshot(snapshot)) {
            return parseWorldCupFixturesJsonSnapshot(snapshot);
        }
        if (isOpenFootballWorldCupJsonSnapshot(snapshot)) {
            return parseOpenFootballWorldCupJsonSnapshot(snapshot);
        }
        if (isFootballDataWorldCupWorkbookSnapshot(snapshot)) {
            return parseFootballDataWorldCupWorkbookSnapshot(snapshot);
        }
        if (isTheSportsDbEventsJsonSnapshot(snapshot)) {
            return parseTheSportsDbEventsJsonSnapshot(snapshot);
        }
        if (isSgoddsResultsCsvSnapshot(snapshot)) {
            return parseSgoddsResultsCsvSnapshot(snapshot);
        }
        if (isApiFootballFixturesJsonSnapshot(snapshot)) {
            return parseApiFootballFixturesJsonSnapshot(snapshot);
        }
        if (isOfficialFixtureHtmlSnapshot(snapshot)) {
            return parseOfficialFixtureSnapshot(snapshot);
        }

        SourceExtractionOptions extractionOptions = extractionOptions(snapshot);
        String csv = stripBom(csvText(snapshot.getRawPayload()));
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .get();

        ExtractionCounters counters = new ExtractionCounters();
        try (var parser = csvFormat.parse(new StringReader(csv))) {
            for (CSVRecord record : parser) {
                if (isBlankRecord(record)) {
                    continue;
                }
                if (!matchesSourceFilters(record, extractionOptions)) {
                    continue;
                }
                CsvColumnMapping columnMapping = columnMappingFor(record);
                counters.rowsSeen++;
                List<RowError> rowErrors = validateRecord(record, columnMapping);
                if (!rowErrors.isEmpty()) {
                    counters.validationErrors += persistErrors(run, snapshot, record, rowErrors);
                    continue;
                }

                NormalizedRow row = toNormalizedRow(snapshot, record, columnMapping);
                upsertNormalizedRow(snapshot, row, counters, true);
            }
        }

        return counters.toSummary();
    }

    private ExtractionSummary parseOfficialFixtureSnapshot(RawSnapshot snapshot) {
        List<OfficialFixtureRow> rows = officialFixtureRows(snapshot);
        ExtractionCounters counters = new ExtractionCounters();

        for (OfficialFixtureRow row : rows) {
            counters.rowsSeen++;
            String homeName = canonicalizeOfficialTeamName(snapshot.getLeague().getCode(), row.homeTeamName());
            String awayName = canonicalizeOfficialTeamName(snapshot.getLeague().getCode(), row.awayTeamName());
            if (!StringUtils.hasText(homeName) || !StringUtils.hasText(awayName)
                    || normalizeKey(homeName).equals(normalizeKey(awayName))) {
                counters.validationErrors++;
                continue;
            }

            NormalizedRow normalizedRow = new NormalizedRow(
                    row.matchDate(),
                    row.kickoffTime(),
                    null,
                    homeName,
                    awayName,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    sourceFixtureKey(snapshot.getLeague().getCode(), row.matchDate(), homeName, awayName)
            );
            upsertNormalizedRow(snapshot, normalizedRow, counters, false);
        }

        return counters.toSummary();
    }

    private ExtractionSummary parseSgoddsResultsCsvSnapshot(RawSnapshot snapshot) throws IOException {
        String csv = stripBom(csvText(snapshot.getRawPayload()));
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .get();
        ExtractionCounters counters = new ExtractionCounters();
        SourceExtractionOptions options = extractionOptions(snapshot);

        try (var parser = csvFormat.parse(new StringReader(csv))) {
            for (CSVRecord record : parser) {
                if (isBlankRecord(record)) {
                    continue;
                }
                if (StringUtils.hasText(options.leagueName())
                        && !options.leagueName().equalsIgnoreCase(value(record, "League"))) {
                    continue;
                }
                counters.rowsSeen++;
                try {
                    Optional<NormalizedRow> row = sgoddsResultRow(snapshot, record);
                    if (row.isEmpty()) {
                        counters.validationErrors++;
                        continue;
                    }
                    upsertNormalizedRow(snapshot, row.get(), counters, false);
                } catch (RuntimeException exception) {
                    counters.validationErrors++;
                }
            }
        }

        return counters.toSummary();
    }

    private Optional<NormalizedRow> sgoddsResultRow(RawSnapshot snapshot, CSVRecord record) {
        TeamNames teams = splitMatchName(value(record, "Match")).orElse(null);
        Optional<LocalDateTime> kickoff = parseSgoddsStartTime(value(record, "Start Time"));
        Optional<FullTimeScore> score = parseSgoddsFullTimeScore(value(record, "Result"));
        if (teams == null || kickoff.isEmpty() || score.isEmpty()) {
            return Optional.empty();
        }

        String homeTeam = canonicalizeOfficialTeamName(snapshot.getLeague().getCode(), teams.homeTeam());
        String awayTeam = canonicalizeOfficialTeamName(snapshot.getLeague().getCode(), teams.awayTeam());
        return Optional.of(new NormalizedRow(
                kickoff.get().toLocalDate(),
                kickoff.get().toLocalTime(),
                null,
                homeTeam,
                awayTeam,
                score.get().homeScore(),
                score.get().awayScore(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                sgoddsSourceFixtureKey(snapshot.getLeague().getCode(), record, kickoff.get().toLocalDate(), homeTeam, awayTeam)
        ));
    }

    private Optional<TeamNames> splitMatchName(String matchName) {
        if (!StringUtils.hasText(matchName)) {
            return Optional.empty();
        }
        String[] teams = matchName.split("\\s+vs\\s+", 2);
        if (teams.length != 2 || !StringUtils.hasText(teams[0]) || !StringUtils.hasText(teams[1])) {
            return Optional.empty();
        }
        return Optional.of(new TeamNames(teams[0].trim(), teams[1].trim()));
    }

    private Optional<LocalDateTime> parseSgoddsStartTime(String rawStartTime) {
        if (!StringUtils.hasText(rawStartTime)) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(rawStartTime.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private Optional<FullTimeScore> parseSgoddsFullTimeScore(String rawResult) {
        if (!StringUtils.hasText(rawResult)) {
            return Optional.empty();
        }
        Matcher matcher = SGODDS_FULL_TIME_PATTERN.matcher(rawResult);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(new FullTimeScore(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2))
        ));
    }

    private String sgoddsSourceFixtureKey(
            LeagueCode leagueCode,
            CSVRecord record,
            LocalDate matchDate,
            String homeTeam,
            String awayTeam
    ) {
        String id = value(record, "ID");
        if (StringUtils.hasText(id)) {
            return truncate("SGODDS:" + leagueCode.name() + ":" + id.trim(), 180);
        }
        return truncate("SGODDS:" + leagueCode.name() + ":" + matchDate + ":"
                + normalizeKey(homeTeam) + ":" + normalizeKey(awayTeam), 180);
    }

    private ExtractionSummary parseTheSportsDbEventsJsonSnapshot(RawSnapshot snapshot) throws IOException {
        JsonNode events = objectMapper.readTree(SnapshotPayloads.text(snapshot.getRawPayload())).path("events");
        ExtractionCounters counters = new ExtractionCounters();
        if (events.isMissingNode() || events.isNull()) {
            return counters.toSummary();
        }
        if (!events.isArray()) {
            throw new InvalidRequestException("TheSportsDB events JSON must contain an events array or null.");
        }

        for (JsonNode event : events) {
            counters.rowsSeen++;
            try {
                Optional<NormalizedRow> row = theSportsDbEventRow(snapshot, event);
                if (row.isEmpty()) {
                    counters.validationErrors++;
                    continue;
                }
                upsertNormalizedRow(snapshot, row.get(), counters, false);
            } catch (RuntimeException exception) {
                counters.validationErrors++;
            }
        }

        return counters.toSummary();
    }

    private ExtractionSummary parseApiFootballFixturesJsonSnapshot(RawSnapshot snapshot) throws IOException {
        JsonNode response = objectMapper.readTree(SnapshotPayloads.text(snapshot.getRawPayload())).path("response");
        ExtractionCounters counters = new ExtractionCounters();
        if (response.isMissingNode() || response.isNull()) {
            return counters.toSummary();
        }
        if (!response.isArray()) {
            throw new InvalidRequestException("API-Football fixtures JSON must contain a response array.");
        }

        for (JsonNode fixtureNode : response) {
            counters.rowsSeen++;
            try {
                Optional<NormalizedRow> row = apiFootballFixtureRow(snapshot, fixtureNode);
                if (row.isEmpty()) {
                    counters.validationErrors++;
                    continue;
                }
                upsertNormalizedRow(snapshot, row.get(), counters, false);
            } catch (RuntimeException exception) {
                counters.validationErrors++;
            }
        }

        return counters.toSummary();
    }

    private Optional<NormalizedRow> apiFootballFixtureRow(RawSnapshot snapshot, JsonNode root) {
        JsonNode fixture = root.path("fixture");
        JsonNode teams = root.path("teams");
        String homeTeam = nodeText(teams.path("home"), "name");
        String awayTeam = nodeText(teams.path("away"), "name");
        if (!StringUtils.hasText(homeTeam) || !StringUtils.hasText(awayTeam)
                || normalizeKey(homeTeam).equals(normalizeKey(awayTeam))) {
            return Optional.empty();
        }

        Optional<OffsetDateTime> kickoffAt = parseOffsetDateTime(nodeText(fixture, "date"));
        LocalDate matchDate = kickoffAt.map(OffsetDateTime::toLocalDate)
                .or(() -> parseIsoLocalDate(nodeText(fixture, "date")))
                .orElse(null);
        if (matchDate == null) {
            return Optional.empty();
        }

        Integer homeScore = apiFootballFullTimeScore(root, "home").orElse(null);
        Integer awayScore = apiFootballFullTimeScore(root, "away").orElse(null);
        String roundLabel = nodeText(root.path("league"), "round");
        String venue = nodeText(fixture.path("venue"), "name");

        return Optional.of(new NormalizedRow(
                matchDate,
                kickoffAt.map(OffsetDateTime::toLocalTime).orElse(DEFAULT_KICKOFF_TIME),
                kickoffAt.orElse(null),
                homeTeam,
                awayTeam,
                homeScore,
                awayScore,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                roundLabel,
                venue,
                apiFootballSourceFixtureKey(snapshot.getLeague().getCode(), root, matchDate, homeTeam, awayTeam)
        ));
    }

    private Optional<Integer> apiFootballFullTimeScore(JsonNode root, String side) {
        Optional<Integer> fullTimeScore = parseNonNegativeInt(nodeText(root.path("score").path("fulltime"), side));
        if (fullTimeScore.isPresent()) {
            return fullTimeScore;
        }
        return parseNonNegativeInt(nodeText(root.path("goals"), side));
    }

    private String apiFootballSourceFixtureKey(
            LeagueCode leagueCode,
            JsonNode root,
            LocalDate matchDate,
            String homeTeam,
            String awayTeam
    ) {
        String fixtureId = nodeText(root.path("fixture"), "id");
        if (StringUtils.hasText(fixtureId)) {
            return truncate("APIF:" + leagueCode.name() + ":" + fixtureId.trim(), 180);
        }
        return truncate("APIF:" + leagueCode.name() + ":" + matchDate + ":"
                + normalizeKey(homeTeam) + ":" + normalizeKey(awayTeam), 180);
    }

    private Optional<NormalizedRow> theSportsDbEventRow(RawSnapshot snapshot, JsonNode event) {
        String sport = nodeText(event, "strSport");
        if (StringUtils.hasText(sport) && !"Soccer".equalsIgnoreCase(sport)) {
            return Optional.empty();
        }

        TeamNames teams = theSportsDbTeams(event).orElse(null);
        Optional<LocalDate> matchDate = theSportsDbDate(event);
        if (teams == null || matchDate.isEmpty()) {
            return Optional.empty();
        }

        OffsetDateTime kickoffAt = theSportsDbKickoff(snapshot, event, matchDate.get()).orElse(null);
        Integer homeScore = parseNonNegativeInt(nodeText(event, "intHomeScore")).orElse(null);
        Integer awayScore = parseNonNegativeInt(nodeText(event, "intAwayScore")).orElse(null);
        String roundLabel = nodeText(event, "strRound", "intRound");
        String venue = nodeText(event, "strVenue");

        return Optional.of(new NormalizedRow(
                matchDate.get(),
                kickoffAt == null ? DEFAULT_KICKOFF_TIME : kickoffAt.toLocalTime(),
                kickoffAt,
                teams.homeTeam(),
                teams.awayTeam(),
                homeScore,
                awayScore,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                roundLabel,
                venue,
                theSportsDbSourceFixtureKey(snapshot.getLeague().getCode(), event, matchDate.get(), teams.homeTeam(), teams.awayTeam())
        ));
    }

    private Optional<TeamNames> theSportsDbTeams(JsonNode event) {
        String homeTeam = nodeText(event, "strHomeTeam", "homeTeam");
        String awayTeam = nodeText(event, "strAwayTeam", "awayTeam");
        if (!StringUtils.hasText(homeTeam) || !StringUtils.hasText(awayTeam)) {
            String eventName = nodeText(event, "strEvent");
            if (StringUtils.hasText(eventName) && eventName.contains(" vs ")) {
                String[] teams = eventName.split("\\s+vs\\s+", 2);
                homeTeam = teams[0];
                awayTeam = teams[1];
            }
        }
        if (!StringUtils.hasText(homeTeam) || !StringUtils.hasText(awayTeam)
                || normalizeKey(homeTeam).equals(normalizeKey(awayTeam))) {
            return Optional.empty();
        }
        return Optional.of(new TeamNames(homeTeam.trim(), awayTeam.trim()));
    }

    private Optional<LocalDate> theSportsDbDate(JsonNode event) {
        String rawDate = nodeText(event, "dateEvent", "dateEventLocal", "strDate");
        Optional<LocalDate> parsedDate = parseIsoLocalDate(rawDate).or(() -> parseDate(rawDate));
        if (parsedDate.isPresent()) {
            return parsedDate;
        }

        String timestamp = nodeText(event, "strTimestamp", "strTimestampLocal");
        if (!StringUtils.hasText(timestamp) || timestamp.length() < 10) {
            return Optional.empty();
        }
        return parseIsoLocalDate(timestamp.substring(0, 10));
    }

    private Optional<OffsetDateTime> theSportsDbKickoff(RawSnapshot snapshot, JsonNode event, LocalDate matchDate) {
        String timestamp = nodeText(event, "strTimestamp");
        if (StringUtils.hasText(timestamp)) {
            try {
                return Optional.of(OffsetDateTime.parse(timestamp.trim()));
            } catch (DateTimeParseException ignored) {
            }
            try {
                return Optional.of(LocalDateTime.parse(timestamp.trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        .atZone(zoneFor(snapshot.getLeague().getCode()))
                        .toOffsetDateTime());
            } catch (DateTimeParseException ignored) {
            }
        }

        Optional<LocalTime> time = parseTime(nodeText(event, "strTimeLocal", "strTime"));
        return time.map(localTime -> matchDate
                .atTime(localTime)
                .atZone(zoneFor(snapshot.getLeague().getCode()))
                .toOffsetDateTime());
    }

    private String theSportsDbSourceFixtureKey(
            LeagueCode leagueCode,
            JsonNode event,
            LocalDate matchDate,
            String homeTeam,
            String awayTeam
    ) {
        String eventId = nodeText(event, "idEvent");
        if (StringUtils.hasText(eventId)) {
            return truncate("TSDB:" + leagueCode.name() + ":" + eventId.trim(), 180);
        }
        return truncate("TSDB:" + leagueCode.name() + ":" + matchDate + ":"
                + normalizeKey(homeTeam) + ":" + normalizeKey(awayTeam), 180);
    }

    private ExtractionSummary parseWorldCupFixturesJsonSnapshot(RawSnapshot snapshot) throws IOException {
        JsonNode fixtures = objectMapper.readTree(SnapshotPayloads.text(snapshot.getRawPayload())).path("fixtures");
        if (!fixtures.isArray()) {
            throw new InvalidRequestException("World Cup fixture JSON must contain a fixtures array.");
        }
        ExtractionCounters counters = new ExtractionCounters();

        for (JsonNode fixture : fixtures) {
            counters.rowsSeen++;
            try {
                String homeTeam = requiredText(fixture, "homeTeam");
                String awayTeam = requiredText(fixture, "awayTeam");
                LocalDate matchDate = parseIsoDate(requiredText(fixture, "date"));
                OffsetDateTime kickoffAt = OffsetDateTime.parse(requiredText(fixture, "kickoffUtc"));
                String roundLabel = worldCupRoundLabel(fixture.path("stage").asText(null), fixture.path("group").asText(null));
                String venue = worldCupVenue(fixture.path("stadium").asText(null), fixture.path("hostCity").asText(null));
                String sourceKey = worldCupSourceFixtureKey(snapshot.getLeague().getCode(), matchDate, homeTeam, awayTeam);

                NormalizedRow row = normalizedWorldCupRow(
                        matchDate,
                        kickoffAt,
                        homeTeam,
                        awayTeam,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        roundLabel,
                        venue,
                        sourceKey
                );
                upsertNormalizedRow(snapshot, row, counters, false);
            } catch (RuntimeException exception) {
                counters.validationErrors++;
            }
        }

        return counters.toSummary();
    }

    private ExtractionSummary parseOpenFootballWorldCupJsonSnapshot(RawSnapshot snapshot) throws IOException {
        JsonNode matches = objectMapper.readTree(SnapshotPayloads.text(snapshot.getRawPayload())).path("matches");
        if (!matches.isArray()) {
            throw new InvalidRequestException("Openfootball World Cup JSON must contain a matches array.");
        }
        ExtractionCounters counters = new ExtractionCounters();

        for (JsonNode matchNode : matches) {
            counters.rowsSeen++;
            try {
                String homeTeam = requiredText(matchNode, "team1");
                String awayTeam = requiredText(matchNode, "team2");
                LocalDate matchDate = parseIsoDate(requiredText(matchNode, "date"));
                OffsetDateTime kickoffAt = parseOpenFootballKickoff(matchDate, matchNode.path("time").asText(null))
                        .orElse(matchDate.atTime(DEFAULT_KICKOFF_TIME).atOffset(ZoneOffset.UTC));
                JsonNode fullTimeScore = matchNode.path("score").path("ft");
                Integer homeScore = fullTimeScore.isArray() && fullTimeScore.size() >= 2 && fullTimeScore.get(0).canConvertToInt()
                        ? fullTimeScore.get(0).asInt()
                        : null;
                Integer awayScore = fullTimeScore.isArray() && fullTimeScore.size() >= 2 && fullTimeScore.get(1).canConvertToInt()
                        ? fullTimeScore.get(1).asInt()
                        : null;
                String roundLabel = worldCupRoundLabel(matchNode.path("round").asText(null), matchNode.path("group").asText(null));
                String sourceKey = worldCupSourceFixtureKey(snapshot.getLeague().getCode(), matchDate, homeTeam, awayTeam);

                NormalizedRow row = normalizedWorldCupRow(
                        matchDate,
                        kickoffAt,
                        homeTeam,
                        awayTeam,
                        homeScore,
                        awayScore,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        roundLabel,
                        matchNode.path("ground").asText(null),
                        sourceKey
                );
                upsertNormalizedRow(snapshot, row, counters, false);
            } catch (RuntimeException exception) {
                counters.validationErrors++;
            }
        }

        return counters.toSummary();
    }

    private ExtractionSummary parseFootballDataWorldCupWorkbookSnapshot(RawSnapshot snapshot) throws IOException {
        ExtractionCounters counters = new ExtractionCounters();
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(SnapshotPayloads.bytes(snapshot.getRawPayload())))) {
            for (Sheet sheet : workbook) {
                Map<String, Integer> headers = headers(sheet.getRow(0), formatter);
                if (headers.isEmpty()) {
                    continue;
                }
                for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null || workbookRowBlank(row)) {
                        continue;
                    }
                    counters.rowsSeen++;
                    try {
                        Optional<NormalizedRow> normalizedRow = footballDataWorldCupWorkbookRow(snapshot, sheet.getSheetName(), row, headers, formatter);
                        if (normalizedRow.isEmpty()) {
                            counters.validationErrors++;
                            continue;
                        }
                        upsertNormalizedRow(snapshot, normalizedRow.get(), counters, true);
                    } catch (RuntimeException exception) {
                        counters.validationErrors++;
                    }
                }
            }
        }
        return counters.toSummary();
    }

    private Optional<NormalizedRow> footballDataWorldCupWorkbookRow(
            RawSnapshot snapshot,
            String sheetName,
            Row row,
            Map<String, Integer> headers,
            DataFormatter formatter
    ) {
        String homeTeam = workbookText(row, headers, formatter, "Home");
        String awayTeam = workbookText(row, headers, formatter, "Away");
        Optional<LocalDate> matchDate = workbookDate(row, headers, formatter, "Date");
        if (!StringUtils.hasText(homeTeam) || !StringUtils.hasText(awayTeam) || matchDate.isEmpty()) {
            return Optional.empty();
        }
        LocalTime kickoffTime = workbookTime(row, headers, formatter, "Time").orElse(DEFAULT_KICKOFF_TIME);
        Integer homeScore = workbookInteger(row, headers, formatter, "HG", "HGFT").orElse(null);
        Integer awayScore = workbookInteger(row, headers, formatter, "AG", "AGFT").orElse(null);
        String roundLabel = "WorldCup2026Qualifiers".equalsIgnoreCase(sheetName)
                ? "World Cup 2026 Qualifier"
                : sheetName;
        String sourceKey = worldCupHistoricalSourceFixtureKey(
                snapshot.getLeague().getCode(),
                sheetName,
                matchDate.get(),
                homeTeam,
                awayTeam
        );

        return Optional.of(normalizedWorldCupRow(
                matchDate.get(),
                matchDate.get().atTime(kickoffTime).atOffset(ZoneOffset.UTC),
                homeTeam,
                awayTeam,
                homeScore,
                awayScore,
                null,
                workbookInteger(row, headers, formatter, "HS").orElse(null),
                workbookInteger(row, headers, formatter, "AS").orElse(null),
                workbookInteger(row, headers, formatter, "HST").orElse(null),
                workbookInteger(row, headers, formatter, "AST").orElse(null),
                workbookInteger(row, headers, formatter, "HF").orElse(null),
                workbookInteger(row, headers, formatter, "AF").orElse(null),
                workbookInteger(row, headers, formatter, "HC").orElse(null),
                workbookInteger(row, headers, formatter, "AC").orElse(null),
                workbookInteger(row, headers, formatter, "HY").orElse(null),
                workbookInteger(row, headers, formatter, "AY").orElse(null),
                workbookInteger(row, headers, formatter, "HR").orElse(null),
                workbookInteger(row, headers, formatter, "AR").orElse(null),
                roundLabel,
                null,
                sourceKey
        ));
    }

    private void upsertNormalizedRow(RawSnapshot snapshot, NormalizedRow row, ExtractionCounters counters, boolean includeStatistics) {
        TeamResolution homeTeam = resolveTeam(snapshot.getLeague(), row.homeTeamName());
        TeamResolution awayTeam = resolveTeam(snapshot.getLeague(), row.awayTeamName());
        Match match = upsertMatch(snapshot, row, homeTeam.team(), awayTeam.team());
        if (includeStatistics) {
            upsertStatistics(snapshot, match, row);
            counters.statsUpserted++;
        }

        counters.rowsAccepted++;
        counters.teamsUpserted += homeTeam.created() ? 1 : 0;
        counters.teamsUpserted += awayTeam.created() ? 1 : 0;
        counters.matchesUpserted++;
    }

    private NormalizedRow normalizedWorldCupRow(
            LocalDate matchDate,
            OffsetDateTime kickoffAt,
            String homeTeamName,
            String awayTeamName,
            Integer homeScore,
            Integer awayScore,
            String referee,
            Integer homeShots,
            Integer awayShots,
            Integer homeShotsOnTarget,
            Integer awayShotsOnTarget,
            Integer homeFouls,
            Integer awayFouls,
            Integer homeCorners,
            Integer awayCorners,
            Integer homeYellowCards,
            Integer awayYellowCards,
            Integer homeRedCards,
            Integer awayRedCards,
            String roundLabel,
            String venue,
            String sourceFixtureKey
    ) {
        return new NormalizedRow(
                matchDate,
                kickoffAt == null ? DEFAULT_KICKOFF_TIME : kickoffAt.toLocalTime(),
                kickoffAt,
                homeTeamName,
                awayTeamName,
                homeScore,
                awayScore,
                referee,
                homeShots,
                awayShots,
                homeShotsOnTarget,
                awayShotsOnTarget,
                homeFouls,
                awayFouls,
                homeCorners,
                awayCorners,
                homeYellowCards,
                awayYellowCards,
                homeRedCards,
                awayRedCards,
                roundLabel,
                venue,
                sourceFixtureKey
        );
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText(null);
        if (!StringUtils.hasText(value)) {
            throw new InvalidRequestException(fieldName + " is required.");
        }
        return value.trim();
    }

    private LocalDate parseIsoDate(String rawDate) {
        try {
            return LocalDate.parse(rawDate.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException exception) {
            throw new InvalidRequestException("Invalid ISO date: " + rawDate + ".");
        }
    }

    private Optional<OffsetDateTime> parseOpenFootballKickoff(LocalDate matchDate, String rawTime) {
        if (!StringUtils.hasText(rawTime)) {
            return Optional.empty();
        }
        Matcher matcher = OPENFOOTBALL_TIME_PATTERN.matcher(rawTime.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        Optional<LocalTime> localTime = parseTime(matcher.group(1));
        if (localTime.isEmpty()) {
            return Optional.empty();
        }
        int offsetHours = Integer.parseInt(matcher.group(2));
        return Optional.of(matchDate.atTime(localTime.get()).atOffset(ZoneOffset.ofHours(offsetHours)));
    }

    private String worldCupRoundLabel(String stageOrRound, String group) {
        String stage = StringUtils.hasText(stageOrRound) ? stageOrRound.trim() : null;
        String groupLabel = StringUtils.hasText(group) ? "Group " + group.trim().replace("Group ", "") : null;
        if (StringUtils.hasText(stage) && StringUtils.hasText(groupLabel) && !stage.toLowerCase(Locale.ROOT).contains("group")) {
            return stage + " - " + groupLabel;
        }
        if (StringUtils.hasText(stage) && StringUtils.hasText(groupLabel) && !stage.contains(groupLabel)) {
            return stage + " - " + groupLabel;
        }
        return StringUtils.hasText(stage) ? stage : groupLabel;
    }

    private String worldCupVenue(String stadium, String hostCity) {
        if (StringUtils.hasText(stadium) && StringUtils.hasText(hostCity)) {
            return stadium.trim() + ", " + hostCity.trim();
        }
        return StringUtils.hasText(stadium) ? stadium.trim() : trimToNull(hostCity);
    }

    private Optional<LocalDate> parseIsoLocalDate(String rawDate) {
        if (!StringUtils.hasText(rawDate)) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(rawDate.trim(), DateTimeFormatter.ISO_LOCAL_DATE));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private Optional<OffsetDateTime> parseOffsetDateTime(String rawDateTime) {
        if (!StringUtils.hasText(rawDateTime)) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(rawDateTime.trim()));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private String worldCupSourceFixtureKey(LeagueCode leagueCode, LocalDate matchDate, String homeTeam, String awayTeam) {
        return truncate("WC:" + leagueCode.name() + ":" + matchDate + ":"
                + normalizeKey(canonicalizeOfficialTeamName(leagueCode, homeTeam)) + ":"
                + normalizeKey(canonicalizeOfficialTeamName(leagueCode, awayTeam)), 180);
    }

    private String worldCupHistoricalSourceFixtureKey(
            LeagueCode leagueCode,
            String sheetName,
            LocalDate matchDate,
            String homeTeam,
            String awayTeam
    ) {
        String home = normalizeKey(canonicalizeOfficialTeamName(leagueCode, homeTeam));
        String away = normalizeKey(canonicalizeOfficialTeamName(leagueCode, awayTeam));
        return truncate("WC-HIST:" + leagueCode.name() + ":" + sheetName + ":" + matchDate + ":" + home + ":" + away, 180);
    }

    private Map<String, Integer> headers(Row row, DataFormatter formatter) {
        Map<String, Integer> headers = new HashMap<>();
        if (row == null) {
            return headers;
        }
        for (Cell cell : row) {
            String value = formatter.formatCellValue(cell);
            if (StringUtils.hasText(value)) {
                headers.put(value.trim().toLowerCase(Locale.ROOT), cell.getColumnIndex());
            }
        }
        return headers;
    }

    private boolean workbookRowBlank(Row row) {
        for (Cell cell : row) {
            if (StringUtils.hasText(cell.toString())) {
                return false;
            }
        }
        return true;
    }

    private String workbookText(Row row, Map<String, Integer> headers, DataFormatter formatter, String header) {
        Cell cell = workbookCell(row, headers, header);
        if (cell == null) {
            return null;
        }
        String value = formatter.formatCellValue(cell);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Optional<Integer> workbookInteger(Row row, Map<String, Integer> headers, DataFormatter formatter, String... headerNames) {
        Cell cell = workbookCell(row, headers, headerNames);
        if (cell == null) {
            return Optional.empty();
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (value < 0) {
                return Optional.empty();
            }
            return Optional.of((int) Math.round(value));
        }
        return parseNonNegativeInt(formatter.formatCellValue(cell));
    }

    private Optional<LocalDate> workbookDate(Row row, Map<String, Integer> headers, DataFormatter formatter, String header) {
        Cell cell = workbookCell(row, headers, header);
        if (cell == null) {
            return Optional.empty();
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return Optional.of(DateUtil.getJavaDate(cell.getNumericCellValue()).toInstant()
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate());
        }
        String value = formatter.formatCellValue(cell);
        Optional<LocalDate> parsed = parseDate(value);
        if (parsed.isPresent()) {
            return parsed;
        }
        try {
            return Optional.of(LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private Optional<LocalTime> workbookTime(Row row, Map<String, Integer> headers, DataFormatter formatter, String header) {
        Cell cell = workbookCell(row, headers, header);
        if (cell == null) {
            return Optional.empty();
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            int seconds = (int) Math.round(value * 24 * 60 * 60);
            return Optional.of(LocalTime.ofSecondOfDay(Math.floorMod(seconds, 24 * 60 * 60)));
        }
        return parseTime(formatter.formatCellValue(cell));
    }

    private Cell workbookCell(Row row, Map<String, Integer> headers, String... headerNames) {
        for (String headerName : headerNames) {
            Integer index = headers.get(headerName.toLowerCase(Locale.ROOT));
            if (index != null) {
                return row.getCell(index);
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<OfficialFixtureRow> officialFixtureRows(RawSnapshot snapshot) {
        String selectorsJson = snapshot.getSourceTarget().getSelectorsJson();
        String format = null;
        if (StringUtils.hasText(selectorsJson)) {
            try {
                format = text(objectMapper.readTree(selectorsJson), "format", "sourceFormat");
            } catch (JsonProcessingException exception) {
                throw new InvalidRequestException("Source selectorsJson is invalid JSON.");
            }
        }
        String text = StringUtils.hasText(snapshot.getExtractedText())
                ? snapshot.getExtractedText()
                : Jsoup.parse(snapshot.getRawPayload(), snapshot.getSourceUrl()).text();

        if ("official-eliteserien-fixtures-html".equalsIgnoreCase(format)) {
            return parseEliteserienFixtures(text);
        }
        if ("official-allsvenskan-fixtures-html".equalsIgnoreCase(format)) {
            return parseAllsvenskanFixtures(snapshot, text);
        }
        throw new InvalidRequestException("Unsupported official fixture HTML format: " + format + ".");
    }

    private List<OfficialFixtureRow> parseEliteserienFixtures(String text) {
        List<OfficialFixtureRow> rows = new ArrayList<>();
        Matcher matcher = ELITESERIEN_FIXTURE_PATTERN.matcher(text);
        while (matcher.find()) {
            Optional<LocalDate> matchDate = parseDottedDate(matcher.group(3));
            Optional<LocalTime> kickoffTime = parseTime(matcher.group(4));
            if (matchDate.isEmpty() || kickoffTime.isEmpty()) {
                continue;
            }
            rows.add(new OfficialFixtureRow(
                    matchDate.get(),
                    kickoffTime.get(),
                    matcher.group(1).trim(),
                    matcher.group(2).trim()
            ));
        }
        return rows;
    }

    private List<OfficialFixtureRow> parseAllsvenskanFixtures(RawSnapshot snapshot, String text) {
        int seasonYear = seasonYear(snapshot);
        List<OfficialFixtureRow> rows = new ArrayList<>();
        Matcher matcher = ALLSVENSKAN_FIXTURE_PATTERN.matcher(text);
        while (matcher.find()) {
            Integer month = swedishMonth(matcher.group(2));
            Optional<LocalTime> kickoffTime = parseTime(matcher.group(3));
            if (month == null || kickoffTime.isEmpty()) {
                continue;
            }
            rows.add(new OfficialFixtureRow(
                    LocalDate.of(seasonYear, month, Integer.parseInt(matcher.group(1))),
                    kickoffTime.get(),
                    matcher.group(4).trim(),
                    matcher.group(5).trim()
            ));
        }
        return rows;
    }

    private List<RowError> validateRecord(CSVRecord record, CsvColumnMapping columnMapping) {
        List<RowError> errors = new ArrayList<>();
        String date = value(record, columnMapping.date());
        String homeTeam = value(record, columnMapping.homeTeam());
        String awayTeam = value(record, columnMapping.awayTeam());
        String homeScore = value(record, columnMapping.homeScore());
        String awayScore = value(record, columnMapping.awayScore());

        if (!StringUtils.hasText(date) || parseDate(date).isEmpty()) {
            errors.add(new RowError("Date", "INVALID_DATE", "Date is required and must use d/M/yyyy or d/M/yy format."));
        }
        if (!StringUtils.hasText(homeTeam)) {
            errors.add(new RowError("HomeTeam", "MISSING_HOME_TEAM", "HomeTeam is required."));
        }
        if (!StringUtils.hasText(awayTeam)) {
            errors.add(new RowError("AwayTeam", "MISSING_AWAY_TEAM", "AwayTeam is required."));
        }
        if (StringUtils.hasText(homeTeam) && StringUtils.hasText(awayTeam)
                && normalizeKey(homeTeam).equals(normalizeKey(awayTeam))) {
            errors.add(new RowError("AwayTeam", "SAME_TEAMS", "HomeTeam and AwayTeam cannot refer to the same team."));
        }

        boolean hasHomeScore = StringUtils.hasText(homeScore);
        boolean hasAwayScore = StringUtils.hasText(awayScore);
        if (hasHomeScore != hasAwayScore) {
            errors.add(new RowError("FTHG/FTAG", "INCOMPLETE_SCORE", "Full-time score must include both home and away values."));
        }
        Optional<Integer> parsedHomeScore = parseNonNegativeInt(homeScore);
        Optional<Integer> parsedAwayScore = parseNonNegativeInt(awayScore);
        if (hasHomeScore && parsedHomeScore.isEmpty()) {
            errors.add(new RowError("FTHG", "INVALID_HOME_SCORE", "FTHG must be a non-negative integer."));
        }
        if (hasAwayScore && parsedAwayScore.isEmpty()) {
            errors.add(new RowError("FTAG", "INVALID_AWAY_SCORE", "FTAG must be a non-negative integer."));
        }
        if (parsedHomeScore.isPresent() && parsedAwayScore.isPresent()) {
            String expectedResult = resultCode(parsedHomeScore.get(), parsedAwayScore.get());
            String sourceResult = value(record, columnMapping.result());
            if (StringUtils.hasText(sourceResult) && !expectedResult.equalsIgnoreCase(sourceResult)) {
                errors.add(new RowError("FTR", "RESULT_SCORE_MISMATCH", "FTR does not match FTHG/FTAG."));
            }
        }

        validateOptionalNonNegativeInt(record, columnMapping.homeShots(), errors);
        validateOptionalNonNegativeInt(record, columnMapping.awayShots(), errors);
        validateOptionalNonNegativeInt(record, columnMapping.homeShotsOnTarget(), errors);
        validateOptionalNonNegativeInt(record, columnMapping.awayShotsOnTarget(), errors);
        validateOptionalNonNegativeInt(record, columnMapping.homeFouls(), errors);
        validateOptionalNonNegativeInt(record, columnMapping.awayFouls(), errors);
        validateOptionalNonNegativeInt(record, columnMapping.homeCorners(), errors);
        validateOptionalNonNegativeInt(record, columnMapping.awayCorners(), errors);
        validateOptionalNonNegativeInt(record, columnMapping.homeYellowCards(), errors);
        validateOptionalNonNegativeInt(record, columnMapping.awayYellowCards(), errors);
        validateOptionalNonNegativeInt(record, columnMapping.homeRedCards(), errors);
        validateOptionalNonNegativeInt(record, columnMapping.awayRedCards(), errors);

        return errors;
    }

    private void validateOptionalNonNegativeInt(CSVRecord record, String field, List<RowError> errors) {
        if (!StringUtils.hasText(field)) {
            return;
        }
        String rawValue = value(record, field);
        if (StringUtils.hasText(rawValue) && parseNonNegativeInt(rawValue).isEmpty()) {
            errors.add(new RowError(field, "INVALID_NON_NEGATIVE_INTEGER", field + " must be a non-negative integer."));
        }
    }

    private NormalizedRow toNormalizedRow(RawSnapshot snapshot, CSVRecord record, CsvColumnMapping columnMapping) {
        LocalDate matchDate = parseDate(value(record, columnMapping.date()))
                .orElseThrow(() -> new InvalidRequestException("Validated row had invalid Date."));
        LocalTime kickoffTime = parseTime(value(record, columnMapping.time())).orElse(DEFAULT_KICKOFF_TIME);
        Integer homeScore = parseNonNegativeInt(value(record, columnMapping.homeScore())).orElse(null);
        Integer awayScore = parseNonNegativeInt(value(record, columnMapping.awayScore())).orElse(null);
        String homeTeam = value(record, columnMapping.homeTeam());
        String awayTeam = value(record, columnMapping.awayTeam());

        return new NormalizedRow(
                matchDate,
                kickoffTime,
                null,
                homeTeam,
                awayTeam,
                homeScore,
                awayScore,
                value(record, columnMapping.referee()),
                parseNonNegativeInt(value(record, columnMapping.homeShots())).orElse(null),
                parseNonNegativeInt(value(record, columnMapping.awayShots())).orElse(null),
                parseNonNegativeInt(value(record, columnMapping.homeShotsOnTarget())).orElse(null),
                parseNonNegativeInt(value(record, columnMapping.awayShotsOnTarget())).orElse(null),
                parseNonNegativeInt(value(record, columnMapping.homeFouls())).orElse(null),
                parseNonNegativeInt(value(record, columnMapping.awayFouls())).orElse(null),
                parseNonNegativeInt(value(record, columnMapping.homeCorners())).orElse(null),
                parseNonNegativeInt(value(record, columnMapping.awayCorners())).orElse(null),
                parseNonNegativeInt(value(record, columnMapping.homeYellowCards())).orElse(null),
                parseNonNegativeInt(value(record, columnMapping.awayYellowCards())).orElse(null),
                parseNonNegativeInt(value(record, columnMapping.homeRedCards())).orElse(null),
                parseNonNegativeInt(value(record, columnMapping.awayRedCards())).orElse(null),
                null,
                null,
                sourceFixtureKey(snapshot.getLeague().getCode(), matchDate, homeTeam, awayTeam)
        );
    }

    private TeamResolution resolveTeam(League league, String sourceTeamName) {
        String aliasNormalized = normalizeKey(sourceTeamName);
        Optional<TeamAlias> alias = teamAliasRepository.findByLeague_CodeAndAliasNormalized(league.getCode(), aliasNormalized);
        if (alias.isPresent()) {
            return new TeamResolution(alias.get().getTeam(), false);
        }

        String canonicalTeamName = canonicalizeOfficialTeamName(league.getCode(), sourceTeamName);
        Optional<Team> canonicalMatch = teamRepository.findByLeague_CodeAndCanonicalNameIgnoreCaseSafely(league.getCode(), canonicalTeamName);
        if (canonicalMatch.isPresent()) {
            Team team = canonicalMatch.get();
            saveAlias(league, team, sourceTeamName, aliasNormalized);
            if (!normalizeKey(canonicalTeamName).equals(aliasNormalized)) {
                saveAlias(league, team, canonicalTeamName, normalizeKey(canonicalTeamName));
            }
            return new TeamResolution(team, false);
        }

        Team team = teamRepository.save(new Team()
                .setLeague(league)
                .setCanonicalName(truncate(canonicalTeamName.trim(), 160))
                .setShortName(truncate(canonicalTeamName.trim(), 80))
                .setCountry(league.getCode() == LeagueCode.FIFA_WORLD_CUP_2026 ? truncate(canonicalTeamName.trim(), 80) : league.getCountry())
                .setExternalKey(truncate("FD:" + league.getCode().name() + ":" + aliasNormalized, 160))
                .setActive(true));
        saveAlias(league, team, sourceTeamName, aliasNormalized);
        if (!normalizeKey(canonicalTeamName).equals(aliasNormalized)) {
            saveAlias(league, team, canonicalTeamName, normalizeKey(canonicalTeamName));
        }
        return new TeamResolution(team, true);
    }

    private void saveAlias(League league, Team team, String sourceTeamName, String aliasNormalized) {
        teamAliasRepository.findByLeague_CodeAndAliasNormalized(league.getCode(), aliasNormalized)
                .orElseGet(() -> teamAliasRepository.save(new TeamAlias()
                        .setLeague(league)
                        .setTeam(team)
                        .setAlias(truncate(sourceTeamName.trim(), 160))
                        .setAliasNormalized(truncate(aliasNormalized, 180))
                        .setSourceName(SOURCE_NAME)));
    }

    private Match upsertMatch(RawSnapshot snapshot, NormalizedRow row, Team homeTeam, Team awayTeam) {
        League league = snapshot.getLeague();
        OffsetDateTime kickoffAt = row.kickoffAt() == null
                ? row.matchDate()
                        .atTime(row.kickoffTime())
                        .atZone(zoneFor(league.getCode()))
                        .toOffsetDateTime()
                : row.kickoffAt();

        Match match = matchRepository.findByLeague_CodeAndSourceFixtureKeySafely(league.getCode(), row.sourceFixtureKey())
                .or(() -> matchRepository.findByLeague_CodeAndHomeTeam_IdAndAwayTeam_IdAndKickoffAtSafely(
                        league.getCode(),
                        homeTeam.getId(),
                        awayTeam.getId(),
                        kickoffAt
                ))
                .or(() -> matchRepository.findByLeague_CodeAndHomeTeam_IdAndAwayTeam_IdAndMatchDateSafely(
                        league.getCode(),
                        homeTeam.getId(),
                        awayTeam.getId(),
                        row.matchDate()
                ))
                .orElseGet(Match::new);


        MatchStatus status = row.homeScore() == null || row.awayScore() == null
                ? MatchStatus.SCHEDULED
                : MatchStatus.FINISHED;
        Integer homeScore = row.homeScore();
        Integer awayScore = row.awayScore();
        if (status == MatchStatus.SCHEDULED
                && match.getStatus() == MatchStatus.FINISHED
                && match.getHomeScore() != null
                && match.getAwayScore() != null) {
            status = MatchStatus.FINISHED;
            homeScore = match.getHomeScore();
            awayScore = match.getAwayScore();
        }
        String seasonLabel = seasonLabel(snapshot, row);

        match.setLeague(league)
                .setHomeTeam(homeTeam)
                .setAwayTeam(awayTeam)
                .setMatchDate(row.matchDate())
                .setKickoffAt(kickoffAt)
                .setStatus(status)
                .setHomeScore(homeScore)
                .setAwayScore(awayScore)
                .setReferee(truncate(row.referee(), 160))
                .setSeasonLabel(seasonLabel)
                .setRoundLabel(StringUtils.hasText(row.roundLabel()) ? truncate(row.roundLabel(), 64) : match.getRoundLabel())
                .setVenue(StringUtils.hasText(row.venue()) ? truncate(row.venue(), 160) : match.getVenue())
                .setSourceFixtureKey(StringUtils.hasText(match.getSourceFixtureKey()) ? match.getSourceFixtureKey() : row.sourceFixtureKey());
        return matchRepository.save(match);
    }

    private String seasonLabel(RawSnapshot snapshot, NormalizedRow row) {
        String configured = StringUtils.hasText(snapshot.getSourceTarget().getTargetSeasonLabel())
                ? snapshot.getSourceTarget().getTargetSeasonLabel()
                : snapshot.getLeague().getCurrentSeason();
        if (snapshot.getLeague().getCode() != LeagueCode.FIFA_WORLD_CUP_2026) {
            return configured;
        }
        return worldCupSeasonLabel(row, configured);
    }

    private String worldCupSeasonLabel(NormalizedRow row, String fallback) {
        String roundLabel = row.roundLabel();
        if (!StringUtils.hasText(roundLabel)) {
            return fallback;
        }
        if ("World Cup 2026 Qualifier".equalsIgnoreCase(roundLabel.trim())) {
            return "2026_QUALIFIERS";
        }
        Matcher matcher = Pattern.compile("(?i)^WorldCup(\\d{4})$").matcher(roundLabel.trim());
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return fallback;
    }

    private void upsertStatistics(RawSnapshot snapshot, Match match, NormalizedRow row) {
        MatchStatistics stats = matchStatisticsRepository.findByMatch_Id(match.getId()).orElseGet(MatchStatistics::new);
        stats.setMatch(match)
                .setRawSnapshot(snapshot)
                .setHomeShots(row.homeShots())
                .setAwayShots(row.awayShots())
                .setHomeShotsOnTarget(row.homeShotsOnTarget())
                .setAwayShotsOnTarget(row.awayShotsOnTarget())
                .setHomeFouls(row.homeFouls())
                .setAwayFouls(row.awayFouls())
                .setHomeCorners(row.homeCorners())
                .setAwayCorners(row.awayCorners())
                .setHomeYellowCards(row.homeYellowCards())
                .setAwayYellowCards(row.awayYellowCards())
                .setHomeRedCards(row.homeRedCards())
                .setAwayRedCards(row.awayRedCards());
        matchStatisticsRepository.save(stats);
    }

    private int persistErrors(ExtractionRun run, RawSnapshot snapshot, CSVRecord record, List<RowError> rowErrors) {
        String rawRecordJson = rawRecordJson(record);
        for (RowError error : rowErrors) {
            extractionValidationErrorRepository.save(new ExtractionValidationError()
                    .setExtractionRun(run)
                    .setRawSnapshot(snapshot)
                    .setRowNumber((int) record.getRecordNumber())
                    .setFieldName(error.fieldName())
                    .setErrorCode(error.errorCode())
                    .setErrorMessage(error.errorMessage())
                    .setRawRecordJson(rawRecordJson));
        }
        return rowErrors.size();
    }

    private ExtractionStatus finalStatus(ExtractionSummary summary) {
        if (summary.rowsSeen() == 0) {
            return ExtractionStatus.SKIPPED;
        }
        if (summary.rowsAccepted() == 0) {
            return ExtractionStatus.FAILED;
        }
        if (summary.validationErrors() > 0) {
            return ExtractionStatus.PARTIAL;
        }
        return ExtractionStatus.SUCCESS;
    }

    private String finalMessage(ExtractionStatus finalStatus) {
        return switch (finalStatus) {
            case SKIPPED -> "No rows matched the configured source filters.";
            case FAILED -> "No valid rows were accepted from the snapshot.";
            default -> null;
        };
    }

    private ExtractionRunResponse toResponse(ExtractionRun run, boolean cacheReused) {
        List<ExtractionValidationErrorResponse> errors = extractionValidationErrorRepository
                .findTop100ByExtractionRun_IdOrderByRowNumberAsc(run.getId())
                .stream()
                .map(ExtractionValidationErrorResponse::from)
                .toList();
        return ExtractionRunResponse.from(run, cacheReused, errors);
    }

    private List<League> resolveLeagues(Set<LeagueCode> requestedCodes) {
        if (requestedCodes == null || requestedCodes.isEmpty()) {
            List<League> leagues = leagueRepository.findByActiveTrueAndScrapeEnabledTrueOrderByNameAsc();
            if (leagues.isEmpty()) {
                throw new ResourceNotFoundException("No active scrape-enabled leagues are configured.");
            }
            return leagues;
        }

        List<League> leagues = leagueRepository.findByCodeInAndActiveTrue(requestedCodes);
        Set<LeagueCode> activeCodes = leagues.stream().map(League::getCode).collect(Collectors.toSet());
        EnumSet<LeagueCode> missing = EnumSet.copyOf(requestedCodes);
        missing.removeAll(activeCodes);
        if (!missing.isEmpty()) {
            throw new ResourceNotFoundException("Unsupported or inactive leagues: " + missing + ".");
        }
        return leagues;
    }

    private boolean isSupportedSnapshot(RawSnapshot snapshot) {
        return isCsvSnapshot(snapshot)
                || isOfficialFixtureHtmlSnapshot(snapshot)
                || isWorldCupFixturesJsonSnapshot(snapshot)
                || isOpenFootballWorldCupJsonSnapshot(snapshot)
                || isFootballDataWorldCupWorkbookSnapshot(snapshot)
                || isTheSportsDbEventsJsonSnapshot(snapshot)
                || isSgoddsResultsCsvSnapshot(snapshot)
                || isApiFootballFixturesJsonSnapshot(snapshot);
    }

    private boolean isCsvSnapshot(RawSnapshot snapshot) {
        String contentType = snapshot.getContentType();
        String selectorsJson = snapshot.getSourceTarget().getSelectorsJson();
        return (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("csv"))
                || (snapshot.getSourceUrl() != null && snapshot.getSourceUrl().toLowerCase(Locale.ROOT).endsWith(".csv"))
                || (selectorsJson != null && selectorsJson.toLowerCase(Locale.ROOT).contains("\"format\":\"csv\""));
    }

    private boolean isOfficialFixtureHtmlSnapshot(RawSnapshot snapshot) {
        String format = snapshotFormat(snapshot);
        return "official-eliteserien-fixtures-html".equalsIgnoreCase(format)
                || "official-allsvenskan-fixtures-html".equalsIgnoreCase(format);
    }

    private boolean isWorldCupFixturesJsonSnapshot(RawSnapshot snapshot) {
        return FORMAT_WORLD_CUP_FIXTURES_JSON.equalsIgnoreCase(snapshotFormat(snapshot));
    }

    private boolean isOpenFootballWorldCupJsonSnapshot(RawSnapshot snapshot) {
        return FORMAT_OPENFOOTBALL_WORLD_CUP_JSON.equalsIgnoreCase(snapshotFormat(snapshot));
    }

    private boolean isFootballDataWorldCupWorkbookSnapshot(RawSnapshot snapshot) {
        return FORMAT_FOOTBALL_DATA_WORLD_CUP_WORKBOOK.equalsIgnoreCase(snapshotFormat(snapshot));
    }

    private boolean isTheSportsDbEventsJsonSnapshot(RawSnapshot snapshot) {
        return FORMAT_THESPORTSDB_EVENTS_JSON.equalsIgnoreCase(snapshotFormat(snapshot));
    }

    private boolean isSgoddsResultsCsvSnapshot(RawSnapshot snapshot) {
        return FORMAT_SGODDS_RESULTS_CSV.equalsIgnoreCase(snapshotFormat(snapshot));
    }

    private boolean isApiFootballFixturesJsonSnapshot(RawSnapshot snapshot) {
        return FORMAT_API_FOOTBALL_FIXTURES_JSON.equalsIgnoreCase(snapshotFormat(snapshot));
    }

    private String snapshotFormat(RawSnapshot snapshot) {
        String selectorsJson = snapshot.getSourceTarget().getSelectorsJson();
        if (!StringUtils.hasText(selectorsJson)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(selectorsJson);
            return root.path("format").asText(null);
        } catch (JsonProcessingException exception) {
            throw new InvalidRequestException("Source selectorsJson is invalid JSON.");
        }
    }

    private SourceExtractionOptions extractionOptions(RawSnapshot snapshot) {
        String selectorsJson = snapshot.getSourceTarget().getSelectorsJson();
        if (!StringUtils.hasText(selectorsJson)) {
            return new SourceExtractionOptions(snapshot.getSourceTarget().getSourceSeasonToken(), null, null, null);
        }
        try {
            JsonNode root = objectMapper.readTree(selectorsJson);
            return new SourceExtractionOptions(
                    text(root, "season", "sourceSeason", snapshot.getSourceTarget().getSourceSeasonToken()),
                    text(root, "divisionCode", "div"),
                    text(root, "country", "sourceCountry"),
                    text(root, "leagueName", "sourceLeague")
            );
        } catch (JsonProcessingException exception) {
            throw new InvalidRequestException("Source selectorsJson is invalid JSON.");
        }
    }

    private boolean matchesSourceFilters(CSVRecord record, SourceExtractionOptions options) {
        if (StringUtils.hasText(options.season())) {
            String season = value(record, "Season");
            if (!options.season().equalsIgnoreCase(season)) {
                return false;
            }
        }
        if (StringUtils.hasText(options.divisionCode())) {
            String div = value(record, "Div");
            if (!options.divisionCode().equalsIgnoreCase(div)) {
                return false;
            }
        }
        if (StringUtils.hasText(options.country())) {
            String country = value(record, "Country");
            if (!options.country().equalsIgnoreCase(country)) {
                return false;
            }
        }
        if (StringUtils.hasText(options.leagueName())) {
            String league = value(record, "League");
            return options.leagueName().equalsIgnoreCase(league);
        }
        return true;
    }

    private CsvColumnMapping columnMappingFor(CSVRecord record) {
        if (record.isMapped("HomeTeam") && record.isMapped("AwayTeam")) {
            return CsvColumnMapping.standard();
        }
        if (record.isMapped("Home") && record.isMapped("Away")) {
            return CsvColumnMapping.extraLeague();
        }
        throw new InvalidRequestException("Unsupported Football-Data CSV schema. Expected HomeTeam/AwayTeam or Home/Away columns.");
    }

    private String text(JsonNode root, String primaryField, String fallbackField) {
        return text(root, primaryField, fallbackField, null);
    }

    private String text(JsonNode root, String primaryField, String fallbackField, String defaultValue) {
        if (root == null || root.isNull()) {
            return defaultValue;
        }
        JsonNode primary = root.get(primaryField);
        if (primary != null && primary.isTextual() && StringUtils.hasText(primary.asText())) {
            return primary.asText().trim();
        }
        JsonNode fallback = root.get(fallbackField);
        if (fallback != null && fallback.isTextual() && StringUtils.hasText(fallback.asText())) {
            return fallback.asText().trim();
        }
        return defaultValue;
    }

    private String nodeText(JsonNode node, String... fieldNames) {
        if (node == null || node.isNull()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private boolean isBlankRecord(CSVRecord record) {
        for (String value : record) {
            if (StringUtils.hasText(value)) {
                return false;
            }
        }
        return true;
    }

    private Optional<LocalDate> parseDate(String rawDate) {
        if (!StringUtils.hasText(rawDate)) {
            return Optional.empty();
        }
        String value = rawDate.trim();
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return Optional.of(LocalDate.parse(value, formatter));
            } catch (DateTimeParseException ignored) {
            }
        }
        return Optional.empty();
    }

    private Optional<LocalDate> parseDottedDate(String rawDate) {
        if (!StringUtils.hasText(rawDate)) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(rawDate.trim(), DateTimeFormatter.ofPattern("dd.MM.uuuu")));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private Integer swedishMonth(String rawMonth) {
        if (!StringUtils.hasText(rawMonth)) {
            return null;
        }
        String month = rawMonth.trim().toLowerCase(Locale.ROOT).replace(".", "");
        return switch (month) {
            case "mars", "mar" -> 3;
            case "april", "apr" -> 4;
            case "maj" -> 5;
            case "juni", "jun" -> 6;
            case "juli", "jul" -> 7;
            case "aug", "augusti" -> 8;
            case "sep", "september" -> 9;
            case "okt", "oktober" -> 10;
            case "nov", "november" -> 11;
            default -> null;
        };
    }

    private Optional<LocalTime> parseTime(String rawTime) {
        if (!StringUtils.hasText(rawTime)) {
            return Optional.empty();
        }
        String value = rawTime.trim();
        for (DateTimeFormatter formatter : TIME_FORMATTERS) {
            try {
                return Optional.of(LocalTime.parse(value, formatter));
            } catch (DateTimeParseException ignored) {
            }
        }
        return Optional.empty();
    }

    private Optional<Integer> parseNonNegativeInt(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return Optional.empty();
        }
        try {
            int parsed = Integer.parseInt(rawValue.trim());
            return parsed >= 0 ? Optional.of(parsed) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private String value(CSVRecord record, String fieldName) {
        if (!StringUtils.hasText(fieldName)) {
            return null;
        }
        if (!record.isMapped(fieldName)) {
            return null;
        }
        String value = record.get(fieldName);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String resultCode(int homeScore, int awayScore) {
        if (homeScore > awayScore) {
            return "H";
        }
        if (awayScore > homeScore) {
            return "A";
        }
        return "D";
    }

    private String sourceFixtureKey(LeagueCode leagueCode, LocalDate matchDate, String homeTeam, String awayTeam) {
        return truncate("FD:" + leagueCode.name() + ":" + matchDate + ":"
                + normalizeKey(homeTeam) + ":" + normalizeKey(awayTeam), 180);
    }

    private String canonicalizeOfficialTeamName(LeagueCode leagueCode, String teamName) {
        String normalized = normalizeKey(teamName);
        if (leagueCode == LeagueCode.ELITESERIEN) {
            return switch (normalized) {
                case "bod_glimt" -> "Bodo/Glimt";
                case "bodo_glimt" -> "Bodo/Glimt";
                case "kfum" -> "KFUM Oslo";
                case "lillestrom" -> "Lillestrom";
                case "sandefjord_fotball" -> "Sandefjord";
                case "troms" -> "Tromso";
                case "tromso" -> "Tromso";
                case "valerenga" -> "Valerenga";
                default -> teamName.trim();
            };
        }
        if (leagueCode == LeagueCode.ALLSVENSKAN) {
            return switch (normalized) {
                case "aik_fotboll" -> "AIK";
                case "bk_hacken" -> "Hacken";
                case "degerfors_if" -> "Degerfors";
                case "djurgardens_if" -> "Djurgarden";
                case "gais" -> "GAIS";
                case "if_brommapojkarna" -> "Brommapojkarna";
                case "if_elfsborg" -> "Elfsborg";
                case "ifk_goteborg" -> "Goteborg";
                case "hammarby_ff" -> "Hammarby";
                case "kalmar_ff" -> "Kalmar";
                case "mjallby_aif" -> "Mjallby";
                case "orgryte_is" -> "Orgryte";
                case "vasteras_sk_fk" -> "Vasteras SK";
                default -> teamName.trim();
            };
        }
        if (leagueCode == LeagueCode.FIFA_WORLD_CUP_2026) {
            return switch (normalized) {
                case "bosnia_herzegovina", "bosnia_and_herzegovina" -> "Bosnia and Herzegovina";
                case "cote_d_ivoire", "cote_divoire", "ivory_coast" -> "Ivory Coast";
                case "curacao" -> "Curacao";
                case "czechia", "czech_republic" -> "Czech Republic";
                case "d_r_congo", "dr_congo", "democratic_republic_of_congo" -> "DR Congo";
                case "iran" -> "Iran";
                case "korea_republic", "south_korea" -> "South Korea";
                case "turkey", "turkiye" -> "Turkiye";
                case "usa", "united_states", "united_states_of_america" -> "United States";
                default -> teamName.trim();
            };
        }
        if (leagueCode == LeagueCode.K_LEAGUE_1 || leagueCode == LeagueCode.K_LEAGUE_2) {
            return switch (normalized) {
                case "anyang" -> "FC Anyang";
                case "bucheon" -> "Bucheon FC 1995";
                case "daejeon" -> "Daejeon Hana Citizen";
                case "gangwon" -> "Gangwon FC";
                case "incheon" -> "Incheon United";
                case "jeju" -> "Jeju SK";
                case "jeonbuk" -> "Jeonbuk Hyundai Motors";
                case "pohang" -> "Pohang Steelers";
                case "sangmu" -> "Gimcheon Sangmu";
                case "seoul" -> "FC Seoul";
                case "ulsan" -> "Ulsan HD";
                default -> teamName.trim();
            };
        }
        return teamName.trim();
    }

    private int seasonYear(RawSnapshot snapshot) {
        String label = StringUtils.hasText(snapshot.getSourceTarget().getTargetSeasonLabel())
                ? snapshot.getSourceTarget().getTargetSeasonLabel()
                : snapshot.getLeague().getCurrentSeason();
        if (StringUtils.hasText(label)) {
            Matcher matcher = Pattern.compile("\\d{4}").matcher(label);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group());
            }
        }
        return snapshot.getSnapshotDate().getYear();
    }

    private String normalizeKey(String value) {
        String asciiFriendly = (value == null ? "" : value)
                .replace("ø", "o")
                .replace("Ø", "O")
                .replace("æ", "ae")
                .replace("Æ", "Ae")
                .replace("ð", "d")
                .replace("Ð", "D")
                .replace("þ", "th")
                .replace("Þ", "Th");
        String normalized = Normalizer.normalize(asciiFriendly, Normalizer.Form.NFD)
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

    private String stripBom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private String csvText(String rawPayload) {
        if (SnapshotPayloads.isBinary(rawPayload)) {
            return new String(SnapshotPayloads.bytes(rawPayload), StandardCharsets.UTF_8);
        }
        return SnapshotPayloads.text(rawPayload);
    }

    private String rawRecordJson(CSVRecord record) {
        try {
            return objectMapper.writeValueAsString(record.toMap());
        } catch (JsonProcessingException exception) {
            return "{\"error\":\"raw record could not be serialized\"}";
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record RowError(String fieldName, String errorCode, String errorMessage) {
    }

    private record TeamResolution(Team team, boolean created) {
    }

    private record TeamNames(String homeTeam, String awayTeam) {
    }

    private record FullTimeScore(int homeScore, int awayScore) {
    }

    private record SourceExtractionOptions(String season, String divisionCode, String country, String leagueName) {
    }

    private record OfficialFixtureRow(
            LocalDate matchDate,
            LocalTime kickoffTime,
            String homeTeamName,
            String awayTeamName
    ) {
    }

    private record CsvColumnMapping(
            String date,
            String time,
            String homeTeam,
            String awayTeam,
            String homeScore,
            String awayScore,
            String result,
            String referee,
            String homeShots,
            String awayShots,
            String homeShotsOnTarget,
            String awayShotsOnTarget,
            String homeFouls,
            String awayFouls,
            String homeCorners,
            String awayCorners,
            String homeYellowCards,
            String awayYellowCards,
            String homeRedCards,
            String awayRedCards
    ) {
        private static CsvColumnMapping standard() {
            return new CsvColumnMapping(
                    "Date",
                    "Time",
                    "HomeTeam",
                    "AwayTeam",
                    "FTHG",
                    "FTAG",
                    "FTR",
                    "Referee",
                    "HS",
                    "AS",
                    "HST",
                    "AST",
                    "HF",
                    "AF",
                    "HC",
                    "AC",
                    "HY",
                    "AY",
                    "HR",
                    "AR"
            );
        }

        private static CsvColumnMapping extraLeague() {
            return new CsvColumnMapping(
                    "Date",
                    "Time",
                    "Home",
                    "Away",
                    "HG",
                    "AG",
                    "Res",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    private record NormalizedRow(
            LocalDate matchDate,
            LocalTime kickoffTime,
            OffsetDateTime kickoffAt,
            String homeTeamName,
            String awayTeamName,
            Integer homeScore,
            Integer awayScore,
            String referee,
            Integer homeShots,
            Integer awayShots,
            Integer homeShotsOnTarget,
            Integer awayShotsOnTarget,
            Integer homeFouls,
            Integer awayFouls,
            Integer homeCorners,
            Integer awayCorners,
            Integer homeYellowCards,
            Integer awayYellowCards,
            Integer homeRedCards,
            Integer awayRedCards,
            String roundLabel,
            String venue,
            String sourceFixtureKey
    ) {
    }

    private record ExtractionSummary(
            int rowsSeen,
            int rowsAccepted,
            int teamsUpserted,
            int matchesUpserted,
            int statsUpserted,
            int validationErrors
    ) {
    }

    private static final class ExtractionCounters {
        private int rowsSeen;
        private int rowsAccepted;
        private int teamsUpserted;
        private int matchesUpserted;
        private int statsUpserted;
        private int validationErrors;

        private ExtractionSummary toSummary() {
            return new ExtractionSummary(
                    rowsSeen,
                    rowsAccepted,
                    teamsUpserted,
                    matchesUpserted,
                    statsUpserted,
                    validationErrors
            );
        }
    }
}
