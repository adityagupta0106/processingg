FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /app

COPY m2-cache /root/.m2/repository
COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY src src

RUN mvn -o clean package -DskipTests -Dmaven.repo.local=/root/.m2/repository

FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

COPY --from=builder /app/target/*.jar /app/processing.jar

EXPOSE 9091

ENTRYPOINT ["java", "-jar", "/app/processing.jar"]