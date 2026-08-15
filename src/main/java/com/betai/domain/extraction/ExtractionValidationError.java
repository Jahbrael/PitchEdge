package com.betai.domain.extraction;

import com.betai.domain.common.BaseEntity;
import com.betai.domain.snapshot.RawSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(
        name = "extraction_validation_errors",
        indexes = {
                @Index(name = "idx_extraction_validation_errors_run", columnList = "extraction_run_id"),
                @Index(name = "idx_extraction_validation_errors_snapshot", columnList = "raw_snapshot_id")
        }
)
public class ExtractionValidationError extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "extraction_run_id", nullable = false)
    private ExtractionRun extractionRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "raw_snapshot_id", nullable = false)
    private RawSnapshot rawSnapshot;

    @Column
    private Integer rowNumber;

    @Column(length = 120)
    private String fieldName;

    @Column(nullable = false, length = 80)
    private String errorCode;

    @Column(nullable = false, length = 1000)
    private String errorMessage;

    @Column(columnDefinition = "text")
    private String rawRecordJson;
}
