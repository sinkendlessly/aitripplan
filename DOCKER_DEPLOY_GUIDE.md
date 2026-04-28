# ATPlan Docker部署指南

> 本文档帮助你快速部署ATPlan AI旅行规划系统，包含Nacos、Redis、MySQL和4个Agent服务。

## 目录

1. [前置要求](#前置要求)
2. [快速开始](#快速开始)
3. [详细配置](#详细配置)
4. [常见问题](#常见问题)
5. [服务管理命令](#服务管理命令)

---

## 前置要求

### 1. 安装Docker

**Windows:**
- 安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)
- 确保WSL2已启用
- 分配至少 **4GB内存** 给Docker (设置 → Resources → Memory)

**Mac:**
- 安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)
- 分配至少 **4GB内存**

**Linux:**
```bash
# Ubuntu/Debian
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER

# 安装docker-compose
sudo apt install docker-compose-plugin
```

### 2. 获取API Key

**阿里灵积平台 (DashScope) - 必需**
1. 访问 https://dashscope.aliyun.com/
2. 注册/登录阿里云账号
3. 创建API Key
4. 记录Key值，后续配置使用

**百度地图MCP (可选)**
- 用于真实地图数据，不配置则使用模拟数据

---

## 快速开始

### 步骤1: 配置环境变量

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑.env文件，填入你的API Key
# Windows用记事本/notepad++，Mac/Linux用vim/nano
```

编辑 `.env` 文件：
```env
# 修改这一行，填入你的真实API Key
DASHSCOPE_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# 如果需要真实地图数据，配置百度MCP
BAIDU_MCP_URL=https://agent-http.baidu.com/mcp/nmap-mcp/nmap-mcp-server
```

### 步骤2: 启动所有服务

```bash
# 在项目根目录执行
docker-compose up -d

# 首次启动需要下载镜像和构建，可能需要10-20分钟
```

### 步骤3: 检查服务状态

```bash
# 查看所有容器状态
docker-compose ps

# 查看日志
docker-compose logs -f

# 只看某个服务的日志
docker-compose logs -f manager-agent
```

### 步骤4: 验证部署

| 服务 | 地址 | 说明 |
|------|------|------|
| Nacos控制台 | http://localhost:8848/nacos | 账号/密码: nacos/nacos |
| ManagerAgent API | http://localhost:8081 | 主管Agent入口 |
| RouteMakingAgent | http://localhost:8082 | 路线规划Agent |
| BudgetAgent | http://localhost:8083 | 预算Agent |
| TripPlannerAgent | http://localhost:8085 | 行程规划Agent |

**健康检查:**
```bash
# ManagerAgent健康检查
curl http://localhost:8081/actuator/health

# 预期返回: {"status":"UP"}
```

---

## 详细配置

### 目录结构

```
aitripplan/
├── docker-compose.yml          # 服务编排配置
├── .env                        # 环境变量 (你创建的)
├── .env.example                # 环境变量模板
├── .dockerignore               # Docker构建忽略文件
├── scripts/
│   └── init-db.sql             # 数据库初始化脚本
├── skills/                     # Agent技能文件
│   ├── RouteMaking.md
│   ├── TripPlanner.md
│   └── Budget.md
└── code/
    └── AiTripPlan/
        └── AiTripPlan-AgentScope/
            ├── manager_agent/
            │   └── Dockerfile
            ├── routeMaking_agent/
            │   └── Dockerfile
            ├── tripPlanner_agent/
            │   └── Dockerfile
            ├── budget_agent/
            │   └── Dockerfile
            └── commons/        # 公共模块
```

### 服务依赖图

```
                    ┌─────────────┐
                    │   Nacos     │
                    │   :8848     │
                    └──────┬──────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
┌───────────────┐ ┌───────────────┐ ┌───────────────┐
│ route-making  │ │ trip-planner  │ │ budget-agent  │
│   -agent      │ │   -agent      │ │               │
│   :8082       │ │   :8085       │ │   :8083       │
└───────┬───────┘ └───────┬───────┘ └───────┬───────┘
        │                  │                  │
        └──────────────────┼──────────────────┘
                           │ A2A调用
                           ▼
                   ┌───────────────┐
                   │ manager-agent │
                   │    :8081      │
                   └───────┬───────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
        ┌─────────┐  ┌─────────┐  ┌─────────┐
        │  MySQL  │  │  Redis  │  │  H2(备) │
        │  :3306  │  │  :6379  │  │         │
        └─────────┘  └─────────┘  └─────────┘
```

### 资源配置

默认配置适合**开发/演示环境**（4GB内存）：

| 服务 | 内存限制 | CPU |
|------|----------|-----|
| Nacos | 512MB | 0.5 |
| MySQL | 512MB | 0.5 |
| Redis | 256MB | 0.25 |
| ManagerAgent | 1GB | 1.0 |
| 其他Agent | 512MB each | 0.5 each |

**生产环境建议：**
- 总内存: 8GB+
- 每个Java Agent: `-Xmx2g`
- MySQL: 1GB+
- Redis: 持久化开启

### 网络配置

所有服务在同一个Docker网络 `atplan-network` 中：
- 子网: `172.20.0.0/16`
- 服务间通过服务名通信 (如 `nacos:8848`)

---

## 常见问题

### Q1: 启动时报错 "port is already allocated"

**原因:** 端口被其他程序占用

**解决:**
```bash
# 查看端口占用 (Windows)
netstat -ano | findstr :8081

# 查看端口占用 (Mac/Linux)
lsof -i :8081

# 修改docker-compose.yml中的端口映射
# 例如将 "8081:8081" 改为 "18081:8081"
```

### Q2: Nacos启动失败，日志显示数据库连接错误

**原因:** MySQL启动较慢，Nacos尝试连接时MySQL还没准备好

**解决:**
```bash
# 单独重启Nacos
docker-compose restart nacos

# 或者等待30秒后手动启动
docker-compose stop nacos
docker-compose up -d nacos
```

### Q3: Agent启动后无法注册到Nacos

**排查步骤:**
```bash
# 1. 检查Nacos是否正常运行
curl http://localhost:8848/nacos/actuator/health

# 2. 检查Agent日志
docker-compose logs manager-agent | grep -i nacos

# 3. 进入Agent容器检查网络
docker exec -it atplan-manager-agent sh
ping nacos
```

### Q4: 构建时Maven下载慢

**解决:** 配置Maven阿里云镜像

修改各Agent的Dockerfile，在`FROM maven...`后添加：
```dockerfile
COPY settings.xml /root/.m2/settings.xml
```

创建 `settings.xml`：
```xml
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <name>Aliyun Maven</name>
      <url>https://maven.aliyun.com/repository/public</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
</settings>
```

### Q5: 如何修改代码后重新部署

```bash
# 重新构建特定服务
docker-compose build --no-cache manager-agent
docker-compose up -d manager-agent

# 重新构建所有服务
docker-compose down
docker-compose build --no-cache
docker-compose up -d
```

### Q6: 数据持久化位置

数据存储在Docker Volume中：
```bash
# 查看数据卷
docker volume ls | grep atplan

# 备份数据
docker run --rm -v atplan_mysql-data:/data -v $(pwd):/backup alpine tar czf /backup/mysql-backup.tar.gz -C /data .
```

---

## 服务管理命令

```bash
# 启动所有服务 (后台运行)
docker-compose up -d

# 查看实时日志
docker-compose logs -f

# 查看最后100行日志
docker-compose logs --tail=100

# 停止所有服务
docker-compose stop

# 停止并删除容器 (数据保留)
docker-compose down

# 停止并删除容器和数据卷 (⚠️ 数据清空)
docker-compose down -v

# 重启单个服务
docker-compose restart manager-agent

# 进入容器内部
docker exec -it atplan-manager-agent sh

# 查看资源使用
docker stats

# 清理无用镜像和卷
docker system prune -a --volumes
```

---

## Nacos使用简介

### 访问控制台

打开 http://localhost:8848/nacos
- 账号: `nacos`
- 密码: `nacos`

### 查看已注册服务

1. 登录后点击左侧菜单 **"服务管理" → "服务列表"**
2. 应能看到以下服务:
   - manager-agent
   - RouteMakingAgent
   - TripPlannerAgent
   - BudgetAgent

### 服务配置管理

如需修改某个Agent的配置：
1. **"配置管理" → "配置列表"**
2. 点击 `+` 新建配置
3. Data ID: `manager-agent.yaml`
4. Group: `DEFAULT_GROUP`
5. 配置格式: YAML
6. 填入配置内容，发布

---

## 下一步

部署完成后，你可以：

1. **开发前端界面** - 对接ManagerAgent的API
2. **测试Agent协作** - 发送旅行规划请求
3. **监控系统运行** - 查看Nacos和各服务健康状态
4. **扩展功能** - 添加WeatherAgent、HotelAgent等

---

## 获取帮助

遇到问题？
1. 查看详细日志: `docker-compose logs -f [服务名]`
2. 检查架构文档: `doc/ARCHITECTURE_DESIGN_V2.md`
3. 查看项目README: `README.md`
