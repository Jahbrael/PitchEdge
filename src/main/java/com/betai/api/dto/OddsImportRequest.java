package com.betai.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OddsImportRequest(
        @Valid @NotEmpty @Size(max = 500) List<OddsImportItem> odds,
        Boolean recalculateExistingSelections
) {
}
