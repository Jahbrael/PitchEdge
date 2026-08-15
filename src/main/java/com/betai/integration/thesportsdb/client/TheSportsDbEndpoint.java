package com.betai.integration.thesportsdb.client;

public enum TheSportsDbEndpoint {
    ALL_LEAGUES("all/leagues"),
    LOOKUP_LEAGUE("lookup/league/%s"),
    LIST_TEAMS("list/teams/%s"),
    LIST_SEASONS("list/seasons/%s"),
    SCHEDULE_LEAGUE("schedule/league/%s/%s"),
    LOOKUP_EVENT("lookup/event/%s"),
    LOOKUP_EVENT_STATS("lookup/event_stats/%s"),
    LOOKUP_EVENT_LINEUP("lookup/event_lineup/%s"),
    LOOKUP_EVENT_TIMELINE("lookup/event_timeline/%s"),
    LOOKUP_EVENT_RESULTS("lookup/event_results/%s"),
    LOOKUP_PLAYER("lookup/player/%s"),
    LOOKUP_PLAYER_STATS("lookup/player_stats/%s"),
    LOOKUP_PLAYER_RESULTS("lookup/player_results/%s"),
    LIVESCORE_SOCCER("livescore/soccer"),
    LIVESCORE_LEAGUE("livescore/%s");

    private final String pathTemplate;

    TheSportsDbEndpoint(String pathTemplate) {
        this.pathTemplate = pathTemplate;
    }

    public String path(Object... args) {
        return args == null || args.length == 0 ? pathTemplate : pathTemplate.formatted(args);
    }
}
