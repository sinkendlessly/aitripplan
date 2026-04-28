# Travel Planning Closed Loop

## 闭环目标

将 `route-planning`、`itinerary-planning`、`budget-optimization` 三个 Skill 串联为可迭代决策环：

`Route 初版` → `Itinerary 排程` → `Budget 优化` → `Route/Itinerary 回灌修正` → `最终执行版`

## 角色分工

- `route-planning`：产出可行路径与风险兜底
- `itinerary-planning`：产出按天时间块日程
- `budget-optimization`：产出预算拆解与降本动作

## 标准输入输出接口

### Route 输出给 Itinerary / Budget
- 候选路线清单
- 各段时长与成本区间
- 关键换乘点与风险点

### Itinerary 输出给 Budget
- 每日活动与移动安排
- 可删减项 / 不可降级项
- 每日预算压力点

### Budget 回灌给 Route / Itinerary
- 需调整的交通段
- 需删减或替换的活动段
- E/B/C 三档目标下的版本建议

## 迭代停止条件

当满足以下条件时停止迭代并输出最终版：
1. 总预算进入可行或临界区间
2. 每日节奏可执行（不超强度）
3. 关键交通段有兜底方案
4. 用户确认主目标（省钱/省时/稳健）已满足

## 推荐触发语句

- “给我做一个路线、行程、预算联动的完整旅行方案。”
- “先排路线和行程，再按预算压缩，最后给我执行版。”
- “预算只有 6000，帮我做一个闭环迭代后的旅行计划。”
