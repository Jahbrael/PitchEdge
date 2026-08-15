package com.betai.scraping;

import com.betai.config.ScrapingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class RobotsTxtService {

    private final HttpClient httpClient;
    private final ScrapingProperties scrapingProperties;

    public boolean isAllowed(URI targetUri, String userAgent) {
        URI robotsUri = URI.create(targetUri.getScheme() + "://" + targetUri.getAuthority() + "/robots.txt");
        HttpRequest request = HttpRequest.newBuilder(robotsUri)
                .timeout(Duration.ofMillis(scrapingProperties.robotsTimeoutMs()))
                .header("User-Agent", userAgent)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return true;
            }
            if (response.statusCode() == 401 || response.statusCode() == 403 || response.statusCode() >= 500) {
                return false;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return true;
            }
            return parse(response.body(), userAgent).allows(targetUri.getRawPath());
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private RobotsRules parse(String robotsTxt, String userAgent) {
        List<RobotsRule> matchedRules = new ArrayList<>();
        List<String> currentAgents = new ArrayList<>();
        boolean currentGroupMatches = false;
        String normalizedAgent = normalizeAgent(userAgent);

        for (String rawLine : robotsTxt.split("\\R")) {
            String line = stripComment(rawLine).trim();
            if (!StringUtils.hasText(line) || !line.contains(":")) {
                if (!StringUtils.hasText(line)) {
                    currentAgents.clear();
                    currentGroupMatches = false;
                }
                continue;
            }

            String[] parts = line.split(":", 2);
            String directive = parts[0].trim().toLowerCase(Locale.ROOT);
            String value = parts[1].trim();

            if ("user-agent".equals(directive)) {
                if (currentAgents.isEmpty()) {
                    currentGroupMatches = false;
                }
                currentAgents.add(value);
                currentGroupMatches = currentGroupMatches || agentMatches(value, normalizedAgent);
                continue;
            }

            if (("allow".equals(directive) || "disallow".equals(directive)) && currentGroupMatches) {
                matchedRules.add(new RobotsRule("allow".equals(directive), value));
            }
        }

        return new RobotsRules(matchedRules);
    }

    private String stripComment(String line) {
        int index = line.indexOf('#');
        return index >= 0 ? line.substring(0, index) : line;
    }

    private boolean agentMatches(String ruleAgent, String normalizedAgent) {
        String normalizedRule = normalizeAgent(ruleAgent);
        return "*".equals(normalizedRule)
                || normalizedAgent.equals(normalizedRule)
                || normalizedAgent.startsWith(normalizedRule);
    }

    private String normalizeAgent(String agent) {
        return agent.toLowerCase(Locale.ROOT).split("[/\\s]", 2)[0].trim();
    }

    private record RobotsRule(boolean allowed, String path) {
        boolean matches(String targetPath) {
            return !StringUtils.hasText(path) || targetPath.startsWith(path);
        }
    }

    private record RobotsRules(List<RobotsRule> rules) {
        boolean allows(String targetPath) {
            RobotsRule winningRule = null;
            for (RobotsRule rule : rules) {
                if (rule.matches(targetPath) && (winningRule == null || rule.path().length() > winningRule.path().length())) {
                    winningRule = rule;
                }
            }
            return winningRule == null || winningRule.allowed() || !StringUtils.hasText(winningRule.path());
        }
    }
}
