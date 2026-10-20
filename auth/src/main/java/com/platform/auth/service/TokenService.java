package com.platform.auth.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.platform.auth.config.AuthProperties;
import com.platform.auth.domain.User;
import com.platform.auth.security.JwtKeys;

import lombok.RequiredArgsConstructor;

/**
 * 액세스 토큰(JWT) 발급. 내용(클레임):
 * - iss 발급자, aud 대상, sub 사용자 id, iat 발급 시각, exp 만료 시각, jti 토큰 고유 id
 * - email, roles ["USER"] 또는 ["ADMIN"]
 * 헤더의 kid(키 id)로 받는 쪽이 JWKS에서 어느 공개키로 검증할지 고른다
 */
@Service
@RequiredArgsConstructor
public class TokenService {

	private final JwtEncoder jwtEncoder;
	private final JwtKeys jwtKeys;
	private final AuthProperties properties;

	public String issueAccessToken(User user) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(properties.issuer())
				.audience(List.of(properties.audience()))
				.subject(String.valueOf(user.getId()))
				.issuedAt(now)
				.expiresAt(now.plus(properties.accessTokenTtl()))
				.id(UUID.randomUUID().toString())
				.claim("email", user.getEmail())
				.claim("roles", List.of(user.getRole().name()))
				.build();
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(jwtKeys.activeKey().getKeyID()).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	public long accessTokenTtlSeconds() {
		return properties.accessTokenTtl().toSeconds();
	}
}
