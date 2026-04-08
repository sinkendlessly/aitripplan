ATPlan-多agent智旅系统                                                    

概述：  基于 Spring Boot + Spring AI Alibaba + AgentScope 构建的智能旅行规划平台，采用多Agent协作架构，通过 A2A协议实现多智能体的自主任务分解与协同执行，支持 ReAct、Graph 状态机等多种工作流编排模式，集成百度地图 MCP Server 获取实时路线数据，为用户提供端到端的智能出行方案。

技术栈：Java 17 +  Spring Boot + Spring AI + AgentScope + MCP + Spring Data JPA + MySQL +PostgreSQL + Docker ——前端  React + Vite

项目要点：

搭建三个 Agent 微服务，基于Nacos + A2A 跨服务通信，封装 RemoteAgentTool 将远程 Agent 代理为本地工具调用。

设计并实现ManagerAgent 任务调度引擎，集成 PlanNotebook 实现复杂旅行需求的自主分解，支持人机协同确认机制。

基于Spring AI Alibaba封装 ReAct 和 StateGraph两种工作流编排模式，Graph模式支持并行节点执行与条件分支回退。

对接 百度地图 MCP Server，通过 SSE 协议实现工具动态发现与热挂载，为路线规划 Agent 提供实时地理数据。

搭建 OpenAI 兼容适配层，使系统可作为标准 LLM 后端被第三方客户端（如 ChatGPT 前端）直接调用。

构建 Agent 稳定性保障体系：Token 感知自动记忆压缩（token计算+LLM摘要）、循环检测、指数退避重试。

采用 Docker Compose 编排多服务部署，实现一键启动完整分布式 Agent 集群。
