package com.lab.edms.lifecycle;

public class IllegalTransitionException extends RuntimeException {
    public IllegalTransitionException(DocumentState from, TransitionId transition) {
        super(String.format("허용되지 않는 전이: %s -[%s]-> ?", from, transition));
    }
}
