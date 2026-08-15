package com.betai.service;

import com.betai.api.dto.OddsImportItem;
import com.betai.api.dto.OddsImportItemResponse;
import com.betai.api.dto.OddsImportRequest;
import com.betai.api.dto.OddsImportResponse;
import com.betai.api.dto.OddsSnapshotResponse;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.match.Match;
import com.betai.domain.odds.Bookmaker;
import com.betai.domain.odds.OddsSnapshot;
import com.betai.domain.team.Team;
import com.betai.exception.InvalidRequestException;
import com.betai.exception.ReferenceDataNotFoundException;
import com.betai.exception.ResourceNotFoundException;
import com.betai.repository.BookmakerRepository;
import com.betai.repository.MarketDefinitionRepository;
import com.betai.repository.MatchRepository;
import com.betai.repository.OddsSnapshotRepository;
import com.betai.repository.TeamAliasRepository;
import com.betai.repository.TeamRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OddsImportServiceImpl implements OddsImportService {

    private static final String DEFAULT_SOURCE_NAME = "manual-odds-import";

    private final BookmakerRepository bookmakerRepository;
    private final OddsSnapshotRepository oddsSnapshotRepository;
    private final MatchRepository matchRepository;
    private final MarketDefinitionRepository marketDefinitionRepository;
    private final TeamRepository teamRepository;
    private final TeamAliasRepository teamAliasRepository;
    private final OddsValueCalculator oddsValueCalculator;
    private final OddsValueService oddsValueService;
    private final EntityManager entityManager;
    private final Clock clock;

    @Override
    @Transactional
    public OddsImportResponse importOdds(OddsImportRequest request) {
        OffsetDateTime importedAt = OffsetDateTime.now(clock);
        boolean recalculateExistingSelections = request.recalculateExistingSelections() == null
                || request.recalculateExistingSelections();
        List<OddsImportItemResponse> results = new ArrayList<>();
        List<OddsSnapshot> importedSnapshots = new ArrayList<>();
        Map<String, Bookmaker> bookmakerCache = new LinkedHashMap<>();
        int snapshotsImported = 0;
        int rejected = 0;
        int selectionsUpdated = 0;

        FlushModeType previousFlushMode = entityManager.getFlushMode();
        entityManager.setFlushMode(FlushModeType.COMMIT);
        try {
            for (int index = 0; index < request.odds().size(); index++) {
                OddsImportItem item = request.odds().get(index);
                try {
                    OddsSnapshot snapshot = importOne(item, importedAt, bookmakerCache);
                    importedSnapshots.add(snapshot);
                    snapshotsImported++;
                    results.add(success(index, snapshot, 0));
                } catch (RuntimeException exception) {
                    rejected++;
                    results.add(rejected(index, item, exception));
                }
            }
            if (recalculateExistingSelections && !importedSnapshots.isEmpty()) {
                oddsSnapshotRepository.flush();
                selectionsUpdated = refreshSelectionsOncePerMatchMarket(importedSnapshots);
            }
        } finally {
            entityManager.setFlushMode(previousFlushMode);
        }

        List<String> warnings = new ArrayList<>();
        if (snapshotsImported == 0) {
            warnings.add("No odds snapshots were imported. Check match resolution, bookmaker code, market code, and decimal odds.");
        }
        if (!recalculateExistingSelections) {
            warnings.add("Existing prediction selections were not recalculated. Importing odds alone will not expose value metrics until predictions are regenerated or odds are re-imported with recalculation enabled.");
        }

        return new OddsImportResponse(
                UUID.randomUUID(),
                importedAt,
                request.odds().size(),
                snapshotsImported,
                rejected,
                selectionsUpdated,
                List.copyOf(results),
                List.copyOf(warnings)
        );
    }

    private int refreshSelectionsOncePerMatchMarket(List<OddsSnapshot> importedSnapshots) {
        Map<String, OddsSnapshot> latestUniqueSnapshots = new LinkedHashMap<>();
        for (OddsSnapshot snapshot : importedSnapshots) {
            String key = snapshot.getMatch().getId() + ":" + snapshot.getMarketDefinition().getId();
            latestUniqueSnapshots.put(key, snapshot);
        }
        int selectionsUpdated = 0;
        for (OddsSnapshot snapshot : latestUniqueSnapshots.values()) {
            selectionsUpdated += oddsValueService.refreshSelectionsForOdds(snapshot);
        }
        return selectionsUpdated;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OddsSnapshotResponse> findSnapshots(
            LeagueCode leagueCode,
            MarketCode marketCode,
            UUID matchId,
            LocalDate fromDate,
            LocalDate toDate,
            int limit
    ) {
        int resolvedLimit = Math.min(Math.max(limit, 1), 500);
        return oddsSnapshotRepository.findSnapshots(
                        leagueCode,
                        marketCode,
                        matchId,
                        fromDate,
                        toDate,
                        PageRequest.of(0, resolvedLimit)
                )
                .stream()
                .map(OddsSnapshotResponse::from)
                .toList();
    }

    private OddsSnapshot importOne(
            OddsImportItem item,
            OffsetDateTime importedAt,
            Map<String, Bookmaker> bookmakerCache
    ) {
        Match match = resolveMatch(item);
        MarketDefinition marketDefinition = marketDefinitionRepository.findByCode(item.marketCode())
                .filter(MarketDefinition::isEnabled)
                .orElseThrow(() -> new ReferenceDataNotFoundException("Unsupported or disabled market: " + item.marketCode() + "."));
        Bookmaker bookmaker = resolveBookmaker(item, bookmakerCache);
        BigDecimal decimalOdds = normalizeDecimalOdds(item.decimalOdds());
        BigDecimal impliedProbability = oddsValueCalculator.impliedProbability(decimalOdds);

        OddsSnapshot snapshot = new OddsSnapshot()
                .setMatch(match)
                .setMarketDefinition(marketDefinition)
                .setBookmaker(bookmaker)
                .setDecimalOdds(decimalOdds)
                .setImpliedProbability(impliedProbability)
                .setCapturedAt(item.capturedAt() == null ? importedAt : item.capturedAt())
                .setSourceName(normalizeOptional(item.sourceName(), DEFAULT_SOURCE_NAME, 160))
                .setSourceUrl(truncate(normalizeBlank(item.sourceUrl()), 500))
                .setRawPayloadReference(truncate(normalizeBlank(item.rawPayloadReference()), 500));
        return oddsSnapshotRepository.save(snapshot);
    }

    private Match resolveMatch(OddsImportItem item) {
        if (item.matchId() != null) {
            return matchRepository.findById(item.matchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Match not found: " + item.matchId() + "."));
        }
        if (item.leagueCode() == null || item.matchDate() == null) {
            throw new InvalidRequestException("matchId is required unless leagueCode, matchDate, homeTeam, and awayTeam are provided.");
        }
        try {
            Team homeTeam = resolveTeam(item.leagueCode(), item.homeTeam(), "homeTeam");
            Team awayTeam = resolveTeam(item.leagueCode(), item.awayTeam(), "awayTeam");
            if (homeTeam.getId().equals(awayTeam.getId())) {
                throw new InvalidRequestException("homeTeam and awayTeam cannot resolve to the same team.");
            }
            return matchRepository.findByLeague_CodeAndHomeTeam_IdAndAwayTeam_IdAndMatchDateSafely(
                            item.leagueCode(),
                            homeTeam.getId(),
                            awayTeam.getId(),
                            item.matchDate()
                    )
                    .or(() -> matchRepository.findByLeague_CodeAndMatchDateBetweenOrderByKickoffAtAsc(
                                    item.leagueCode(),
                                    item.matchDate().minusDays(1),
                                    item.matchDate().plusDays(1)
                            )
                            .stream()
                            .filter(match -> match.getHomeTeam().getId().equals(homeTeam.getId()))
                            .filter(match -> match.getAwayTeam().getId().equals(awayTeam.getId()))
                            .findFirst())
                    .orElseGet(() -> resolveMatchByFixtureNames(item));
        } catch (ReferenceDataNotFoundException exception) {
            return resolveMatchByFixtureNames(item);
        }
    }

    private Match resolveMatchByFixtureNames(OddsImportItem item) {
        String requestedHome = normalizeKey(canonicalizeTeamName(item.leagueCode(), normalizeRequired(item.homeTeam(), "homeTeam")));
        String requestedAway = normalizeKey(canonicalizeTeamName(item.leagueCode(), normalizeRequired(item.awayTeam(), "awayTeam")));
        return matchRepository.findByLeague_CodeAndMatchDateBetweenOrderByKickoffAtAsc(
                        item.leagueCode(),
                        item.matchDate().minusDays(1),
                        item.matchDate().plusDays(1)
                )
                .stream()
                .filter(match -> teamNamesCompatible(match.getHomeTeam().getCanonicalName(), requestedHome))
                .filter(match -> teamNamesCompatible(match.getAwayTeam().getCanonicalName(), requestedAway))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Match not found for "
                        + item.leagueCode() + " " + item.matchDate() + " "
                        + item.homeTeam() + " vs " + item.awayTeam() + "."));
    }

    private boolean teamNamesCompatible(String localTeamName, String requestedTeamKey) {
        String localKey = normalizeKey(localTeamName);
        if (localKey.equals(requestedTeamKey)) {
            return true;
        }
        String looseLocal = looseTeamKey(localTeamName);
        String looseRequested = looseTeamKey(requestedTeamKey);
        if (looseLocal.equals(looseRequested)) {
            return true;
        }
        if (looseLocal.startsWith(looseRequested + "-") || looseRequested.startsWith(looseLocal + "-")) {
            return true;
        }
        String[] localParts = looseLocal.split("-");
        String[] requestedParts = looseRequested.split("-");
        return localParts.length >= 2
                && requestedParts.length >= 2
                && localParts[0].equals(requestedParts[0])
                && localParts[localParts.length - 1].equals(requestedParts[requestedParts.length - 1]);
    }

    private String looseTeamKey(String value) {
        return normalizeKey(value)
                .replaceAll("(^|-)(fc|sc|afc|cf|club|women|woman|w)(-|$)", "-")
                .replaceAll("(^|-)(red)(-|$)", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-+|-+$)", "");
    }

    private Team resolveTeam(LeagueCode leagueCode, String teamName, String fieldName) {
        String normalizedName = canonicalizeTeamName(leagueCode, normalizeRequired(teamName, fieldName));
        return teamRepository.findByLeague_CodeAndCanonicalNameIgnoreCaseSafely(leagueCode, normalizedName)
                .or(() -> teamAliasRepository.findByLeague_CodeAndAliasNormalized(leagueCode, normalizeKey(normalizedName))
                        .map(alias -> alias.getTeam()))
                .orElseThrow(() -> new ReferenceDataNotFoundException("Unknown " + fieldName + " for "
                        + leagueCode + ": " + normalizedName + "."));
    }

    private String canonicalizeTeamName(LeagueCode leagueCode, String teamName) {
        String normalized = normalizeKey(teamName);
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
                default -> teamName;
            };
        }
        if (leagueCode == LeagueCode.FIFA_WORLD_CUP_2026) {
            return switch (normalized) {
                case "turkey" -> "Turkiye";
                case "usa", "united-states-of-america", "us" -> "United States";
                case "ivory-coast", "cote-d-ivoire" -> "Ivory Coast";
                case "south-korea", "korea-republic" -> "South Korea";
                case "north-korea", "korea-dpr" -> "North Korea";
                default -> teamName;
            };
        }
        if (leagueCode == LeagueCode.ALLSVENSKAN) {
            return switch (normalized) {
                case "ik-sirius", "sirius-fk" -> "Sirius";
                case "mjallby-aif", "mjällby-aif" -> "Mjallby";
                case "malmo-ff", "malmö-ff" -> "Malmo FF";
                case "ifk-goteborg", "ifk-göteborg", "goteborg", "göteborg" -> "Goteborg";
                case "bk-hacken", "bk-häcken", "hacken", "häcken" -> "Hacken";
                case "djurgardens-if", "djurgårdens-if" -> "Djurgarden";
                case "halmstads-bk" -> "Halmstad";
                case "if-elfsborg" -> "Elfsborg";
                case "if-brommapojkarna" -> "Brommapojkarna";
                case "kalmar-ff" -> "Kalmar";
                case "hammarby-if" -> "Hammarby";
                case "vasteras-sk", "västerås-sk" -> "Vasteras SK";
                case "degerfors-if" -> "Degerfors";
                case "orgryte-is", "örgryte-is" -> "Orgryte";
                default -> teamName;
            };
        }
        if (leagueCode == LeagueCode.ELITESERIEN) {
            return switch (normalized) {
                case "viking-fk" -> "Viking";
                case "fk-bodo-glimt", "bodo-glimt", "bodø-glimt" -> "Bodo/Glimt";
                case "lillestrom-sk", "lillestrøm-sk", "lillestrøm" -> "Lillestrom";
                case "tromso-il", "tromsø-il", "tromsø" -> "Tromso";
                case "valerenga", "vålerenga", "valerenga-if", "vålerenga-if" -> "Valerenga";
                case "rosenborg-bk" -> "Rosenborg";
                case "molde-fk" -> "Molde";
                case "sk-brann" -> "Brann";
                case "hamarkameratene" -> "HamKam";
                case "sarpsborg-08-ff" -> "Sarpsborg 08";
                case "sandefjord-fotball" -> "Sandefjord";
                case "fredrikstad-fk" -> "Fredrikstad";
                case "kristiansund-bk" -> "Kristiansund";
                case "aalesunds-fk", "alesund", "ålesund" -> "Aalesund";
                case "ik-start" -> "Start";
                default -> teamName;
            };
        }
        return teamName;
    }

    private Bookmaker resolveBookmaker(OddsImportItem item, Map<String, Bookmaker> bookmakerCache) {
        String code = normalizeBookmakerCode(item.bookmakerCode());
        Bookmaker cached = bookmakerCache.get(code);
        if (cached != null) {
            cached.setDisplayName(normalizeOptional(item.bookmakerName(), cached.getDisplayName(), 128))
                    .setActive(true);
            return cached;
        }
        return bookmakerRepository.findByCode(code)
                .map(existing -> {
                    String displayName = normalizeOptional(item.bookmakerName(), existing.getDisplayName(), 128);
                    existing.setDisplayName(displayName).setActive(true);
                    bookmakerCache.put(code, existing);
                    return existing;
                })
                .orElseGet(() -> {
                    Bookmaker created = bookmakerRepository.save(new Bookmaker()
                            .setCode(code)
                            .setDisplayName(normalizeOptional(item.bookmakerName(), code, 128))
                            .setActive(true));
                    bookmakerCache.put(code, created);
                    return created;
                });
    }

    private OddsImportItemResponse success(int index, OddsSnapshot snapshot, int selectionsUpdated) {
        Match match = snapshot.getMatch();
        return new OddsImportItemResponse(
                index,
                "IMPORTED",
                snapshot.getId(),
                match.getId(),
                match.getHomeTeam().getCanonicalName() + " vs " + match.getAwayTeam().getCanonicalName(),
                snapshot.getMarketDefinition().getCode().name(),
                snapshot.getBookmaker().getCode(),
                snapshot.getDecimalOdds(),
                snapshot.getImpliedProbability(),
                selectionsUpdated,
                "Odds snapshot imported and value metrics recalculated for " + selectionsUpdated + " selections."
        );
    }

    private OddsImportItemResponse rejected(int index, OddsImportItem item, RuntimeException exception) {
        return new OddsImportItemResponse(
                index,
                "REJECTED",
                null,
                item.matchId(),
                null,
                item.marketCode() == null ? null : item.marketCode().name(),
                item.bookmakerCode(),
                item.decimalOdds(),
                null,
                0,
                truncate(exception.getMessage(), 500)
        );
    }

    private BigDecimal normalizeDecimalOdds(BigDecimal decimalOdds) {
        if (decimalOdds == null) {
            throw new InvalidRequestException("decimalOdds is required.");
        }
        return decimalOdds.setScale(4, RoundingMode.HALF_UP);
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeBlank(value);
        if (!StringUtils.hasText(normalized)) {
            throw new InvalidRequestException(fieldName + " is required.");
        }
        return normalized;
    }

    private String normalizeOptional(String value, String fallback, int maxLength) {
        String normalized = normalizeBlank(value);
        if (!StringUtils.hasText(normalized)) {
            normalized = fallback;
        }
        return truncate(normalized, maxLength);
    }

    private String normalizeBlank(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeBookmakerCode(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized)) {
            throw new InvalidRequestException("bookmakerCode is required.");
        }
        return truncate(normalized, 64);
    }

    private String normalizeKey(String value) {
        String normalized = Normalizer.normalize(transliterateCommonFootballCharacters(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return StringUtils.hasText(normalized) ? normalized : "unknown";
    }

    private String transliterateCommonFootballCharacters(String value) {
        return (value == null ? "" : value)
                .replace("Æ", "AE")
                .replace("æ", "ae")
                .replace("Ø", "O")
                .replace("ø", "o")
                .replace("Ð", "D")
                .replace("ð", "d")
                .replace("Þ", "Th")
                .replace("þ", "th")
                .replace("Ł", "L")
                .replace("ł", "l");
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
