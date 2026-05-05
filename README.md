# ATPlan - 多 Agent 智旅系统

[![Java](https://img.shields.io/badge/Java-17-blue)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-brightgreen)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-61DAFB)](https://react.dev/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)
![Uploading image.png…]()

ATPlan 是一个基于多智能体协作的 AI 旅行规划系统。用户输入出发地、目的地和旅行天数，系统通过四个专业 Agent 自动生成包含路线、行程和预算的完整旅行方案。

```
用户输入 "从深圳到惠州，3天自驾游"
        │
        ▼
┌────────────────────────────────────────────────┐
│              ManagerAgent (调度中心)              │
│  接收请求 → 分配任务 → 汇总结果 → SSE 实时推送     │
└────────────────────────────────────────────────┘
        │
        ├────────── 并行 ──────────┐
        ▼                          ▼
┌──────────────────┐   ┌──────────────────────┐
│ RouteMakingAgent │   │ TripPlannerAgent      │
│   路线规划 Agent   │   │   行程编排 Agent       │
│  (百度地图 MCP)   │   │   (景点/餐饮/住宿)     │
└────────┬─────────┘   └──────────┬───────────┘
         │                        │
         └──────────┬─────────────┘
                    ▼
         ┌──────────────────────┐
         │   BudgetAgent         │
         │   预算分析 Agent       │
         │   (费用明细/优化建议)   │
         └──────────┬───────────┘
                    │
                    ▼
         ┌──────────────────────┐
         │   完整旅行方案          │
         │  路线 + 行程 + 预算    │
         └──────────────────────┘
```

## 技术栈

| 层 | 技术 |
|------|--------|
| 后端语言 | Java 17 |
| 框架 | Spring Boot 4.0 |
| AI 框架 | AgentScope 1.8 (A2A 协议) |
| 大模型 | DashScope (阿里灵积) |
| 服务发现 | Nacos 3.1 |
| 数据库 | MySQL 8 (生产) / H2 (开发) |
| 缓存 | Redis 7 |
| 前端 | React 19 + TypeScript + Vite + Tailwind CSS |
| 部署 | Docker Compose |

## 项目要点

**核心链路：** 后端采用「并行调用 + 串行聚合」的任务编排模式——ManagerAgent 同时调起路线规划和行程编排两个子 Agent，两者都返回结果后，再调用预算 Agent 做费用分析；单个 Agent 超时或失败不影响整体，最终按成功数量判定状态（完全成功/部分成功/失败）。

**统一调用：** 基于 Nacos + A2A 协议实现子 Agent 的注册发现与远程调用；封装 `RemoteAgentTool`，通过 `@Tool` 注解将远程 Agent 代理为 LLM 可调用的本地工具，统一调用接口，新增或替换子 Agent 只需改配置无需改代码。

**流式与持久化：** 使用 Reactor Flux + SSE 实时推送规划进度，前端逐条展示各阶段进展并支持断线重连及 HTTP 降级兜底；Redis 缓存路线结果避免重复 AI 调用，MySQL 持久化规划记录支持历史查询和统计分析。

**能力接入：** 设计路线规划、行程制定、费用预算三个领域 Skill，通过 Prompt 注入 Agent 上下文；接入百度地图 MCP 服务获取实时地理数据，MCP 连接失败时自动降级不影响整体流程。

## 快速启动

### 前置条件

- [Docker Desktop](https://www.docker.com/products/docker-desktop/)（运行 7 个容器）
- DashScope API Key（[免费申请](https://dashscope.aliyun.com/)）

### 步骤

```bash
# 1. 克隆仓库
git clone https://github.com/sinkendlessly/aitripplan.git
cd aitripplan

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env，填入 DASHSCOPE_API_KEY

# 3. 一键启动
docker compose up -d --build
```

首次构建需 10-20 分钟（下载 Maven 依赖 + 构建 Docker 镜像）。启动后：

| 服务 | 地址 |
|------|------|
| 前端页面 | http://localhost:5173 |
| ManagerAgent API | http://localhost:8081 |
| Nacos 控制台 | http://localhost:8848/nacos |

### 本地开发模式

```bash
# 基础设施（只需启动一次）
docker compose up -d nacos redis mysql

# 后端（IDEA 直接运行）
# 激活 dev profile，用 H2 内存库，无需 MySQL

# 前端
cd frontend && npm install && npm run dev
```

## 项目结构

```
aitripplan/
├── frontend/                          # React 前端
│   └── src/
│       ├── pages/                     # 页面组件
│       │   ├── HomePage.tsx           # 旅行需求提交表单
│       │   ├── PlanPage.tsx           # SSE 实时结果展示
│       │   ├── HistoryPage.tsx        # 历史记录
│       │   └── StatsPage.tsx          # 数据统计
│       ├── components/                # 通用组件
│       │   ├── RouteView.tsx          # 路线展示
│       │   ├── ItineraryView.tsx      # 行程展示
│       │   ├── BudgetView.tsx         # 预算展示
│       │   └── ProgressTimeline.tsx   # 进度时间线
│       ├── hooks/
│       │   └── usePlanStream.ts       # SSE 连接与重连
│       └── services/
│           ├── api.ts                 # HTTP 请求封装
│           └── parser.ts              # LLM JSON 解析
│
├── code/AiTripPlan/AiTripPlan-AgentScope/
│   ├── manager_agent/                 # 调度服务 (:8081)
│   │   └── src/main/java/managerAgent/
│   │       ├── controller/            # REST API
│   │       │   ├── TravelPlanController.java
│   │       │   ├── HistoryController.java
│   │       │   └── AdminController.java
│   │       ├── service/
│   │       │   └── TravelPlanService.java   # 核心编排
│   │       ├── agents/
│   │       │   └── ManagerAgent.java        # v1 ReAct 模式
│   │       └── tool/
│   │           └── RemoteAgentTool.java     # Agent 调用封装
│   ├── routeMaking_agent/             # 路线规划 (:8082)
│   │   └── src/main/java/.../
│   │       ├── agents/RouteMakingAgent.java
│   │       └── mcp/BaiduMapMCP.java
│   ├── tripPlanner_agent/             # 行程编排 (:8085)
│   ├── budget_agent/                  # 预算分析 (:8083)
│   └── commons/                       # 公共模块
│       └── src/main/java/
│           ├── utils/
│           │   ├── ParallelAgentService.java
│           │   ├── SimpleCircuitBreaker.java
│           │   ├── JsonValidator.java
│           │   ├── PromptSanitizer.java
│           │   └── CachingAgentCardResolver.java
│           ├── model/                 # 数据模型
│           ├── entity/                # JPA 实体
│           └── service/               # 缓存/历史服务
│
├── docker-compose.yml                 # 7 服务编排
├── skills/                            # Agent Skill 定义
└── scripts/init-db.sql                # MySQL 初始化脚本
```

## API 文档

### 创建规划

```http
POST /api/v1/plan
Content-Type: application/json

{
    "prompt": "从深圳到惠州，3天自驾游，预算3000元",
    "options": {
        "budget": 3000,
        "travelers": 2
    }
}
```

**响应：**
```json
{
    "planId": "plan_20260504_120000_a1b2c3",
    "status": "PROCESSING",
    "streamUrl": "/api/v1/plan/plan_20260504_120000_a1b2c3/stream"
}
```

### SSE 实时流

```http
GET /api/v1/plan/{planId}/stream
Accept: text/event-stream
```

```
event: thinking
data: {"type":"thinking","content":"正在分析您的旅行需求..."}

event: agent_start
data: {"type":"agent_start","agent":"RouteMakingAgent"}

event: route_progress
data: {"type":"progress","agent":"RouteMakingAgent","content":"从深圳出发，沿G25长深高速..."}

event: complete
data: {"type":"complete","planId":"plan_...","totalTime":45230}
```

### 历史与统计

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/v1/history?page=0&size=10` | 分页历史记录 |
| `GET` | `/api/v1/stats` | 规划统计（成功率/热门目的地） |

### 管理接口（Basic Auth 保护）

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/admin/cache/stats` | 缓存统计 |
| `DELETE` | `/admin/cache/route?origin=深圳&destination=惠州` | 清除路线缓存 |

## 环境变量

| 变量 | 必须 | 说明 |
|------|--------|------|
| `DASHSCOPE_API_KEY` | 是 | 阿里灵积 API 密钥 |
| `MYSQL_ROOT_PASSWORD` | 否 | 默认 `atplan2026` |
| `BAIDU_MCP_URL` | 否 | 百度地图 MCP 服务地址 |
| `NACOS_SERVER_ADDR` | 否 | 默认 `nacos:8848` |

## 配置说明

项目支持两套运行环境：

- **dev**（开发）：使用 H2 内存数据库，自动建表，启动无需 MySQL
- **prod**（生产）：使用 MySQL，Docker Compose 默认激活

通过 `SPRING_PROFILES_ACTIVE=dev` 或 `=prod` 切换。

## 安全机制

- **Propmt 注入防护：** 用户输入经过 `PromptSanitizer` 过滤，拦截越狱指令后再拼接 Prompt
- **AI 输出校验：** `JsonValidator` 校验 LLM 返回的 JSON 包含必要字段，格式不对不崩溃
- **接口鉴权：** `/admin/` 路径受 Basic Auth 保护
- **请求校验：** `@Valid` + `@Size(min=5, max=2000)` 限制输入长度
- **熔断降级：** `SimpleCircuitBreaker` 隔离故障 Agent，Fast Fail 快速拒绝
- **超时控制：** 每个 Agent 调用 60s 超时，非超时异常自动重试 1 次

## 设计模式

| 模式 | 应用位置 |
|-------|---------|
| **编排器模式** | TravelPlanService 编排子 Agent 调用顺序 |
| **CQRS** | 写操作同步返回 planId，读操作通过 SSE 异步推送 |
| **装饰器模式** | CachingAgentCardResolver 为服务发现添加本地+Redis 缓存 |
| **熔断器模式** | SimpleCircuitBreaker 实现服务隔离 |
| **外观模式** | RemoteAgentTool 统一封装 Agent 调用细节 |

## License

MIT
