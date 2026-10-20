package com.platform.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import com.platform.auth.security.JsonErrorHandlers;

@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				// 세션·쿠키를 쓰지 않는 API라 CSRF(쿠키를 노린 위조 요청) 방어가 필요 없다. 매 요청이 Authorization 헤더의 토큰으로 인증된다
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, "/signup", "/login", "/refresh").permitAll()
						.requestMatchers(HttpMethod.GET, "/.well-known/jwks.json").permitAll()
						.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/error").permitAll()
						// 관리용 엔드포인트(운영은 8081 포트로 분리, Service·Ingress로는 안 열림)
						.requestMatchers("/actuator/**").permitAll()
						.anyRequest().authenticated())
				// 인증 서버도 자기가 발급한 토큰을 다른 서비스와 같은 방식(공개키 검증)으로 확인한다 (/me, /logout)
				.oauth2ResourceServer(oauth -> oauth
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
						.authenticationEntryPoint(JsonErrorHandlers.unauthorized())
						.accessDeniedHandler(JsonErrorHandlers.forbidden()))
				.exceptionHandling(e -> e
						.authenticationEntryPoint(JsonErrorHandlers.unauthorized())
						.accessDeniedHandler(JsonErrorHandlers.forbidden()));
		return http.build();
	}

	/** 토큰의 roles 클레임(["USER"])을 권한 ROLE_USER로 바꾼다 → hasRole("ADMIN") 같은 검사에 쓴다 */
	public static JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName("roles");
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	/** BCrypt: 일부러 느린 해시(강도 10 = 2^10번 반복)와 사용자마다 다른 소금값. 해시가 새어도 비밀번호를 거꾸로 알아내기 어렵다 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
