package com.platform.auth.exception;

import lombok.Getter;

@Getter
public class AuthException extends RuntimeException {

	private final ErrorCode errorCode;
	/** LOGIN_LOCKED일 때 몇 초 뒤에 다시 시도할 수 있는지 (응답 헤더 Retry-After) */
	private final Long retryAfterSeconds;

	public AuthException(ErrorCode errorCode) {
		this(errorCode, null);
	}

	public AuthException(ErrorCode errorCode, Long retryAfterSeconds) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
		this.retryAfterSeconds = retryAfterSeconds;
	}
}
