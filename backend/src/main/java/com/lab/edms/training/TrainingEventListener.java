package com.lab.edms.training;

import com.lab.edms.workflow.event.EffectiveTransitionedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class TrainingEventListener {

    private static final org.slf4j.Logger log =
        org.slf4j.LoggerFactory.getLogger(TrainingEventListener.class);

    private final TrainingService trainingService;

    public TrainingEventListener(TrainingService trainingService) {
        this.trainingService = trainingService;
    }

    @EventListener
    @Async
    public void onEffectiveTransitioned(EffectiveTransitionedEvent event) {
        try {
            trainingService.createAssignmentsForVersion(event);
        } catch (Exception e) {
            log.error("교육 과제 생성 실패: versionId={}", event.documentVersionId(), e);
        }
    }
}
