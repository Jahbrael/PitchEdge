package com.betai.util;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotPayloadsTest {

    @Test
    void storesXlsxPayloadsAsBase64AndRestoresBytes() {
        byte[] payload = "xlsx-bytes".getBytes(StandardCharsets.UTF_8);

        String encoded = SnapshotPayloads.encodeBinary(payload);

        assertThat(SnapshotPayloads.shouldStoreAsBase64(
                URI.create("https://www.football-data.co.uk/WorldCup2026.xlsx"),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )).isTrue();
        assertThat(SnapshotPayloads.isBinary(encoded)).isTrue();
        assertThat(SnapshotPayloads.bytes(encoded)).isEqualTo(payload);
    }

    @Test
    void keepsCsvPayloadsAsTextWhenServedAsOctetStream() {
        assertThat(SnapshotPayloads.shouldStoreAsBase64(
                URI.create("https://sgodds.com/downloads/sgodds-1781398081-k-league.csv"),
                "application/octet-stream"
        )).isFalse();
    }
}
