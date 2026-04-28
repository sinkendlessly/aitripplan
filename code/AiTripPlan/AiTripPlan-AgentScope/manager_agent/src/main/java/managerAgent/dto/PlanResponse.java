package managerAgent.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PlanResponse {

    private String planId;
    private String sessionId;
    private String status;
    private String streamUrl;
    private LocalDateTime createdAt;

    private String userRequest;
    private String routeResult;
    private String itineraryResult;
    private String budgetResult;
    private Long executionTime;
    private String errorMessage;
}
