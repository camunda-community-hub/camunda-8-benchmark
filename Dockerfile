FROM maven:3-openjdk-17 as builder
WORKDIR /usr/src/app
COPY pom.xml pom.xml
RUN mvn dependency:resolve-plugins dependency:resolve -DskipTests -DskipChecks
COPY src/ src/
RUN mvn package -DskipTests -DskipChecks

FROM azul/zulu-openjdk-alpine:17-jre-headless
CMD java $JAVA_OPTIONS -jar app.jar
COPY --from=builder /usr/src/app/target/*.jar /app.jar
