package dev.rohit.rag;

import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import jakarta.servlet.DispatcherType;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.core.*;

@Configuration
public class SecurityConfig {
    @Bean JwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String secret) {
        byte[] bytes = Base64.getDecoder().decode(secret);
        if (bytes.length < 32) throw new IllegalArgumentException("JWT secret must be at least 256 bits");
        var decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(bytes, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> audience=jwt->jwt.getAudience()!=null && jwt.getAudience().contains("project1-api")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Expected project1-api audience",null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("project1-local"),audience));
        return decoder;
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                .requestMatchers("/health").permitAll()
                .requestMatchers(HttpMethod.GET,"/","/ui/**").permitAll()
                .requestMatchers("/metrics").hasAuthority("SCOPE_rag.metrics")
                .requestMatchers(HttpMethod.GET,"/api/documents").hasAuthority("SCOPE_rag.read")
                .requestMatchers("/ingest", "/api/documents").hasAuthority("SCOPE_rag.write")
                .requestMatchers(HttpMethod.PUT,"/sources/**").hasAuthority("SCOPE_rag.write")
                .requestMatchers(HttpMethod.DELETE,"/sources/**").hasAuthority("SCOPE_rag.write")
                .requestMatchers("/feedback").hasAuthority("SCOPE_rag.read")
                .requestMatchers("/query", "/sources/**", "/mcp").hasAuthority("SCOPE_rag.read")
                .anyRequest().denyAll())
            .oauth2ResourceServer(o -> o.jwt(j -> {})).build();
    }
}
