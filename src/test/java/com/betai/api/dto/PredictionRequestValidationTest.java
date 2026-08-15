package com.betai.api.dto;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PredictionRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsMoreThanTheLegacyTwelveSelectedMarkets() {
        Set<MarketCode> marketCodes = Arrays.stream(MarketCode.values())
                .limit(13)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(MarketCode.class)));

        var violations = validator.validate(request(marketCodes));

        assertThat(violations).isEmpty();
    }

    @Test
    void acceptsTheFullHundredMarketCatalogue() {
        Set<MarketCode> marketCodes = EnumSet.allOf(MarketCode.class);

        var violations = validator.validate(request(marketCodes));

        assertThat(marketCodes).hasSize(100);
        assertThat(violations).isEmpty();
    }

    @Test
    void acceptsEveryEnabledMarketSelectedByTheUi() {
        Set<MarketCode> marketCodes = Arrays.stream(MarketCode.values())
                .filter(MarketCode::isEnabled)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(MarketCode.class)));

        var violations = validator.validate(request(marketCodes));

        assertThat(marketCodes).hasSize(74);
        assertThat(violations).isEmpty();
    }

    @Test
    void acceptsExpandedVerifiedLeagueCatalogue() {
        Set<LeagueCode> leagueCodes = EnumSet.allOf(LeagueCode.class);

        var violations = validator.validate(request(leagueCodes, Set.of(MarketCode.HOME_WIN)));

        assertThat(leagueCodes).hasSize(178);
        assertThat(violations).isEmpty();
    }

    private PredictionRequest request(Set<MarketCode> marketCodes) {
        return request(Set.of(LeagueCode.PREMIER_LEAGUE), marketCodes);
    }

    private PredictionRequest request(Set<LeagueCode> leagueCodes, Set<MarketCode> marketCodes) {
        return new PredictionRequest(
                leagueCodes,
                marketCodes,
                LocalDate.parse("2026-06-16"),
                LocalDate.parse("2026-06-17"),
                null,
                null,
                null,
                "FOOTBALL",
                SelectionStrategy.BALANCED,
                1,
                10,
                1,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                null,
                null,
                null,
                BigDecimal.ZERO,
                0,
                null,
                null,
                false,
                true,
                false,
                1,
                null,
                null,
                false,
                1,
                false,
                true,
                false,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
