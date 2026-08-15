package com.betai.service;

import com.betai.api.dto.SourceTargetRequest;
import com.betai.api.dto.SourceTargetResponse;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.source.SourceType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceTargetService {

    SourceTargetResponse create(SourceTargetRequest request);

    SourceTargetResponse update(UUID id, SourceTargetRequest request);

    SourceTargetResponse get(UUID id);

    SourceTargetResponse setActive(UUID id, boolean active);

    List<SourceTargetResponse> list(Optional<LeagueCode> leagueCode, Optional<SourceType> sourceType);
}
