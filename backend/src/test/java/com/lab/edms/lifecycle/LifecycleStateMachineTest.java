package com.lab.edms.lifecycle;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LifecycleStateMachineTest {

    private final LifecycleStateMachine sm = new LifecycleStateMachine();

    @ParameterizedTest(name = "{0} -[{1}]-> {2}")
    @MethodSource("allCombinations")
    void 전이매트릭스_전수검증(DocumentState from, TransitionId t, Optional<DocumentState> expected) {
        if (expected.isPresent()) {
            assertThat(sm.apply(from, t)).isEqualTo(expected.get());
        } else {
            assertThatThrownBy(() -> sm.apply(from, t))
                .isInstanceOf(IllegalTransitionException.class);
        }
    }

    static Stream<Arguments> allCombinations() {
        Map<DocumentState, Map<TransitionId, DocumentState>> table = Map.of(
            DocumentState.DRAFT,           Map.of(TransitionId.T_01, DocumentState.UNDER_REVIEW),
            DocumentState.UNDER_REVIEW,    Map.of(TransitionId.T_02, DocumentState.UNDER_APPROVAL,
                                                  TransitionId.T_04, DocumentState.DRAFT),
            DocumentState.UNDER_APPROVAL,  Map.of(TransitionId.T_03, DocumentState.EFFECTIVE,
                                                  TransitionId.T_05, DocumentState.DRAFT),
            DocumentState.EFFECTIVE,       Map.of(TransitionId.T_06, DocumentState.UNDER_REVISION,
                                                  TransitionId.T_08, DocumentState.RETIRED),
            DocumentState.UNDER_REVISION,  Map.of(TransitionId.T_07, DocumentState.SUPERSEDED),
            DocumentState.SUPERSEDED,      Map.of(TransitionId.T_09, DocumentState.RETIRED),
            DocumentState.RETIRED,         Map.of()
        );
        return Arrays.stream(DocumentState.values())
            .flatMap(from -> Arrays.stream(TransitionId.values())
                .map(t -> Arguments.of(from, t, Optional.ofNullable(table.getOrDefault(from, Map.of()).get(t))))
            );
    }
}
