package com.platform.auth.controller;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.platform.auth.security.JwtKeys;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * JWKS (JSON Web Key Set): 토큰 검증용 공개키 목록. 다른 서비스(쿠폰 서버 등)가 이 주소에서 공개키를 받아 서명을 검증한다.
 * 공개키라 누구에게 보여도 된다. 개인키 값(d, p, q ...)은 들어 있지 않다.
 */
@Tag(name = "공개키")
@RestController
@RequiredArgsConstructor
public class JwksController {

	private final JwtKeys jwtKeys;

	@Operation(summary = "JWKS 공개키 목록", description = "토큰 헤더의 kid와 같은 키로 서명을 검증한다")
	@GetMapping("/.well-known/jwks.json")
	public ResponseEntity<Map<String, Object>> jwks() {
		// 받는 쪽이 5분 동안 캐시해도 된다고 알려준다. 키를 바꿀 때 이 시간만큼 옛 키를 남겨둔다 (docs/auth.md)
		return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
				.body(jwtKeys.publicKeys().toJSONObject());
	}
}
