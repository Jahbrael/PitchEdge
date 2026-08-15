package com.betai.service;

import com.betai.domain.odds.OddsSnapshot;
import com.betai.domain.prediction.PredictionSelection;

public interface OddsValueService {

    void applyBestOdds(PredictionSelection selection);

    int refreshSelectionsForOdds(OddsSnapshot oddsSnapshot);
}
