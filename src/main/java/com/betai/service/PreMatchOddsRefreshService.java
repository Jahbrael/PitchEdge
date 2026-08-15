package com.betai.service;

import com.betai.api.dto.PreMatchOddsRefreshRequest;
import com.betai.api.dto.PreMatchOddsRefreshResponse;

public interface PreMatchOddsRefreshService {

    PreMatchOddsRefreshResponse refreshPreMatchOdds(PreMatchOddsRefreshRequest request);
}
