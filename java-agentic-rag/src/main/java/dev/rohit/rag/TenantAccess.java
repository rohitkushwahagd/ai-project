package dev.rohit.rag;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.AccessDeniedException;

public record TenantAccess(String tenant, String token, boolean escalate) {
    public static TenantAccess from(Jwt jwt) {
        String tenant = jwt.getClaimAsString("tenant_id");
        if (tenant == null || !tenant.matches("[A-Za-z0-9_-]{1,80}"))
            throw new AccessDeniedException("A valid tenant_id claim is required");
        String scope = jwt.getClaimAsString("scope");
        return new TenantAccess(tenant, jwt.getTokenValue(), scope != null &&
                java.util.Arrays.asList(scope.split(" ")).contains("rag.escalate"));
    }
}
