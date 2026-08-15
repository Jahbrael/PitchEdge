package com.betai.api.dto;

import com.betai.domain.extraction.ExtractionValidationError;

public record ExtractionValidationErrorResponse(
        Integer rowNumber,
        String fieldName,
        String errorCode,
        String errorMessage
) {
    public static ExtractionValidationErrorResponse from(ExtractionValidationError error) {
        return new ExtractionValidationErrorResponse(
                error.getRowNumber(),
                error.getFieldName(),
                error.getErrorCode(),
                error.getErrorMessage()
        );
    }
}
