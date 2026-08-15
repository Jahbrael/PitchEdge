package com.betai.service;

import com.betai.api.dto.BacktestRequest;
import com.betai.api.dto.BacktestResponse;

public interface BacktestService {

    BacktestResponse runBacktest(BacktestRequest request);
}
