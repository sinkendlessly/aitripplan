package managerAgent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@EntityScan("entity")
@EnableJpaRepositories("repository")
@SpringBootApplication(scanBasePackages = {
        "managerAgent",
        "utils",
        "config",
        "service",
        "repository",
        "entity"
})
public class ManagerAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(ManagerAgentApplication.class, args);
    }
}
