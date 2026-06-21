FROM maven:3.9.4-eclipse-temurin-21-jdk-jammy AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

# Use a supported OpenJDK JRE base image (jammy variant)
FROM eclipse-temurin:21.0.1-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get upgrade -y && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/preh-wldt-system-1.0-SNAPSHOT.jar app.jar

CMD ["java", "-jar", "app.jar"]