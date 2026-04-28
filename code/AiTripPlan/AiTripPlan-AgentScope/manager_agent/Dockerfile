# ManagerAgent Dockerfile
# 主管Agent - 负责任务编排和结果聚合

# 构建阶段
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# 先复制父pom和commons模块（依赖缓存优化）
COPY pom.xml .
COPY commons/pom.xml commons/
COPY manager_agent/pom.xml manager_agent/

# 下载依赖（利用Docker缓存层）
RUN mvn dependency:go-offline -B -pl commons,manager_agent -am

# 复制源代码
COPY commons/src commons/src
COPY manager_agent/src manager_agent/src

# 构建JAR包
RUN mvn clean package -B -DskipTests -pl commons,manager_agent -am

# 运行阶段
FROM eclipse-temurin:17-jre-alpine

# 安装必要的工具（健康检查用）
RUN apk add --no-cache wget

WORKDIR /app

# 创建非root用户运行应用
RUN addgroup -g 1001 atplan && \
    adduser -D -u 1001 -G atplan atplan

# 复制JAR文件
COPY --from=builder /build/manager_agent/target/*.jar app.jar

# 创建skills目录
RUN mkdir -p /app/skills && chown -R atplan:atplan /app

# 切换到非root用户
USER atplan

# 暴露端口
EXPOSE 8081

# JVM参数优化
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+OptimizeStringConcat \
               -XX:+UseStringDeduplication \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.backgroundpreinitializer.ignore=true \
               -Dspring.jmx.enabled=false"

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=5 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
