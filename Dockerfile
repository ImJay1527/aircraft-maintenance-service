FROM eclipse-temurin:21-jre
WORKDIR /app
# Assumimos que o utilizador correu `mvn clean package` no host
COPY target/*.jar app.jar
EXPOSE 8081 8082 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
