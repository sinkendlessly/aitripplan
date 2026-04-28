# TripPlannerAgent Dockerfile
# 行程规划Agent - 负责详细行程安排

FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

COPY pom.xml .
COPY commons/pom.xml commons/
COPY tripPlanner_agent/pom.xml tripPlanner_agent/

RUN mvn dependency:go-offline -B -pl commons,tripPlanner_agent -am

COPY commons/src commons/src
COPY tripPlanner_agent/src tripPlanner_agent/src

RUN mvn clean package -B -DskipTests -pl commons,tripPlanner_agent -am

FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache wget

WORKDIR /app

RUN addgroup -g 1001 atplan && \
    adduser -D -u 1001 -G atplan atplan

COPY --from=builder /build/tripPlanner_agent/target/*.jar app.jar

RUN mkdir -p /app/skills && chown -R atplan:atplan /app

USER atplan

EXPOSE 8085

ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+OptimizeStringConcat \
               -XX:+UseStringDeduplication \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.backgroundpreinitializer.ignore=true \
               -Dspring.jmx.enabled=false"

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=5 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8085/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
