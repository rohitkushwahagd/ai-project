package dev.rohit.rag;

import java.time.Instant;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import static org.assertj.core.api.Assertions.*;

class JwtAudienceTest {
    @Test void correctlySignedTokenStillNeedsTheExpectedAudience() {
        JwtDecoder decoder=new SecurityConfig().jwtDecoder("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        assertThat(decoder.decode(token("project1-api")).getSubject()).isEqualTo("test");
        assertThatThrownBy(()->decoder.decode(token("unrelated-service"))).isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(()->decoder.decode(token(null))).isInstanceOf(JwtValidationException.class);
    }
    private String token(String audience) {
        var encoder=new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(new byte[32],"HmacSHA256")));
        var claims=JwtClaimsSet.builder().issuer("project1-local").subject("test").issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if(audience!=null) claims.audience(List.of(audience));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims.build())).getTokenValue();
    }
}
