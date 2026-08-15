package com.betai.service;

import com.betai.api.dto.DailyOddsExtractionRequest;
import com.betai.api.dto.DailyOddsExtractionResponse;
import com.betai.api.dto.OddsExtractionRunResponse;
import com.betai.api.dto.OddsImportItem;
import com.betai.api.dto.OddsImportRequest;
import com.betai.api.dto.OddsImportResponse;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.odds.OddsExtractionRun;
import com.betai.domain.odds.OddsExtractionStatus;
import com.betai.domain.snapshot.RawSnapshot;
import com.betai.domain.snapshot.ScrapeStatus;
import com.betai.domain.source.SourceTarget;
import com.betai.domain.source.SourceType;
import com.betai.exception.InvalidRequestException;
import com.betai.exception.ReferenceDataNotFoundException;
import com.betai.exception.ResourceNotFoundException;
import com.betai.repository.LeagueRepository;
import com.betai.repository.OddsExtractionRunRepository;
import com.betai.repository.RawSnapshotRepository;
import com.betai.util.SnapshotPayloads;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OddsSourceExtractionServiceImpl implements OddsSourceExtractionService {

    private static final String FORMAT_FOOTBALL_DATA_WORLD_CUP_WORKBOOK = "football-data-world-cup-workbook";
    private static final String FORMAT_SGODDS_OPENING_ODDS_CSV = "sgodds-opening-odds-csv";
    private static final String FORMAT_THE_ODDS_API_V4_JSON = "the-odds-api-v4-json";
    private static final String FORMAT_SHARPAPI_JSON = "sharpapi-odds-json";

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("d/M/uu"),
            DateTimeFormatter.ofPattern("dd/MM/uu")
    );

    private final RawSnapshotRepository rawSnapshotRepository;
    private final OddsExtractionRunRepository oddsExtractionRunRepository;
    private final LeagueRepository leagueRepository;
    private final OddsImportService oddsImportService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    @Transactional
    public OddsExtractionRunResponse extractRawSnapshot(UUID rawSnapshotId, boolean forceReprocess, boolean recalculateExistingSelections) {
        if (!forceReprocess) {
            Optional<OddsExtractionRun> cached = oddsExtractionRunRepository
                    .findFirstByRawSnapshot_IdAndExtractionStatusOrderByStartedAtDesc(rawSnapshotId, OddsExtractionStatus.SUCCESS);
            if (cached.isPresent()) {
                return OddsExtractionRunResponse.from(cached.get(), true);
            }
        }

        RawSnapshot snapshot = rawSnapshotRepository.findById(rawSnapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("Raw snapshot not found: " + rawSnapshotId + "."));
        OddsExtractionRun run = oddsExtractionRunRepository.save(new OddsExtractionRun()
                .setRawSnapshot(snapshot)
                .setExtractionStatus(OddsExtractionStatus.RUNNING)
                .setStartedAt(OffsetDateTime.now(clock)));

        try {
            ExtractionResult result = extractSnapshot(snapshot, recalculateExistingSelections);
            OddsExtractionStatus status = result.snapshotsImported() > 0
                    ? OddsExtractionStatus.SUCCESS
                    : OddsExtractionStatus.SKIPPED;
            String failureReason = status == OddsExtractionStatus.SKIPPED
                    ? result.message()
                    : null;
            run.finish(
                    OffsetDateTime.now(clock),
                    status,
                    result.rowsSeen(),
                    result.rowsAccepted(),
                    result.snapshotsImported(),
                    result.selectionsUpdated(),
                    result.validationErrors(),
                    truncate(failureReason, 1000)
            );
            return OddsExtractionRunResponse.from(oddsExtractionRunRepository.save(run), false);
        } catch (Exception exception) {
            run.finish(
                    OffsetDateTime.now(clock),
                    OddsExtractionStatus.FAILED,
                    run.getRowsSeen(),
                    run.getRowsAccepted(),
                    run.getSnapshotsImported(),
                    run.getSelectionsUpdated(),
                    run.getValidationErrorCount(),
                    truncate(exception.getMessage(), 1000)
            );
            return OddsExtractionRunResponse.from(oddsExtractionRunRepository.save(run), false);
        }
    }

    @Override
    @Transactional
    public DailyOddsExtractionResponse extractDailyOddsSnapshots(DailyOddsExtractionRequest request) {
        OffsetDateTime triggeredAt = OffsetDateTime.now(clock);
        LocalDate snapshotDate = request.snapshotDate() == null ? LocalDate.now(clock) : request.snapshotDate();
        boolean forceReprocess = Boolean.TRUE.equals(request.forceReprocess());
        boolean recalculateExistingSelections = request.recalculateExistingSelections() == null
                || request.recalculateExistingSelections();
        List<League> leagues = resolveLeagues(request.leagueCodes());
        List<OddsExtractionRunResponse> runs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (League league : leagues) {
            List<RawSnapshot> snapshots = rawSnapshotRepository.findByLeagueCodeDateStatusAndSourceType(
                    league.getCode(),
                    snapshotDate,
                    ScrapeStatus.SUCCESS,
                    SourceType.ODDS_REFERENCE
            );
            snapshots = latestApiSnapshotsBySourceTarget(snapshots);
            if (snapshots.isEmpty()) {
                warnings.add("No successful API ODDS_REFERENCE raw snapshots exist for " + league.getCode()
                        + " on " + snapshotDate + ".");
                continue;
            }
            for (RawSnapshot snapshot : snapshots) {
                runs.add(extractRawSnapshot(snapshot.getId(), forceReprocess, recalculateExistingSelections));
            }
        }

        return new DailyOddsExtractionResponse(UUID.randomUUID(), triggeredAt, List.copyOf(runs), List.copyOf(warnings));
    }

    private boolean isTheOddsApiSnapshot(RawSnapshot snapshot) {
        SourceTarget sourceTarget = snapshot.getSourceTarget();
        if (sourceTarget == null) {
            return false;
        }
        String name = sourceTarget.getName() == null ? "" : sourceTarget.getName();
        String url = sourceTarget.getUrlTemplate() == null ? "" : sourceTarget.getUrlTemplate();
        String selectors = sourceTarget.getSelectorsJson() == null ? "" : sourceTarget.getSelectorsJson();
        return name.contains("The Odds API")
                || url.contains("{theOddsApiBaseUrl}")
                || url.contains("the-odds-api.com")
                || selectors.toLowerCase(Locale.ROOT).contains("the-odds-api");
    }

    private boolean isSharpApiSnapshot(RawSnapshot snapshot) {
        SourceTarget sourceTarget = snapshot.getSourceTarget();
        if (sourceTarget == null) {
            return false;
        }
        String name = sourceTarget.getName() == null ? "" : sourceTarget.getName();
        String url = sourceTarget.getUrlTemplate() == null ? "" : sourceTarget.getUrlTemplate();
        String selectors = sourceTarget.getSelectorsJson() == null ? "" : sourceTarget.getSelectorsJson();
        return name.contains("SharpAPI")
                || url.contains("{sharpApiBaseUrl}")
                || url.contains("sharpapi.io")
                || selectors.toLowerCase(Locale.ROOT).contains("sharpapi");
    }

    private List<RawSnapshot> latestApiSnapshotsBySourceTarget(List<RawSnapshot> snapshots) {
        Map<UUID, RawSnapshot> latest = new LinkedHashMap<>();
        for (RawSnapshot snapshot : snapshots) {
            if (!(isTheOddsApiSnapshot(snapshot) || isSharpApiSnapshot(snapshot)) || snapshot.getSourceTarget() == null || snapshot.getSourceTarget().getId() == null) {
                continue;
            }
            UUID sourceTargetId = snapshot.getSourceTarget().getId();
            latest.merge(sourceTargetId, snapshot, this::newerSnapshot);
        }
        return latest.values().stream()
                .sorted(Comparator.comparing(snapshot -> snapshot.getSourceTarget().getName()))
                .toList();
    }

    private RawSnapshot newerSnapshot(RawSnapshot left, RawSnapshot right) {
        return snapshotTimestamp(right).isAfter(snapshotTimestamp(left)) ? right : left;
    }

    private OffsetDateTime snapshotTimestamp(RawSnapshot snapshot) {
        if (snapshot.getFetchedAt() != null) {
            return snapshot.getFetchedAt();
        }
        if (snapshot.getCreatedAt() != null) {
            return snapshot.getCreatedAt();
        }
        return OffsetDateTime.MIN;
    }

    private ExtractionResult extractSnapshot(RawSnapshot snapshot, boolean recalculateExistingSelections) throws IOException {
        if (snapshot.getScrapeStatus() != ScrapeStatus.SUCCESS) {
            return ExtractionResult.skipped("Snapshot scrape status is " + snapshot.getScrapeStatus() + ".");
        }
        if (snapshot.getSourceTarget().getSourceType() != SourceType.ODDS_REFERENCE) {
            return ExtractionResult.skipped("Snapshot source type is " + snapshot.getSourceTarget().getSourceType()
                    + ", not ODDS_REFERENCE.");
        }
        if (!isTheOddsApiSnapshot(snapshot) && !isSharpApiSnapshot(snapshot)) {
            return ExtractionResult.skipped("Only supported API odds snapshots are processed by the active odds pipeline.");
        }
        if (!StringUtils.hasText(snapshot.getRawPayload())) {
            throw new InvalidRequestException("Raw odds snapshot payload is empty.");
        }

        OddsCsvOptions options = options(snapshot);
        if (options.footballDataWorldCupWorkbook()) {
            return extractFootballDataWorldCupWorkbookOdds(snapshot, options, recalculateExistingSelections);
        }
        if (options.sgoddsOpeningOdds()) {
            return extractSgoddsOpeningOdds(snapshot, options, recalculateExistingSelections);
        }
        if (FORMAT_THE_ODDS_API_V4_JSON.equalsIgnoreCase(options.format())) {
            return extractTheOddsApiV4Json(snapshot, options, recalculateExistingSelections);
        }
        if (FORMAT_SHARPAPI_JSON.equalsIgnoreCase(options.format())) {
            return extractSharpApiJson(snapshot, options, recalculateExistingSelections);
        }
        String payload = stripBom(SnapshotPayloads.text(snapshot.getRawPayload()));
        if (options.footballDataWideFormat()) {
            return extractFootballDataWideOdds(snapshot, payload, options, recalculateExistingSelections);
        }
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .get();
        List<OddsImportItem> importItems = new ArrayList<>();
        int rowsSeen = 0;
        int validationErrors = 0;

        try (var parser = csvFormat.parse(new StringReader(payload))) {
            for (CSVRecord record : parser) {
                if (isBlankRecord(record)) {
                    continue;
                }
                rowsSeen++;
                try {
                    importItems.add(toImportItem(snapshot, record, options));
                } catch (RuntimeException exception) {
                    validationErrors++;
                }
            }
        }

        if (importItems.isEmpty()) {
            return new ExtractionResult(rowsSeen, 0, 0, 0, validationErrors, "No valid odds rows were found.");
        }

        OddsImportResponse importResponse = oddsImportService.importOdds(new OddsImportRequest(
                List.copyOf(importItems),
                recalculateExistingSelections
        ));
        return new ExtractionResult(
                rowsSeen,
                importResponse.snapshotsImported(),
                importResponse.snapshotsImported(),
                importResponse.selectionsUpdated(),
                validationErrors + importResponse.rejected(),
                importResponse.snapshotsImported() == 0 ? "All parsed odds rows were rejected." : null
        );
    }

    private ExtractionResult extractTheOddsApiV4Json(
            RawSnapshot snapshot,
            OddsCsvOptions options,
            boolean recalculateExistingSelections
    ) throws IOException {
        JsonNode events = objectMapper.readTree(SnapshotPayloads.text(snapshot.getRawPayload()));
        if (!events.isArray()) {
            throw new InvalidRequestException("The Odds API payload must be a JSON array.");
        }

        List<OddsImportItem> importItems = new ArrayList<>();
        int rowsSeen = 0;
        int validationErrors = 0;

        for (JsonNode event : events) {
            rowsSeen++;
            try {
                importItems.addAll(toTheOddsApiImportItems(snapshot, event, options));
            } catch (RuntimeException exception) {
                validationErrors++;
            }
        }

        if (importItems.isEmpty()) {
            return new ExtractionResult(rowsSeen, 0, 0, 0, validationErrors, "No valid The Odds API rows were found.");
        }

        OddsImportResponse importResponse = oddsImportService.importOdds(new OddsImportRequest(
                List.copyOf(importItems),
                recalculateExistingSelections
        ));
        return new ExtractionResult(
                rowsSeen,
                importResponse.snapshotsImported(),
                importResponse.snapshotsImported(),
                importResponse.selectionsUpdated(),
                validationErrors + importResponse.rejected(),
                importResponse.snapshotsImported() == 0 ? "All The Odds API odds rows were rejected." : null
        );
    }

    private ExtractionResult extractSharpApiJson(
            RawSnapshot snapshot,
            OddsCsvOptions options,
            boolean recalculateExistingSelections
    ) throws IOException {
        JsonNode root = objectMapper.readTree(SnapshotPayloads.text(snapshot.getRawPayload()));
        JsonNode dataNode = root.has("data") ? root.get("data") : root;
        JsonNode eventsNode = root.has("events") ? root.get("events") : objectMapper.createArrayNode();
        if (!dataNode.isArray()) {
            throw new InvalidRequestException("SharpAPI payload data must be a JSON array.");
        }

        Map<String, JsonNode> eventMap = new HashMap<>();
        if (eventsNode.isArray()) {
            for (JsonNode ev : eventsNode) {
                eventMap.put(ev.path("id").asText(""), ev);
            }
        }

        List<OddsImportItem> importItems = new ArrayList<>();
        int rowsSeen = 0;
        int validationErrors = 0;

        for (JsonNode odd : dataNode) {
            rowsSeen++;
            try {
                importItems.addAll(toSharpApiImportItems(snapshot, odd, eventMap, options));
            } catch (RuntimeException exception) {
                validationErrors++;
            }
        }

        if (importItems.isEmpty()) {
            return new ExtractionResult(rowsSeen, 0, 0, 0, validationErrors, "No valid SharpAPI rows were found.");
        }

        OddsImportResponse importResponse = oddsImportService.importOdds(new OddsImportRequest(
                List.copyOf(importItems),
                recalculateExistingSelections
        ));
        return new ExtractionResult(
                rowsSeen,
                importResponse.snapshotsImported(),
                importResponse.snapshotsImported(),
                importResponse.selectionsUpdated(),
                validationErrors + importResponse.rejected(),
                importResponse.snapshotsImported() == 0 ? "All SharpAPI odds rows were rejected." : null
        );
    }

    private List<OddsImportItem> toTheOddsApiImportItems(
            RawSnapshot snapshot,
            JsonNode event,
            OddsCsvOptions options
    ) {
        String eventId = event.path("id").asText("");
        String homeTeam = required(event.path("home_team").asText(null), "home_team");
        String awayTeam = required(event.path("away_team").asText(null), "away_team");
        OffsetDateTime commenceAt = parseOffsetDateTime(required(event.path("commence_time").asText(null), "commence_time"))
                .orElseThrow();
        LocalDate matchDate = commenceAt.toLocalDate();
        List<OddsImportItem> items = new ArrayList<>();

        for (JsonNode bookmaker : event.path("bookmakers")) {
            String bookmakerCode = required(bookmaker.path("key").asText(null), "bookmaker.key");
            String bookmakerName = bookmaker.path("title").asText(bookmakerCode);
            OffsetDateTime capturedAt = parseOffsetDateTime(bookmaker.path("last_update").asText(null))
                    .orElse(snapshot.getFetchedAt() == null ? OffsetDateTime.now(clock) : snapshot.getFetchedAt());
            for (JsonNode market : bookmaker.path("markets")) {
                String marketKey = market.path("key").asText("");
                if ("h2h".equalsIgnoreCase(marketKey) && options.includeOneXTwo()) {
                    addTheOddsApiH2hItems(items, snapshot, market, eventId, matchDate, homeTeam, awayTeam,
                            capturedAt, bookmakerCode, bookmakerName);
                }
                if ("totals".equalsIgnoreCase(marketKey) && options.includeOverUnder25()) {
                    addTheOddsApiTotalsItems(items, snapshot, market, eventId, matchDate, homeTeam, awayTeam,
                            capturedAt, bookmakerCode, bookmakerName);
                }
            }
        }

        return items;
    }

    private void addTheOddsApiH2hItems(
            List<OddsImportItem> items,
            RawSnapshot snapshot,
            JsonNode market,
            String eventId,
            LocalDate matchDate,
            String homeTeam,
            String awayTeam,
            OffsetDateTime capturedAt,
            String bookmakerCode,
            String bookmakerName
    ) {
        for (JsonNode outcome : market.path("outcomes")) {
            String name = outcome.path("name").asText("");
            Optional<BigDecimal> price = jsonDecimal(outcome.path("price"));
            if (price.isEmpty()) {
                continue;
            }
            MarketCode marketCode = theOddsApiH2hMarketCode(name, homeTeam, awayTeam);
            if (marketCode == null) {
                continue;
            }
            addTheOddsApiItem(items, snapshot, eventId, matchDate, homeTeam, awayTeam,
                    marketCode, bookmakerCode, bookmakerName, price.get(), capturedAt, "h2h:" + name);
        }
    }

    private void addTheOddsApiTotalsItems(
            List<OddsImportItem> items,
            RawSnapshot snapshot,
            JsonNode market,
            String eventId,
            LocalDate matchDate,
            String homeTeam,
            String awayTeam,
            OffsetDateTime capturedAt,
            String bookmakerCode,
            String bookmakerName
    ) {
        for (JsonNode outcome : market.path("outcomes")) {
            Optional<BigDecimal> point = jsonDecimal(outcome.path("point"));
            Optional<BigDecimal> price = jsonDecimal(outcome.path("price"));
            if (point.isEmpty() || price.isEmpty() || point.get().compareTo(new BigDecimal("2.5")) != 0) {
                continue;
            }
            String name = outcome.path("name").asText("");
            MarketCode marketCode = switch (normalizeKey(name)) {
                case "over" -> MarketCode.OVER_2_5_GOALS;
                case "under" -> MarketCode.UNDER_2_5_GOALS;
                default -> null;
            };
            if (marketCode == null) {
                continue;
            }
            addTheOddsApiItem(items, snapshot, eventId, matchDate, homeTeam, awayTeam,
                    marketCode, bookmakerCode, bookmakerName, price.get(), capturedAt, "totals:" + name + ":" + point.get());
        }
    }

    private MarketCode theOddsApiH2hMarketCode(String outcomeName, String homeTeam, String awayTeam) {
        String normalizedOutcome = normalizeKey(outcomeName);
        if ("draw".equals(normalizedOutcome) || "tie".equals(normalizedOutcome)) {
            return MarketCode.DRAW;
        }
        if (normalizedOutcome.equals(normalizeKey(homeTeam))) {
            return MarketCode.HOME_WIN;
        }
        if (normalizedOutcome.equals(normalizeKey(awayTeam))) {
            return MarketCode.AWAY_WIN;
        }
        return null;
    }

    private List<OddsImportItem> toSharpApiImportItems(
            RawSnapshot snapshot,
            JsonNode odd,
            Map<String, JsonNode> eventMap,
            OddsCsvOptions options
    ) {
        String eventId = odd.path("event_id").asText("");
        JsonNode eventNode = eventMap.get(eventId);

        String homeTeam = required(firstText(eventNode, odd, "home_team"), "home_team");
        String awayTeam = required(firstText(eventNode, odd, "away_team"), "away_team");
        OffsetDateTime commenceAt = parseOffsetDateTime(required(
                        firstText(eventNode, odd, "start_time", "event_start_time"),
                        "start_time"
                ))
                .orElseThrow();
        LocalDate matchDate = commenceAt.toLocalDate();
        String marketType = odd.path("market_type").asText("");
        String selection = odd.path("selection").asText("");
        String selectionType = odd.path("selection_type").asText("");
        String bookmakerCode = odd.path("sportsbook").asText("");
        String bookmakerName = firstText(odd.path("sportsbook_ref"), odd, "label", "sportsbook");

        Optional<BigDecimal> decimalOdds = jsonDecimal(odd.path("odds").path("decimal"))
                .or(() -> jsonDecimal(odd.path("odds_decimal")));
        if (decimalOdds.isEmpty()) {
            return List.of();
        }

        OffsetDateTime capturedAt = parseOffsetDateTime(odd.path("timestamp").asText(null))
                .orElse(snapshot.getFetchedAt() == null ? OffsetDateTime.now(clock) : snapshot.getFetchedAt());

        List<OddsImportItem> items = new ArrayList<>();

        if ("moneyline".equalsIgnoreCase(marketType) && options.includeOneXTwo()) {
            MarketCode marketCode = null;
            if ("home".equalsIgnoreCase(selectionType)) {
                marketCode = MarketCode.HOME_WIN;
            } else if ("away".equalsIgnoreCase(selectionType)) {
                marketCode = MarketCode.AWAY_WIN;
            } else if ("draw".equalsIgnoreCase(selectionType) || "tie".equalsIgnoreCase(selectionType)) {
                marketCode = MarketCode.DRAW;
            }
            if (marketCode != null) {
                addSharpApiItem(items, snapshot, eventId, matchDate, homeTeam, awayTeam, marketCode,
                        bookmakerCode, bookmakerName, decimalOdds.get(), capturedAt, "moneyline:" + selectionType);
            }
        }

        if ("total_points".equalsIgnoreCase(marketType) || "total_goals".equalsIgnoreCase(marketType)) {
            if (options.includeOverUnder25()) {
                Optional<BigDecimal> line = jsonDecimal(odd.path("line"));
                if (line.isPresent() && line.get().compareTo(new BigDecimal("2.5")) == 0) {
                    MarketCode marketCode = null;
                    if ("over".equalsIgnoreCase(selectionType)) {
                        marketCode = MarketCode.OVER_2_5_GOALS;
                    } else if ("under".equalsIgnoreCase(selectionType)) {
                        marketCode = MarketCode.UNDER_2_5_GOALS;
                    }
                    if (marketCode != null) {
                        addSharpApiItem(items, snapshot, eventId, matchDate, homeTeam, awayTeam, marketCode,
                                bookmakerCode, bookmakerName, decimalOdds.get(), capturedAt, "total:2.5:" + selectionType);
                    }
                }
            }
        }

        return items;
    }

    private void addSharpApiItem(
            List<OddsImportItem> items,
            RawSnapshot snapshot,
            String eventId,
            LocalDate matchDate,
            String homeTeam,
            String awayTeam,
            MarketCode marketCode,
            String bookmakerCode,
            String bookmakerName,
            BigDecimal decimalOdds,
            OffsetDateTime capturedAt,
            String referenceSuffix
    ) {
        items.add(new OddsImportItem(
                null,
                snapshot.getLeague().getCode(),
                matchDate,
                homeTeam,
                awayTeam,
                marketCode,
                bookmakerCode,
                bookmakerName,
                decimalOdds,
                capturedAt,
                snapshot.getSourceTarget().getName(),
                snapshot.getSourceUrl(),
                "rawSnapshot=" + snapshot.getId() + "; event=" + eventId + "; market=" + referenceSuffix
        ));
    }

    private String firstText(JsonNode primary, JsonNode fallback, String... fields) {
        for (String field : fields) {
            if (primary != null) {
                String value = primary.path(field).asText(null);
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
            if (fallback != null) {
                String value = fallback.path(field).asText(null);
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private void addTheOddsApiItem(
            List<OddsImportItem> items,
            RawSnapshot snapshot,
            String eventId,
            LocalDate matchDate,
            String homeTeam,
            String awayTeam,
            MarketCode marketCode,
            String bookmakerCode,
            String bookmakerName,
            BigDecimal decimalOdds,
            OffsetDateTime capturedAt,
            String referenceSuffix
    ) {
        items.add(new OddsImportItem(
                null,
                snapshot.getLeague().getCode(),
                matchDate,
                homeTeam,
                awayTeam,
                marketCode,
                bookmakerCode,
                bookmakerName,
                decimalOdds,
                capturedAt,
                snapshot.getSourceTarget().getName(),
                snapshot.getSourceUrl(),
                "rawSnapshot=" + snapshot.getId() + "; event=" + eventId + "; market=" + referenceSuffix
        ));
    }

    private OddsImportItem toImportItem(RawSnapshot snapshot, CSVRecord record, OddsCsvOptions options) {
        UUID matchId = parseUuid(value(record, options.matchId())).orElse(null);
        LeagueCode leagueCode = parseLeagueCode(value(record, options.leagueCode())).orElse(snapshot.getLeague().getCode());
        LocalDate matchDate = parseDate(value(record, options.matchDate())).orElse(null);
        String homeTeam = value(record, options.homeTeam());
        String awayTeam = value(record, options.awayTeam());
        MarketCode marketCode = parseMarketCode(value(record, options.marketCode()))
                .orElseThrow(() -> new InvalidRequestException("marketCode is required and must be supported."));
        BigDecimal decimalOdds = parseDecimal(value(record, options.decimalOdds()))
                .orElseThrow(() -> new InvalidRequestException("decimalOdds is required and must be numeric."));
        OffsetDateTime capturedAt = parseOffsetDateTime(value(record, options.capturedAt())).orElse(snapshot.getFetchedAt());

        return new OddsImportItem(
                matchId,
                matchId == null ? leagueCode : null,
                matchId == null ? matchDate : null,
                matchId == null ? homeTeam : null,
                matchId == null ? awayTeam : null,
                marketCode,
                required(value(record, options.bookmakerCode()), "bookmakerCode"),
                value(record, options.bookmakerName()),
                decimalOdds,
                capturedAt,
                StringUtils.hasText(value(record, options.sourceName()))
                        ? value(record, options.sourceName())
                        : snapshot.getSourceTarget().getName(),
                StringUtils.hasText(value(record, options.sourceUrl()))
                        ? value(record, options.sourceUrl())
                        : snapshot.getSourceUrl(),
                StringUtils.hasText(value(record, options.rawPayloadReference()))
                        ? value(record, options.rawPayloadReference())
                        : "rawSnapshot=" + snapshot.getId()
        );
    }

    private ExtractionResult extractSgoddsOpeningOdds(
            RawSnapshot snapshot,
            OddsCsvOptions options,
            boolean recalculateExistingSelections
    ) throws IOException {
        String csv = stripBom(csvText(snapshot.getRawPayload()));
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .get();
        List<OddsImportItem> importItems = new ArrayList<>();
        int rowsSeen = 0;
        int validationErrors = 0;

        try (var parser = csvFormat.parse(new StringReader(csv))) {
            for (CSVRecord record : parser) {
                if (isBlankRecord(record)) {
                    continue;
                }
                if (StringUtils.hasText(options.leagueName())
                        && !options.leagueName().equalsIgnoreCase(value(record, "League"))) {
                    continue;
                }
                rowsSeen++;
                try {
                    importItems.addAll(toSgoddsImportItems(snapshot, record, options));
                } catch (RuntimeException exception) {
                    validationErrors++;
                }
            }
        }

        if (importItems.isEmpty()) {
            return new ExtractionResult(rowsSeen, 0, 0, 0, validationErrors, "No valid Sgodds odds rows were found.");
        }

        OddsImportResponse importResponse = oddsImportService.importOdds(new OddsImportRequest(
                List.copyOf(importItems),
                recalculateExistingSelections
        ));
        return new ExtractionResult(
                rowsSeen,
                importResponse.snapshotsImported(),
                importResponse.snapshotsImported(),
                importResponse.selectionsUpdated(),
                validationErrors + importResponse.rejected(),
                importResponse.snapshotsImported() == 0 ? "All Sgodds odds rows were rejected." : null
        );
    }

    private List<OddsImportItem> toSgoddsImportItems(RawSnapshot snapshot, CSVRecord record, OddsCsvOptions options) {
        TeamNames teams = splitSgoddsMatch(required(value(record, "Match"), "Match"));
        LocalDate matchDate = parseSgoddsStartTime(required(value(record, "Start Time"), "Start Time")).toLocalDate();
        OffsetDateTime capturedAt = snapshot.getFetchedAt() == null ? OffsetDateTime.now(clock) : snapshot.getFetchedAt();
        String bookmakerCode = StringUtils.hasText(options.defaultBookmakerCode()) ? options.defaultBookmakerCode() : "SGODDS";
        String bookmakerName = StringUtils.hasText(options.defaultBookmakerName()) ? options.defaultBookmakerName() : "Sgodds Opening Odds";
        List<OddsImportItem> items = new ArrayList<>();

        if (options.includeOneXTwo()) {
            addSgoddsItem(items, snapshot, record, matchDate, teams, capturedAt, bookmakerCode, bookmakerName,
                    MarketCode.HOME_WIN, "Ft1X2_01");
            addSgoddsItem(items, snapshot, record, matchDate, teams, capturedAt, bookmakerCode, bookmakerName,
                    MarketCode.DRAW, "Ft1X2_02");
            addSgoddsItem(items, snapshot, record, matchDate, teams, capturedAt, bookmakerCode, bookmakerName,
                    MarketCode.AWAY_WIN, "Ft1X2_03");
        }

        if (options.includeOverUnder25() && isTwoPointFive(value(record, "Ou_hcap"))) {
            addSgoddsItem(items, snapshot, record, matchDate, teams, capturedAt, bookmakerCode, bookmakerName,
                    MarketCode.OVER_2_5_GOALS, "Ou_01");
            addSgoddsItem(items, snapshot, record, matchDate, teams, capturedAt, bookmakerCode, bookmakerName,
                    MarketCode.UNDER_2_5_GOALS, "Ou_02");
        }

        return items;
    }

    private void addSgoddsItem(
            List<OddsImportItem> items,
            RawSnapshot snapshot,
            CSVRecord record,
            LocalDate matchDate,
            TeamNames teams,
            OffsetDateTime capturedAt,
            String bookmakerCode,
            String bookmakerName,
            MarketCode marketCode,
            String oddsColumn
    ) {
        Optional<BigDecimal> decimalOdds = parseDecimal(value(record, oddsColumn));
        if (decimalOdds.isEmpty()) {
            return;
        }
        items.add(new OddsImportItem(
                null,
                snapshot.getLeague().getCode(),
                matchDate,
                teams.homeTeam(),
                teams.awayTeam(),
                marketCode,
                bookmakerCode,
                bookmakerName,
                decimalOdds.get(),
                capturedAt,
                snapshot.getSourceTarget().getName(),
                snapshot.getSourceUrl(),
                "rawSnapshot=" + snapshot.getId() + "; rowId=" + value(record, "ID") + "; column=" + oddsColumn
        ));
    }

    private TeamNames splitSgoddsMatch(String matchName) {
        String[] teams = matchName.split("\\s+vs\\s+", 2);
        if (teams.length != 2 || !StringUtils.hasText(teams[0]) || !StringUtils.hasText(teams[1])) {
            throw new InvalidRequestException("Sgodds Match must use 'Home vs Away' format.");
        }
        return new TeamNames(teams[0].trim(), teams[1].trim());
    }

    private LocalDateTime parseSgoddsStartTime(String rawStartTime) {
        try {
            return LocalDateTime.parse(rawStartTime.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException exception) {
            throw new InvalidRequestException("Sgodds Start Time has an unsupported format: " + rawStartTime + ".");
        }
    }

    private boolean isTwoPointFive(String value) {
        return parseDecimal(value)
                .map(decimal -> decimal.compareTo(new BigDecimal("2.5")) == 0)
                .orElse(false);
    }

    private ExtractionResult extractFootballDataWideOdds(
            RawSnapshot snapshot,
            String payload,
            OddsCsvOptions options,
            boolean recalculateExistingSelections
    ) throws IOException {
        String csv = stripBom(payload);
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .get();
        List<OddsImportItem> importItems = new ArrayList<>();
        int rowsSeen = 0;
        int validationErrors = 0;

        try (var parser = csvFormat.parse(new StringReader(csv))) {
            for (CSVRecord record : parser) {
                if (isBlankRecord(record)) {
                    continue;
                }
                if (!matchesFootballDataFilters(record, options)) {
                    continue;
                }
                rowsSeen++;
                try {
                    importItems.addAll(toFootballDataWideImportItems(snapshot, record, options));
                } catch (RuntimeException exception) {
                    validationErrors++;
                }
            }
        }

        if (importItems.isEmpty()) {
            return new ExtractionResult(rowsSeen, 0, 0, 0, validationErrors, "No valid Football-Data odds rows were found.");
        }

        OddsImportResponse importResponse = oddsImportService.importOdds(new OddsImportRequest(
                List.copyOf(importItems),
                recalculateExistingSelections
        ));
        return new ExtractionResult(
                rowsSeen,
                importResponse.snapshotsImported(),
                importResponse.snapshotsImported(),
                importResponse.selectionsUpdated(),
                validationErrors + importResponse.rejected(),
                importResponse.snapshotsImported() == 0 ? "All Football-Data odds rows were rejected." : null
        );
    }

    private ExtractionResult extractFootballDataWorldCupWorkbookOdds(
            RawSnapshot snapshot,
            OddsCsvOptions options,
            boolean recalculateExistingSelections
    ) throws IOException {
        List<OddsImportItem> importItems = new ArrayList<>();
        int rowsSeen = 0;
        int validationErrors = 0;
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
                    rowsSeen++;
                    try {
                        importItems.addAll(toFootballDataWorldCupWorkbookImportItems(snapshot, row, headers, formatter, options));
                    } catch (RuntimeException exception) {
                        validationErrors++;
                    }
                }
            }
        }

        if (importItems.isEmpty()) {
            return new ExtractionResult(rowsSeen, 0, 0, 0, validationErrors, "No valid Football-Data World Cup workbook odds rows were found.");
        }

        OddsImportResponse importResponse = oddsImportService.importOdds(new OddsImportRequest(
                List.copyOf(importItems),
                recalculateExistingSelections
        ));
        return new ExtractionResult(
                rowsSeen,
                importResponse.snapshotsImported(),
                importResponse.snapshotsImported(),
                importResponse.selectionsUpdated(),
                validationErrors + importResponse.rejected(),
                importResponse.snapshotsImported() == 0 ? "All Football-Data World Cup workbook odds rows were rejected." : null
        );
    }

    private List<OddsImportItem> toFootballDataWorldCupWorkbookImportItems(
            RawSnapshot snapshot,
            Row row,
            Map<String, Integer> headers,
            DataFormatter formatter,
            OddsCsvOptions options
    ) {
        Optional<LocalDate> matchDate = workbookDate(row, headers, formatter, "Date");
        String homeTeam = workbookText(row, headers, formatter, "Home");
        String awayTeam = workbookText(row, headers, formatter, "Away");
        if (matchDate.isEmpty() || !StringUtils.hasText(homeTeam) || !StringUtils.hasText(awayTeam)) {
            return List.of();
        }

        OffsetDateTime capturedAt = snapshot.getFetchedAt() == null ? OffsetDateTime.now(clock) : snapshot.getFetchedAt();
        List<OddsImportItem> items = new ArrayList<>();
        footballDataWorldCupOneXTwoColumns(options).forEach((bookmakerCode, columns) -> {
            addWorldCupWorkbookItem(items, snapshot, row, headers, formatter, matchDate.get(), homeTeam, awayTeam, capturedAt,
                    MarketCode.HOME_WIN, bookmakerCode, columns.home());
            addWorldCupWorkbookItem(items, snapshot, row, headers, formatter, matchDate.get(), homeTeam, awayTeam, capturedAt,
                    MarketCode.DRAW, bookmakerCode, columns.draw());
            addWorldCupWorkbookItem(items, snapshot, row, headers, formatter, matchDate.get(), homeTeam, awayTeam, capturedAt,
                    MarketCode.AWAY_WIN, bookmakerCode, columns.away());
        });
        return items;
    }

    private void addWorldCupWorkbookItem(
            List<OddsImportItem> items,
            RawSnapshot snapshot,
            Row row,
            Map<String, Integer> headers,
            DataFormatter formatter,
            LocalDate matchDate,
            String homeTeam,
            String awayTeam,
            OffsetDateTime capturedAt,
            MarketCode marketCode,
            String bookmakerCode,
            String oddsColumn
    ) {
        Optional<BigDecimal> decimalOdds = workbookDecimal(row, headers, formatter, oddsColumn.split(","));
        if (decimalOdds.isEmpty()) {
            return;
        }
        items.add(new OddsImportItem(
                null,
                snapshot.getLeague().getCode(),
                matchDate,
                homeTeam,
                awayTeam,
                marketCode,
                bookmakerCode,
                bookmakerDisplayName(bookmakerCode),
                decimalOdds.get(),
                capturedAt,
                snapshot.getSourceTarget().getName(),
                snapshot.getSourceUrl(),
                "rawSnapshot=" + snapshot.getId() + "; column=" + oddsColumn
        ));
    }

    private Map<String, OneXTwoColumns> footballDataWorldCupOneXTwoColumns(OddsCsvOptions options) {
        Map<String, OneXTwoColumns> columns = new LinkedHashMap<>();
        String[] bookmakerPrefixes = StringUtils.hasText(options.bookmakerPrefixes())
                ? options.bookmakerPrefixes().split(",")
                : new String[]{"B365", "PINNY", "BETFAIR_EXCH", "MAX", "AVG"};
        for (String rawPrefix : bookmakerPrefixes) {
            String prefix = rawPrefix.trim();
            if (!StringUtils.hasText(prefix)) {
                continue;
            }
            switch (prefix.toUpperCase(Locale.ROOT)) {
                case "B365", "BET365" -> columns.put("B365", new OneXTwoColumns("bet365-H", "bet365-D", "bet365-A"));
                case "PINNY", "PINNACLE" -> columns.put("PINNY", new OneXTwoColumns("Pinny-H", "Pinny-D", "Pinny-A"));
                case "BETFAIR_EXCH", "BFE" -> columns.put("BFE", new OneXTwoColumns("Betfair_Exch-H", "Betfair_Exch-D", "Betfair_Exch-A"));
                case "MAX" -> columns.put("MAX", new OneXTwoColumns("H-Max,H_Max", "D-Max,D_Max", "A-Max,A_Max"));
                case "AVG" -> columns.put("AVG", new OneXTwoColumns("H-Avg,H_Avg", "D-Avg,D_Avg", "A-Avg,A_Avg"));
                default -> columns.put(prefix.toUpperCase(Locale.ROOT), new OneXTwoColumns(prefix + "-H", prefix + "-D", prefix + "-A"));
            }
        }
        return columns;
    }

    private boolean matchesFootballDataFilters(CSVRecord record, OddsCsvOptions options) {
        if (StringUtils.hasText(options.divisionCode())) {
            return options.divisionCode().equalsIgnoreCase(value(record, "Div"));
        }
        if (StringUtils.hasText(options.country()) && !options.country().equalsIgnoreCase(value(record, "Country"))) {
            return false;
        }
        if (StringUtils.hasText(options.leagueName()) && !options.leagueName().equalsIgnoreCase(value(record, "League"))) {
            return false;
        }
        return !StringUtils.hasText(options.season()) || options.season().equalsIgnoreCase(value(record, "Season"));
    }

    private List<OddsImportItem> toFootballDataWideImportItems(RawSnapshot snapshot, CSVRecord record, OddsCsvOptions options) {
        String dateColumn = StringUtils.hasText(options.matchDate()) ? options.matchDate() : "Date";
        String homeColumn = StringUtils.hasText(options.homeTeam()) ? options.homeTeam() : "HomeTeam";
        String awayColumn = StringUtils.hasText(options.awayTeam()) ? options.awayTeam() : "AwayTeam";
        LocalDate matchDate = parseDate(required(value(record, dateColumn), dateColumn)).orElseThrow();
        String homeTeam = required(value(record, homeColumn), homeColumn);
        String awayTeam = required(value(record, awayColumn), awayColumn);
        OffsetDateTime capturedAt = snapshot.getFetchedAt() == null ? OffsetDateTime.now(clock) : snapshot.getFetchedAt();
        List<OddsImportItem> items = new ArrayList<>();

        footballDataOneXTwoColumns(options).forEach((bookmakerCode, columns) -> {
            addFootballDataWideItem(items, snapshot, record, options, matchDate, homeTeam, awayTeam, capturedAt,
                    MarketCode.HOME_WIN, bookmakerCode, columns.home());
            addFootballDataWideItem(items, snapshot, record, options, matchDate, homeTeam, awayTeam, capturedAt,
                    MarketCode.DRAW, bookmakerCode, columns.draw());
            addFootballDataWideItem(items, snapshot, record, options, matchDate, homeTeam, awayTeam, capturedAt,
                    MarketCode.AWAY_WIN, bookmakerCode, columns.away());
        });

        footballDataOver25Columns(options).forEach((bookmakerCode, column) ->
                addFootballDataWideItem(items, snapshot, record, options, matchDate, homeTeam, awayTeam, capturedAt,
                        MarketCode.OVER_2_5_GOALS, bookmakerCode, column));
        footballDataUnder25Columns(options).forEach((bookmakerCode, column) ->
                addFootballDataWideItem(items, snapshot, record, options, matchDate, homeTeam, awayTeam, capturedAt,
                        MarketCode.UNDER_2_5_GOALS, bookmakerCode, column));

        return items;
    }

    private void addFootballDataWideItem(
            List<OddsImportItem> items,
            RawSnapshot snapshot,
            CSVRecord record,
            OddsCsvOptions options,
            LocalDate matchDate,
            String homeTeam,
            String awayTeam,
            OffsetDateTime capturedAt,
            MarketCode marketCode,
            String bookmakerCode,
            String oddsColumn
    ) {
        Optional<BigDecimal> decimalOdds = parseDecimal(value(record, oddsColumn));
        if (decimalOdds.isEmpty()) {
            return;
        }
        String sourceName = snapshot.getSourceTarget().getName();
        String reference = "rawSnapshot=" + snapshot.getId() + "; column=" + oddsColumn;
        items.add(new OddsImportItem(
                null,
                snapshot.getLeague().getCode(),
                matchDate,
                homeTeam,
                awayTeam,
                marketCode,
                bookmakerCode,
                bookmakerDisplayName(bookmakerCode),
                decimalOdds.get(),
                capturedAt,
                sourceName,
                snapshot.getSourceUrl(),
                reference
        ));
    }

    private Map<String, OneXTwoColumns> footballDataOneXTwoColumns(OddsCsvOptions options) {
        Map<String, OneXTwoColumns> columns = new LinkedHashMap<>();
        String[] bookmakerPrefixes = StringUtils.hasText(options.bookmakerPrefixes())
                ? options.bookmakerPrefixes().split(",")
                : new String[]{"B365", "PS", "Max", "Avg", "BFE"};
        for (String rawPrefix : bookmakerPrefixes) {
            String prefix = rawPrefix.trim();
            if (!StringUtils.hasText(prefix)) {
                continue;
            }
            if (options.useClosingOdds()) {
                columns.put(prefix.toUpperCase(Locale.ROOT), new OneXTwoColumns(prefix + "CH", prefix + "CD", prefix + "CA"));
            } else {
                columns.put(prefix.toUpperCase(Locale.ROOT), new OneXTwoColumns(prefix + "H", prefix + "D", prefix + "A"));
            }
        }
        return columns;
    }

    private Map<String, String> footballDataOver25Columns(OddsCsvOptions options) {
        Map<String, String> columns = new LinkedHashMap<>();
        if (!options.includeOver25()) {
            return columns;
        }
        String[] bookmakerPrefixes = StringUtils.hasText(options.over25Prefixes())
                ? options.over25Prefixes().split(",")
                : new String[]{"B365", "P", "Max", "Avg", "BFE"};
        for (String rawPrefix : bookmakerPrefixes) {
            String prefix = rawPrefix.trim();
            if (!StringUtils.hasText(prefix)) {
                continue;
            }
            String column = options.useClosingOdds() ? prefix + "C>2.5" : prefix + ">2.5";
            columns.put(prefix.toUpperCase(Locale.ROOT), column);
        }
        return columns;
    }

    private Map<String, String> footballDataUnder25Columns(OddsCsvOptions options) {
        Map<String, String> columns = new LinkedHashMap<>();
        if (!options.includeUnder25()) {
            return columns;
        }
        String[] bookmakerPrefixes = StringUtils.hasText(options.under25Prefixes())
                ? options.under25Prefixes().split(",")
                : new String[]{"B365", "P", "Max", "Avg", "BFE"};
        for (String rawPrefix : bookmakerPrefixes) {
            String prefix = rawPrefix.trim();
            if (!StringUtils.hasText(prefix)) {
                continue;
            }
            String column = options.useClosingOdds() ? prefix + "C<2.5" : prefix + "<2.5";
            columns.put(prefix.toUpperCase(Locale.ROOT), column);
        }
        return columns;
    }

    private String bookmakerDisplayName(String bookmakerCode) {
        return switch (bookmakerCode.toUpperCase(Locale.ROOT)) {
            case "B365" -> "Bet365";
            case "PS", "P", "PINNY" -> "Pinnacle";
            case "MAX" -> "Market Maximum";
            case "AVG" -> "Market Average";
            case "BFE" -> "Betfair Exchange";
            default -> bookmakerCode;
        };
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
        return parseDate(formatter.formatCellValue(cell));
    }

    private Optional<BigDecimal> workbookDecimal(Row row, Map<String, Integer> headers, DataFormatter formatter, String... headerNames) {
        Cell cell = workbookCell(row, headers, headerNames);
        if (cell == null) {
            return Optional.empty();
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return Optional.of(BigDecimal.valueOf(cell.getNumericCellValue()));
        }
        return parseDecimal(formatter.formatCellValue(cell));
    }

    private Cell workbookCell(Row row, Map<String, Integer> headers, String... headerNames) {
        for (String headerName : headerNames) {
            Integer index = headers.get(headerName.trim().toLowerCase(Locale.ROOT));
            if (index != null) {
                return row.getCell(index);
            }
        }
        return null;
    }

    private OddsCsvOptions options(RawSnapshot snapshot) {
        JsonNode root = parseSelectors(snapshot.getSourceTarget().getSelectorsJson());
        JsonNode columns = root.path("columns");
        String format = root.path("format").asText("");
        boolean footballDataWide = "football-data-latest-fixtures-odds-csv".equalsIgnoreCase(format)
                || "football-data-extra-fixtures-odds-csv".equalsIgnoreCase(format)
                || "football-data-historical-odds-csv".equalsIgnoreCase(format)
                || "football-data-extra-historical-odds-csv".equalsIgnoreCase(format);
        boolean footballDataWorldCupWorkbook = FORMAT_FOOTBALL_DATA_WORLD_CUP_WORKBOOK.equalsIgnoreCase(format);
        boolean footballDataExtra = "football-data-extra-fixtures-odds-csv".equalsIgnoreCase(format)
                || "football-data-extra-historical-odds-csv".equalsIgnoreCase(format);
        return new OddsCsvOptions(
                format,
                footballDataWide,
                footballDataWorldCupWorkbook,
                FORMAT_SGODDS_OPENING_ODDS_CSV.equalsIgnoreCase(format),
                root.path("divisionCode").asText(null),
                root.path("country").asText(null),
                root.path("leagueName").asText(null),
                root.path("season").asText(null),
                root.path("oddsColumnMode").asText("CURRENT"),
                root.path("bookmakerPrefixes").asText(null),
                root.path("over25Prefixes").asText(null),
                root.path("under25Prefixes").asText(null),
                root.path("includeOver25").isMissingNode() || root.path("includeOver25").asBoolean(true),
                root.path("includeUnder25").isMissingNode() || root.path("includeUnder25").asBoolean(true),
                root.path("includeOneXTwo").isMissingNode() || root.path("includeOneXTwo").asBoolean(true),
                root.path("includeOverUnder25").isMissingNode() || root.path("includeOverUnder25").asBoolean(true),
                root.path("bookmakerCode").asText(null),
                root.path("bookmakerName").asText(null),
                column(columns, "matchId", "MatchId"),
                column(columns, "leagueCode", "LeagueCode"),
                column(columns, "matchDate", footballDataWide ? "Date" : "MatchDate"),
                column(columns, "homeTeam", footballDataExtra ? "Home" : "HomeTeam"),
                column(columns, "awayTeam", footballDataExtra ? "Away" : "AwayTeam"),
                column(columns, "marketCode", "MarketCode"),
                column(columns, "bookmakerCode", "BookmakerCode"),
                column(columns, "bookmakerName", "BookmakerName"),
                column(columns, "decimalOdds", "DecimalOdds"),
                column(columns, "capturedAt", "CapturedAt"),
                column(columns, "sourceName", "SourceName"),
                column(columns, "sourceUrl", "SourceUrl"),
                column(columns, "rawPayloadReference", "RawPayloadReference")
        );
    }

    private JsonNode parseSelectors(String selectorsJson) {
        if (!StringUtils.hasText(selectorsJson)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(selectorsJson);
        } catch (IOException exception) {
            throw new InvalidRequestException("ODDS_REFERENCE selectorsJson must be valid JSON.");
        }
    }

    private String column(JsonNode columns, String key, String fallback) {
        String value = columns.path(key).asText(null);
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String value(CSVRecord record, String column) {
        if (!StringUtils.hasText(column) || !record.isMapped(column)) {
            return null;
        }
        String value = record.get(column);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean isBlankRecord(CSVRecord record) {
        for (String value : record) {
            if (StringUtils.hasText(value)) {
                return false;
            }
        }
        return true;
    }

    private Optional<UUID> parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("matchId must be a UUID.");
        }
    }

    private Optional<LeagueCode> parseLeagueCode(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(LeagueCode.valueOf(value.trim().toUpperCase()));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("leagueCode is unsupported: " + value + ".");
        }
    }

    private Optional<MarketCode> parseMarketCode(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(MarketCode.valueOf(value.trim().toUpperCase()));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("marketCode is unsupported: " + value + ".");
        }
    }

    private Optional<BigDecimal> parseDecimal(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(value.trim()));
        } catch (NumberFormatException exception) {
            throw new InvalidRequestException("decimalOdds must be numeric.");
        }
    }

    private Optional<BigDecimal> jsonDecimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        return parseDecimal(node.asText());
    }

    private Optional<LocalDate> parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return Optional.of(LocalDate.parse(value.trim(), formatter));
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new InvalidRequestException("matchDate has an unsupported date format: " + value + ".");
    }

    private Optional<OffsetDateTime> parseOffsetDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(value.trim()));
        } catch (DateTimeParseException exception) {
            throw new InvalidRequestException("capturedAt must be an ISO-8601 offset timestamp.");
        }
    }

    private String required(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new InvalidRequestException(fieldName + " is required.");
        }
        return value.trim();
    }

    private List<League> resolveLeagues(Set<LeagueCode> requestedCodes) {
        if (requestedCodes == null || requestedCodes.isEmpty()) {
            List<League> leagues = leagueRepository.findByActiveTrueAndScrapeEnabledTrueOrderByNameAsc();
            if (leagues.isEmpty()) {
                throw new ReferenceDataNotFoundException("No active leagues are configured.");
            }
            return leagues;
        }

        List<League> leagues = leagueRepository.findByCodeInAndActiveTrue(requestedCodes);
        Set<LeagueCode> activeCodes = leagues.stream().map(League::getCode).collect(Collectors.toSet());
        EnumSet<LeagueCode> missing = EnumSet.copyOf(requestedCodes);
        missing.removeAll(activeCodes);
        if (!missing.isEmpty()) {
            throw new ReferenceDataNotFoundException("Unsupported or inactive leagues: " + missing + ".");
        }
        return leagues;
    }

    private String stripBom(String value) {
        return value != null && value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private String csvText(String rawPayload) {
        if (SnapshotPayloads.isBinary(rawPayload)) {
            return new String(SnapshotPayloads.bytes(rawPayload), StandardCharsets.UTF_8);
        }
        return SnapshotPayloads.text(rawPayload);
    }

    private String normalizeKey(String value) {
        return java.text.Normalizer.normalize(value == null ? "" : value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record OddsCsvOptions(
            String format,
            boolean footballDataWideFormat,
            boolean footballDataWorldCupWorkbook,
            boolean sgoddsOpeningOdds,
            String divisionCode,
            String country,
            String leagueName,
            String season,
            String oddsColumnMode,
            String bookmakerPrefixes,
            String over25Prefixes,
            String under25Prefixes,
            boolean includeOver25,
            boolean includeUnder25,
            boolean includeOneXTwo,
            boolean includeOverUnder25,
            String defaultBookmakerCode,
            String defaultBookmakerName,
            String matchId,
            String leagueCode,
            String matchDate,
            String homeTeam,
            String awayTeam,
            String marketCode,
            String bookmakerCode,
            String bookmakerName,
            String decimalOdds,
            String capturedAt,
            String sourceName,
            String sourceUrl,
            String rawPayloadReference
    ) {
        private boolean useClosingOdds() {
            return "CLOSING".equalsIgnoreCase(oddsColumnMode);
        }
    }

    private record TeamNames(String homeTeam, String awayTeam) {
    }

    private record OneXTwoColumns(String home, String draw, String away) {
    }

    private record ExtractionResult(
            int rowsSeen,
            int rowsAccepted,
            int snapshotsImported,
            int selectionsUpdated,
            int validationErrors,
            String message
    ) {
        static ExtractionResult skipped(String message) {
            return new ExtractionResult(0, 0, 0, 0, 0, message);
        }
    }
}
