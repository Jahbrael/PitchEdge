package com.betai.service;

import com.betai.api.dto.FixtureDiscoveryRequest;
import com.betai.api.dto.FixtureDiscoveryResponse;

public interface FixtureDiscoveryService {

    FixtureDiscoveryResponse discoverFixtures(FixtureDiscoveryRequest request);
}
