# ATPlan 快速启动指南

## 前置要求

- JDK 17+
- Maven 3.8+
- Nacos 3.1.0+（注册中心）
- DashScope API Key

## 1. 配置环境变量

```bash
# Windows PowerShell
$env:DASHSCOPE_API_KEY = "your-actual-api-key"
$env:NACOS_SERVER_ADDR = "127.0.0.1:8848"
$env:AGENT_SKILLS_DIR = "C:/Users/33321/Desktop/aitripplan/skills"

# Linux/Mac
export DASHSCOPE_API_KEY="your-actual-api-key"
export NACOS_SERVER_ADDR="127.0.0.1:8848"
export AGENT_SKILLS_DIR="/path/to/skills"
```

## 2. 启动Nacos

```bash
docker run --rm --name nacos \
  -e MODE=standalone \
  -e NACOS_AUTH_ENABLE=false \
  -p 8848:8848 -p 9848:9848 -p 8088:8080 \
  nacos/nacos-server:v3.1.0
```

访问 http://127.0.0.1:8088 确认Nacos启动成功

## 3. 编译项目

```bash
cd C:/Users/33321/Desktop/aitripplan/code/AiTripPlan/AiTripPlan-AgentScope
mvn clean install -DskipTests
```

## 4. 启动服务（按顺序）

### 4.1 启动 BudgetAgent（端口8083）

```bash
cd budget_agent
mvn spring-boot:run
```

### 4.2 启动 RouteMakingAgent（端口8082）

```bash
cd routeMaking_agent
mvn spring-boot:run
```

### 4.3 启动 TripPlannerAgent（端口8085）

```bash
cd tripPlanner_agent
mvn spring-boot:run
```

### 4.4 启动 ManagerAgent（端口8081）

```bash
cd manager_agent
mvn spring-boot:run
```

## 5. 测试

### 查看Nacos服务注册

打开 http://127.0.0.1:8088，查看服务列表：
- BudgetAgent
- RouteMakingAgent
- TripPlannerAgent
- ManagerAgent

### 查看日志

ManagerAgent启动后会自动执行示例规划任务：

```
========== 开始旅行规划（并行模式） ==========
用户请求: 帮我制定2026年元旦，深圳到惠州3日游自驾游计划...
开始并行调用两个Agent: [RouteMakingAgent] 和 [TripPlannerAgent]
[RouteMakingAgent] 开始调用...
[TripPlannerAgent] 开始调用...
前置Agent完成，开始调用 [BudgetAgent] 进行汇总分析
========== 旅行规划完成，总耗时: XXXXms ==========
```

## 6. 自定义配置

### 6.1 修改超时时间

编辑 `manager_agent/src/main/resources/application.yml`：

```yaml
agent:
  timeout-seconds: 120  # 改为2分钟
```

### 6.2 修改模型

```yaml
agent:
  model-name: qwen-max  # 使用更强的模型
```

### 6.3 配置MCP（百度地图）

```yaml
agent:
  mcp:
    baidu-map:
      url: https://your-mcp-server.com
      timeout: 180
```

## 7. 运行测试

```bash
cd commons
mvn test
```

预期输出：
```
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## 8. 故障排查

### 问题：API Key无效

```
错误：鉴权失败
解决：检查 DASHSCOPE_API_KEY 环境变量
```

### 问题：Nacos连接失败

```
错误：连接超时
解决：检查 NACOS_SERVER_ADDR 和 Nacos是否启动
```

### 问题：Agent未注册

```
错误：找不到Agent
解决：按顺序启动Agent，检查Nacos服务列表
```

### 问题：端口被占用

```
错误：Address already in use
解决：修改对应模块的 application.yml 中的 server.port
```

## 9. 项目结构

```
AiTripPlan-AgentScope/
├── commons/              # 公共模块
│   ├── src/main/java/
│   │   ├── config/       # 配置类
│   │   ├── model/        # 数据模型
│   │   └── utils/        # 工具类
│   └── src/test/         # 单元测试 ⭐
├── manager_agent/        # 主管Agent (8081)
├── routeMaking_agent/    # 路线Agent (8082)
├── tripPlanner_agent/    # 行程Agent (8085)
├── budget_agent/         # 费用Agent (8083) ⭐
├── pom.xml
├── ARCHITECTURE.md       # 架构文档
├── README-IMPROVEMENTS.md # 改进说明
└── QUICKSTART.md         # 本文件
```

## 10. 关键特性

- ✅ **并行调用** - RouteMakingAgent + TripPlannerAgent 并行执行
- ✅ **容错处理** - 单个Agent失败不影响整体
- ✅ **超时控制** - 可配置超时时间
- ✅ **配置外部化** - 所有配置可环境变量覆盖
- ✅ **单元测试** - 核心功能有测试覆盖

## 11. 性能优化建议

1. **并行度调优** - 根据服务器CPU调整线程池
2. **超时设置** - 网络不稳定时增加超时时间
3. **日志级别** - 生产环境建议 WARN 级别
4. **内存优化** - 大模型返回内容多，注意堆内存设置

## 12. 联系方式

如有问题，请参考：
- 架构文档：`ARCHITECTURE.md`
- 改进说明：`README-IMPROVEMENTS.md`
- AgentScope文档：https://java.agentscope.io/zh/intro.html
