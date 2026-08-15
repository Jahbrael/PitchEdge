package com.betai.service;

import com.betai.api.dto.SourceTargetRequest;
import com.betai.api.dto.SourceTargetResponse;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.source.SourceTarget;
import com.betai.domain.source.SourceType;
import com.betai.exception.InvalidRequestException;
import com.betai.exception.ReferenceDataNotFoundException;
import com.betai.exception.ResourceNotFoundException;
import com.betai.repository.LeagueRepository;
import com.betai.repository.SourceTargetRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SourceTargetServiceImpl implements SourceTargetService {

    private static final String DEFAULT_USER_AGENT = "BetAIResearchBot/0.1 (+local-development)";

    private final SourceTargetRepository sourceTargetRepository;
    private final LeagueRepository leagueRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public SourceTargetResponse create(SourceTargetRequest request) {
        League league = leagueRepository.findByCode(request.leagueCode())
                .orElseThrow(() -> new ReferenceDataNotFoundException("League is not configured: " + request.leagueCode() + "."));
        SourceTarget sourceTarget = apply(new SourceTarget().setLeague(league), request);
        return SourceTargetResponse.from(sourceTargetRepository.save(sourceTarget));
    }

    @Override
    @Transactional
    public SourceTargetResponse update(UUID id, SourceTargetRequest request) {
        SourceTarget sourceTarget = sourceTargetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Source target not found: " + id + "."));
        League league = leagueRepository.findByCode(request.leagueCode())
                .orElseThrow(() -> new ReferenceDataNotFoundException("League is not configured: " + request.leagueCode() + "."));
        sourceTarget.setLeague(league);
        return SourceTargetResponse.from(sourceTargetRepository.save(apply(sourceTarget, request)));
    }

    @Override
    @Transactional(readOnly = true)
    public SourceTargetResponse get(UUID id) {
        return sourceTargetRepository.findById(id)
                .map(SourceTargetResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Source target not found: " + id + "."));
    }

    @Override
    @Transactional
    public SourceTargetResponse setActive(UUID id, boolean active) {
        SourceTarget sourceTarget = sourceTargetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Source target not found: " + id + "."));
        sourceTarget.setActive(active);
        return SourceTargetResponse.from(sourceTargetRepository.save(sourceTarget));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SourceTargetResponse> list(Optional<LeagueCode> leagueCode, Optional<SourceType> sourceType) {
        List<SourceTarget> targets;
        if (leagueCode.isPresent() && sourceType.isPresent()) {
            targets = sourceTargetRepository.findByLeague_CodeAndSourceTypeOrderByReliabilityScoreDescNameAsc(
                    leagueCode.get(),
                    sourceType.get()
            );
        } else if (leagueCode.isPresent()) {
            targets = sourceTargetRepository.findByLeague_CodeOrderBySourceTypeAscNameAsc(leagueCode.get());
        } else {
            targets = sourceTargetRepository.findAll().stream()
                    .sorted(Comparator.comparing((SourceTarget target) -> target.getLeague().getCode().name())
                            .thenComparing(target -> target.getSourceType().name())
                            .thenComparing(SourceTarget::getName))
                    .toList();
        }
        return targets.stream().map(SourceTargetResponse::from).toList();
    }

    private SourceTarget apply(SourceTarget sourceTarget, SourceTargetRequest request) {
        validateUrlTemplate(request.urlTemplate());
        String selectorsJson = selectorsJsonToStorage(request.selectorsJson());

        return sourceTarget
                .setSourceType(request.sourceType())
                .setName(request.name().trim())
                .setUrlTemplate(request.urlTemplate().trim())
                .setSourceSeasonToken(StringUtils.hasText(request.sourceSeasonToken()) ? request.sourceSeasonToken().trim() : null)
                .setTargetSeasonLabel(StringUtils.hasText(request.targetSeasonLabel()) ? request.targetSeasonLabel().trim() : null)
                .setRenderMode(request.renderMode())
                .setActive(request.active() == null || request.active())
                .setRobotsTxtRequired(request.robotsTxtRequired() == null || request.robotsTxtRequired())
                .setUserAgent(StringUtils.hasText(request.userAgent()) ? request.userAgent().trim() : DEFAULT_USER_AGENT)
                .setRateLimitPerMinute(request.rateLimitPerMinute() == null ? 12 : request.rateLimitPerMinute())
                .setTimeoutMs(request.timeoutMs() == null ? 10000 : request.timeoutMs())
                .setReliabilityScore(request.reliabilityScore() == null ? new BigDecimal("50.00") : request.reliabilityScore())
                .setFallbackPriority(request.fallbackPriority() == null ? 100 : request.fallbackPriority())
                .setSelectorsJson(selectorsJson);
    }

    private void validateUrlTemplate(String urlTemplate) {
        String concreteUrl = urlTemplate
                .replace("{leagueCode}", "PREMIER_LEAGUE")
                .replace("{date}", "2026-06-06")
                .replace("{yyyyMMdd}", "20260606")
                .replace("{season}", "2026-2027")
                .replace("{seasonLabel}", "2026-2027")
                .replace("{seasonToken}", "2627");
        try {
            URI uri = new URI(concreteUrl);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new InvalidRequestException("Source target URL must use http or https.");
            }
            if (!StringUtils.hasText(uri.getHost())) {
                throw new InvalidRequestException("Source target URL must include a host.");
            }
        } catch (URISyntaxException exception) {
            throw new InvalidRequestException("Source target URL template is invalid: " + exception.getMessage());
        }
    }

    private String selectorsJsonToStorage(JsonNode selectorsJson) {
        if (selectorsJson == null || selectorsJson.isNull()) {
            return null;
        }

        try {
            if (selectorsJson.isTextual()) {
                String rawJson = selectorsJson.asText();
                if (!StringUtils.hasText(rawJson)) {
                    return null;
                }
                objectMapper.readTree(rawJson);
                return rawJson.trim();
            }
            return objectMapper.writeValueAsString(selectorsJson);
        } catch (JsonProcessingException exception) {
            throw new InvalidRequestException("selectorsJson must be valid JSON.");
        }
    }
}
