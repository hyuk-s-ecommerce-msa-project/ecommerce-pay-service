FROM eclipse-temurin:21-jdk-jammy
VOLUME /tmp
COPY build/libs/pay-service-1.0.jar PayServer.jar
ENTRYPOINT ["java", "-jar", "PayServer.jar"]