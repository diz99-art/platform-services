package com.platform.auth.service;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;

/** 인증 지표: auth_login_total{result=success|failure|locked}, auth_signup_total, auth_refresh_total{result} */
@Component
@RequiredArgsConstructor
public class AuthMetrics {

	private final MeterRegistry registry;

	public void login(String result) {
		Counter.builder("auth.login").description("로그인 시도").tag("result", result).register(registry).increment();
	}

	public void signup() {
		Counter.builder("auth.signup").description("회원가입").register(registry).increment();
	}

	public void refresh(String result) {
		Counter.builder("auth.refresh").description("토큰 재발급").tag("result", result).register(registry).increment();
	}
}
