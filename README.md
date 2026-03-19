ATplan — AI智旅规划　　后端开发　　2026.01 — 2026.03

概述：基于多Agent协同的AI旅游行程规划系统，支持自动生成包含吃住行、天气、费用的完整旅游方案

技术栈：Java 17 + Spring Boot 3.5 + Spring AI Alibaba + AgentScope + MCP + Nacos

项目要点：

设计多Agent微服务架构，将旅游规划拆分为路线制定、行程规划、费用统筹三个独立Agent，通过Nacos实现服务注册与发现

基于Spring AI Alibaba Graph实现DAG工作流，通过条件边实现费用超限时自动回退重新规划的闭环控制

集成百度地图MCP Server，通过SSE协议实现异步通信，采用双重检查锁保证客户端线程安全初始化
