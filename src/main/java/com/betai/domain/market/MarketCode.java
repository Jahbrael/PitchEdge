package com.betai.domain.market;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public enum MarketCode {
    HOME_WIN("Home Win", MarketType.MATCH_RESULT, MarketDirection.HOME, null, MarketPeriod.FULL_TIME, MarketTeamScope.HOME_TEAM, MarketTargetType.RESULT, 10, "Home team wins in regular time"),
    DRAW("Draw", MarketType.MATCH_RESULT, MarketDirection.DRAW, null, MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.RESULT, 10, "Match ends level in regular time"),
    AWAY_WIN("Away Win", MarketType.MATCH_RESULT, MarketDirection.AWAY, null, MarketPeriod.FULL_TIME, MarketTeamScope.AWAY_TEAM, MarketTargetType.RESULT, 10, "Away team wins in regular time"),
    HOME_OR_DRAW("Home Or Draw", MarketType.DOUBLE_CHANCE, MarketDirection.HOME, null, MarketPeriod.FULL_TIME, MarketTeamScope.HOME_TEAM, MarketTargetType.RESULT, 10, "Home team wins or the match is drawn"),
    AWAY_OR_DRAW("Away Or Draw", MarketType.DOUBLE_CHANCE, MarketDirection.AWAY, null, MarketPeriod.FULL_TIME, MarketTeamScope.AWAY_TEAM, MarketTargetType.RESULT, 10, "Away team wins or the match is drawn"),
    HOME_OR_AWAY("Home Or Away", MarketType.DOUBLE_CHANCE, MarketDirection.HOME, null, MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.RESULT, 10, "Either team wins and the match is not drawn"),
    HOME_DRAW_NO_BET("Home Draw No Bet", MarketType.DRAW_NO_BET, MarketDirection.HOME, null, MarketPeriod.FULL_TIME, MarketTeamScope.HOME_TEAM, MarketTargetType.RESULT, 10, "Home team wins; draw returns void"),
    AWAY_DRAW_NO_BET("Away Draw No Bet", MarketType.DRAW_NO_BET, MarketDirection.AWAY, null, MarketPeriod.FULL_TIME, MarketTeamScope.AWAY_TEAM, MarketTargetType.RESULT, 10, "Away team wins; draw returns void"),

    OVER_0_5_GOALS("Over 0.5 Goals", MarketType.TOTAL_GOALS, MarketDirection.OVER, "0.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.GOALS, 10, "Total match goals greater than 0.5"),
    OVER_1_5_GOALS("Over 1.5 Goals", MarketType.TOTAL_GOALS, MarketDirection.OVER, "1.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.GOALS, 10, "Total match goals greater than 1.5"),
    OVER_2_5_GOALS("Over 2.5 Goals", MarketType.TOTAL_GOALS, MarketDirection.OVER, "2.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.GOALS, 10, "Total match goals greater than 2.5"),
    OVER_3_5_GOALS("Over 3.5 Goals", MarketType.TOTAL_GOALS, MarketDirection.OVER, "3.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.GOALS, 10, "Total match goals greater than 3.5"),
    OVER_4_5_GOALS("Over 4.5 Goals", MarketType.TOTAL_GOALS, MarketDirection.OVER, "4.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.GOALS, 10, "Total match goals greater than 4.5"),
    OVER_5_5_GOALS("Over 5.5 Goals", MarketType.TOTAL_GOALS, MarketDirection.OVER, "5.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.GOALS, 10, "Total match goals greater than 5.5", false, true, false, false, false),
    OVER_6_5_GOALS("Over 6.5 Goals", MarketType.TOTAL_GOALS, MarketDirection.OVER, "6.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.GOALS, 10, "Total match goals greater than 6.5", false, true, false, false, false),
    OVER_7_5_GOALS("Over 7.5 Goals", MarketType.TOTAL_GOALS, MarketDirection.OVER, "7.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.GOALS, 10, "Total match goals greater than 7.5", false, true, false, false, false),
    UNDER_0_5_GOALS("Under 0.5 Goals", MarketType.TOTAL_GOALS, MarketDirection.UNDER, "0.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.GOALS, 10, "Total match goals lower than 0.5", false, true, false, false, false),
    UNDER_1_5_GOALS("Under 1.5 Goals", MarketType.TOTAL_GOALS, MarketDirection.UNDER, "1.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.GOALS, 10, "Total match goals lower than 1.5"),
    UNDER_2_5_GOALS("Under 2.5 Goals", MarketType.TOTAL_GOALS, MarketDirection.UNDER, "2.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.GOALS, 10, "Total match goals lower than 2.5"),
    UNDER_3_5_GOALS("Under 3.5 Goals", MarketType.TOTAL_GOALS, MarketDirection.UNDER, "3.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.GOALS, 10, "Total match goals lower than 3.5"),
    UNDER_4_5_GOALS("Under 4.5 Goals", MarketType.TOTAL_GOALS, MarketDirection.UNDER, "4.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.GOALS, 10, "Total match goals lower than 4.5"),
    UNDER_5_5_GOALS("Under 5.5 Goals", MarketType.TOTAL_GOALS, MarketDirection.UNDER, "5.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.GOALS, 10, "Total match goals lower than 5.5", false, true, false, false, false),
    UNDER_6_5_GOALS("Under 6.5 Goals", MarketType.TOTAL_GOALS, MarketDirection.UNDER, "6.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.GOALS, 10, "Total match goals lower than 6.5", false, true, false, false, false),
    UNDER_7_5_GOALS("Under 7.5 Goals", MarketType.TOTAL_GOALS, MarketDirection.UNDER, "7.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.GOALS, 10, "Total match goals lower than 7.5", false, true, false, false, false),

    HOME_TEAM_OVER_0_5_GOALS("Home Team Over 0.5 Goals", MarketType.TEAM_TOTAL_GOALS, MarketDirection.OVER, "0.5", MarketPeriod.FULL_TIME, MarketTeamScope.HOME_TEAM, MarketTargetType.GOALS, 10, "Home team goals greater than 0.5"),
    HOME_TEAM_OVER_1_5_GOALS("Home Team Over 1.5 Goals", MarketType.TEAM_TOTAL_GOALS, MarketDirection.OVER, "1.5", MarketPeriod.FULL_TIME, MarketTeamScope.HOME_TEAM, MarketTargetType.GOALS, 10, "Home team goals greater than 1.5"),
    HOME_TEAM_OVER_2_5_GOALS("Home Team Over 2.5 Goals", MarketType.TEAM_TOTAL_GOALS, MarketDirection.OVER, "2.5", MarketPeriod.FULL_TIME, MarketTeamScope.HOME_TEAM, MarketTargetType.GOALS, 10, "Home team goals greater than 2.5"),
    HOME_TEAM_OVER_3_5_GOALS("Home Team Over 3.5 Goals", MarketType.TEAM_TOTAL_GOALS, MarketDirection.OVER, "3.5", MarketPeriod.FULL_TIME, MarketTeamScope.HOME_TEAM, MarketTargetType.GOALS, 10, "Home team goals greater than 3.5", false, true, false, false, false),
    HOME_TEAM_OVER_4_5_GOALS("Home Team Over 4.5 Goals", MarketType.TEAM_TOTAL_GOALS, MarketDirection.OVER, "4.5", MarketPeriod.FULL_TIME, MarketTeamScope.HOME_TEAM, MarketTargetType.GOALS, 10, "Home team goals greater than 4.5", false, true, false, false, false),
    HOME_TEAM_UNDER_1_5_GOALS("Home Team Under 1.5 Goals", MarketType.TEAM_TOTAL_GOALS, MarketDirection.UNDER, "1.5", MarketPeriod.FULL_TIME, MarketTeamScope.HOME_TEAM, MarketTargetType.GOALS, 10, "Home team goals lower than 1.5"),
    HOME_TEAM_UNDER_2_5_GOALS("Home Team Under 2.5 Goals", MarketType.TEAM_TOTAL_GOALS, MarketDirection.UNDER, "2.5", MarketPeriod.FULL_TIME, MarketTeamScope.HOME_TEAM, MarketTargetType.GOALS, 10, "Home team goals lower than 2.5"),
    HOME_TEAM_UNDER_3_5_GOALS("Home Team Under 3.5 Goals", MarketType.TEAM_TOTAL_GOALS, MarketDirection.UNDER, "3.5", MarketPeriod.FULL_TIME, MarketTeamScope.HOME_TEAM, MarketTargetType.GOALS, 10, "Home team goals lower than 3.5", false, true, false, false, false),
    HOME_TEAM_UNDER_4_5_GOALS("Home Team Under 4.5 Goals", MarketType.TEAM_TOTAL_GOALS, MarketDirection.UNDER, "4.5", MarketPeriod.FULL_TIME, MarketTeamScope.HOME_TEAM, MarketTargetType.GOALS, 10, "Home team goals lower than 4.5", false, true, false, false, false),
    AWAY_TEAM_OVER_0_5_GOALS("Away Team Over 0.5 Goals", MarketType.TEAM_TOTAL_GOALS, MarketDirection.OVER, "0.5", MarketPeriod.FULL_TIME, MarketTeamScope.AWAY_TEAM, MarketTargetType.GOALS, 10, "Away team goals greater than 0.5"),
    AWAY_TEAM_OVER_1_5_GOALS("Away Team Over 1.5 Goals", MarketType.TEAM_TOTAL_GOALS, MarketDirection.OVER, "1.5", MarketPeriod.FULL_TIME, MarketTeamScope.AWAY_TEAM, MarketTargetType.GOALS, 10, "Away team goals greater than 1.5"),
    AWAY_TEAM_OVER_2_5_GOALS("Away Team Over 2.5 Goals", MarketType.TEAM_TOTAL_GOALS, MarketDirection.OVER, "2.5", MarketPeriod.FULL_TIME, MarketTeamScope.AWAY_TEAM, MarketTargetType.GOALS, 10, "Away team goals greater than 2.5"),
    AWAY_TEAM_OVER_3_5_GOALS("Away Team Over 3.5 Goals", MarketType.TEAM_TOTAL_GOALS, MarketDirection.OVER, "3.5", MarketPeriod.FULL_TIME, MarketTeamScope.AWAY_TEAM, MarketTargetType.GOALS, 10, "Away team goals greater than 3.5", false, true, false, false, false),
    AWAY_TEAM_OVER_4_5_GOALS("Away Team Over 4.5 Goals", MarketType.TEAM_TOTAL_GOALS, MarketDirection.OVER, "4.5", MarketPeriod.FULL_TIME, MarketTeamScope.AWAY_TEAM, MarketTargetType.GOALS, 10, "Away team goals greater than 4.5", false, true, false, false, false),
    AWAY_TEAM_UNDER_1_5_GOALS("Away Team Under 1.5 Goals", MarketType.TEAM_TOTAL_GOALS, MarketDirection.UNDER, "1.5", MarketPeriod.FULL_TIME, MarketTeamScope.AWAY_TEAM, MarketTargetType.GOALS, 10, "Away team goals lower than 1.5"),
    AWAY_TEAM_UNDER_2_5_GOALS("Away Team Under 2.5 Goals", MarketType.TEAM_TOTAL_GOALS, MarketDirection.UNDER, "2.5", MarketPeriod.FULL_TIME, MarketTeamScope.AWAY_TEAM, MarketTargetType.GOALS, 10, "Away team goals lower than 2.5"),
    AWAY_TEAM_UNDER_3_5_GOALS("Away Team Under 3.5 Goals", MarketType.TEAM_TOTAL_GOALS, MarketDirection.UNDER, "3.5", MarketPeriod.FULL_TIME, MarketTeamScope.AWAY_TEAM, MarketTargetType.GOALS, 10, "Away team goals lower than 3.5", false, true, false, false, false),
    AWAY_TEAM_UNDER_4_5_GOALS("Away Team Under 4.5 Goals", MarketType.TEAM_TOTAL_GOALS, MarketDirection.UNDER, "4.5", MarketPeriod.FULL_TIME, MarketTeamScope.AWAY_TEAM, MarketTargetType.GOALS, 10, "Away team goals lower than 4.5", false, true, false, false, false),

    FIRST_HALF_OVER_0_5_GOALS("First Half Over 0.5 Goals", MarketType.TOTAL_GOALS, MarketDirection.OVER, "0.5", MarketPeriod.FIRST_HALF, MarketTeamScope.MATCH, MarketTargetType.GOALS, 30, "First-half goals greater than 0.5", false, true, false, true, false),
    FIRST_HALF_OVER_1_5_GOALS("First Half Over 1.5 Goals", MarketType.TOTAL_GOALS, MarketDirection.OVER, "1.5", MarketPeriod.FIRST_HALF, MarketTeamScope.MATCH, MarketTargetType.GOALS, 30, "First-half goals greater than 1.5", false, true, false, true, false),
    FIRST_HALF_UNDER_1_5_GOALS("First Half Under 1.5 Goals", MarketType.TOTAL_GOALS, MarketDirection.UNDER, "1.5", MarketPeriod.FIRST_HALF, MarketTeamScope.MATCH, MarketTargetType.GOALS, 30, "First-half goals lower than 1.5", false, true, false, true, false),
    SECOND_HALF_OVER_0_5_GOALS("Second Half Over 0.5 Goals", MarketType.TOTAL_GOALS, MarketDirection.OVER, "0.5", MarketPeriod.SECOND_HALF, MarketTeamScope.MATCH, MarketTargetType.GOALS, 30, "Second-half goals greater than 0.5", false, true, false, true, false),
    SECOND_HALF_OVER_1_5_GOALS("Second Half Over 1.5 Goals", MarketType.TOTAL_GOALS, MarketDirection.OVER, "1.5", MarketPeriod.SECOND_HALF, MarketTeamScope.MATCH, MarketTargetType.GOALS, 30, "Second-half goals greater than 1.5", false, true, false, true, false),
    SECOND_HALF_UNDER_1_5_GOALS("Second Half Under 1.5 Goals", MarketType.TOTAL_GOALS, MarketDirection.UNDER, "1.5", MarketPeriod.SECOND_HALF, MarketTeamScope.MATCH, MarketTargetType.GOALS, 30, "Second-half goals lower than 1.5", false, true, false, true, false),

    BTTS_YES("Both Teams To Score", MarketType.BOTH_TEAMS_TO_SCORE, MarketDirection.YES, null, MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.GOALS, 10, "Both teams score at least one goal"),
    BTTS_NO("Both Teams Not To Score", MarketType.BOTH_TEAMS_TO_SCORE, MarketDirection.NO, null, MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.GOALS, 10, "At least one team fails to score"),
    HOME_TEAM_TO_SCORE_FIRST("Home Team To Score First", MarketType.TEAM_TO_SCORE_FIRST, MarketDirection.HOME, null, MarketPeriod.FULL_TIME, MarketTeamScope.HOME_TEAM, MarketTargetType.EVENT, 30, "Home team scores the first goal", false, true, false, false, true),
    AWAY_TEAM_TO_SCORE_FIRST("Away Team To Score First", MarketType.TEAM_TO_SCORE_FIRST, MarketDirection.AWAY, null, MarketPeriod.FULL_TIME, MarketTeamScope.AWAY_TEAM, MarketTargetType.EVENT, 30, "Away team scores the first goal", false, true, false, false, true),
    HOME_TEAM_CLEAN_SHEET("Home Team Clean Sheet", MarketType.CLEAN_SHEET, MarketDirection.YES, null, MarketPeriod.FULL_TIME, MarketTeamScope.HOME_TEAM, MarketTargetType.GOALS, 10, "Away team scores zero goals"),
    AWAY_TEAM_CLEAN_SHEET("Away Team Clean Sheet", MarketType.CLEAN_SHEET, MarketDirection.YES, null, MarketPeriod.FULL_TIME, MarketTeamScope.AWAY_TEAM, MarketTargetType.GOALS, 10, "Home team scores zero goals"),
    GOAL_IN_BOTH_HALVES("Goal In Both Halves", MarketType.GOAL_PERIOD, MarketDirection.YES, null, MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.GOALS, 30, "At least one goal is scored in each half", false, true, false, true, false),
    HOME_TEAM_TO_WIN_EITHER_HALF("Home Team To Win Either Half", MarketType.TEAM_TO_WIN_PERIOD, MarketDirection.HOME, null, MarketPeriod.FULL_TIME, MarketTeamScope.HOME_TEAM, MarketTargetType.GOALS, 30, "Home team wins at least one half", false, true, false, true, false),
    AWAY_TEAM_TO_WIN_EITHER_HALF("Away Team To Win Either Half", MarketType.TEAM_TO_WIN_PERIOD, MarketDirection.AWAY, null, MarketPeriod.FULL_TIME, MarketTeamScope.AWAY_TEAM, MarketTargetType.GOALS, 30, "Away team wins at least one half", false, true, false, true, false),

    CORNERS_OVER_5_5("Corners Over 5.5", MarketType.TOTAL_CORNERS, MarketDirection.OVER, "5.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CORNERS, 10, "Total match corners greater than 5.5"),
    CORNERS_OVER_6_5("Corners Over 6.5", MarketType.TOTAL_CORNERS, MarketDirection.OVER, "6.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CORNERS, 10, "Total match corners greater than 6.5"),
    CORNERS_OVER_7_5("Corners Over 7.5", MarketType.TOTAL_CORNERS, MarketDirection.OVER, "7.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CORNERS, 10, "Total match corners greater than 7.5"),
    CORNERS_OVER_8_5("Corners Over 8.5", MarketType.TOTAL_CORNERS, MarketDirection.OVER, "8.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CORNERS, 10, "Total match corners greater than 8.5"),
    CORNERS_OVER_9_5("Corners Over 9.5", MarketType.TOTAL_CORNERS, MarketDirection.OVER, "9.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CORNERS, 10, "Total match corners greater than 9.5"),
    CORNERS_OVER_10_5("Corners Over 10.5", MarketType.TOTAL_CORNERS, MarketDirection.OVER, "10.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CORNERS, 10, "Total match corners greater than 10.5"),
    CORNERS_OVER_11_5("Corners Over 11.5", MarketType.TOTAL_CORNERS, MarketDirection.OVER, "11.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CORNERS, 10, "Total match corners greater than 11.5"),
    CORNERS_OVER_12_5("Corners Over 12.5", MarketType.TOTAL_CORNERS, MarketDirection.OVER, "12.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CORNERS, 10, "Total match corners greater than 12.5"),
    CORNERS_OVER_13_5("Corners Over 13.5", MarketType.TOTAL_CORNERS, MarketDirection.OVER, "13.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CORNERS, 10, "Total match corners greater than 13.5"),
    CORNERS_UNDER_5_5("Corners Under 5.5", MarketType.TOTAL_CORNERS, MarketDirection.UNDER, "5.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CORNERS, 10, "Total match corners lower than 5.5"),
    CORNERS_UNDER_6_5("Corners Under 6.5", MarketType.TOTAL_CORNERS, MarketDirection.UNDER, "6.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CORNERS, 10, "Total match corners lower than 6.5"),
    CORNERS_UNDER_7_5("Corners Under 7.5", MarketType.TOTAL_CORNERS, MarketDirection.UNDER, "7.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CORNERS, 10, "Total match corners lower than 7.5"),
    CORNERS_UNDER_8_5("Corners Under 8.5", MarketType.TOTAL_CORNERS, MarketDirection.UNDER, "8.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CORNERS, 10, "Total match corners lower than 8.5"),
    CORNERS_UNDER_9_5("Corners Under 9.5", MarketType.TOTAL_CORNERS, MarketDirection.UNDER, "9.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CORNERS, 10, "Total match corners lower than 9.5"),
    CORNERS_UNDER_10_5("Corners Under 10.5", MarketType.TOTAL_CORNERS, MarketDirection.UNDER, "10.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CORNERS, 10, "Total match corners lower than 10.5"),
    CORNERS_UNDER_11_5("Corners Under 11.5", MarketType.TOTAL_CORNERS, MarketDirection.UNDER, "11.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CORNERS, 10, "Total match corners lower than 11.5"),
    CORNERS_UNDER_12_5("Corners Under 12.5", MarketType.TOTAL_CORNERS, MarketDirection.UNDER, "12.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CORNERS, 10, "Total match corners lower than 12.5"),
    CORNERS_UNDER_13_5("Corners Under 13.5", MarketType.TOTAL_CORNERS, MarketDirection.UNDER, "13.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CORNERS, 10, "Total match corners lower than 13.5"),
    HOME_TEAM_CORNERS_OVER_2_5("Home Team Corners Over 2.5", MarketType.TEAM_CORNERS, MarketDirection.OVER, "2.5", MarketPeriod.FULL_TIME, MarketTeamScope.HOME_TEAM, MarketTargetType.CORNERS, 10, "Home team corners greater than 2.5"),
    HOME_TEAM_CORNERS_OVER_3_5("Home Team Corners Over 3.5", MarketType.TEAM_CORNERS, MarketDirection.OVER, "3.5", MarketPeriod.FULL_TIME, MarketTeamScope.HOME_TEAM, MarketTargetType.CORNERS, 10, "Home team corners greater than 3.5"),
    HOME_TEAM_CORNERS_OVER_4_5("Home Team Corners Over 4.5", MarketType.TEAM_CORNERS, MarketDirection.OVER, "4.5", MarketPeriod.FULL_TIME, MarketTeamScope.HOME_TEAM, MarketTargetType.CORNERS, 10, "Home team corners greater than 4.5"),
    HOME_TEAM_CORNERS_OVER_5_5("Home Team Corners Over 5.5", MarketType.TEAM_CORNERS, MarketDirection.OVER, "5.5", MarketPeriod.FULL_TIME, MarketTeamScope.HOME_TEAM, MarketTargetType.CORNERS, 10, "Home team corners greater than 5.5"),
    HOME_TEAM_CORNERS_UNDER_2_5("Home Team Corners Under 2.5", MarketType.TEAM_CORNERS, MarketDirection.UNDER, "2.5", MarketPeriod.FULL_TIME, MarketTeamScope.HOME_TEAM, MarketTargetType.CORNERS, 10, "Home team corners lower than 2.5"),
    HOME_TEAM_CORNERS_UNDER_3_5("Home Team Corners Under 3.5", MarketType.TEAM_CORNERS, MarketDirection.UNDER, "3.5", MarketPeriod.FULL_TIME, MarketTeamScope.HOME_TEAM, MarketTargetType.CORNERS, 10, "Home team corners lower than 3.5"),
    HOME_TEAM_CORNERS_UNDER_4_5("Home Team Corners Under 4.5", MarketType.TEAM_CORNERS, MarketDirection.UNDER, "4.5", MarketPeriod.FULL_TIME, MarketTeamScope.HOME_TEAM, MarketTargetType.CORNERS, 10, "Home team corners lower than 4.5"),
    HOME_TEAM_CORNERS_UNDER_5_5("Home Team Corners Under 5.5", MarketType.TEAM_CORNERS, MarketDirection.UNDER, "5.5", MarketPeriod.FULL_TIME, MarketTeamScope.HOME_TEAM, MarketTargetType.CORNERS, 10, "Home team corners lower than 5.5"),
    AWAY_TEAM_CORNERS_OVER_2_5("Away Team Corners Over 2.5", MarketType.TEAM_CORNERS, MarketDirection.OVER, "2.5", MarketPeriod.FULL_TIME, MarketTeamScope.AWAY_TEAM, MarketTargetType.CORNERS, 10, "Away team corners greater than 2.5"),
    AWAY_TEAM_CORNERS_OVER_3_5("Away Team Corners Over 3.5", MarketType.TEAM_CORNERS, MarketDirection.OVER, "3.5", MarketPeriod.FULL_TIME, MarketTeamScope.AWAY_TEAM, MarketTargetType.CORNERS, 10, "Away team corners greater than 3.5"),
    AWAY_TEAM_CORNERS_OVER_4_5("Away Team Corners Over 4.5", MarketType.TEAM_CORNERS, MarketDirection.OVER, "4.5", MarketPeriod.FULL_TIME, MarketTeamScope.AWAY_TEAM, MarketTargetType.CORNERS, 10, "Away team corners greater than 4.5"),
    AWAY_TEAM_CORNERS_OVER_5_5("Away Team Corners Over 5.5", MarketType.TEAM_CORNERS, MarketDirection.OVER, "5.5", MarketPeriod.FULL_TIME, MarketTeamScope.AWAY_TEAM, MarketTargetType.CORNERS, 10, "Away team corners greater than 5.5"),
    AWAY_TEAM_CORNERS_UNDER_2_5("Away Team Corners Under 2.5", MarketType.TEAM_CORNERS, MarketDirection.UNDER, "2.5", MarketPeriod.FULL_TIME, MarketTeamScope.AWAY_TEAM, MarketTargetType.CORNERS, 10, "Away team corners lower than 2.5"),
    AWAY_TEAM_CORNERS_UNDER_3_5("Away Team Corners Under 3.5", MarketType.TEAM_CORNERS, MarketDirection.UNDER, "3.5", MarketPeriod.FULL_TIME, MarketTeamScope.AWAY_TEAM, MarketTargetType.CORNERS, 10, "Away team corners lower than 3.5"),
    AWAY_TEAM_CORNERS_UNDER_4_5("Away Team Corners Under 4.5", MarketType.TEAM_CORNERS, MarketDirection.UNDER, "4.5", MarketPeriod.FULL_TIME, MarketTeamScope.AWAY_TEAM, MarketTargetType.CORNERS, 10, "Away team corners lower than 4.5"),
    AWAY_TEAM_CORNERS_UNDER_5_5("Away Team Corners Under 5.5", MarketType.TEAM_CORNERS, MarketDirection.UNDER, "5.5", MarketPeriod.FULL_TIME, MarketTeamScope.AWAY_TEAM, MarketTargetType.CORNERS, 10, "Away team corners lower than 5.5"),

    YELLOW_CARDS_OVER_2_5("Yellow Cards Over 2.5", MarketType.TOTAL_YELLOW_CARDS, MarketDirection.OVER, "2.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CARDS, 10, "Total match yellow cards greater than 2.5"),
    YELLOW_CARDS_OVER_3_5("Yellow Cards Over 3.5", MarketType.TOTAL_YELLOW_CARDS, MarketDirection.OVER, "3.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CARDS, 10, "Total match yellow cards greater than 3.5"),
    YELLOW_CARDS_OVER_4_5("Yellow Cards Over 4.5", MarketType.TOTAL_YELLOW_CARDS, MarketDirection.OVER, "4.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CARDS, 10, "Total match yellow cards greater than 4.5"),
    YELLOW_CARDS_OVER_5_5("Yellow Cards Over 5.5", MarketType.TOTAL_YELLOW_CARDS, MarketDirection.OVER, "5.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CARDS, 10, "Total match yellow cards greater than 5.5"),
    YELLOW_CARDS_UNDER_2_5("Yellow Cards Under 2.5", MarketType.TOTAL_YELLOW_CARDS, MarketDirection.UNDER, "2.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CARDS, 10, "Total match yellow cards lower than 2.5"),
    YELLOW_CARDS_UNDER_4_5("Yellow Cards Under 4.5", MarketType.TOTAL_YELLOW_CARDS, MarketDirection.UNDER, "4.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CARDS, 10, "Total match yellow cards lower than 4.5"),
    YELLOW_CARDS_UNDER_5_5("Yellow Cards Under 5.5", MarketType.TOTAL_YELLOW_CARDS, MarketDirection.UNDER, "5.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CARDS, 10, "Total match yellow cards lower than 5.5"),
    RED_CARD_YES("Red Card Yes", MarketType.RED_CARD, MarketDirection.YES, "0.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CARDS, 20, "At least one red card is shown"),
    RED_CARD_NO("Red Card No", MarketType.RED_CARD, MarketDirection.NO, "0.5", MarketPeriod.FULL_TIME, MarketTeamScope.MATCH, MarketTargetType.CARDS, 20, "No red card is shown");

    private final String displayName;
    private final MarketType marketType;
    private final MarketDirection direction;
    private final String selectionValue;
    private final BigDecimal threshold;
    private final MarketPeriod period;
    private final MarketTeamScope teamScope;
    private final MarketTargetType targetType;
    private final boolean requiresTeamData;
    private final boolean requiresPlayerData;
    private final boolean requiresHalfTimeData;
    private final boolean requiresEventData;
    private final boolean requiresOdds;
    private final boolean enabled;
    private final int minimumSampleSize;
    private final String settlementDescription;

    MarketCode(
            String displayName,
            MarketType marketType,
            MarketDirection direction,
            String threshold,
            MarketPeriod period,
            MarketTeamScope teamScope,
            MarketTargetType targetType,
            int minimumSampleSize,
            String settlementDescription
    ) {
        this(displayName, marketType, direction, threshold, period, teamScope, targetType,
                minimumSampleSize, settlementDescription, true, true, false, false, false);
    }

    MarketCode(
            String displayName,
            MarketType marketType,
            MarketDirection direction,
            String threshold,
            MarketPeriod period,
            MarketTeamScope teamScope,
            MarketTargetType targetType,
            int minimumSampleSize,
            String settlementDescription,
            boolean enabled,
            boolean requiresTeamData,
            boolean requiresPlayerData,
            boolean requiresHalfTimeData,
            boolean requiresEventData
    ) {
        this.displayName = displayName;
        this.marketType = marketType;
        this.direction = direction;
        this.selectionValue = selectionValue(direction, teamScope);
        this.threshold = threshold == null ? null : new BigDecimal(threshold);
        this.period = period;
        this.teamScope = teamScope;
        this.targetType = targetType;
        this.requiresTeamData = requiresTeamData;
        this.requiresPlayerData = requiresPlayerData;
        this.requiresHalfTimeData = requiresHalfTimeData;
        this.requiresEventData = requiresEventData;
        this.requiresOdds = false;
        this.enabled = enabled;
        this.minimumSampleSize = minimumSampleSize;
        this.settlementDescription = settlementDescription;
    }

    private static String selectionValue(MarketDirection direction, MarketTeamScope teamScope) {
        if (direction == MarketDirection.HOME || teamScope == MarketTeamScope.HOME_TEAM) {
            return direction == MarketDirection.OVER || direction == MarketDirection.UNDER || direction == MarketDirection.YES
                    ? direction.name()
                    : "HOME";
        }
        if (direction == MarketDirection.AWAY || teamScope == MarketTeamScope.AWAY_TEAM) {
            return direction == MarketDirection.OVER || direction == MarketDirection.UNDER || direction == MarketDirection.YES
                    ? direction.name()
                    : "AWAY";
        }
        return direction.name();
    }
}
