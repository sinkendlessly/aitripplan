-- ============================================
-- ATPlan 数据库初始化脚本
-- 在MySQL容器启动时自动执行
-- ============================================

-- 创建atplan业务数据库
CREATE DATABASE IF NOT EXISTS atplan CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建nacos配置数据库
CREATE DATABASE IF NOT EXISTS nacos CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 授予atplan用户权限
GRANT ALL PRIVILEGES ON atplan.* TO 'atplan'@'%';
GRANT ALL PRIVILEGES ON nacos.* TO 'atplan'@'%';
FLUSH PRIVILEGES;

-- 切换到atplan数据库创建表
USE atplan;

-- 旅行规划历史记录表
CREATE TABLE IF NOT EXISTS travel_plan_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id VARCHAR(64) NOT NULL UNIQUE COMMENT '规划任务ID',
    session_id VARCHAR(64) COMMENT '会话ID',
    user_prompt TEXT NOT NULL COMMENT '用户输入',
    origin VARCHAR(100) COMMENT '出发地',
    destination VARCHAR(100) COMMENT '目的地',
    days INT COMMENT '旅行天数',
    budget DECIMAL(10,2) COMMENT '预算',
    travelers INT COMMENT '出行人数',
    travel_mode VARCHAR(50) COMMENT '出行方式',
    status VARCHAR(20) NOT NULL COMMENT '状态: PROCESSING/SUCCESS/FAILED',
    route_result JSON COMMENT '路线规划结果',
    itinerary_result JSON COMMENT '行程规划结果',
    budget_result JSON COMMENT '预算规划结果',
    final_plan JSON COMMENT '最终整合方案',
    error_message TEXT COMMENT '错误信息',
    duration_ms BIGINT COMMENT '总耗时(毫秒)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id),
    INDEX idx_created_at (created_at),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='旅行规划历史记录';

-- Agent调用日志表 (用于监控和调试)
CREATE TABLE IF NOT EXISTS agent_call_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id VARCHAR(64) NOT NULL COMMENT '规划任务ID',
    trace_id VARCHAR(64) COMMENT '链路追踪ID',
    agent_name VARCHAR(50) NOT NULL COMMENT 'Agent名称',
    call_type VARCHAR(20) COMMENT '调用类型: SYNC/ASYNC',
    request_params JSON COMMENT '请求参数',
    response_result JSON COMMENT '响应结果',
    status VARCHAR(20) COMMENT '状态: SUCCESS/FAILED/TIMEOUT',
    duration_ms INT COMMENT '耗时(毫秒)',
    error_message TEXT COMMENT '错误信息',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_plan_id (plan_id),
    INDEX idx_trace_id (trace_id),
    INDEX idx_agent_name (agent_name),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent调用日志';
