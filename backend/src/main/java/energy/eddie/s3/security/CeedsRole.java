package energy.eddie.s3.security;

import java.util.Arrays;
import java.util.Optional;

public enum CeedsRole {
    VIEWER,
    PARTICIPANT,
    NDSF,
    COORDINATING_NDSF,
    OPERATIONAL_ENTITY;

    public boolean isNdsf() {
        return this == NDSF || this == COORDINATING_NDSF;
    }

    public String authority() {
        return "ROLE_" + name();
    }

    public static Optional<CeedsRole> assignable(String value) {
        return Arrays.stream(values())
                .filter(role -> role.isNdsf() || role == OPERATIONAL_ENTITY)
                .filter(role -> role.name().equalsIgnoreCase(value.trim()))
                .findFirst();
    }
}
