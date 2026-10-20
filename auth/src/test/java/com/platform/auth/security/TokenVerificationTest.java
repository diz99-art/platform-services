package com.platform.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.platform.auth.config.AuthProperties;
import com.platform.auth.config.JwtConfig;
import com.platform.auth.domain.Role;
import com.platform.auth.domain.User;
import com.platform.auth.service.TokenService;

/** 서명한 토큰이 공개키로 검증되는지, 위조·만료·다른 발급자 토큰은 거부되는지 (스프링·DB 없이) */
class TokenVerificationTest {

	private final AuthProperties properties = new AuthProperties("platform-auth", "platform", Duration.ofMinutes(15),
			Duration.ofDays(14), new AuthProperties.Keys("", ""), new AuthProperties.Lock(5, Duration.ofMinutes(15), Duration.ofMinutes(5)),
			new AuthProperties.Admin("", ""));
	private final JwtKeys keys = JwtKeys.load("", "");
	private final JwtConfig config = new JwtConfig();
	private final JwtEncoder encoder = config.jwtEncoder(keys);
	private final JwtDecoder decoder = config.jwtDecoder(keys, properties);

	@Test
	@DisplayName("발급한 토큰을 공개키로 검증하면 사용자 id·역할·kid가 그대로 나온다")
	void issuedTokenVerifies() {
		User user = User.create("a@example.com", "hash", "에이", Role.ADMIN);
		ReflectionTestUtils.setField(user, "id", 42L);
		String token = new TokenService(encoder, keys, properties).issueAccessToken(user);

		Jwt jwt = decoder.decode(token);

		assertThat(jwt.getSubject()).isEqualTo("42");
		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
		assertThat(jwt.getAudience()).containsExactly("platform");
		assertThat(jwt.getHeaders()).containsEntry("kid", keys.activeKey().getKeyID()).containsEntry("alg", "RS256");
	}

	@Test
	@DisplayName("내용을 바꾼 토큰(서명 불일치)은 거부한다")
	void tamperedTokenRejected() {
		String token = sign(encoder, claims(Instant.now().plusSeconds(60), "platform-auth").claim("roles", List.of("USER")).build());
		String[] parts = token.split("\\.");
		String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
				new String(Base64.getUrlDecoder().decode(parts[1])).replace("\"USER\"", "\"ADMIN\"").getBytes());
		assertThatThrownBy(() -> decoder.decode(parts[0] + "." + forgedPayload + "." + parts[2])).isInstanceOf(JwtException.class);
	}

	@Test
	@DisplayName("다른 키로 서명한 토큰은 kid가 같아도 거부한다")
	void tokenSignedWithOtherKeyRejected() {
		RSAKey attacker = JwtKeys.generate(keys.activeKey().getKeyID());
		JwtEncoder attackerEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(attacker)));
		assertThatThrownBy(() -> decoder.decode(sign(attackerEncoder, claims(Instant.now().plusSeconds(60), "platform-auth").build())))
				.isInstanceOf(JwtException.class);
	}

	@Test
	@DisplayName("만료된 토큰은 거부한다")
	void expiredTokenRejected() {
		assertThatThrownBy(() -> decoder.decode(sign(encoder, claims(Instant.now().minusSeconds(120), "platform-auth").build())))
				.isInstanceOf(JwtException.class).hasMessageContaining("expired");
	}

	@Test
	@DisplayName("발급자(iss)가 다르면 거부한다")
	void otherIssuerRejected() {
		assertThatThrownBy(() -> decoder.decode(sign(encoder, claims(Instant.now().plusSeconds(60), "someone-else").build())))
				.isInstanceOf(JwtException.class);
	}

	private JwtClaimsSet.Builder claims(Instant expiresAt, String issuer) {
		return JwtClaimsSet.builder().issuer(issuer).audience(List.of("platform")).subject("1")
				.issuedAt(expiresAt.minusSeconds(600)).expiresAt(expiresAt);
	}

	private String sign(JwtEncoder jwtEncoder, JwtClaimsSet claims) {
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keys.activeKey().getKeyID()).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}
}
