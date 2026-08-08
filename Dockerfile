# Multi-stage image for FinLedger server (plan §18.3).
# Tests run in CI before this build; package skips tests here for a lean image build.
# BuildKit cache mount keeps ~/.m2 across rebuilds; go-offline layer only invalidates on POM changes.

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY finledger-security-policy/pom.xml finledger-security-policy/
COPY finledger/pom.xml finledger/
COPY finledger-cli/pom.xml finledger-cli/
COPY sdk-reference/pom.xml sdk-reference/

RUN chmod +x mvnw

# Resolve deps when POMs change only (BuildKit cache persists /root/.m2).
# Parent reactor lists all modules — each module dir must exist (POM only for siblings).
RUN --mount=type=cache,target=/root/.m2 \
	./mvnw -B -pl finledger -am dependency:go-offline -DskipTests

COPY finledger-security-policy/src finledger-security-policy/src
COPY finledger/src finledger/src

RUN --mount=type=cache,target=/root/.m2 \
	./mvnw -B -pl finledger -am package -DskipTests \
	&& java -Djarmode=tools -jar finledger/target/finledger-*.jar extract \
		--layers --destination /extracted

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN apk add --no-cache curl \
	&& addgroup -g 1000 finledger \
	&& adduser -u 1000 -G finledger -h /app -D finledger

WORKDIR /app

COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./

RUN mkdir -p /workspace/config \
	&& chown -R finledger:finledger /app /workspace

USER finledger:finledger

ENV SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/workspace/config/ \
	MANAGEMENT_SERVER_PORT=8081 \
	JAVA_OPTS=""

EXPOSE 8080 8081
VOLUME ["/workspace/config"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
	CMD curl -fsS http://127.0.0.1:8081/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/finledger-0.1.0.jar"]
