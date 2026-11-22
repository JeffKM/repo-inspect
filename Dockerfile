# 빌드: JDK 이미지에서 실행 jar 만 만든다. 테스트는 CI 와 로컬 run_tests.sh 에서 돌린다
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar -x test

# 실행: JRE 만, root 가 아닌 사용자로
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /src/build/libs/repo-inspect.jar app.jar
USER app
# 압축을 푼 텍스트가 최대 200MB 까지 메모리에 올라온다. 컨테이너 메모리의 75% 를 힙으로 쓰고, 넘치면 바로 내려 재시작되게 한다
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
