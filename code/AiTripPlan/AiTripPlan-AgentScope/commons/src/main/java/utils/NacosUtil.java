package utils;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.exception.NacosException;

import java.util.Properties;

/**
 * author: Sinkendlessly
 * description: Nacos 客户端工具类，支持认证配置
 *      地址优先从 NACOS_SERVER_ADDR 环境变量读取，默认 localhost:8848
 *      认证信息优先从 NACOS_USERNAME / NACOS_PASSWORD 环境变量读取
 * date: 2026
 */

public class NacosUtil {

    private static String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = System.getProperty(key);
        }
        return value != null ? value : defaultValue;
    }

    public static AiService getNacosClient() throws NacosException {

        // 设置 Nacos 地址（优先环境变量，次选系统属性，默认 localhost:8848）
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, getEnvOrDefault("NACOS_SERVER_ADDR", "localhost:8848"));

        // 在认证启用时设置用户名和密码
        // Nacos 默认凭据为 nacos/nacos，可通过 NACOS_USERNAME / NACOS_PASSWORD 覆盖
        String username = getEnvOrDefault("NACOS_USERNAME", "nacos");
        String password = getEnvOrDefault("NACOS_PASSWORD", "nacos");
        properties.put(PropertyKeyConst.USERNAME, username);
        properties.put(PropertyKeyConst.PASSWORD, password);

        // 创建 Nacos Client
        return AiFactory.createAiService(properties);

    }
}
