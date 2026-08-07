# 1단계: Gradle로 JAR 빌드
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
RUN chmod +x gradlew

# Gradle 홈을 빌드 간에 유지한다. 실패해도 받아둔 jar 가 남아 재시도 비용이 줄어든다.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon

COPY src src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar -x test --no-daemon

# 2단계: 실행 환경
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Java2D 텍스트 렌더링에 필요. Alpine JRE에는 폰트가 없어 drawString 이 실패한다.
RUN apk add --no-cache fontconfig ttf-dejavu

COPY --from=builder /app/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-Djava.awt.headless=true", "-jar", "app.jar"]
