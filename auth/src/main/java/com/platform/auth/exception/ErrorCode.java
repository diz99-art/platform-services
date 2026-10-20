package com.platform.auth.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
	EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입한 이메일입니다."),
	// 이메일이 없는지 비밀번호가 틀렸는지 구분하지 않는다: 구분하면 가입된 이메일 목록을 알아낼 수 있다
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
	LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "로그인 실패가 많아 잠시 잠겼습니다. 잠시 후 다시 시도하세요."),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 유효하지 않습니다. 다시 로그인하세요."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다. 유효한 액세스 토큰을 보내세요."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");

	private final HttpStatus status;
	private final String message;
}
