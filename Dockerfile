FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app
COPY . .
RUN cd backend && mvn -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /app/backend/target/gamecopy-backend-0.2.0.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
