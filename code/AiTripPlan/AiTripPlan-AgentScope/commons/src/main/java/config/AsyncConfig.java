package config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * author: Sinkendlessly
 * description: 异步线程池配置
 *      1. 自定义历史记录保存线程池
 *      2. 自定义缓存操作线程池
 *      3. 线程池监控和优雅停机
 * date: 2026
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * 历史记录保存线程池
     * 用于异步保存规划历史到数据库
     */
    @Bean(name = "historyExecutor")
    public ThreadPoolTaskExecutor historyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("history-");
        
        // 拒绝策略：由调用线程执行（保证数据不丢失）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 优雅停机设置
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        // 线程池预热
        executor.setPrestartAllCoreThreads(true);
        
        executor.initialize();
        
        log.info("[AsyncConfig] 历史记录线程池初始化完成: core={}, max={}, queue={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }

    /**
     * 缓存操作线程池
     * 用于异步Redis操作
     */
    @Bean(name = "cacheExecutor")
    public ThreadPoolTaskExecutor cacheExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("cache-");
        
        // 拒绝策略：丢弃最老的任务（缓存可以丢）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        executor.initialize();
        
        log.info("[AsyncConfig] 缓存操作线程池初始化完成: core={}, max={}, queue={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }

    /**
     * 通用异步线程池（默认）
     * 用于其他异步任务
     */
    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        
        log.info("[AsyncConfig] 默认异步线程池初始化完成: core={}, max={}, queue={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }

    /**
     * 全局默认异步执行器
     */
    @Override
    public Executor getAsyncExecutor() {
        return taskExecutor();
    }

    /**
     * 异步异常处理器
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AsyncExceptionHandler();
    }

    /**
     * 自定义异步异常处理器
     */
    public static class AsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            log.error("[异步异常] 方法: {}, 异常: {}", method.getName(), ex.getMessage(), ex);
            // 可以在这里添加告警通知（如钉钉、企业微信）
        }
    }
}
