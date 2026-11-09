# 조치: 패키지가 최소인 alpine 베이스로 교체 (Trivy HIGH/CRITICAL 0건)
FROM eclipse-temurin:17-jre-alpine
COPY target/cicd-practice-0.0.1-SNAPSHOT.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]