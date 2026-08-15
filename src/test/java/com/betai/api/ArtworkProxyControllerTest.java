package com.betai.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArtworkProxyControllerTest {

    private HttpClient httpClient;
    private ArtworkProxyController controller;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        controller = new ArtworkProxyController(httpClient);
    }

    @Test
    void proxiesAllowedTheSportsDbImageUrls() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> upstream = mock(HttpResponse.class);
        when(upstream.statusCode()).thenReturn(200);
        when(upstream.body()).thenReturn(new byte[]{1, 2, 3});
        when(upstream.headers()).thenReturn(HttpResponseHeaders.imagePng());
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(upstream);

        var response = controller.proxy("https://r2.thesportsdb.com/images/media/team/badge/test.png");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(1, 2, 3);
        verify(httpClient).send(any(), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void rejectsNonTheSportsDbUrlsBeforeNetworkCall() throws Exception {
        var response = controller.proxy("https://example.com/images/media/team/badge/test.png");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(httpClient, never()).send(any(), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void rejectsTheSportsDbNonImagePathsBeforeNetworkCall() throws Exception {
        var response = controller.proxy("https://www.thesportsdb.com/api/v2/json/lookup/team/133604");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(httpClient, never()).send(any(), any(HttpResponse.BodyHandler.class));
    }

    private static final class HttpResponseHeaders {
        private static java.net.http.HttpHeaders imagePng() {
            return java.net.http.HttpHeaders.of(
                    java.util.Map.of("Content-Type", java.util.List.of("image/png")),
                    (left, right) -> true
            );
        }
    }
}
