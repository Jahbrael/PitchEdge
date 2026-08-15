package com.betai.service;

import com.betai.api.dto.DailyOddsExtractionRequest;
import com.betai.api.dto.DailyOddsExtractionResponse;
import com.betai.api.dto.OddsExtractionRunResponse;

import java.util.UUID;

public interface OddsSourceExtractionService {

    OddsExtractionRunResponse extractRawSnapshot(UUID rawSnapshotId, boolean forceReprocess, boolean recalculateExistingSelections);

    DailyOddsExtractionResponse extractDailyOddsSnapshots(DailyOddsExtractionRequest request);
}
