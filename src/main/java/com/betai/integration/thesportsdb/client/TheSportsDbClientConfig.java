package com.betai.integration.thesportsdb.client;

import com.betai.integration.thesportsdb.TheSportsDbProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class TheSportsDbClientConfig {

    @Bean
    @Qualifier("theSportsDbHttpClient")
    HttpClient theSportsDbHttpClient(TheSportsDbProperties properties) {
        Duration timeout = properties.connectionTimeout() == null
                ? Duration.ofSeconds(10)
                : properties.connectionTimeout();
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(timeout)
                .build();
    }
}
