package com.betai.domain.prediction;

import com.betai.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(
        name = "prediction_form_runs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "ux_prediction_form_runs_request_id",
                        columnNames = "request_id"
                )
        },
        indexes = {
                @Index(name = "idx_prediction_form_runs_generated_at", columnList = "generated_at"),
                @Index(name = "idx_prediction_form_runs_fixture_window", columnList = "fixture_date_from, fixture_date_to")
        }
)
public class PredictionFormRun extends BaseEntity {

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    @Column(name = "fixture_date_from")
    private LocalDate fixtureDateFrom;

    @Column(name = "fixture_date_to")
    private LocalDate fixtureDateTo;

    @Column(length = 80)
    private String modelVersion;

    @Column(length = 40)
    private String strategy;

    private Integer fixturesConsidered;

    private Integer returnedSelections;

    @Column(length = 40)
    private String status;

    @Column(columnDefinition = "text", nullable = false)
    private String responseJson;
}
