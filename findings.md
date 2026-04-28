# Findings

## Project Findings
- 仓库不是单一工程：根目录下有 doc、docker、skills、frontend、code；code 里同时包含教学 Demo、Jmanus 完整项目、AiTripPlan 三种实现。
- 本次重点项目是 `code/AiTripPlan/AiTripPlan-AgentScope`，Maven 父工程包含 5 个模块：`manager_agent`、`routeMaking_agent`、`tripPlanner_agent`、`budget_agent`、`commons`。
- 核心架构是 ManagerAgent 作为统一入口和编排层，RouteMakingAgent 与 TripPlannerAgent 并行执行，再把结果交给 BudgetAgent 汇总预算。
- 技术栈以 Java 17、Spring Boot、AgentScope、A2A、Nacos、DashScope 模型、Reactor、Redis、JPA/H2/MySQL 为主；前端是 React 19 + Vite + Tailwind。
- `manager_agent` 暴露 `/api/v1/plan`、`/{planId}/stream`、`/history`、`/stats` 等接口；`commons` 承载配置、缓存、历史记录、AgentResult、解析器、熔断器等公共能力。
- 运行流：前端 POST 创建规划任务，Manager 创建 planId/sessionId，SSE 推送进度，并行调用路线/行程 Agent，随后调用预算 Agent，最后保存历史和返回结果。
- 已有文档声称支持缓存、持久化、异步线程池、监控和配置外部化；代码里也能看到 Redis/JPA/ThreadPoolMonitor/HistoryService 等实现痕迹。

## Architecture Risks
- 根仓库内容混杂：教学 Demo、真实项目、前端依赖、Jmanus 源码放在一起，初学者很难分清“主项目”和“参考项目”。
- `commons` 职责过重：既有配置、模型、缓存、数据库、工具、服务发现、解析器，公共模块逐渐变成大杂烩。
- 多处使用静态工具和 `SpringContextHolder` 获取 Bean，依赖关系隐藏，不利于测试、替换和排查启动顺序问题。
- Agent 调用链仍有阻塞点：`RemoteAgentTool` 和 `TravelPlanService` 中存在 `.block()`，响应式只是局部使用。
- `RouteMakingAgent` 手动 `new BaiduMapMCP()`，绕过 Spring 注入，`agentProperties` 有空指针风险。
- 文档与代码/依赖可能不完全一致：文档写 Spring Boot 3.x，父 pom 写 4.0.2；部分文档中的性能指标像简历包装，未见基准测试证明。
- 输入输出强依赖提示词尾部 JSON，靠“最佳努力解析”，缺少强 schema、失败重试修复、字段校验和版本化协议。
- Docker Compose 包含后端基础设施和 Agent 服务，但未纳入前端服务；端到端本地体验还不完整。
- Nacos、Redis、DB、API Key、MCP 都是外部依赖，缺少一键本地编排和端到端验收脚本时，启动门槛偏高。
- 安全治理还偏弱：有 AdminAuthFilter 和 PromptSanitizer，但用户 API 鉴权、限流、敏感信息脱敏、日志脱敏、Prompt Injection 防护仍需要系统化。

## Improvement Ideas
- 先把仓库结构收敛：把主项目、Demo、第三方参考项目、前端、文档分区说明清楚，最好为主项目单独 README 和启动脚本。
- 拆 `commons`：按 `agent-core`、`agent-infra`、`agent-domain` 或至少 config/model/service/util 分层，减少循环依赖和静态调用。
- 修正 Spring 注入问题：RouteMakingAgent 通过构造器注入 BaiduMapMCP，不要手动 new Spring Bean。
- 把 Agent 间协议结构化：定义 RoutePlan/ItineraryPlan/BudgetPlan JSON Schema 或 DTO，要求模型工具输出严格 JSON，解析失败走修复链路。
- 编排层服务化：让 ManagerAgent 只负责智能决策，旅行规划主链路放到 `TravelPlanService`，统一处理缓存、并发、超时、持久化和 SSE。
- 减少阻塞：统一使用 Reactor 链路或统一使用线程池阻塞模型，不要半响应式半阻塞。
- 补齐工程化：docker-compose 加前端服务，增加 smoke test、集成测试、CI、README 中的最小可运行路径。
- 补可观测性闭环：统一 traceId/planId，记录每个 Agent 耗时、token、成功率、缓存命中率，并暴露 Prometheus 指标。
- 提升安全：API 鉴权、请求限流、日志脱敏、API Key 只走环境变量、Prompt 注入测试集、MCP 工具白名单。
