package com.lab.edms.training;

import com.lab.edms.workflow.event.EffectiveTransitionedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class TrainingEventListener {

    private final TrainingService trainingService;

    public TrainingEventListener(TrainingService trainingService) {
        this.trainingService = trainingService;
    }

    @EventListener
    @Async
    public void onEffectiveTransitioned(EffectiveTransitionedEvent event) {
        trainingService.createAssignmentsForVersion(event);
    }
}
