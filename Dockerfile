FROM openjdk:8-jdk-alpine
VOLUME /tmp
EXPOSE 8080
COPY target/demo-test-0.0.6.jar app.jar
ENTRYPOINT java -Djava.security.egd=file:/dev/./urandom -jar /app.jar