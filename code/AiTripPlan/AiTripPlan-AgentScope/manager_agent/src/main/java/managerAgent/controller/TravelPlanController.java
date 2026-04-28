package managerAgent.controller;

import lombok.extern.slf4j.Slf4j;
import managerAgent.dto.PlanRequest;
import managerAgent.dto.PlanResponse;
import managerAgent.dto.StreamEvent;
import managerAgent.service.TravelPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/v1/plan")
public class TravelPlanController {

    @Autowired
    private TravelPlanService travelPlanService;

    @PostMapping
    public ResponseEntity<PlanResponse> createPlan(@Valid @RequestBody PlanRequest request) {
        log.info("[API] 创建规划, prompt长度: {}", request.getPrompt().length());

        PlanResponse response = travelPlanService.createPlan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping(value = "/{planId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<StreamEvent>> streamPlan(@PathVariable String planId) {
        log.info("[API] SSE连接, planId: {}", planId);

        return travelPlanService.getStream(planId)
                .map(event -> ServerSentEvent.<StreamEvent>builder()
                        .event(event.getType())
                        .data(event)
                        .build());
    }

    @GetMapping("/{planId}")
    public ResponseEntity<PlanResponse> getPlan(@PathVariable String planId) {
        PlanResponse plan = travelPlanService.getPlan(planId);
        if (plan == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(plan);
    }
}
