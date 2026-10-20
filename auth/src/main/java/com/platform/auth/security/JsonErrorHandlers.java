package com.platform.auth.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.platform.auth.exception.ErrorCode;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring Security가 컨트롤러 앞에서 거절할 때(401 토큰 없음·위조·만료, 403 권한 없음)도 공통 에러 응답 모양으로 쓴다.
 * 메시지가 고정 문자열뿐이라 JSON을 직접 만든다.
 */
public final class JsonErrorHandlers {

	private JsonErrorHandlers() {
	}

	public static AuthenticationEntryPoint unauthorized() {
		return (request, response, e) -> write(response, ErrorCode.UNAUTHORIZED);
	}

	public static AccessDeniedHandler forbidden() {
		return (request, response, e) -> write(response, ErrorCode.FORBIDDEN);
	}

	private static void write(HttpServletResponse response, ErrorCode code) throws IOException {
		byte[] body = ("{\"code\":\"" + code.name() + "\",\"message\":\"" + code.getMessage() + "\",\"errors\":[]}")
				.getBytes(StandardCharsets.UTF_8);
		response.setStatus(code.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		if (code == ErrorCode.UNAUTHORIZED) {
			// 표준(RFC 6750): 401에는 어떤 방식의 인증이 필요한지 알려준다
			response.setHeader("WWW-Authenticate", "Bearer");
		}
		response.setContentLength(body.length);
		response.getOutputStream().write(body);
	}
}
