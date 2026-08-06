# 1단계: Gradle로 JAR 빌드
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# 의존성 캐시 최적화 (소스 변경 시 재다운로드 방지)
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon

# 소스 복사 및 빌드
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# 2단계: 실행 환경 (JRE만 포함 - 이미지 경량화)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Java2D 텍스트 렌더링(가시적 워터마크)에 필요한 폰트·fontconfig.
# Alpine JRE에는 폰트가 포함되어 있지 않아 설치하지 않으면 drawString이 실패한다.
RUN apk add --no-cache fontconfig ttf-dejavu

COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-jar", "app.jar"]