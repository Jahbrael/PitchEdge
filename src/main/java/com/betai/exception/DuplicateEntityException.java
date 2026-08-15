package com.betai.exception;

public class DuplicateEntityException extends RuntimeException {

    public DuplicateEntityException(
            String entityName,
            String sourceProvider,
            String entityType,
            String leagueCode,
            String externalId,
            int duplicateCount,
            String recommendedFix
    ) {
        super(String.format(
                "Duplicate %s rows found. Expected 1, found %d. Provider: %s, Type: %s, League: %s, External ID: %s. Fix: %s",
                entityName, duplicateCount,
                sourceProvider != null ? sourceProvider : "INTERNAL",
                entityType != null ? entityType : "N/A",
                leagueCode != null ? leagueCode : "N/A",
                externalId != null ? externalId : "N/A",
                recommendedFix
        ));
    }
}
