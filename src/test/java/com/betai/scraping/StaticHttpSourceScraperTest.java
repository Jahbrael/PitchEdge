package com.betai.scraping;

import com.betai.config.ApiFootballProperties;
import com.betai.config.ScrapingProperties;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.snapshot.ScrapeStatus;
import com.betai.domain.source.RenderMode;
import com.betai.domain.source.SourceTarget;
import com.betai.domain.source.SourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaticHttpSourceScraperTest {

    @Mock
    private HttpClient httpClient;
    @Mock
    private UrlTemplateRenderer urlTemplateRenderer;
    @Mock
    private RobotsTxtService robotsTxtService;
    @Mock
    private HostRateLimiter hostRateLimiter;

    private StaticHttpSourceScraper scraper;

    @BeforeEach
    void setUp() {
        scraper = new StaticHttpSourceScraper(
                httpClient,
                urlTemplateRenderer,
                robotsTxtService,
                hostRateLimiter,
                new HashingService(),
                new ObjectMapper(),
                new ScrapingProperties(5_242_880L, 5_000),
                new ApiFootballProperties(true, "", "https://v3.football.api-sports.io", "UTC"),
                Clock.fixed(Instant.parse("2026-06-18T10:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void resolvesCurrentSgoddsLeagueCsvFromStableDataPage() throws Exception {
        SourceTarget sourceTarget = sgoddsSource();
        URI dataPageUri = URI.create("https://sgodds.com/football/data");
        String currentCsvUrl = "https://sgodds.com/downloads/sgodds-1781743681-k-league.csv";
        String html = """
                <html><body><table>
                  <tr><td>K League</td><td><a href="/downloads/sgodds-1781743681-k-league.csv">Download</a></td></tr>
                </table></body></html>
                """;
        String csv = """
                ID,Match,"Start Time",League,Live Bet,Result,Ft1X2_01,Ft1X2_02,Ft1X2_03
                4185,"Gangwon vs Ulsan","2026-05-17 18:00:00","K League",0,"HT:2-0, FT:2-0",2.20,3.05,2.95
                """;

        when(urlTemplateRenderer.render(sourceTarget, LocalDate.of(2026, 6, 18))).thenReturn(dataPageUri);
        when(robotsTxtService.isAllowed(any(URI.class), any())).thenReturn(true);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response(200, "text/html; charset=UTF-8", html.getBytes()))
                .thenReturn(response(200, "application/octet-stream", csv.getBytes()));

        ScrapeOutcome outcome = scraper.scrape(sourceTarget, LocalDate.of(2026, 6, 18));

        assertThat(outcome.scrapeStatus()).isEqualTo(ScrapeStatus.SUCCESS);
        assertThat(outcome.sourceUrl()).isEqualTo(currentCsvUrl);
        assertThat(outcome.rawPayload()).startsWith("ID,Match");
        assertThat(outcome.rawPayload()).contains("Gangwon vs Ulsan");
    }

    private SourceTarget sgoddsSource() {
        League league = new League()
                .setCode(LeagueCode.K_LEAGUE_1)
                .setName("K League 1")
                .setCountry("South Korea")
                .setTier(1)
                .setCurrentSeason("2026");
        return new SourceTarget()
                .setLeague(league)
                .setSourceType(SourceType.RESULTS)
                .setName("Sgodds Results K League CSV")
                .setUrlTemplate("https://sgodds.com/football/data")
                .setRenderMode(RenderMode.STATIC_HTML)
                .setRobotsTxtRequired(true)
                .setUserAgent("BetAIResearchBot/0.1 (+local-development)")
                .setRateLimitPerMinute(6)
                .setTimeoutMs(15000)
                .setSelectorsJson("{\"format\":\"sgodds-results-csv\","
                        + "\"leagueName\":\"K League\","
                        + "\"downloadPageFormat\":\"sgodds-league-download-page\"}");
    }

    private HttpResponse<byte[]> response(int statusCode, String contentType, byte[] body) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return statusCode;
            }

            @Override
            public HttpRequest request() {
                return null;
            }

            @Override
            public java.util.Optional<HttpResponse<byte[]>> previousResponse() {
                return java.util.Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(Map.of("Content-Type", List.of(contentType)), (left, right) -> true);
            }

            @Override
            public byte[] body() {
                return body;
            }

            @Override
            public java.util.Optional<javax.net.ssl.SSLSession> sslSession() {
                return java.util.Optional.empty();
            }

            @Override
            public URI uri() {
                return URI.create("https://sgodds.com");
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_2;
            }
        };
    }
}
