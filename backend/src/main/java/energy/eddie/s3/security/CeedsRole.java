package energy.eddie.s3.security;

import java.util.Arrays;
import java.util.Optional;

public enum CeedsRole {
    VIEWER,
    PARTICIPANT,
    NDSF,
    OPERATIONAL_ENTITY;

    public String authority() {
        return "ROLE_" + name();
    }

    public static Optional<CeedsRole> assignable(String value) {
        return Arrays.stream(values())
                .filter(role -> role == NDSF || role == OPERATIONAL_ENTITY)
                .filter(role -> role.name().equalsIgnoreCase(value.trim()))
                .findFirst();
    }
}
