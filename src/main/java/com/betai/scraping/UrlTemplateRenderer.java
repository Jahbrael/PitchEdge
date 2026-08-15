package com.betai.scraping;

import com.betai.config.OddsProviderProperties;
import com.betai.config.ApiFootballProperties;
import com.betai.domain.source.SourceTarget;
import com.betai.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class UrlTemplateRenderer {

    private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final OddsProviderProperties oddsProviderProperties;
    private final ApiFootballProperties apiFootballProperties;

    public URI render(SourceTarget sourceTarget, LocalDate refreshDate) {
        String season = sourceTarget.getLeague().getCurrentSeason().replace('/', '-');
        String seasonLabel = sourceTarget.getTargetSeasonLabel() == null
                ? season
                : sourceTarget.getTargetSeasonLabel().replace('/', '-');
        String seasonToken = sourceTarget.getSourceSeasonToken() == null
                ? sourceSeasonToken(sourceTarget.getLeague().getCurrentSeason())
                : sourceTarget.getSourceSeasonToken();
        String url = sourceTarget.getUrlTemplate()
                .replace("{leagueCode}", sourceTarget.getLeague().getCode().name())
                .replace("{date}", refreshDate.toString())
                .replace("{yyyyMMdd}", COMPACT_DATE.format(refreshDate))
                .replace("{season}", season)
                .replace("{seasonLabel}", seasonLabel)
                .replace("{seasonToken}", seasonToken)
                .replace("{theOddsApiBaseUrl}", trimTrailingSlash(oddsProviderProperties.baseUrl()))
                .replace("{theOddsApiKey}", encode(oddsProviderProperties.apiKey()))
                .replace("{theOddsApiRegions}", encode(oddsProviderProperties.regions()))
                .replace("{theOddsApiMarkets}", encode(oddsProviderProperties.markets()))
                .replace("{theOddsApiOddsFormat}", encode(oddsProviderProperties.oddsFormat()))
                .replace("{theOddsApiDateFormat}", encode(oddsProviderProperties.dateFormat()))
                .replace("{apiFootballBaseUrl}", trimTrailingSlash(apiFootballProperties.baseUrl()))
                .replace("{apiFootballTimezone}", encode(apiFootballProperties.timezone()));
        try {
            return new URI(url);
        } catch (URISyntaxException exception) {
            throw new InvalidRequestException("Rendered source URL is invalid for target "
                    + sourceTarget.getName() + ": " + exception.getMessage());
        }
    }

    private String sourceSeasonToken(String seasonLabel) {
        String[] parts = seasonLabel.split("/");
        if (parts.length == 2 && parts[0].length() >= 2 && parts[1].length() >= 2) {
            return parts[0].substring(parts[0].length() - 2) + parts[1].substring(parts[1].length() - 2);
        }
        return seasonLabel.replace("/", "").replace("-", "");
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value.trim(), StandardCharsets.UTF_8);
    }
}
