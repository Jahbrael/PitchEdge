package com.betai.domain.feature;

import com.betai.domain.market.MarketDefinition;
import com.betai.domain.market.MarketTargetType;
import com.betai.domain.market.MarketType;

public enum FeatureGroup {
    RESULTS,
    GOALS,
    CORNERS,
    CARDS,
    PLAYER_GOALS,
    PLAYER_ASSISTS,
    PLAYER_PASSES,
    GOALKEEPER_SAVES;

    public static FeatureGroup fromMarket(MarketDefinition market) {
        if (market == null) {
            return RESULTS;
        }
        if (market.getMarketFamily() == MarketType.TOTAL_CORNERS
                || market.getMarketFamily() == MarketType.TEAM_CORNERS
                || market.getTargetType() == MarketTargetType.CORNERS) {
            return CORNERS;
        }
        if (market.getMarketFamily() == MarketType.TOTAL_YELLOW_CARDS
                || market.getMarketFamily() == MarketType.RED_CARD
                || market.getTargetType() == MarketTargetType.CARDS) {
            return CARDS;
        }
        if (market.getTargetType() == MarketTargetType.GOALS
                || market.getMarketFamily() == MarketType.TOTAL_GOALS
                || market.getMarketFamily() == MarketType.TEAM_TOTAL_GOALS
                || market.getMarketFamily() == MarketType.BOTH_TEAMS_TO_SCORE
                || market.getMarketFamily() == MarketType.CLEAN_SHEET) {
            return GOALS;
        }
        return RESULTS;
    }
}
