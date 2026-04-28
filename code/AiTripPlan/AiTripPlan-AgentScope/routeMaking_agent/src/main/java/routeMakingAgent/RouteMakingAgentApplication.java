package routeMakingAgent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * author: Imooc
 * description: 启动类
 * date: 2026
 */

@SpringBootApplication(scanBasePackages = {
        "routeMakingAgent",
        "utils",
        "config"
})
public class RouteMakingAgentApplication {
    public static void main(String[] args) {

        SpringApplication.run(RouteMakingAgentApplication.class, args);
    }
}
