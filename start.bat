@echo off
chcp 65001 >nul
echo ============================================
echo   ATPlan AI旅行规划系统 - Docker部署脚本
echo ============================================
echo.

REM 检查Docker是否安装
docker --version >nul 2>&1
if errorlevel 1 (
    echo [错误] Docker未安装，请先安装Docker Desktop
    echo 下载地址: https://www.docker.com/products/docker-desktop/
    pause
    exit /b 1
)

REM 检查docker-compose
docker-compose --version >nul 2>&1
if errorlevel 1 (
    echo [错误] docker-compose未安装
    pause
    exit /b 1
)

echo [1/4] 检查环境变量文件...
if not exist .env (
    echo [提示] 未找到.env文件，从模板创建...
    copy .env.example .env
    echo ⚠️  请编辑.env文件，填入你的DASHSCOPE_API_KEY
    echo.
    notepad .env
    echo.
    echo 请保存.env文件后按任意键继续...
    pause >nul
)

echo [2/4] 检查API Key配置...
findstr /C:"DASHSCOPE_API_KEY=your-api-key" .env >nul 2>&1
if not errorlevel 1 (
    echo ⚠️  警告: DASHSCOPE_API_KEY未配置！
    echo 请编辑.env文件，将DASHSCOPE_API_KEY替换为你的真实API Key
    echo 获取地址: https://dashscope.aliyun.com/
    echo.
    notepad .env
    echo.
    echo 请保存.env文件后按任意键继续...
    pause >nul
)

echo [3/4] 构建并启动服务...
echo 首次启动可能需要10-20分钟，请耐心等待...
echo.

docker-compose up -d --build

if errorlevel 1 (
    echo.
    echo [错误] 启动失败！请查看上方错误信息。
    pause
    exit /b 1
)

echo.
echo [4/4] 等待服务就绪...
timeout /t 30 /nobreak >nul

echo.
echo ============================================
echo   ✅ 服务启动完成！
echo ============================================
echo.
echo 访问地址:
echo   Nacos控制台:    http://localhost:8848/nacos  (nacos/nacos)
echo   ManagerAgent:   http://localhost:8081
echo   RouteAgent:     http://localhost:8082
echo   BudgetAgent:    http://localhost:8083
echo   TripAgent:      http://localhost:8085
echo.
echo 常用命令:
echo   查看日志: docker-compose logs -f
echo   停止服务: docker-compose down
echo   查看状态: docker-compose ps
echo.
echo 查看详细指南: DOCKER_DEPLOY_GUIDE.md
echo.
pause
