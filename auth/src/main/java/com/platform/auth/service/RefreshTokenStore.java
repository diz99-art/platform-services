package com.platform.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.platform.auth.config.AuthProperties;
import com.platform.auth.exception.AuthException;
import com.platform.auth.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 리프레시 토큰: 무작위 문자열(JWT 아님). Redis에 "누구의 토큰인지"를 저장하고 지우면 바로 무효가 된다.
 * - 원문이 아니라 SHA-256 해시를 키로 저장한다: Redis 내용이 새어도 그 값으로는 토큰을 쓸 수 없다
 * - 재발급할 때마다 교체(rotation): 쓴 토큰은 지우고 새 토큰을 준다
 * - 이미 교체된 토큰이 다시 오면 탈취로 보고(재사용 감지) 그 사용자의 리프레시 토큰을 전부 지운다
 *
 * 키 (모두 auth: 접두어, TTL = 리프레시 토큰 수명)
 *   auth:refresh:{해시}       → 사용자 id      (살아 있는 토큰)
 *   auth:refresh:used:{해시}  → 사용자 id      (교체되어 쓰면 안 되는 토큰)
 *   auth:refresh:user:{id}    → {해시...}      (그 사용자의 살아 있는 토큰 목록, 전부 지울 때)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

	private static final String TOKEN = "auth:refresh:";
	private static final String USED = "auth:refresh:used:";
	private static final String USER = "auth:refresh:user:";
	private static final SecureRandom RANDOM = new SecureRandom();

	private final StringRedisTemplate redis;
	private final AuthProperties properties;

	public String issue(Long userId) {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		String hash = hash(token);
		redis.opsForValue().set(TOKEN + hash, String.valueOf(userId), properties.refreshTokenTtl());
		redis.opsForSet().add(USER + userId, hash);
		redis.expire(USER + userId, properties.refreshTokenTtl());
		return token;
	}

	/** 토큰을 쓰고(지우고) 주인 id를 돌려준다. GETDEL 한 번이라 같은 토큰으로 동시에 두 번 재발급되지 않는다 */
	public Long consume(String token) {
		String hash = hash(token);
		String userId = redis.opsForValue().getAndDelete(TOKEN + hash);
		if (userId == null) {
			String reusedBy = redis.opsForValue().get(USED + hash);
			if (reusedBy != null) {
				log.warn("교체된 리프레시 토큰이 다시 쓰였습니다(탈취 의심). 사용자 {}의 리프레시 토큰을 모두 지웁니다.", reusedBy);
				revokeAll(Long.valueOf(reusedBy));
			}
			throw new AuthException(ErrorCode.INVALID_REFRESH_TOKEN);
		}
		redis.opsForValue().set(USED + hash, userId, properties.refreshTokenTtl());
		redis.opsForSet().remove(USER + userId, hash);
		return Long.valueOf(userId);
	}

	/** 로그아웃: 그 사용자의 토큰일 때만 지운다 */
	public void revoke(String token, Long userId) {
		String hash = hash(token);
		String owner = redis.opsForValue().get(TOKEN + hash);
		if (owner != null && owner.equals(String.valueOf(userId))) {
			redis.delete(TOKEN + hash);
			redis.opsForSet().remove(USER + userId, hash);
		}
	}

	public void revokeAll(Long userId) {
		Set<String> hashes = redis.opsForSet().members(USER + userId);
		if (hashes != null) {
			hashes.forEach(h -> redis.delete(TOKEN + h));
		}
		redis.delete(USER + userId);
	}

	static String hash(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
