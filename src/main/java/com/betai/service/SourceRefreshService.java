package com.betai.service;

import com.betai.domain.league.League;
import com.betai.domain.refresh.DataRefreshLog;
import com.betai.scraping.ScrapeRunSummary;

import java.time.LocalDate;

public interface SourceRefreshService {

    ScrapeRunSummary refreshLeagueSources(League league, DataRefreshLog refreshLog, LocalDate refreshDate);
}
