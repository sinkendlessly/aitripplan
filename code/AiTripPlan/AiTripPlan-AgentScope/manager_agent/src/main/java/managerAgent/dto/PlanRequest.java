package managerAgent.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
public class PlanRequest {

    @NotBlank(message = "prompt不能为空")
    @Size(min = 5, max = 2000, message = "prompt长度需在5-2000字符之间")
    private String prompt;

    private PlanOptions options;

    @Data
    public static class PlanOptions {
        private Integer budget;
        private Integer travelers = 2;
        private String travelMode;
        private List<String> preferences;
    }
}
