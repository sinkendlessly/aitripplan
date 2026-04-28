package managerAgent.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StreamEvent {

    private String type;
    private String agent;
    private String content;
    private String planId;
    private Long totalTime;

    public static StreamEvent agentStart(String agent) {
        return StreamEvent.builder()
                .type("agent_start")
                .agent(agent)
                .build();
    }

    public static StreamEvent progress(String agent, String content) {
        return StreamEvent.builder()
                .type(agent.toLowerCase().contains("route") ? "route_progress"
                        : agent.toLowerCase().contains("trip") ? "itinerary_progress"
                        : "budget_progress")
                .agent(agent)
                .content(content)
                .build();
    }

    public static StreamEvent thinking(String content) {
        return StreamEvent.builder()
                .type("thinking")
                .agent("ManagerAgent")
                .content(content)
                .build();
    }

    public static StreamEvent complete(String planId, long totalTime) {
        return StreamEvent.builder()
                .type("complete")
                .planId(planId)
                .totalTime(totalTime)
                .build();
    }

    public static StreamEvent error(String message) {
        return StreamEvent.builder()
                .type("error")
                .content(message)
                .build();
    }
}
