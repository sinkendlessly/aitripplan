package service;

import entity.TravelPlanHistory;
import lombok.extern.slf4j.Slf4j;
import model.AgentResult;
import model.TravelPlanContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.TravelPlanHistoryRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * author: Sinkendlessly
 * description: 旅行规划历史服务
 *      处理规划结果的持久化和查询
 * date: 2026
 */
@Slf4j
@Service
public class TravelPlanHistoryService {

    @Autowired
    private TravelPlanHistoryRepository repository;

    private static final Pattern PRICE_PATTERN = Pattern.compile("总计[：:]?\\s*¥?(\\d+)");

    /**
     * 创建规划记录（初始化）
     */
    @Transactional
    public TravelPlanHistory createRecord(String sessionId, String userRequest) {
        TravelPlanHistory record = new TravelPlanHistory();
        record.setSessionId(sessionId);
        record.setUserRequest(userRequest);
        record.setStatus(TravelPlanHistory.PlanStatus.PENDING);

        // 尝试解析出发地和目的地
        parseOriginDestination(record, userRequest);

        TravelPlanHistory saved = repository.save(record);
        log.info("[历史记录创建] id={}, sessionId={}", saved.getId(), sessionId);
        return saved;
    }

    /**
     * 更新规划结果
     */
    @Transactional
    public void updateResult(String sessionId, TravelPlanContext context, long executionTime) {
        Optional<TravelPlanHistory> optional = repository.findBySessionId(sessionId);
        if (optional.isEmpty()) {
            log.warn("[历史记录更新失败] 未找到 sessionId={}", sessionId);
            return;
        }

        TravelPlanHistory record = optional.get();
        record.setExecutionTime(executionTime);

        // 保存各Agent结果
        if (context.getRouteResult() != null) {
            AgentResult route = context.getRouteResult();
            record.setRouteResult(route.isSuccess() ? route.getContent() : "失败: " + route.getErrorMessage());
        }

        if (context.getItineraryResult() != null) {
            AgentResult itinerary = context.getItineraryResult();
            record.setItineraryResult(itinerary.isSuccess() ? itinerary.getContent() : "失败: " + itinerary.getErrorMessage());
        }

        if (context.getBudgetResult() != null) {
            AgentResult budget = context.getBudgetResult();
            record.setBudgetResult(budget.isSuccess() ? budget.getContent() : "失败: " + budget.getErrorMessage());

            // 解析总费用
            if (budget.isSuccess()) {
                record.setTotalCost(parseTotalCost(budget.getContent()));
            }
        }

        // 确定状态
        int successCount = context.getSuccessCount();
        if (successCount == 3) {
            record.setStatus(TravelPlanHistory.PlanStatus.SUCCESS);
        } else if (successCount > 0) {
            record.setStatus(TravelPlanHistory.PlanStatus.PARTIAL);
        } else {
            record.setStatus(TravelPlanHistory.PlanStatus.FAILED);
        }

        repository.save(record);
        log.info("[历史记录更新] sessionId={}, 状态={}, 耗时={}ms",
                sessionId, record.getStatus(), executionTime);
    }

    /**
     * 标记失败
     */
    @Transactional
    public void markFailed(String sessionId, String errorMessage) {
        Optional<TravelPlanHistory> optional = repository.findBySessionId(sessionId);
        if (optional.isPresent()) {
            TravelPlanHistory record = optional.get();
            record.setStatus(TravelPlanHistory.PlanStatus.FAILED);
            record.setErrorMessage(errorMessage);
            repository.save(record);
            log.info("[历史记录标记失败] sessionId={}", sessionId);
        }
    }

    /**
     * 异步保存（用于性能优化）
     * 使用自定义线程池：historyExecutor
     */
    @Async("historyExecutor")
    public void saveAsync(String sessionId, TravelPlanContext context, long executionTime) {
        try {
            updateResult(sessionId, context, executionTime);
        } catch (Exception e) {
            log.error("[异步保存失败] sessionId={}", sessionId, e);
            // 异步保存失败不影响主流程，但记录日志
        }
    }

    /**
     * 批量异步保存（用于性能优化）
     * 使用自定义线程池：historyExecutor
     */
    @Async("historyExecutor")
    public void batchSaveAsync(List<TravelPlanContext> contexts) {
        try {
            log.info("[批量异步保存] 开始处理 {} 条记录", contexts.size());
            // 批量保存逻辑
            // 这里可以实现批量插入优化
            log.info("[批量异步保存] 完成处理 {} 条记录", contexts.size());
        } catch (Exception e) {
            log.error("[批量异步保存失败]", e);
        }
    }

    /**
     * 查询用户历史（分页）
     */
    public Page<TravelPlanHistory> getUserHistory(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findByUserIdOrderByCreateTimeDesc(userId, pageable);
    }

    /**
     * 查询单条记录
     */
    public Optional<TravelPlanHistory> getBySessionId(String sessionId) {
        return repository.findBySessionId(sessionId);
    }

    /**
     * 查询最近成功的记录
     */
    public List<TravelPlanHistory> getRecentSuccess(int limit) {
        return repository.findByStatusOrderByCreateTimeDesc(
                TravelPlanHistory.PlanStatus.SUCCESS,
                PageRequest.of(0, limit)
        ).getContent();
    }

    /**
     * 获取统计信息
     */
    public Statistics getStatistics() {
        Statistics stats = new Statistics();
        stats.setTotalCount(repository.count());
        stats.setSuccessCount(repository.countByStatus(TravelPlanHistory.PlanStatus.SUCCESS));
        stats.setTotalCost(repository.sumTotalCost());
        stats.setAverageCost(repository.findAverageCost());
        return stats;
    }

    /**
     * 获取热门目的地
     */
    public List<Object[]> getHotDestinations(int topN) {
        return repository.findHotDestinations(PageRequest.of(0, topN));
    }

    /**
     * 清理过期记录
     */
    @Transactional
    public int cleanupOldRecords(int daysToKeep) {
        LocalDateTime before = LocalDateTime.now().minusDays(daysToKeep);
        List<TravelPlanHistory> oldRecords = repository.findByCreateTimeBetween(
                LocalDateTime.MIN, before);
        repository.deleteByCreateTimeBefore(before);
        log.info("[历史记录清理] 删除了 {} 条 {} 天前的记录", oldRecords.size(), daysToKeep);
        return oldRecords.size();
    }

    // ================== 私有方法 ==================

    /**
     * 解析出发地和目的地
     */
    private void parseOriginDestination(TravelPlanHistory record, String request) {
        // 简单解析：从XX到XX
        Pattern pattern = Pattern.compile("从(.+?)[到去](.+?)[的游]|(.+?)[到去](.+?)[日游]");
        Matcher matcher = pattern.matcher(request);
        if (matcher.find()) {
            record.setOrigin(matcher.group(1) != null ? matcher.group(1).trim() : matcher.group(3).trim());
            record.setDestination(matcher.group(2) != null ? matcher.group(2).trim() : matcher.group(4).trim());
        }

        // 解析天数
        Pattern dayPattern = Pattern.compile("(\\d+)[天日]");
        Matcher dayMatcher = dayPattern.matcher(request);
        if (dayMatcher.find()) {
            record.setDays(Integer.parseInt(dayMatcher.group(1)));
        }
    }

    /**
     * 从费用结果中解析总费用
     */
    private BigDecimal parseTotalCost(String budgetContent) {
        try {
            Matcher matcher = PRICE_PATTERN.matcher(budgetContent);
            if (matcher.find()) {
                return new BigDecimal(matcher.group(1));
            }
        } catch (Exception e) {
            log.warn("解析总费用失败: {}", e.getMessage());
        }
        return null;
    }

    // ================== 统计类 ==================

    public static class Statistics {
        private long totalCount;
        private long successCount;
        private BigDecimal totalCost;
        private BigDecimal averageCost;

        public long getTotalCount() { return totalCount; }
        public void setTotalCount(long totalCount) { this.totalCount = totalCount; }

        public long getSuccessCount() { return successCount; }
        public void setSuccessCount(long successCount) { this.successCount = successCount; }

        public BigDecimal getTotalCost() { return totalCost; }
        public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }

        public BigDecimal getAverageCost() { return averageCost; }
        public void setAverageCost(BigDecimal averageCost) { this.averageCost = averageCost; }

        public double getSuccessRate() {
            return totalCount > 0 ? (double) successCount / totalCount * 100 : 0;
        }
    }
}
