package repository;

import entity.TravelPlanHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * author: Imooc
 * description: 旅行规划历史数据访问层
 * date: 2026
 */
@Repository
public interface TravelPlanHistoryRepository extends JpaRepository<TravelPlanHistory, Long> {

    /**
     * 根据会话ID查询
     */
    Optional<TravelPlanHistory> findBySessionId(String sessionId);

    Optional<TravelPlanHistory> findByPlanId(String planId);

    long countByStatus(TravelPlanHistory.PlanStatus status);

    Page<TravelPlanHistory> findAllByOrderByCreateTimeDesc(Pageable pageable);

    /**
     * 根据用户ID查询历史记录（分页）
     */
    Page<TravelPlanHistory> findByUserIdOrderByCreateTimeDesc(String userId, Pageable pageable);

    /**
     * 查询所有成功记录（分页）
     */
    Page<TravelPlanHistory> findByStatusOrderByCreateTimeDesc(
            TravelPlanHistory.PlanStatus status, Pageable pageable);

    /**
     * 查询时间范围内的记录
     */
    List<TravelPlanHistory> findByCreateTimeBetween(
            LocalDateTime start, LocalDateTime end);

    /**
     * 统计用户规划次数
     */
    long countByUserId(String userId);

    /**
     * 统计总费用
     */
    @Query("SELECT SUM(t.totalCost) FROM TravelPlanHistory t WHERE t.status = 'SUCCESS'")
    BigDecimal sumTotalCost();

    /**
     * 查询热门目的地TOP10
     */
    @Query("SELECT t.destination, COUNT(t) as count FROM TravelPlanHistory t " +
           "WHERE t.destination IS NOT NULL " +
           "GROUP BY t.destination ORDER BY count DESC")
    List<Object[]> findHotDestinations(Pageable pageable);

    /**
     * 查询平均费用
     */
    @Query("SELECT AVG(t.totalCost) FROM TravelPlanHistory t WHERE t.status = 'SUCCESS' AND t.totalCost > 0")
    BigDecimal findAverageCost();

    /**
     * 根据出发地和目的地查询历史记录
     */
    List<TravelPlanHistory> findByOriginAndDestinationAndStatus(
            String origin, String destination, TravelPlanHistory.PlanStatus status);

    /**
     * 搜索用户请求
     */
    @Query("SELECT t FROM TravelPlanHistory t WHERE t.userRequest LIKE %:keyword% ORDER BY t.createTime DESC")
    List<TravelPlanHistory> searchByRequest(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 删除过期记录（用于清理）
     */
    void deleteByCreateTimeBefore(LocalDateTime before);
}
