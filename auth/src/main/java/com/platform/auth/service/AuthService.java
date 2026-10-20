package com.platform.auth.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.platform.auth.controller.dto.AuthDtos.LoginRequest;
import com.platform.auth.controller.dto.AuthDtos.SignupRequest;
import com.platform.auth.controller.dto.AuthDtos.TokenResponse;
import com.platform.auth.domain.Role;
import com.platform.auth.domain.User;
import com.platform.auth.exception.AuthException;
import com.platform.auth.exception.ErrorCode;
import com.platform.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

	/**
	 * 없는 이메일일 때도 BCrypt 비교를 한 번 해서 응답 시간을 맞춘다 (시간 차이로 가입 여부를 알아내지 못하게).
	 * 진짜 BCrypt 해시여야 한다: 형식이 틀리면 비교를 건너뛰고 바로 false라 시간 차이가 그대로 생긴다
	 */
	private static final String DUMMY_HASH = new BCryptPasswordEncoder().encode("timing-equalizer");

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final TokenService tokenService;
	private final RefreshTokenStore refreshTokenStore;
	private final LoginAttemptService loginAttempts;
	private final AuthMetrics metrics;

	@Transactional
	public User signup(SignupRequest request) {
		String email = normalize(request.email());
		if (userRepository.existsByEmail(email)) {
			throw new AuthException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}
		try {
			User user = userRepository.saveAndFlush(User.create(email, passwordEncoder.encode(request.password()), request.name(), Role.USER));
			metrics.signup();
			return user;
		} catch (DataIntegrityViolationException e) {
			// 같은 이메일로 거의 동시에 가입하면 위의 확인을 둘 다 통과한다 → DB의 UNIQUE 제약이 막는다
			throw new AuthException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}
	}

	public TokenResponse login(LoginRequest request) {
		String email = normalize(request.email());
		long locked = loginAttempts.lockedSeconds(email);
		if (locked > 0) {
			metrics.login("locked");
			throw new AuthException(ErrorCode.LOGIN_LOCKED, locked);
		}
		User user = userRepository.findByEmail(email).orElse(null);
		boolean matches = passwordEncoder.matches(request.password(), user != null ? user.getPasswordHash() : DUMMY_HASH);
		if (user == null || !matches) {
			long lockedNow = loginAttempts.recordFailure(email);
			metrics.login("failure");
			if (lockedNow > 0) {
				throw new AuthException(ErrorCode.LOGIN_LOCKED, lockedNow);
			}
			throw new AuthException(ErrorCode.INVALID_CREDENTIALS);
		}
		loginAttempts.reset(email);
		metrics.login("success");
		return issueTokens(user);
	}

	/** 리프레시 토큰을 쓰고 새 액세스·리프레시 토큰을 준다. 옛 리프레시 토큰은 더 쓸 수 없다 */
	public TokenResponse refresh(String refreshToken) {
		Long userId;
		try {
			userId = refreshTokenStore.consume(refreshToken);
		} catch (AuthException e) {
			metrics.refresh("invalid");
			throw e;
		}
		User user = userRepository.findById(userId).orElseThrow(() -> new AuthException(ErrorCode.INVALID_REFRESH_TOKEN));
		metrics.refresh("success");
		return issueTokens(user);
	}

	/** 리프레시 토큰을 지운다. 이미 나간 액세스 토큰은 만료(15분)까지 유효하다 → 수명을 짧게 둔 이유 */
	public void logout(Long userId, String refreshToken) {
		refreshTokenStore.revoke(refreshToken, userId);
	}

	@Transactional(readOnly = true)
	public User me(Long userId) {
		return userRepository.findById(userId).orElseThrow(() -> new AuthException(ErrorCode.USER_NOT_FOUND));
	}

	private TokenResponse issueTokens(User user) {
		return TokenResponse.of(tokenService.issueAccessToken(user), refreshTokenStore.issue(user.getId()), tokenService.accessTokenTtlSeconds());
	}

	private static String normalize(String email) {
		return email.trim().toLowerCase();
	}
}
