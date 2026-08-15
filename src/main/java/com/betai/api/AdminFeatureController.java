package com.betai.api;

import com.betai.api.dto.DailyFeatureGenerationRequest;
import com.betai.api.dto.DailyFeatureGenerationResponse;
import com.betai.api.dto.LeagueBaselineResponse;
import com.betai.api.dto.TeamFeatureSnapshotResponse;
import com.betai.domain.league.LeagueCode;
import com.betai.service.FeatureEngineeringService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/features")
public class AdminFeatureController {

    private final FeatureEngineeringService featureEngineeringService;

    @PostMapping("/daily")
    public ResponseEntity<DailyFeatureGenerationResponse> generateDailyFeatures(
            @Valid @RequestBody DailyFeatureGenerationRequest request
    ) {
        return ResponseEntity.ok(featureEngineeringService.generateFeatures(request));
    }

    @GetMapping("/league-baseline")
    public ResponseEntity<LeagueBaselineResponse> getLeagueBaseline(
            @RequestParam LeagueCode leagueCode,
            @RequestParam LocalDate calculationDate
    ) {
        return ResponseEntity.ok(featureEngineeringService.getLeagueBaseline(leagueCode, calculationDate));
    }

    @GetMapping("/team-snapshots")
    public ResponseEntity<List<TeamFeatureSnapshotResponse>> listTeamFeatures(
            @RequestParam LeagueCode leagueCode,
            @RequestParam LocalDate calculationDate
    ) {
        return ResponseEntity.ok(featureEngineeringService.listTeamFeatures(leagueCode, calculationDate));
    }
}
