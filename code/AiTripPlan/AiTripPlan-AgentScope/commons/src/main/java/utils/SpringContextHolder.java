package utils;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        applicationContext = ctx;
    }

    public static <T> T getBean(Class<T> clazz) {
        assertContext();
        return applicationContext.getBean(clazz);
    }

    public static <T> T getBean(String name, Class<T> clazz) {
        assertContext();
        return applicationContext.getBean(name, clazz);
    }

    public static boolean containsBean(String name) {
        return applicationContext != null && applicationContext.containsBean(name);
    }

    private static void assertContext() {
        if (applicationContext == null) {
            throw new IllegalStateException(
                    "Spring ApplicationContext 未初始化。请确保 SpringContextHolder 被 Spring 扫描到");
        }
    }
}
