FROM node:24-alpine AS frontend
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm ci --no-fund
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
COPY --from=frontend /frontend/dist/fieldnotes/browser ./src/main/resources/static/ui
RUN --mount=type=cache,target=/root/.m2 MAVEN_OPTS="-Xmx384m -XX:ActiveProcessorCount=2" mvn -B -ntp -DskipTests package
FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd -m app
WORKDIR /app
COPY --from=build /workspace/target/java-agentic-rag-0.1.0-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
