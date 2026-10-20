package com.platform.auth.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(AuthException.class)
	public ResponseEntity<ErrorResponse> handle(AuthException e) {
		ResponseEntity.BodyBuilder response = ResponseEntity.status(e.getErrorCode().getStatus());
		if (e.getRetryAfterSeconds() != null) {
			response.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()));
		}
		return response.body(ErrorResponse.of(e.getErrorCode()));
	}

	/** @Valid 검증 실패: 어느 필드가 왜 틀렸는지 모두 알려준다 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handle(MethodArgumentNotValidException e) {
		var errors = e.getBindingResult().getFieldErrors().stream()
				.map(f -> new ErrorResponse.FieldError(f.getField(), f.getDefaultMessage()))
				.toList();
		ErrorCode code = ErrorCode.INVALID_REQUEST;
		return ResponseEntity.status(code.getStatus()).body(new ErrorResponse(code.name(), code.getMessage(), errors));
	}

	/** JSON 형식이 깨졌거나 본문이 없음 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handle(HttpMessageNotReadableException e) {
		return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_REQUEST));
	}
}
