package config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * author: Imooc
 * description: 线程池监控端点
 *      提供线程池实时状态查看
 * date: 2026
 */
@Slf4j
@RestController
@RequestMapping("/admin/monitor")
public class ThreadPoolMonitor {

    @Autowired
    @Qualifier("historyExecutor")
    private ThreadPoolTaskExecutor historyExecutor;

    @Autowired
    @Qualifier("cacheExecutor")
    private ThreadPoolTaskExecutor cacheExecutor;

    @Autowired
    @Qualifier("taskExecutor")
    private ThreadPoolTaskExecutor taskExecutor;

    /**
     * 获取所有线程池状态
     */
    @GetMapping("/thread-pools")
    public Map<String, ThreadPoolStats> getAllThreadPoolStats() {
        Map<String, ThreadPoolStats> stats = new HashMap<>();
        
        stats.put("historyExecutor", getStats(historyExecutor));
        stats.put("cacheExecutor", getStats(cacheExecutor));
        stats.put("taskExecutor", getStats(taskExecutor));
        
        return stats;
    }

    /**
     * 获取单个线程池状态
     */
    private ThreadPoolStats getStats(ThreadPoolTaskExecutor executor) {
        ThreadPoolStats stats = new ThreadPoolStats();
        
        if (executor.getThreadPoolExecutor() != null) {
            var pool = executor.getThreadPoolExecutor();
            
            stats.setCorePoolSize(pool.getCorePoolSize());
            stats.setMaximumPoolSize(pool.getMaximumPoolSize());
            stats.setActiveCount(pool.getActiveCount());
            stats.setPoolSize(pool.getPoolSize());
            stats.setQueueSize(pool.getQueue().size());
            stats.setQueueRemainingCapacity(pool.getQueue().remainingCapacity());
            stats.setCompletedTaskCount(pool.getCompletedTaskCount());
            stats.setTaskCount(pool.getTaskCount());
            
            // 计算负载率
            double loadRate = pool.getMaximumPoolSize() > 0 
                ? (double) pool.getActiveCount() / pool.getMaximumPoolSize() * 100 
                : 0;
            stats.setLoadRate(String.format("%.2f%%", loadRate));
            
            // 队列使用率
            int queueCapacity = pool.getQueue().size() + pool.getQueue().remainingCapacity();
            double queueRate = queueCapacity > 0 
                ? (double) pool.getQueue().size() / queueCapacity * 100 
                : 0;
            stats.setQueueUsageRate(String.format("%.2f%%", queueRate));
            
            // 状态判断
            if (loadRate > 80 || queueRate > 80) {
                stats.setStatus("WARNING");
            } else if (loadRate > 50 || queueRate > 50) {
                stats.setStatus("BUSY");
            } else {
                stats.setStatus("HEALTHY");
            }
        }
        
        return stats;
    }

    /**
     * 线程池统计信息
     */
    @Data
    public static class ThreadPoolStats {
        /** 核心线程数 */
        private int corePoolSize;
        /** 最大线程数 */
        private int maximumPoolSize;
        /** 活跃线程数 */
        private int activeCount;
        /** 当前线程池大小 */
        private int poolSize;
        /** 队列大小 */
        private int queueSize;
        /** 队列剩余容量 */
        private int queueRemainingCapacity;
        /** 已完成任务数 */
        private long completedTaskCount;
        /** 总任务数 */
        private long taskCount;
        /** 负载率 */
        private String loadRate;
        /** 队列使用率 */
        private String queueUsageRate;
        /** 状态: HEALTHY/BUSY/WARNING */
        private String status;
    }
}
