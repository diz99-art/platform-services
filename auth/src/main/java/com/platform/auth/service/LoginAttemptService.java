package com.platform.auth.service;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.platform.auth.config.AuthProperties;

import lombok.RequiredArgsConstructor;

/**
 * 로그인 실패 잠금. 이메일마다 실패 횟수를 Redis에 센다 (서버 여러 대가 같이 본다).
 * failure-window(15분) 안에 max-failures(5번) 틀리면 duration(5분) 동안 잠근다.
 * 없는 이메일도 똑같이 센다: 다르게 처리하면 "잠기는 이메일 = 가입된 이메일"로 목록을 알아낼 수 있다.
 */
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
			return duration.toSeconds();
		}
		return 0;
	}

	public void reset(String email) {
		redis.delete(FAIL + email);
	}
}
