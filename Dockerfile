FROM openjdk:8-alpine

COPY target/uberjar/reuse.jar /reuse/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/reuse/app.jar"]
