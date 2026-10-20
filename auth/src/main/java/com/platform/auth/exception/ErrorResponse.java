package com.platform.auth.exception;

import java.util.List;

/** 모든 오류 응답의 모양. 쿠폰 서버와 같다: {"code": "...", "message": "...", "errors": [{"field", "message"}]} */
public record ErrorResponse(String code, String message, List<FieldError> errors) {

	public record FieldError(String field, String message) {
	}

	public static ErrorResponse of(ErrorCode code) {
		return new ErrorResponse(code.name(), code.getMessage(), List.of());
	}
}
