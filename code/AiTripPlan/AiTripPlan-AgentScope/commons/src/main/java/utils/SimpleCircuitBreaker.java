package utils;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 轻量级熔断器（无外部依赖）
 * 替代 Resilience4j CircuitBreaker，用于 Agent 调用的故障隔离
 */
@Slf4j
public class SimpleCircuitBreaker {

    private static final Map<String, SimpleCircuitBreaker> INSTANCES = new ConcurrentHashMap<>();

    private final String name;
    private final int failureThreshold;
    private final long waitDurationMs;

    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile State state = State.CLOSED;
    private final AtomicLong lastFailureTime = new AtomicLong(0);

    public enum State { CLOSED, OPEN, HALF_OPEN }

    public SimpleCircuitBreaker(String name, int failureThreshold, long waitDurationMs) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.waitDurationMs = waitDurationMs;
    }

    public static SimpleCircuitBreaker getOrCreate(String name) {
        return INSTANCES.computeIfAbsent(name, k ->
                new SimpleCircuitBreaker(k, 5, 10_000));
    }

    public static SimpleCircuitBreaker getOrCreate(String name, int failureThreshold, long waitDurationMs) {
        return INSTANCES.computeIfAbsent(name, k ->
                new SimpleCircuitBreaker(k, failureThreshold, waitDurationMs));
    }

    /**
     * 检查请求是否允许通过
     */
    public boolean isCallAllowed() {
        State current = state;
        switch (current) {
            case CLOSED:
                return true;
            case OPEN:
                if (System.currentTimeMillis() - lastFailureTime.get() >= waitDurationMs) {
                    state = State.HALF_OPEN;
                    log.info("[熔断器-{}] OPEN -> HALF_OPEN，允许探测请求", name);
                    return true;
                }
                log.warn("[熔断器-{}] OPEN 状态，请求被拒绝", name);
                return false;
            case HALF_OPEN:
                return true;
            default:
                return true;
        }
    }

    /**
     * 记录成功
     */
    public void onSuccess() {
        failureCount.set(0);
        if (state == State.HALF_OPEN) {
            state = State.CLOSED;
            log.info("[熔断器-{}] HALF_OPEN -> CLOSED，服务恢复", name);
        }
    }

    /**
     * 记录失败
     */
    public void onFailure() {
        int failures = failureCount.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());

        if (state == State.HALF_OPEN) {
            state = State.OPEN;
            log.warn("[熔断器-{}] HALF_OPEN -> OPEN，探测请求失败", name);
        } else if (failures >= failureThreshold) {
            state = State.OPEN;
            log.warn("[熔断器-{}] CLOSED -> OPEN，连续{}次失败", name, failures);
        }
    }

    public State getState() { return state; }
    public int getFailureCount() { return failureCount.get(); }
}
