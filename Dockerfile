FROM maven:3-openjdk-17 as builder
WORKDIR /usr/src/app
COPY src/ src/
COPY pom.xml pom.xml
RUN mvn clean package -DskipTests -DskipChecks

FROM azul/zulu-openjdk-alpine:17-jre-headless
CMD java $JAVA_OPTIONS -jar app.jar
COPY --from=builder /usr/src/app/target/*.jar /app.jar
