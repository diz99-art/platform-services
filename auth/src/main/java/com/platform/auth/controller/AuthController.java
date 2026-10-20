package com.platform.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.platform.auth.controller.dto.AuthDtos.LoginRequest;
import com.platform.auth.controller.dto.AuthDtos.LogoutRequest;
import com.platform.auth.controller.dto.AuthDtos.RefreshRequest;
import com.platform.auth.controller.dto.AuthDtos.SignupRequest;
import com.platform.auth.controller.dto.AuthDtos.TokenResponse;
import com.platform.auth.controller.dto.AuthDtos.UserResponse;
import com.platform.auth.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "인증", description = "회원가입, 로그인, 토큰 재발급, 로그아웃, 내 정보")
@RestController
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@Operation(summary = "회원가입", description = "역할은 USER. 실패: 400 INVALID_REQUEST(이메일 형식, 비밀번호 규칙), 409 EMAIL_ALREADY_EXISTS")
	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse signup(@Valid @RequestBody SignupRequest request) {
		return UserResponse.of(authService.signup(request));
	}

	@Operation(summary = "로그인", description = """
			액세스 토큰(15분)과 리프레시 토큰(14일)을 준다.
			실패: 401 INVALID_CREDENTIALS, 429 LOGIN_LOCKED(15분 안에 5번 틀리면 5분 잠금, Retry-After 헤더)""")
	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@Operation(summary = "토큰 재발급", description = "리프레시 토큰을 새 토큰 쌍으로 바꾼다. 쓴 리프레시 토큰은 더 쓸 수 없다. 실패: 401 INVALID_REFRESH_TOKEN")
	@PostMapping("/refresh")
	public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
		return authService.refresh(request.refreshToken());
	}

	@Operation(summary = "로그아웃", description = "리프레시 토큰을 지운다. 액세스 토큰은 만료까지 유효하다", security = @SecurityRequirement(name = "bearer"))
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LogoutRequest request) {
		authService.logout(Long.valueOf(jwt.getSubject()), request.refreshToken());
	}

	@Operation(summary = "내 정보", security = @SecurityRequirement(name = "bearer"))
	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
		return UserResponse.of(authService.me(Long.valueOf(jwt.getSubject())));
	}
}
