package com.betai.service;

import com.betai.api.dto.FootballDataFixtureSourceRegistrationRequest;
import com.betai.api.dto.FootballDataFixtureSourceRegistrationResponse;

public interface FootballDataFixtureSourceService {

    FootballDataFixtureSourceRegistrationResponse registerLatestFixtureSources(
            FootballDataFixtureSourceRegistrationRequest request
    );
}
