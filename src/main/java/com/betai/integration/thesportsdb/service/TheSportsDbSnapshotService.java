package com.betai.integration.thesportsdb.service;

import com.betai.domain.snapshot.RawSnapshot;
import com.betai.integration.thesportsdb.client.TheSportsDbClientResponse;

public interface TheSportsDbSnapshotService {

    RawSnapshot persist(TheSportsDbClientResponse response, TheSportsDbSnapshotMetadata metadata);
}
