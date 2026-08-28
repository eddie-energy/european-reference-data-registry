FROM maven:3.9.11-eclipse-temurin-21 AS provider-build

WORKDIR /build
COPY org-default-attrs/pom.xml ./
RUN mvn dependency:go-offline
COPY org-default-attrs/src ./src
RUN mvn clean package

FROM quay.io/keycloak/keycloak:26.3.5 AS keycloak-build

COPY --from=provider-build /build/target/org-default-attrs-1.0.0.jar /opt/keycloak/providers/
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.3.5

COPY --from=keycloak-build /opt/keycloak/ /opt/keycloak/
