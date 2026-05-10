package com.lab.edms.lifecycle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentStateTest {

    @Test
    void parse_roundTrip_allStates() {
        for (DocumentState state : DocumentState.values()) {
            assertThat(DocumentState.parse(state.name())).isEqualTo(state);
        }
    }

    @Test
    void parse_caseInsensitive() {
        assertThat(DocumentState.parse("draft")).isEqualTo(DocumentState.DRAFT);
        assertThat(DocumentState.parse("UNDER_REVIEW")).isEqualTo(DocumentState.UNDER_REVIEW);
        assertThat(DocumentState.parse("effective")).isEqualTo(DocumentState.EFFECTIVE);
    }

    @ParameterizedTest
    @EnumSource(value = DocumentState.class, names = {"DRAFT", "UNDER_REVIEW", "UNDER_APPROVAL", "UNDER_REVISION"})
    void isInFlight_returnsTrue_forInFlightStates(DocumentState state) {
        assertThat(state.isInFlight()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = DocumentState.class, names = {"EFFECTIVE", "SUPERSEDED", "RETIRED"})
    void isInFlight_returnsFalse_forNonInFlightStates(DocumentState state) {
        assertThat(state.isInFlight()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = DocumentState.class, names = {"EFFECTIVE", "SUPERSEDED", "RETIRED"})
    void isReadOnly_returnsTrue_forReadOnlyStates(DocumentState state) {
        assertThat(state.isReadOnly()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = DocumentState.class, names = {"DRAFT", "UNDER_REVIEW", "UNDER_APPROVAL", "UNDER_REVISION"})
    void isReadOnly_returnsFalse_forNonReadOnlyStates(DocumentState state) {
        assertThat(state.isReadOnly()).isFalse();
    }
}
