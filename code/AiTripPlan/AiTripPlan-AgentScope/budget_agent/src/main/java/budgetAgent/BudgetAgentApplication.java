package budgetAgent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * author: Imooc
 * description: 费用统计Agent启动类
 * date: 2026
 */
@SpringBootApplication(scanBasePackages = {
        "budgetAgent",
        "utils",
        "config"
})
public class BudgetAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(BudgetAgentApplication.class, args);
    }
}
