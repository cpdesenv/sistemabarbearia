# --- Etapa de build ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# --- Etapa de runtime ---
FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S barbearia && adduser -S barbearia -G barbearia
WORKDIR /app
COPY --from=build /build/target/sistema-barbearia.jar app.jar
USER barbearia
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
