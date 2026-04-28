package entity;

import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * author: Sinkendlessly
 * description: 旅行规划历史实体
 *      持久化存储用户的规划记录
 * date: 2026
 */
@Data
@Entity
@Table(name = "travel_plan_history", indexes = {
        @Index(name = "idx_user_id", columnList = "userId"),
        @Index(name = "idx_create_time", columnList = "createTime"),
        @Index(name = "idx_status", columnList = "status")
})
public class TravelPlanHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 外部可见的规划ID
     */
    @Column(name = "plan_id", length = 64, unique = true)
    private String planId;

    /**
     * 会话ID（UUID）
     */
    @Column(name = "session_id", length = 64, unique = true, nullable = false)
    private String sessionId;

    /**
     * 用户ID（可选，用于关联用户）
     */
    @Column(name = "user_id", length = 64)
    private String userId;

    /**
     * 用户原始请求
     */
    @Column(name = "user_request", length = 2000, nullable = false)
    private String userRequest;

    /**
     * 出发地
     */
    @Column(name = "origin", length = 100)
    private String origin;

    /**
     * 目的地
     */
    @Column(name = "destination", length = 100)
    private String destination;

    /**
     * 行程天数
     */
    @Column(name = "days")
    private Integer days;

    /**
     * 路线规划结果
     */
    @Column(name = "route_result", length = 5000)
    @Lob
    private String routeResult;

    /**
     * 行程规划结果
     */
    @Column(name = "itinerary_result", length = 8000)
    @Lob
    private String itineraryResult;

    /**
     * 费用统计结果
     */
    @Column(name = "budget_result", length = 5000)
    @Lob
    private String budgetResult;

    /**
     * 总费用估算
     */
    @Column(name = "total_cost", precision = 10, scale = 2)
    private BigDecimal totalCost;

    /**
     * 状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private PlanStatus status = PlanStatus.PENDING;

    /**
     * 执行耗时（毫秒）
     */
    @Column(name = "execution_time")
    private Long executionTime;

    /**
     * 是否使用缓存
     */
    @Column(name = "from_cache")
    private Boolean fromCache = false;

    /**
     * 错误信息（失败时）
     */
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /**
     * 创建时间
     */
    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /**
     * 规划状态枚举
     */
    public enum PlanStatus {
        PENDING,    // 待处理
        PROCESSING, // 处理中
        SUCCESS,    // 成功
        PARTIAL,    // 部分成功
        FAILED      // 失败
    }

    /**
     * 获取完整结果摘要
     */
    public String getFullSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 旅行规划结果 ===\n\n");
        sb.append("请求: ").append(userRequest).append("\n");
        sb.append("状态: ").append(status).append("\n");
        sb.append("总费用: ").append(totalCost != null ? "¥" + totalCost : "未计算").append("\n\n");

        if (routeResult != null) {
            sb.append("【路线规划】\n").append(routeResult.substring(0, Math.min(500, routeResult.length()))).append("...\n\n");
        }

        if (itineraryResult != null) {
            sb.append("【行程规划】\n").append(itineraryResult.substring(0, Math.min(500, itineraryResult.length()))).append("...\n\n");
        }

        if (budgetResult != null) {
            sb.append("【费用统计】\n").append(budgetResult.substring(0, Math.min(500, budgetResult.length()))).append("...\n\n");
        }

        return sb.toString();
    }
}
