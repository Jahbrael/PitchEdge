package com.betai.service;

import com.betai.domain.odds.OddsSnapshot;
import com.betai.domain.odds.ValueRating;
import com.betai.domain.prediction.PredictionSelection;
import com.betai.repository.OddsSnapshotRepository;
import com.betai.repository.PredictionSelectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OddsValueServiceImpl implements OddsValueService {

    private final OddsSnapshotRepository oddsSnapshotRepository;
    private final PredictionSelectionRepository predictionSelectionRepository;
    private final OddsValueCalculator oddsValueCalculator;
    private final Clock clock;

    @Override
    public void applyBestOdds(PredictionSelection selection) {
        List<OddsSnapshot> currentQuotes = oddsSnapshotRepository.findCurrentBookmakerQuotes(
                selection.getMatch().getId(),
                selection.getMarketDefinition().getId(),
                PageRequest.of(0, 1)
        );
        if (currentQuotes.isEmpty()) {
            clearValue(selection);
            return;
        }
        OddsSnapshot bestQuote = currentQuotes.getFirst();
        OddsValueAssessment assessment = oddsValueCalculator.assess(
                selection.getProbability(),
                bestQuote.getDecimalOdds()
        );
        selection.setBestDecimalOdds(bestQuote.getDecimalOdds())
                .setBestImpliedProbability(assessment.impliedProbability())
                .setValueEdge(assessment.valueEdge())
                .setExpectedValue(assessment.expectedValue())
                .setValueRating(assessment.valueRating())
                .setBestOddsBookmaker(bestQuote.getBookmaker())
                .setBestOddsSnapshot(bestQuote)
                .setOddsCapturedAt(bestQuote.getCapturedAt())
                .setValueAssessedAt(OffsetDateTime.now(clock))
                .setValueNote(assessment.note());
    }

    @Override
    @Transactional
    public int refreshSelectionsForOdds(OddsSnapshot oddsSnapshot) {
        List<PredictionSelection> selections = predictionSelectionRepository.findByMatch_IdAndMarketDefinition_Id(
                oddsSnapshot.getMatch().getId(),
                oddsSnapshot.getMarketDefinition().getId()
        );
        selections.forEach(this::applyBestOdds);
        predictionSelectionRepository.saveAll(selections);
        return selections.size();
    }

    private void clearValue(PredictionSelection selection) {
        selection.setBestDecimalOdds(null)
                .setBestImpliedProbability(null)
                .setValueEdge(null)
                .setExpectedValue(null)
                .setValueRating(ValueRating.NO_ODDS)
                .setBestOddsBookmaker(null)
                .setBestOddsSnapshot(null)
                .setOddsCapturedAt(null)
                .setValueAssessedAt(OffsetDateTime.now(clock))
                .setValueNote("No active odds quote is available for this match and market.");
    }
}
