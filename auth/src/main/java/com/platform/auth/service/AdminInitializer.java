package com.platform.auth.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.platform.auth.config.AuthProperties;
import com.platform.auth.domain.Role;
import com.platform.auth.domain.User;
import com.platform.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 관리자 계정 만들기. 회원가입 API로는 USER만 만들 수 있으므로, 첫 ADMIN은 설정(운영은 vault → Secret)으로 만든다.
 * 이미 있으면 건드리지 않는다 (비밀번호도 바꾸지 않는다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

	private final AuthProperties properties;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	public void run(ApplicationArguments args) {
		String email = properties.admin().email();
		String password = properties.admin().password();
		if (email == null || email.isBlank() || password == null || password.isBlank()) {
			return;
		}
		String normalized = email.trim().toLowerCase();
		if (userRepository.existsByEmail(normalized)) {
			return;
		}
		try {
			userRepository.saveAndFlush(User.create(normalized, passwordEncoder.encode(password), "관리자", Role.ADMIN));
			log.info("관리자 계정을 만들었습니다: {}", normalized);
		} catch (DataIntegrityViolationException e) {
			// 서버 두 대가 동시에 시작하면 한쪽만 성공한다
			log.info("관리자 계정은 다른 서버가 먼저 만들었습니다: {}", normalized);
		}
	}
}
