package com.lab.edms.lifecycle;

public enum DocumentState {
    DRAFT,
    UNDER_REVIEW,
    UNDER_APPROVAL,
    EFFECTIVE,
    UNDER_REVISION,
    SUPERSEDED,
    RETIRED;

    public static DocumentState parse(String s) {
        return DocumentState.valueOf(s.toUpperCase(java.util.Locale.ROOT));
    }

    public boolean isInFlight() {
        return this == DRAFT || this == UNDER_REVIEW
            || this == UNDER_APPROVAL || this == UNDER_REVISION;
    }

    public boolean isReadOnly() {
        return this == EFFECTIVE || this == SUPERSEDED || this == RETIRED;
    }
}
