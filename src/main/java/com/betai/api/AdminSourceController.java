package com.betai.api;

import com.betai.api.dto.RawSnapshotResponse;
import com.betai.api.dto.SourceTargetRequest;
import com.betai.api.dto.SourceTargetResponse;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.source.SourceType;
import com.betai.service.RawSnapshotQueryService;
import com.betai.service.SourceTargetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminSourceController {

    private final SourceTargetService sourceTargetService;
    private final RawSnapshotQueryService rawSnapshotQueryService;

    @PostMapping("/sources")
    public ResponseEntity<SourceTargetResponse> createSourceTarget(@Valid @RequestBody SourceTargetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sourceTargetService.create(request));
    }

    @PutMapping("/sources/{id}")
    public ResponseEntity<SourceTargetResponse> updateSourceTarget(
            @PathVariable UUID id,
            @Valid @RequestBody SourceTargetRequest request
    ) {
        return ResponseEntity.ok(sourceTargetService.update(id, request));
    }

    @GetMapping("/sources/{id}")
    public ResponseEntity<SourceTargetResponse> getSourceTarget(@PathVariable UUID id) {
        return ResponseEntity.ok(sourceTargetService.get(id));
    }

    @PatchMapping("/sources/{id}/active")
    public ResponseEntity<SourceTargetResponse> setSourceTargetActive(
            @PathVariable UUID id,
            @RequestParam boolean active
    ) {
        return ResponseEntity.ok(sourceTargetService.setActive(id, active));
    }

    @GetMapping("/sources")
    public ResponseEntity<List<SourceTargetResponse>> listSourceTargets(
            @RequestParam Optional<LeagueCode> leagueCode,
            @RequestParam Optional<SourceType> sourceType
    ) {
        return ResponseEntity.ok(sourceTargetService.list(leagueCode, sourceType));
    }

    @GetMapping("/raw-snapshots")
    public ResponseEntity<List<RawSnapshotResponse>> listRawSnapshots(
            @RequestParam LeagueCode leagueCode,
            @RequestParam LocalDate snapshotDate
    ) {
        return ResponseEntity.ok(rawSnapshotQueryService.findRecentSnapshots(leagueCode, snapshotDate));
    }
}
