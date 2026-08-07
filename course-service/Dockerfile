# 1단계: Gradle로 JAR 빌드
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# 의존성 캐시 최적화 (소스 변경 시 재다운로드 방지)
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
RUN chmod +x gradlew

# --mount=type=cache 로 Gradle 홈을 빌드 간에 유지한다.
# 이게 없으면 빌드가 실패할 때마다 받아둔 jar가 통째로 버려져,
# Maven Central 레이트 리밋(429)에 걸리면 재시도해도 계속 처음부터 받게 된다.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon

# 소스 복사 및 빌드
COPY src src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar -x test --no-daemon

# 2단계: 실행 환경 (JRE만 포함 - 이미지 경량화)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Alpine JRE에는 폰트가 없어 Graphics2D.drawString()이 실패한다.
# 가시적 워터마크를 그리려면 반드시 필요하다.
RUN apk add --no-cache fontconfig ttf-dejavu

COPY --from=builder /app/build/libs/*.jar app.jar

# headless 모드: 디스플레이 없는 서버에서 Java2D를 쓰려면 켜야 한다
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-jar", "app.jar"]
