FROM maven:3-openjdk-17 as builder
WORKDIR /usr/src/app
COPY pom.xml pom.xml
RUN mvn dependency:resolve-plugins dependency:resolve package -Dspring-boot.repackage.skip=true -Dmaven.test.skip=true -DskipTests -DskipChecks
COPY src/ src/
RUN mvn package -Dspring-boot.repackage.layers.enabled=true -Dmaven.test.skip=true -DskipTests -DskipChecks
RUN java -Djarmode=layertools -jar target/*.jar extract

FROM azul/zulu-openjdk-alpine:17-jre-headless
CMD java $JAVA_OPTIONS org.springframework.boot.loader.JarLauncher
COPY --from=builder /usr/src/app/dependencies/ ./
COPY --from=builder /usr/src/app/snapshot-dependencies/ ./
COPY --from=builder /usr/src/app/spring-boot-loader/ ./
COPY --from=builder /usr/src/app/application/ ./
