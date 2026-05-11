package com.lab.edms.signature;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP+userId 조합 기준 서명 요청 Rate Limiter.
 * 5회/분 초과 시 TooManyRequestsException 유발 대상.
 *
 * 21 CFR Part 11 — 브루트포스 방어 1차 방어선 (lockout 카운터보다 먼저 동작).
 */
@Component
public class SignatureRateLimiter {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(5)
                .refillIntervally(5, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * 요청 허용 여부를 반환한다.
     *
     * @param userId   인증된 사용자 ID
     * @param clientIp 클라이언트 IP 주소
     * @return true: 허용, false: rate limit 초과
     */
    public boolean tryConsume(String userId, String clientIp) {
        String key = userId + ":" + clientIp;
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket());
        return bucket.tryConsume(1);
    }
}
