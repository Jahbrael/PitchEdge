package com.betai.repository;

import com.betai.domain.pipeline.PipelineRun;
import com.betai.domain.pipeline.PipelineStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PipelineRunRepository extends JpaRepository<PipelineRun, UUID> {

    List<PipelineRun> findTop10ByOrderByStartedAtDesc();

    List<PipelineRun> findByPipelineStatus(PipelineStatus pipelineStatus);
}
