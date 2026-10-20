package com.platform.auth.controller.dto;

import java.time.LocalDateTime;

import com.platform.auth.domain.Role;
import com.platform.auth.domain.User;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 인증 API의 요청·응답 모양 */
public final class AuthDtos {

	private AuthDtos() {
	}

	public record SignupRequest(
			@Schema(example = "user1@example.com") @NotBlank @Email @Size(max = 100) String email,
			@Schema(example = "Passw0rd!") @NotBlank @StrongPassword String password,
			@Schema(example = "홍길동") @NotBlank @Size(max = 30) String name) {
	}

	public record LoginRequest(
			@Schema(example = "user1@example.com") @NotBlank @Email String email,
			@Schema(example = "Passw0rd!") @NotBlank String password) {
	}

	public record RefreshRequest(@NotBlank String refreshToken) {
	}

	public record LogoutRequest(@NotBlank String refreshToken) {
	}

	public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {

		public static TokenResponse of(String accessToken, String refreshToken, long expiresInSeconds) {
			return new TokenResponse(accessToken, refreshToken, "Bearer", expiresInSeconds);
		}
	}

	public record UserResponse(Long id, String email, String name, Role role, LocalDateTime createdAt) {

		public static UserResponse of(User user) {
			return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole(), user.getCreatedAt());
		}
	}
}
