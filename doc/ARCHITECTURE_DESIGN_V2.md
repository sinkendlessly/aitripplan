# ATPlan 落地架构设计方案 V2.0

> 编写日期：2026-04-24
> 定位：从教学Demo升级为可演示、可部署、可扩展的生产级系统

---

## 一、现状诊断与问题清单

### 1.1 当前架构拓扑

```
Nacos 3.1 (A2A注册中心, Docker)
    │
    ├── ManagerAgent     :8081  (入口, ReAct + PlanNotebook)
    ├── RouteMakingAgent :8082  (百度地图MCP)
    ├── TripPlannerAgent :8085  (行程规划)
    └── BudgetAgent      :8083  (费用统计)

Redis  → 路线缓存 (TTL 1h)
H2/MySQL → 规划历史持久化
```

### 1.2 已实现的能力（可直接复用）

| 模块 | 能力 | 质量评估 |
|------|------|----------|
| ParallelAgentService | Reactor并行编排 + 超时 + 容错 | 生产可用 |
| AgentResult / TravelPlanContext | 跨Agent结果聚合模型 | 生产可用 |
| RouteCacheService | Redis路线缓存 | 生产可用 |
| TravelPlanHistoryService | 异步历史持久化 + 统计 | 生产可用 |
| AgentProperties | 配置化Agent属性 | 生产可用 |
| Skills体系 | 4个Skill文件 + SkillPromptLoader | 生产可用 |
| BaiduMapMCP | MCP协议集成百度地图 | 生产可用 |
| NacosUtil / A2A | 服务发现与Agent间通信 | 生产可用 |

### 1.3 阻碍落地的7个关键问题

#### P0 - 系统无法被用户使用

| # | 问题 | 具体表现 | 影响 |
|---|------|----------|------|
| 1 | **API层不可用** | `AppController`用`name="/app"`而非`value="/app"`，端点不生效；每次`new ManagerAgent()`，无法接收用户输入；返回`void`无响应体 | 用户无法通过HTTP调用系统 |
| 2 | **无流式推送** | Agent结果通过`System.out.print`输出到控制台，Web客户端无法接收 | 用户看不到实时规划过程 |
| 3 | **Hook阻塞控制台** | `planHook`中`UserAgent.call().block()`等待控制台输入，Web环境下死锁 | API调用会永久挂起 |

#### P1 - 系统不可部署

| # | 问题 | 具体表现 | 影响 |
|---|------|----------|------|
| 4 | **无容器化方案** | 4个Agent模块无Dockerfile，无docker-compose | 无法一键部署 |
| 5 | **无前端界面** | 核心模块无Web UI，Jmanus的Vue3前端是独立产品 | 无法演示 |

#### P2 - 系统不可信赖

| # | 问题 | 具体表现 | 影响 |
|---|------|----------|------|
| 6 | **无安全防护** | 无认证、无限流、无输入校验、API Key明文配置 | 安全风险 |
| 7 | **无可观测性** | 无健康检查、无Metrics、无链路追踪 | 故障无法定位 |

---

## 二、目标架构

### 2.1 架构全景图

```
                         用户层
    ┌─────────────────────────────────────────────┐
    │  Web前端 (Vue3 + Vite)                       │
    │  ├── 对话式交互界面                            │
    │  ├── SSE流式消息展示                           │
    │  ├── 历史记录查看                              │
    │  └── 规划结果可视化                            │
    └──────────────────┬──────────────────────────┘
                       │ HTTPS
                       │
    ┌──────────────────▼──────────────────────────┐
    │  Nginx                                       │
    │  ├── SSL卸载                                  │
    │  ├── 静态资源 (/assets)                       │
    │  ├── API反代 (/api → :8081)                  │
    │  └── 限流 (100 req/min per IP)               │
    └──────────────────┬──────────────────────────┘
                       │
    ┌──────────────────▼──────────────────────────┐
    │  ManagerAgent BFF (Spring Boot :8081)  ← 改造│
    │                                              │
    │  ┌─ REST API Layer ────────────────────────┐ │
    │  │ POST /api/v1/plan          创建规划任务   │ │
    │  │ GET  /api/v1/plan/stream   SSE流式推送   │ │
    │  │ GET  /api/v1/plan/{id}     查询规划结果   │ │
    │  │ GET  /api/v1/history       历史记录       │ │
    │  │ GET  /api/v1/stats         统计数据       │ │
    │  │ GET  /actuator/health      健康检查       │ │
    │  └──────────────────────────────────────────┘ │
    │                                              │
    │  ┌─ Service Layer ─────────────────────────┐ │
    │  │ TravelPlanService  (编排主逻辑)          │ │
    │  │ SessionManager     (会话管理)            │ │
    │  │ InputValidator     (输入校验+安全过滤)    │ │
    │  └──────────────────────────────────────────┘ │
    └──────────────────┬──────────────────────────┘
                       │ A2A (Nacos)
         ┌─────────────┼─────────────┐
         │             │             │
    ┌────▼────┐  ┌─────▼────┐  ┌────▼─────┐
    │ Route   │  │ Trip     │  │ Budget   │
    │ Agent   │  │ Planner  │  │ Agent    │
    │ :8082   │  │ :8085    │  │ :8083    │
    │         │  │          │  │          │
    │ MCP:    │  │ Skill:   │  │ Skill:   │
    │ 百度地图 │  │ 行程规划  │  │ 费用优化  │
    └─────────┘  └──────────┘  └──────────┘

    ┌─────────────────────────────────────────────┐
    │  基础设施层 (docker-compose)                   │
    │                                              │
    │  Nacos 3.1    Redis 7     MySQL 8            │
    │  :8848/:9848  :6379       :3306              │
    │  (注册中心)    (缓存)      (持久化)             │
    └─────────────────────────────────────────────┘
```

### 2.2 关键设计决策

| 决策点 | 选型 | 理由 |
|--------|------|------|
| 流式推送 | SSE (Server-Sent Events) | 单向推送、HTTP协议原生支持、无需额外依赖、与Spring WebFlux天然兼容 |
| API风格 | RESTful + SSE | 简单明了，前端对接成本低 |
| 前端框架 | Vue3 + Vite + TailwindCSS | 与Jmanus技术栈一致，团队可复用经验 |
| 部署方式 | docker-compose | 单机多容器，适合演示和小规模生产 |
| 认证方案 | 第一期不做认证 | 优先打通核心链路，认证后续迭代加入 |
| 网关 | Nginx (非Spring Cloud Gateway) | 轻量、够用、不增加Java进程 |

---

## 三、模块改造详细设计

### 3.1 ManagerAgent BFF层改造（核心工作量）

#### 3.1.1 新增REST API

```
目录结构变更：
manager_agent/src/main/java/managerAgent/
├── ManagerAgentApplication.java        (启动类，已有)
├── agents/
│   └── ManagerAgent.java               (改造：移除main()，注入为Bean)
├── controller/
│   ├── AppController.java              (删除，替换为下面两个)
│   ├── TravelPlanController.java       (新增：核心API)
│   └── HistoryController.java          (新增：历史查询)
├── service/
│   └── TravelPlanService.java          (新增：编排逻辑)
├── dto/
│   ├── PlanRequest.java                (新增：请求DTO)
│   ├── PlanResponse.java               (新增：响应DTO)
│   └── StreamEvent.java                (新增：SSE事件DTO)
├── hook/
│   └── planHook.java                   (改造：移除UserAgent阻塞)
├── tool/
│   └── RemoteAgentTool.java            (保留，微调)
├── config/
│   └── WebConfig.java                  (新增：CORS等配置)
└── plan/
    └── TripPlan.java                   (保留)
```

#### 3.1.2 API接口设计

**POST /api/v1/plan — 创建旅行规划**

```json
// Request
{
  "prompt": "帮我制定深圳到惠州3日游自驾游计划",
  "options": {
    "budget": 5000,
    "travelers": 2,
    "travelMode": "self-driving",
    "preferences": ["美食", "自然风光"]
  }
}

// Response (立即返回，不等待Agent完成)
{
  "planId": "plan_20260424_a1b2c3",
  "sessionId": "sess_xxx",
  "status": "PROCESSING",
  "streamUrl": "/api/v1/plan/plan_20260424_a1b2c3/stream",
  "createdAt": "2026-04-24T10:30:00"
}
```

**GET /api/v1/plan/{planId}/stream — SSE流式推送**

```
Content-Type: text/event-stream

event: agent_start
data: {"agent":"RouteMakingAgent","status":"started"}

event: thinking
data: {"agent":"ManagerAgent","content":"正在分析您的需求..."}

event: route_progress
data: {"agent":"RouteMakingAgent","content":"深圳→惠州，全程约150km..."}

event: itinerary_progress
data: {"agent":"TripPlannerAgent","content":"Day1: 上午抵达惠州西湖..."}

event: budget_progress
data: {"agent":"BudgetAgent","content":"交通费用：¥200..."}

event: complete
data: {"planId":"plan_xxx","totalTime":5200,"result":{...}}

event: error
data: {"code":"TIMEOUT","message":"规划超时，请重试"}
```

**GET /api/v1/plan/{planId} — 查询规划结果**

```json
// Response
{
  "planId": "plan_xxx",
  "status": "SUCCESS",
  "userRequest": "...",
  "route": { "content": "...", "executionTime": 3200 },
  "itinerary": { "content": "...", "executionTime": 3500 },
  "budget": { "content": "...", "executionTime": 2100 },
  "totalTime": 5600,
  "createdAt": "2026-04-24T10:30:00"
}
```

**GET /api/v1/history — 历史记录**

```json
// Query: ?page=0&size=10
// Response
{
  "content": [...],
  "totalPages": 5,
  "totalElements": 42
}
```

**GET /api/v1/stats — 统计数据**

```json
{
  "totalPlans": 42,
  "successRate": 92.5,
  "averageCost": 3500.00,
  "hotDestinations": ["惠州", "丽江", "大理"],
  "avgResponseTime": 5200
}
```

#### 3.1.3 SSE流式推送实现方案

```
核心链路:

TravelPlanController
    │
    ▼
TravelPlanService.planWithStream(request)
    │
    ├── 创建 Sinks.Many<StreamEvent> (Reactor热流)
    │
    ├── 异步启动 ParallelAgentService.callParallelThenSequential()
    │       │
    │       ├── RouteMakingAgent.stream()
    │       │       └── 每个Event → sink.tryEmitNext(routeEvent)
    │       │
    │       ├── TripPlannerAgent.stream()
    │       │       └── 每个Event → sink.tryEmitNext(itineraryEvent)
    │       │
    │       └── BudgetAgent
    │               └── 每个Event → sink.tryEmitNext(budgetEvent)
    │
    └── 返回 Flux<ServerSentEvent<StreamEvent>>
            │
            └── 前端 EventSource 消费
```

关键实现点：
- 使用 `Sinks.many().multicast().onBackpressureBuffer()` 创建多播流
- 每个Agent的流式输出通过sink推送到前端
- 超时控制：整体60秒，单Agent 30秒
- 完成/错误时发送终止事件并关闭sink

#### 3.1.4 planHook改造

```
当前问题:
  PostReasoningEvent中调用 UserAgent.call().block()
  → 等待控制台输入 → Web环境死锁

改造方案:
  - 移除 UserAgent 交互式阻塞
  - 改为：将Plan状态推送到SSE流
  - 前端展示计划步骤（只读，不交互）
  - 后续迭代：支持用户通过API提交修改意见
```

### 3.2 ManagerAgent Bean化改造

```
当前问题:
  ManagerAgent是普通类，在Controller中 new 创建
  → 每次请求创建新实例
  → 无法使用Spring的依赖注入
  → 内部hard-coded prompt

改造方案:
  - ManagerAgent 添加 @Component 注解
  - 注入 AgentProperties 配置
  - runWithPrompt() 接收外部传入的prompt
  - 返回 Flux<Event> 给Service层（已有streamResponse方法）
  - 移除 main() 方法
```

---

## 四、前端设计

### 4.1 技术选型

```
Vue 3.4 + TypeScript
Vite 6
TailwindCSS 4
Markdown-it (渲染Agent输出的Markdown)
EventSource API (SSE消费)
```

### 4.2 页面结构

```
ATPlan Web
├── / (首页)
│   ├── 左侧：历史记录列表
│   ├── 中间：对话交互区
│   │   ├── 用户输入框 (底部)
│   │   ├── 消息气泡流 (中间)
│   │   │   ├── 用户消息 (右侧)
│   │   │   ├── Agent思考中... (左侧, 带动画)
│   │   │   ├── 路线规划结果 (左侧, 可折叠卡片)
│   │   │   ├── 行程规划结果 (左侧, 可折叠卡片)
│   │   │   └── 费用统计结果 (左侧, 可折叠卡片)
│   │   └── Agent状态指示器 (顶部)
│   │       ├── RouteMaking: ● 运行中 / ✓ 完成
│   │       ├── TripPlanner: ● 运行中 / ✓ 完成
│   │       └── Budget:      ○ 等待中
│   └── 右侧：规划结果面板 (可选)
│       ├── 路线地图预览 (百度地图)
│       ├── 费用饼图
│       └── 天气信息
└── /history (历史详情页)
```

### 4.3 SSE消费流程

```
用户点击"开始规划"
    │
    ▼
POST /api/v1/plan → 获取 planId
    │
    ▼
new EventSource(`/api/v1/plan/${planId}/stream`)
    │
    ├── onmessage("agent_start")  → 更新状态指示器
    ├── onmessage("thinking")     → 显示"思考中"动画
    ├── onmessage("route_progress") → 实时追加路线文本 (typewriter效果)
    ├── onmessage("itinerary_progress") → 实时追加行程文本
    ├── onmessage("budget_progress") → 实时追加费用文本
    ├── onmessage("complete")     → 显示完成状态，渲染最终结果
    └── onerror                   → 显示错误，提供重试按钮
```

---

## 五、数据库设计

### 5.1 表结构（复用现有，微调）

```sql
-- 现有的 travel_plan_history 表（已在代码中通过JPA定义）
-- 新增字段标注 [NEW]

CREATE TABLE travel_plan_history (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id         VARCHAR(64) UNIQUE NOT NULL,      -- [NEW] 外部可见ID
    session_id      VARCHAR(64) UNIQUE,
    user_request    TEXT NOT NULL,
    origin          VARCHAR(100),
    destination     VARCHAR(100),
    days            INT,
    route_result    LONGTEXT,
    itinerary_result LONGTEXT,
    budget_result   LONGTEXT,
    total_cost      DECIMAL(10,2),
    status          VARCHAR(20) DEFAULT 'PENDING',     -- PENDING/PROCESSING/SUCCESS/PARTIAL/FAILED
    error_message   TEXT,
    execution_time  BIGINT,                            -- 毫秒
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_plan_id (plan_id),
    INDEX idx_status (status),
    INDEX idx_destination (destination),
    INDEX idx_created_at (created_at)
);
```

### 5.2 Redis缓存策略（复用现有）

```
atplan:route:{hash}      → AgentResult    TTL 1h    (路线缓存)
atplan:session:{id}      → SessionData    TTL 24h   (会话缓存)
atplan:plan:{planId}     → PlanStatus     TTL 2h    (规划状态,SSE重连用) [NEW]
atplan:ratelimit:{ip}    → counter        TTL 1min  (限流计数器) [NEW]
```

---

## 六、部署方案

### 6.1 docker-compose 全景

```yaml
# docker-compose.yml 结构设计

services:
  # 基础设施
  nacos:        # Nacos 3.1, standalone模式
  redis:        # Redis 7 Alpine
  mysql:        # MySQL 8 (生产) / 可选H2(演示)
  
  # Agent服务
  manager-agent:       # :8081, 依赖nacos/redis/mysql
  route-agent:         # :8082, 依赖nacos
  trip-planner-agent:  # :8085, 依赖nacos
  budget-agent:        # :8083, 依赖nacos
  
  # 前端 + 网关
  nginx:               # :80/:443, 反代API + 托管前端静态文件

# 网络
networks:
  atplan-net:
    driver: bridge

# 数据卷
volumes:
  mysql-data:
  redis-data:
  nacos-data:
```

### 6.2 Dockerfile 设计（4个Agent共用基础镜像）

```dockerfile
# 基础镜像策略
FROM eclipse-temurin:17-jre-alpine    # ~80MB，比JDK镜像小60%

# 多阶段构建
# Stage 1: Maven构建（CI中执行或在builder阶段）
# Stage 2: 运行时镜像（仅包含JRE + JAR）
```

### 6.3 端口规划

```
外部暴露:
  80/443  → Nginx (唯一入口)

内部网络 (atplan-net):
  8081    → ManagerAgent
  8082    → RouteMakingAgent
  8083    → BudgetAgent
  8085    → TripPlannerAgent
  8848    → Nacos HTTP
  9848    → Nacos gRPC
  6379    → Redis
  3306    → MySQL
```

### 6.4 环境变量管理

```bash
# .env 文件（不提交到Git）
DASHSCOPE_API_KEY=sk-xxx
BAIDU_MCP_URL=https://xxx
MYSQL_ROOT_PASSWORD=xxx
REDIS_PASSWORD=xxx
NACOS_SERVER_ADDR=nacos:8848

# 各Agent通过环境变量注入
# 已在application.yml中用${ENV_VAR:default}格式支持
```

---

## 七、可观测性设计

### 7.1 健康检查

```
每个Agent模块添加:
  spring-boot-starter-actuator
  
端点:
  GET /actuator/health          → 基础健康
  GET /actuator/health/    → Nacos连接状态
  GET /actuator/health/redis    → Redis连接状态
  GET /actuator/health/db       → 数据库连接状态

docker-compose中:
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:808x/actuator/health"]
    interval: 30s
    timeout: 10s
    retries: 3
```

### 7.2 业务指标（Micrometer + Prometheus）

```
自定义Metrics:
  atplan.plan.total              (Counter)  规划总次数
  atplan.plan.success            (Counter)  成功次数
  atplan.plan.duration           (Timer)    规划耗时分布
  atplan.agent.call.duration     (Timer)    单Agent调用耗时
  atplan.agent.call.error        (Counter)  Agent调用失败次数
  atplan.cache.hit               (Counter)  缓存命中次数
  atplan.cache.miss              (Counter)  缓存未命中次数
  atplan.llm.token.usage         (Counter)  LLM Token消耗量
```

### 7.3 日志规范

```
统一日志格式 (JSON):
{
  "timestamp": "2026-04-24T10:30:00.123",
  "level": "INFO",
  "traceId": "abc123",            ← 全链路追踪ID
  "planId": "plan_xxx",           ← 业务ID
  "agent": "RouteMakingAgent",    ← Agent标识
  "message": "调用成功，耗时3200ms",
  "duration": 3200
}

通过 MDC + logback 实现
traceId 在 ManagerAgent 入口生成，传递给所有子Agent
```

---

## 八、安全防护设计

### 8.1 第一期（最小可行安全）

```
1. 输入校验
   - prompt长度限制：10-2000字符
   - XSS过滤：移除HTML标签
   - 注入防护：参数化查询（JPA已保证）

2. 速率限制
   - Nginx层：100 req/min per IP
   - 应用层：10 plan/min per session（Redis计数器）

3. API Key保护
   - 通过环境变量注入，不在配置文件硬编码
   - .env文件加入.gitignore

4. CORS配置
   - 仅允许前端域名
```

### 8.2 第二期（完整安全）

```
1. JWT认证
2. 用户注册/登录
3. LLM成本控制（每日Token额度）
4. 请求审计日志
5. HTTPS强制
```

---

## 九、分阶段实施路线图

### Phase 1: API层打通 + SSE流式推送（1-2天）

```
目标：让前端可以通过HTTP调用Agent并实时看到结果

改造文件：
  [删除] AppController.java
  [新增] TravelPlanController.java    — REST API + SSE端点
  [新增] HistoryController.java       — 历史查询API
  [新增] TravelPlanService.java       — 编排逻辑，桥接API与Agent
  [新增] dto/PlanRequest.java         — 请求DTO
  [新增] dto/PlanResponse.java        — 响应DTO
  [新增] dto/StreamEvent.java         — SSE事件DTO
  [新增] config/WebConfig.java        — CORS配置
  [改造] ManagerAgent.java            — Bean化，移除main()
  [改造] planHook.java                — 移除UserAgent阻塞
  [改造] RemoteAgentTool.java         — 接入SSE sink
  [改造] TravelPlanHistory.java       — 新增planId字段
```

### Phase 2: Web前端（1-2天）

```
目标：可演示的对话式交互界面

新增目录：
  code/AiTripPlan/AiTripPlan-AgentScope/frontend/
  ├── package.json
  ├── vite.config.ts
  ├── src/
  │   ├── App.vue
  │   ├── views/
  │   │   └── ChatView.vue           — 主对话页
  │   ├── components/
  │   │   ├── ChatInput.vue          — 输入框
  │   │   ├── MessageBubble.vue      — 消息气泡
  │   │   ├── AgentStatus.vue        — Agent状态指示器
  │   │   ├── ResultCard.vue         — 结果折叠卡片
  │   │   └── HistorySidebar.vue     — 历史侧栏
  │   ├── composables/
  │   │   └── useSSE.ts              — SSE连接管理
  │   ├── api/
  │   │   └── plan.ts                — API调用封装
  │   └── types/
  │       └── index.ts               — 类型定义
  └── tailwind.config.ts
```

### Phase 3: Docker一键部署（0.5天）

```
目标：docker-compose up -d 启动全部服务

新增文件：
  docker-compose.yml                  — 服务编排
  docker-compose.dev.yml              — 开发环境覆盖
  .env.example                        — 环境变量模板
  code/AiTripPlan/AiTripPlan-AgentScope/Dockerfile   — 多阶段构建
  nginx/nginx.conf                    — 反代 + 静态资源
  nginx/default.conf                  — server配置
```

### Phase 4: 可观测性 + 安全加固（0.5天）

```
目标：基本的健康检查、指标监控、输入校验

改造：
  各模块pom.xml → 添加 spring-boot-starter-actuator
  commons/ → 添加 InputValidator, RateLimiter
  application.yml → 添加 actuator 配置
  logback-spring.xml → JSON格式日志
```

### Phase 5: 扩展Agent + 高级特性（未来迭代）

```
- WeatherAgent: 接入天气API，自动获取目的地天气预报
- HotelAgent: 接入酒店比价API，推荐住宿
- 多轮对话: 用户反馈 → Agent迭代优化方案
- 用户认证: JWT + 用户体系
- 成本控制: LLM Token额度管理
- 地图可视化: 百度地图JS SDK渲染路线
```

---

## 十、技术风险与应对

| 风险 | 可能性 | 影响 | 应对方案 |
|------|--------|------|----------|
| Agent调用超时（LLM响应慢） | 高 | 用户等待过长 | 60s超时 + 优雅降级（返回已完成的部分） |
| Nacos注册中心不可用 | 中 | 所有Agent间通信中断 | 健康检查 + 自动重连 + 本地缓存Agent地址 |
| Redis宕机 | 低 | 缓存失效，每次都调LLM | 缓存穿透降级为直接调用，不影响核心流程 |
| 百度地图MCP不可用 | 中 | 路线规划无真实地图数据 | RouteMakingAgent降级为纯LLM规划 |
| DashScope API Key额度耗尽 | 中 | 全部Agent不可用 | 监控Token用量 + 告警 + 成本控制 |
| 并发请求过多 | 中 | 系统过载 | Nginx限流 + 应用层限流 + 队列缓冲 |

---

## 十一、工作量评估

| Phase | 内容 | 预计工时 | 优先级 |
|-------|------|----------|--------|
| Phase 1 | API层 + SSE流式推送 | 8-12h | P0 |
| Phase 2 | Web前端 | 8-12h | P0 |
| Phase 3 | Docker部署 | 3-4h | P1 |
| Phase 4 | 可观测性 + 安全 | 3-4h | P1 |
| Phase 5 | 扩展Agent | 按需 | P2 |

**总计：Phase 1-4 约 22-32h 工作量，可在3-5天内完成核心落地。**

---

## 十二、验收标准

### MVP验收 (Phase 1 + 2 完成)

- [ ] 用户通过浏览器输入旅行需求
- [ ] 系统实时展示3个Agent的工作进度
- [ ] 30秒内返回包含路线、行程、预算的完整方案
- [ ] 历史记录可查看
- [ ] 支持多人同时使用（至少5并发）

### 生产验收 (Phase 1-4 全部完成)

- [ ] docker-compose up -d 一键启动所有服务
- [ ] 健康检查端点正常
- [ ] 异常请求被拦截（过长prompt、频繁请求）
- [ ] 系统重启后历史数据不丢失
- [ ] 日志中包含traceId可追踪完整调用链
