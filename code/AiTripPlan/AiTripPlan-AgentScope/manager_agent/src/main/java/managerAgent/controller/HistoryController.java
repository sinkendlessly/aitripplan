package managerAgent.controller;

import entity.TravelPlanHistory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import repository.TravelPlanHistoryRepository;
import service.TravelPlanHistoryService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class HistoryController {

    @Autowired
    private TravelPlanHistoryService historyService;

    @Autowired
    private TravelPlanHistoryRepository historyRepository;

    @GetMapping("/history")
    public ResponseEntity<Page<TravelPlanHistory>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<TravelPlanHistory> result = historyRepository
                .findAllByOrderByCreateTimeDesc(PageRequest.of(page, size));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        TravelPlanHistoryService.Statistics stats = historyService.getStatistics();
        List<Object[]> hotDest = historyService.getHotDestinations(5);

        Map<String, Object> result = new HashMap<>();
        result.put("totalPlans", stats.getTotalCount());
        result.put("successRate", stats.getSuccessRate());
        result.put("averageCost", stats.getAverageCost());
        result.put("hotDestinations", hotDest);

        return ResponseEntity.ok(result);
    }
}
