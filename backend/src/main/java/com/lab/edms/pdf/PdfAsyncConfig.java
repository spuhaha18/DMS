package com.lab.edms.pdf;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * M7 PDF 파이프라인 전용 스레드풀.
 *
 * corePoolSize=2, maxPoolSize=4, queueCapacity=200 으로
 * 변환 요청을 순차적으로 처리하면서 급증 시 최대 4개 병렬 변환을 허용한다.
 * threadNamePrefix="pdf-worker-"로 모니터링 시 식별이 용이하다.
 */
@Configuration
public class PdfAsyncConfig {

    @Bean("pdfWorkerExecutor")
    public Executor pdfWorkerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("pdf-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
