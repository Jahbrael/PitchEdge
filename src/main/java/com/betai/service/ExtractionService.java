package com.betai.service;

import com.betai.api.dto.DailyExtractionRequest;
import com.betai.api.dto.DailyExtractionResponse;
import com.betai.api.dto.ExtractionRunResponse;

import java.util.UUID;

public interface ExtractionService {

    ExtractionRunResponse extractRawSnapshot(UUID rawSnapshotId, boolean forceReprocess);

    DailyExtractionResponse extractDailySnapshots(DailyExtractionRequest request);
}
