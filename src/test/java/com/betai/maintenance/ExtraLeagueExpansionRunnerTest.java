package com.betai.maintenance;

import com.betai.domain.league.LeagueCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExtraLeagueExpansionRunnerTest {

    @Test
    void targetsExactlyTheVerifiedExtraHundredLeagues() {
        assertThat(ExtraLeagueExpansionRunner.targetLeagues())
                .hasSize(100)
                .contains(
                        LeagueCode.RUSSIAN_FOOTBALL_PREMIER_LEAGUE,
                        LeagueCode.ITALIAN_SERIE_B,
                        LeagueCode.GERMAN_2_BUNDESLIGA,
                        LeagueCode.AFC_CHAMPIONS_LEAGUE_ELITE,
                        LeagueCode.ITALY_SERIE_D_GIRONE_F
                );
    }

    @Test
    void migrationAddsImportPendingTheSportsDbMappingsForExtraHundredLeagues() throws IOException {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V37__add_100_verified_thesportsdb_leagues.sql"));

        assertThat(migration)
                .contains(
                        "RUSSIAN_FOOTBALL_PREMIER_LEAGUE",
                        "ITALY_SERIE_D_GIRONE_F",
                        "'THESPORTSDB'",
                        "'LEAGUE'",
                        "'RESOLVED'",
                        "scrape_enabled",
                        "false"
                )
                .doesNotContain("The Odds API");
    }
}
