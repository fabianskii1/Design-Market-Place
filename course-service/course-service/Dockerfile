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

# Alpine JRE에는 폰트가 없어 Graphics2D.drawString()이 실패한다.
# 가시적 워터마크를 그리려면 반드시 필요하다.
RUN apk add --no-cache fontconfig ttf-dejavu

COPY --from=builder /app/build/libs/*.jar app.jar

# headless 모드: 디스플레이 없는 서버에서 Java2D를 쓰려면 켜야 한다
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-jar", "app.jar"]
