#!/bin/bash

# ATPlan AI旅行规划系统 - Docker部署脚本 (Linux/Mac)

set -e

echo "============================================"
echo "   ATPlan AI旅行规划系统 - Docker部署脚本"
echo "============================================"
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}[错误] Docker未安装${NC}"
    echo "请访问 https://docs.docker.com/get-docker/ 安装Docker"
    exit 1
fi

# 检查docker-compose
if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}[错误] docker-compose未安装${NC}"
    echo "请访问 https://docs.docker.com/compose/install/ 安装"
    exit 1
fi

echo -e "${GREEN}[1/4] 检查环境变量文件...${NC}"
if [ ! -f .env ]; then
    echo -e "${YELLOW}[提示] 未找到.env文件，从模板创建...${NC}"
    cp .env.example .env
    echo -e "${YELLOW}⚠️  请编辑.env文件，填入你的DASHSCOPE_API_KEY${NC}"
    
    # 尝试用默认编辑器打开
    if command -v code &> /dev/null; then
        code .env
    elif [ "$OSTYPE" == "darwin"* ]; then
        open -e .env
    else
        nano .env
    fi
    
    echo ""
    read -p "请保存.env文件后按回车键继续..."
fi

echo -e "${GREEN}[2/4] 检查API Key配置...${NC}"
if grep -q "DASHSCOPE_API_KEY=your-api-key" .env; then
    echo -e "${YELLOW}⚠️  警告: DASHSCOPE_API_KEY未配置！${NC}"
    echo "请编辑.env文件，将DASHSCOPE_API_KEY替换为你的真实API Key"
    echo "获取地址: https://dashscope.aliyun.com/"
    echo ""
    read -p "请保存.env文件后按回车键继续..."
fi

echo -e "${GREEN}[3/4] 构建并启动服务...${NC}"
echo "首次启动可能需要10-20分钟，请耐心等待..."
echo ""

docker-compose up -d --build

echo ""
echo -e "${GREEN}[4/4] 等待服务就绪...${NC}"
sleep 30

echo ""
echo "============================================"
echo -e "   ${GREEN}✅ 服务启动完成！${NC}"
echo "============================================"
echo ""
echo "访问地址:"
echo "  Nacos控制台:    http://localhost:8848/nacos  (nacos/nacos)"
echo "  ManagerAgent:   http://localhost:8081"
echo "  RouteAgent:     http://localhost:8082"
echo "  BudgetAgent:    http://localhost:8083"
echo "  TripAgent:      http://localhost:8085"
echo ""
echo "常用命令:"
echo "  查看日志: docker-compose logs -f"
echo "  停止服务: docker-compose down"
echo "  查看状态: docker-compose ps"
echo ""
echo "查看详细指南: DOCKER_DEPLOY_GUIDE.md"
echo ""
