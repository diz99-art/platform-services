# 인증 서버 이미지. 빌드 위치(context)는 저장소 최상위다: docker build -f auth/Dockerfile .
# 1단계: 빌드. 멀티 모듈이라 루트의 Gradle 설정과 auth 모듈 설정을 먼저 복사해 의존성만 내려받는다 (소스만 바뀌면 이 층은 캐시)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY auth/build.gradle auth/build.gradle
RUN ./gradlew :auth:dependencies --no-daemon -q > /dev/null
COPY auth/src auth/src
RUN ./gradlew :auth:bootJar --no-daemon -q -x test
# jar를 의존성·스프링 로더·내 코드 층으로 풀어 둔다. 내 코드만 바뀌면 마지막 층만 새로 받는다
RUN java -Djarmode=tools -jar auth/build/libs/auth.jar extract --layers --launcher --destination extracted

# 2단계: 실행. JRE만 담은 작은 이미지
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
USER app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
# 쿠버네티스에서는 ConfigMap의 JAVA_TOOL_OPTIONS로 덮어쓴다 (platform-infra의 k8s/apps/auth/config.env)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50"
EXPOSE 8080 8081
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
