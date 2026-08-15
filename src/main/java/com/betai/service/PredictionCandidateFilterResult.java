package com.betai.service;

import java.util.List;

record PredictionCandidateFilterResult(
        List<PredictionCandidate> candidates,
        int qualifiedSelectionsFound
) {
}
