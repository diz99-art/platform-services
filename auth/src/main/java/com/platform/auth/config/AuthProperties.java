package com.platform.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml의 auth.* 설정. 값의 뜻은 application.yml 주석에 있다. */
@ConfigurationProperties("auth")
public record AuthProperties(
		String issuer,
		String audience,
		Duration accessTokenTtl,
		Duration refreshTokenTtl,
		Keys keys,
		Lock lock,
		Admin admin) {

	public record Keys(String dir, String activeKid) {
	}

	public record Lock(int maxFailures, Duration failureWindow, Duration duration) {
	}

	public record Admin(String email, String password) {
	}
}
