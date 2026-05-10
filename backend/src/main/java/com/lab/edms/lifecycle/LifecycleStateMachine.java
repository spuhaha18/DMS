package com.lab.edms.lifecycle;

import java.util.*;

@org.springframework.stereotype.Component
public class LifecycleStateMachine {

    private static final Map<DocumentState, Map<TransitionId, DocumentState>> TABLE;

    static {
        TABLE = Map.of(
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
    }

    public DocumentState apply(DocumentState current, TransitionId transition) {
        var next = TABLE.getOrDefault(current, Map.of()).get(transition);
        if (next == null) {
            throw new IllegalTransitionException(current, transition);
        }
        return next;
    }

    public Set<TransitionId> allowedFrom(DocumentState current) {
        return Collections.unmodifiableSet(TABLE.getOrDefault(current, Map.of()).keySet());
    }
}
