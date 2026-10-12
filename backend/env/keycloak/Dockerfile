ARG KEYCLOAK_VERSION=26.7.5

FROM registry.access.redhat.com/ubi9 AS runtime-tools
RUN dnf install --installroot /mnt/rootfs jq coreutils --releasever 9 \
    --setopt install_weak_deps=false --nodocs -y \
    && dnf --installroot /mnt/rootfs clean all

FROM maven:3.9.11-eclipse-temurin-21 AS provider-build

WORKDIR /build
COPY org-default-attrs/pom.xml ./
RUN mvn dependency:go-offline
COPY org-default-attrs/src ./src
RUN mvn clean package

FROM quay.io/keycloak/keycloak:${KEYCLOAK_VERSION} AS keycloak-build

COPY --from=provider-build /build/target/org-default-attrs-1.0.0.jar /opt/keycloak/providers/
ENV KC_HEALTH_ENABLED=true
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:${KEYCLOAK_VERSION}

COPY --from=runtime-tools /mnt/rootfs/ /
COPY --from=keycloak-build /opt/keycloak/ /opt/keycloak/
COPY --chown=1000:0 --chmod=0755 entrypoint.sh org-bootstrap.sh bootstrap-config.sh healthcheck.sh /opt/keycloak/bootstrap/
COPY --chown=1000:0 --chmod=0644 ceeds-realm.json user-profile.json /opt/keycloak/bootstrap/
ENV KC_HEALTH_ENABLED=true KC_HTTP_ENABLED=true
HEALTHCHECK --interval=5s --timeout=3s --start-period=180s --retries=3 \
  CMD ["/opt/keycloak/bootstrap/healthcheck.sh"]
ENTRYPOINT ["/opt/keycloak/bootstrap/entrypoint.sh"]
CMD ["start"]
