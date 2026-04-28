# ATPlan 项目改进说明

## 本次完成的改进（P0优先级）

### ✅ 1. 配置外部化

**改进前：**
```java
// 硬编码在代码中
.apiKey("你的API_KEY")
.modelName("qwen3-max")
```

**改进后：**
```yaml
# application.yml
agent:
  api-key: ${DASHSCOPE_API_KEY:your-api-key-here}
  model-name: ${AGENT_MODEL_NAME:qwen3-max}
  timeout-seconds: ${AGENT_TIMEOUT:60}
```

**支持的配置项：**

| 配置项 | 环境变量 | 默认值 | 说明 |
|--------|---------|--------|------|
| `agent.api-key` | `DASHSCOPE_API_KEY` | `your-api-key-here` | DashScope API Key |
| `agent.model-name` | `AGENT_MODEL_NAME` | `qwen3-max` | 模型名称 |
| `agent.stream` | - | `true` | 是否流式响应 |
| `agent.timeout-seconds` | `AGENT_TIMEOUT` | `60` | 调用超时时间 |
| `agent.mcp.baidu-map.url` | `BAIDU_MCP_URL` | - | 百度地图MCP地址 |
| `agent.mcp.baidu-map.timeout` | `BAIDU_MCP_TIMEOUT` | `120` | MCP超时时间 |
| `agent.skills.dir` | `AGENT_SKILLS_DIR` | `./skills` | Skills目录 |

**配置类：**
- `config.AgentProperties` - 配置属性类
- `config.AgentConfig` - 配置启用类

### ✅ 2. 单元测试

**测试覆盖：**

| 测试类 | 测试内容 |
|--------|---------|
| `model.AgentResultTest` | Agent结果创建、摘要生成 |
| `model.TravelPlanContextTest` | 上下文构建、结果统计 |
| `config.AgentPropertiesTest` | 配置加载、默认值验证 |

**运行测试：**
```bash
cd commons
mvn test
```

## 使用方式

### 1. 启动前配置

```bash
# 设置环境变量（推荐）
export DASHSCOPE_API_KEY=your-actual-api-key
export BAIDU_MCP_URL=https://your-mcp-server.com
export NACOS_SERVER_ADDR=127.0.0.1:8848

# 或者启动时传入
java -jar manager_agent.jar \
  --agent.api-key=your-api-key \
  --baidu.mcp.url=https://your-mcp-server.com
```

### 2. 配置文件覆盖

在各模块的 `application.yml` 中覆盖公共配置：

```yaml
# manager_agent/application.yml
agent:
  api-key: ${DASHSCOPE_API_KEY}
  timeout-seconds: 90  # 主管Agent超时更长
  
  names:
    route: RouteMakingAgent
    itinerary: TripPlannerAgent
    budget: BudgetAgent
```

### 3. 代码中使用配置

```java
@Service
public class MyService {
    
    @Autowired
    private AgentProperties agentProperties;
    
    public void doSomething() {
        String apiKey = agentProperties.getApiKey();
        int timeout = agentProperties.getTimeoutSeconds();
        String routeAgentName = agentProperties.getAgentName("route");
    }
}
```

## 后续改进计划（P1/P2）

### P1 - 功能完善

- [ ] **数据库持久化** - 保存规划历史
- [ ] **Redis缓存** - 缓存热门路线
- [ ] **日志增强** - 结构化日志输出

### P2 - 稳定性

- [ ] **熔断器** - Resilience4j集成
- [ ] **限流** - 防止过载
- [ ] **健康检查** - Actuator端点

### P3 - 可观测性

- [ ] **链路追踪** - Micrometer + Zipkin
- [ ] **监控告警** - Prometheus + Grafana
- [ ] **性能指标** - 调用耗时统计

## 项目结构更新

```
commons/
├── src/main/java/
│   ├── config/
│   │   ├── AgentConfig.java          # 配置启用
│   │   └── AgentProperties.java      # 配置属性
│   ├── model/
│   │   ├── AgentResult.java
│   │   └── TravelPlanContext.java
│   └── utils/
│       ├── AgentUtils.java           # 配置化版本
│       └── ParallelAgentService.java # 配置化版本
├── src/main/resources/
│   └── application-commons.yml       # 默认配置
└── src/test/java/                     # ⭐ 新增测试
    ├── config/AgentPropertiesTest.java
    ├── model/AgentResultTest.java
    └── model/TravelPlanContextTest.java

manager_agent/
routeMaking_agent/
tripPlanner_agent/
budget_agent/
└── src/main/resources/
    └── application.yml               # 各模块配置
```

## 检查清单

- [x] 配置外部化 - 所有硬编码参数移到yml
- [x] 环境变量支持 - 生产环境可注入
- [x] 配置验证 - 非空校验
- [x] 单元测试 - 核心模型测试
- [x] 默认值 - 提供合理的默认值
- [x] 文档 - 配置说明文档

## 注意事项

1. **API Key安全** - 不要将真实API Key提交到git
2. **环境变量优先** - 生产环境使用环境变量覆盖
3. **配置热加载** - 目前不支持热加载，修改后需重启
