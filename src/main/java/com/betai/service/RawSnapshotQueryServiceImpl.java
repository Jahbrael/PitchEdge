package com.betai.service;

import com.betai.api.dto.RawSnapshotResponse;
import com.betai.domain.league.LeagueCode;
import com.betai.repository.RawSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RawSnapshotQueryServiceImpl implements RawSnapshotQueryService {

    private final RawSnapshotRepository rawSnapshotRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RawSnapshotResponse> findRecentSnapshots(LeagueCode leagueCode, LocalDate snapshotDate) {
        return rawSnapshotRepository.findTop50ByLeague_CodeAndSnapshotDateOrderByCreatedAtDesc(leagueCode, snapshotDate)
                .stream()
                .map(RawSnapshotResponse::from)
                .toList();
    }
}
