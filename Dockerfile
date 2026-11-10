FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY br.com.faculdadedonaduzzi/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]