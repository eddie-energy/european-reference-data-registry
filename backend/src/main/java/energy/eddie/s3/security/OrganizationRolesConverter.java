package energy.eddie.s3.security;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class OrganizationRolesConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String NDSF_NATION_AUTHORITY_PREFIX = "NDSF_NATION_";
    private static final String USERNAME_CLAIM = "preferred_username";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, authorities(jwt), name(jwt));
    }

    private static List<GrantedAuthority> authorities(Jwt jwt) {
        Set<String> authorities = new LinkedHashSet<>();
        authorities.add(CeedsRole.PARTICIPANT.authority());
        for (var membership : OrganizationClaim.read(jwt)) {
            membership.roles().forEach(role -> authorities.add(role.authority()));
            membership.ndsfNations().forEach(nation -> authorities.add(NDSF_NATION_AUTHORITY_PREFIX + nation.name()));
        }
        return authorities.stream().map(SimpleGrantedAuthority::new).map(GrantedAuthority.class::cast).toList();
    }

    private static String name(Jwt jwt) {
        var username = jwt.getClaimAsString(USERNAME_CLAIM);
        return username != null ? username : jwt.getSubject();
    }
}
