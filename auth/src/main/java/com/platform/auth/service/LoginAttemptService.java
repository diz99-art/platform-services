package com.platform.auth.service;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.platform.auth.config.AuthProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 로그인 실패 잠금. 이메일마다 실패 횟수를 Redis에 센다 (서버 여러 대가 같이 본다).
 * failure-window(15분) 안에 max-failures(5번) 틀리면 duration(5분) 동안 잠근다.
 * 없는 이메일도 똑같이 센다: 다르게 처리하면 "잠기는 이메일 = 가입된 이메일"로 목록을 알아낼 수 있다.
 * 실패·잠금은 로그로 남긴다 (보안 감사용. 줄마다 요청 ID가 붙어 Traefik 접근 로그의 IP와 이어 볼 수 있다). 이메일은 가린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptService {

	private static final String FAIL = "auth:login:fail:";
	private static final String LOCK = "auth:login:lock:";

	private final StringRedisTemplate redis;
	private final AuthProperties properties;

	/** 잠겨 있으면 남은 초, 아니면 0 */
	public long lockedSeconds(String email) {
		Long ttl = redis.getExpire(LOCK + email);
		return ttl != null && ttl > 0 ? ttl : 0;
	}

	/** 실패를 기록하고, 이번에 잠겼으면 잠금 시간(초)을 돌려준다 */
	public long recordFailure(String email) {
		Long count = redis.opsForValue().increment(FAIL + email);
		if (count != null && count == 1) {
			redis.expire(FAIL + email, properties.lock().failureWindow());
		}
		if (count != null && count >= properties.lock().maxFailures()) {
			Duration duration = properties.lock().duration();
			redis.opsForValue().set(LOCK + email, "1", duration);
			redis.delete(FAIL + email);
			log.warn("로그인 잠금 {} ({}번 실패, {}초)", mask(email), count, duration.toSeconds());
			return duration.toSeconds();
		}
		log.info("로그인 실패 {} ({}번째)", mask(email), count);
		return 0;
	}

	public void reset(String email) {
		redis.delete(FAIL + email);
	}

	/** a***@example.com 처럼 가린다 (로그에 전체 이메일을 남기지 않는다) */
	static String mask(String email) {
		int at = email.indexOf('@');
		return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
	}
}
