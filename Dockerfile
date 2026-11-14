FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY java-backend/pom.xml java-backend/pom.xml
RUN mvn -f java-backend/pom.xml dependency:go-offline -q
COPY java-backend/src java-backend/src
COPY frontend frontend
RUN mvn -f java-backend/pom.xml package -DskipTests -q

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system tecnopc && useradd --system --gid tecnopc tecnopc
COPY --from=build /app/java-backend/target/tecnopc-admin-api-1.0.0.jar /app/server.jar
USER tecnopc
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -Dfile.encoding=UTF-8"
EXPOSE 10000
ENTRYPOINT ["java","-jar","/app/server.jar"]
