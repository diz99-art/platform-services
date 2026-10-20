package com.platform.auth.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.platform.auth.security.JwtKeys;

@Configuration
public class JwtConfig {

	@Bean
	public JwtKeys jwtKeys(AuthProperties properties) {
		return JwtKeys.load(properties.keys().dir(), properties.keys().activeKid());
	}

	/** 서명: 활성 키(개인키)만 쓴다 */
	@Bean
	public JwtEncoder jwtEncoder(JwtKeys keys) {
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(keys.activeKey())));
	}

	/**
	 * 검증 (인증 서버 자신의 /me, /logout): 다른 서비스와 똑같이 공개키 목록으로 검증한다.
	 * RS256만 받는다 → 헤더의 alg를 none이나 HS256으로 바꾼 위조 토큰을 거부한다
	 */
	@Bean
	public JwtDecoder jwtDecoder(JwtKeys keys, AuthProperties properties) {
		DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
		processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, new ImmutableJWKSet<>(keys.publicKeys())));
		// 만료·발급자·대상 확인은 아래 Spring 검증기가 한다 (nimbus 기본 검증은 끈다)
		processor.setJWTClaimsSetVerifier((claims, context) -> {
		});
		NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(properties.issuer()),
				new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(properties.audience()))));
		return decoder;
	}
}
