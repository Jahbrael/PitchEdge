package com.betai.service;

import com.betai.api.dto.DailyRefreshRequest;
import com.betai.api.dto.DailyRefreshResponse;

public interface DailyRefreshService {

    DailyRefreshResponse triggerDailyRefresh(DailyRefreshRequest request);
}
