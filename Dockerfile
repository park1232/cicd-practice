# 실습: 2022년 베이스 이미지 (취약점 포함) -> Trivy가 막는지 확인
FROM eclipse-temurin:17.0.1_12-jre-focal
COPY target/cicd-practice-0.0.1-SNAPSHOT.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]