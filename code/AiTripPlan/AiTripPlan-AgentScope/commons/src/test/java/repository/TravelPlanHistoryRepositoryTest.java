package repository;

import entity.TravelPlanHistory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * author: Sinkendlessly
 * description: TravelPlanHistoryRepository单元测试
 * date: 2026
 */
@SpringBootTest(classes = TravelPlanHistoryRepositoryTest.RepositoryTestApplication.class)
@ActiveProfiles("test")
@Transactional
class TravelPlanHistoryRepositoryTest {

    @Autowired
    private TravelPlanHistoryRepository repository;

    @Test
    void testSaveAndFind() {
        TravelPlanHistory record = new TravelPlanHistory();
        record.setSessionId("test-session-001");
        record.setUserRequest("深圳到惠州3日游");
        record.setOrigin("深圳");
        record.setDestination("惠州");
        record.setDays(3);
        record.setStatus(TravelPlanHistory.PlanStatus.SUCCESS);
        record.setTotalCost(new BigDecimal("2000.00"));

        TravelPlanHistory saved = repository.save(record);
        assertNotNull(saved.getId());

        Optional<TravelPlanHistory> found = repository.findBySessionId("test-session-001");
        assertTrue(found.isPresent());
        assertEquals("深圳", found.get().getOrigin());
    }

    @Test
    void testFindByUserId() {
        // 创建多条记录
        for (int i = 0; i < 5; i++) {
            TravelPlanHistory record = new TravelPlanHistory();
            record.setSessionId("session-" + i);
            record.setUserId("user123");
            record.setUserRequest("请求" + i);
            record.setStatus(TravelPlanHistory.PlanStatus.SUCCESS);
            repository.save(record);
        }

        Page<TravelPlanHistory> page = repository.findByUserIdOrderByCreateTimeDesc(
                "user123", PageRequest.of(0, 10));

        assertEquals(5, page.getTotalElements());
    }

    @Test
    void testCountByStatus() {
        TravelPlanHistory record = new TravelPlanHistory();
        record.setSessionId("session-status");
        record.setUserRequest("测试");
        record.setStatus(TravelPlanHistory.PlanStatus.SUCCESS);
        repository.save(record);

        long count = repository.countByStatus(TravelPlanHistory.PlanStatus.SUCCESS);
        assertTrue(count >= 1);
    }

    @SpringBootConfiguration
    @EntityScan("entity")
    @EnableJpaRepositories("repository")
    @ComponentScan("repository")
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataJpaRepositoriesAutoConfiguration.class,
            TransactionAutoConfiguration.class
    })
    static class RepositoryTestApplication {
    }
}
