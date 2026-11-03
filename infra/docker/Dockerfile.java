FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml ./
COPY code ./code
ARG MODULE
RUN mvn -B -pl code/${MODULE} -am package -DskipTests
RUN cp code/${MODULE}/target/${MODULE}-1.0.0-SNAPSHOT.jar /app.jar
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir /app/data && chown 10001:10001 /app/data
COPY --from=build /app.jar /app/app.jar
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"
ENTRYPOINT ["java","-jar","/app/app.jar"]
