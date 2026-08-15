package com.betai.service;

import com.betai.api.dto.UpcomingFixtureImportRequest;
import com.betai.api.dto.UpcomingFixtureImportResponse;

public interface UpcomingFixtureImportService {

    UpcomingFixtureImportResponse importFixtures(UpcomingFixtureImportRequest request);
}
