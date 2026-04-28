# ATPlan 多Agent架构重构说明

## 一、新架构概览

```
┌─────────────────────────────────────────────────────────────────────┐
│                         ManagerAgent (8081)                          │
│                         主管Agent - 统一入口                          │
│  职责: 接收用户请求 → 调用并行规划工具 → 整合输出最终结果                │
└──────────────────────┬──────────────────────────────────────────────┘
                       │ 调用 planTravelWithBudget()
                       ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    ParallelAgentService                              │
│                    并行调用服务 - 统一调度层                          │
└──────────────┬─────────────────────────────┬────────────────────────┘
               │ 并行调用                      │ 并行调用
               ▼                             ▼
┌──────────────────────────┐    ┌──────────────────────────┐
│ RouteMakingAgent (8082)  │    │ TripPlannerAgent (8085)  │
│ 路线规划Agent             │    │ 行程规划Agent             │
│ 职责: 规划最优路线         │    │ 职责: 安排每日行程         │
│ 输出: 路线方案+交通成本    │    │ 输出: 行程安排+门票餐饮    │
└──────────┬───────────────┘    └──────────┬───────────────┘
           │ 返回结果                        │ 返回结果
           │                                │
           └──────────────┬─────────────────┘
                          │ 汇总两个结果
                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      BudgetAgent (8083)  ← 新增                      │
│                      费用统计Agent                                    │
│  职责: 接收路线+行程结果 → 统计总费用 → 输出预算分析+优化建议           │
│  输出: 费用明细表 + 三档预算方案 + 优化建议                           │
└─────────────────────────────────────────────────────────────────────┘
```

## 二、核心改进点

### 1. 并行调用提升性能

**旧架构（串行）:**
```
ManagerAgent → RouteMakingAgent (等待3s) 
             → TripPlannerAgent (等待3s)
             → 总耗时: 6s+
```

**新架构（并行）:**
```
ManagerAgent → RouteMakingAgent (3s) ─┐
             → TripPlannerAgent (3s) ─┼→ BudgetAgent (2s)
                                       │
             总耗时: max(3s) + 2s = 5s
```

**性能提升:** 理论上可节省 30-50% 的响应时间

### 2. 容错机制避免单点阻塞

```java
// ParallelAgentService 提供多种容错策略:

1. 超时控制: 每个Agent独立设置超时时间(默认60s)
2. 错误隔离: 单个Agent失败不影响其他Agent
3. 优雅降级: BudgetAgent可以处理部分失败的情况
4. 快速失败: 可选模式，关键Agent失败立即终止
```

### 3. 新增 BudgetAgent 费用统计

**职责:**
- 接收路线规划的交通费用
- 接收行程规划的住宿/餐饮/门票费用
- 汇总输出完整的费用分析报告

**输出格式:**
```
=== 费用明细表 ===
- 交通费用: ¥XXX
- 住宿费用: ¥XXX
- 餐饮费用: ¥XXX
- 门票费用: ¥XXX
- 其他费用: ¥XXX

=== 三档预算方案 ===
- E档(经济): ¥XXX
- B档(均衡): ¥XXX
- C档(舒适): ¥XXX

=== 优化建议 ===
- 可节省项目1: XXX (省¥XXX)
- 可节省项目2: XXX (省¥XXX)
```

## 三、关键代码文件

| 文件 | 说明 |
|------|------|
| `budget_agent/` | 新增的费用统计Agent模块 |
| `commons/src/main/java/utils/ParallelAgentService.java` | 并行调用服务，支持超时和容错 |
| `commons/src/main/java/model/AgentResult.java` | Agent结果封装类 |
| `commons/src/main/java/model/TravelPlanContext.java` | 旅行规划上下文 |
| `manager_agent/src/main/java/managerAgent/tool/RemoteAgentTool.java` | 重构后的远程Agent工具 |
| `manager_agent/src/main/java/managerAgent/agents/ManagerAgent.java` | 适配新架构的主管Agent |

## 四、启动顺序

```bash
# 1. 启动 Nacos 注册中心
# 2. 启动 BudgetAgent (新增)
cd budget_agent
mvn spring-boot:run

# 3. 启动 RouteMakingAgent
cd routeMaking_agent
mvn spring-boot:run

# 4. 启动 TripPlannerAgent
cd tripPlanner_agent
mvn spring-boot:run

# 5. 启动 ManagerAgent
cd manager_agent
mvn spring-boot:run
```

**端口分配:**
- ManagerAgent: 8081
- RouteMakingAgent: 8082
- BudgetAgent: 8083 (新增)
- TripPlannerAgent: 8085

## 五、调用流程示例

```
用户: "帮我制定深圳到惠州3日游计划"

ManagerAgent:
  ↓ 调用 planTravelWithBudget()

ParallelAgentService:
  ├─→ 并行调用 RouteMakingAgent
  │   输出: "路线: 深圳→惠州，距离150km，油费¥120，过路费¥80"
  │
  └─→ 并行调用 TripPlannerAgent
      输出: "Day1: 西湖(门票¥50)...住宿¥300/晚"

  ↓ 两个都完成后

BudgetAgent:
  输入: 路线结果 + 行程结果
  输出:
    === 费用统计 ===
    交通: ¥200
    住宿: ¥900 (3晚)
    门票: ¥200
    餐饮: ¥600
    -------------
    总计: ¥1900
    
    优化建议: 选择民宿可节省¥300

ManagerAgent:
  ↓ 整合输出给用户

用户看到: 完整路线 + 详细行程 + 费用分析
```

## 六、扩展建议

### 1. 添加缓存层
```java
// 对常见路线查询结果进行Redis缓存
@Cacheable(value = "route", key = "#origin + '_' + #destination")
public String getRoute(String origin, String destination) { ... }
```

### 2. 异步消息队列
```java
// 对于耗时较长的规划任务，使用MQ异步处理
rabbitTemplate.convertAndSend("travel.plan.queue", planRequest);
```

### 3. 更多子Agent
```
WeatherAgent - 获取目的地天气预报
HotelAgent - 酒店比价和预订
FoodAgent - 美食推荐
```

### 4. 数据库持久化
```sql
-- 保存规划历史
CREATE TABLE travel_plan_history (
    id BIGINT PRIMARY KEY,
    user_id VARCHAR(64),
    request TEXT,
    route_result TEXT,
    itinerary_result TEXT,
    budget_result TEXT,
    total_cost DECIMAL(10,2),
    create_time TIMESTAMP
);
```

## 七、面试亮点

这次重构体现了以下技术能力:

1. **响应式编程**: 使用 Reactor Mono 实现非阻塞异步调用
2. **并行计算**: 通过并行调用提升系统吞吐量
3. **容错设计**: 超时控制、错误隔离、优雅降级
4. **架构演进**: 从串行到并行，从简单到完善的架构升级
5. **模块化设计**: 新增Agent不影响现有系统，符合开闭原则
