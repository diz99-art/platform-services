package com.platform.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.jayway.jsonpath.JsonPath;
import com.platform.auth.repository.UserRepository;

/**
 * 진짜 MySQL·Redis(컨테이너)로 HTTP API 전체를 시험한다. Docker가 없으면 건너뛴다 (CI에서는 돈다).
 * MockMvc는 context-path(/auth)를 빼고 부른다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {"auth.admin.email=admin@example.com", "auth.admin.password=Adm1n!pass"})
class AuthApiTest {

	@Container
	@ServiceConnection
	static MySQLContainer mysql = new MySQLContainer("mysql:8.4.11");

	@Container
	@ServiceConnection(name = "redis")
	static GenericContainer<?> redis = new GenericContainer<>("redis:7.4.9-alpine").withExposedPorts(6379);

	@Autowired
	MockMvc mvc;
	@Autowired
	UserRepository userRepository;
	@Autowired
	StringRedisTemplate redisTemplate;

	@BeforeEach
	void clean() {
		userRepository.findAll().stream().filter(u -> !u.getEmail().equals("admin@example.com")).forEach(userRepository::delete);
		redisTemplate.keys("auth:*").forEach(redisTemplate::delete);
	}

	@Test
	@DisplayName("회원가입 → 로그인 → 내 정보 → 재발급(교체) → 로그아웃")
	void fullFlow() throws Exception {
		postJson("/signup", Map.of("email", "User1@Example.com", "password", "Passw0rd!", "name", "유저1"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("user1@example.com"))   // 소문자로 저장
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$", not(hasKey("passwordHash"))));

		String login = postJson("/login", Map.of("email", "user1@example.com", "password", "Passw0rd!"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(900))
				.andReturn().getResponse().getContentAsString();
		String access = JsonPath.read(login, "$.accessToken");
		String refresh = JsonPath.read(login, "$.refreshToken");

		mvc.perform(get("/me").header("Authorization", "Bearer " + access))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("user1@example.com"));

		String refreshed = postJson("/refresh", Map.of("refreshToken", refresh))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String newRefresh = JsonPath.read(refreshed, "$.refreshToken");
		assertThat(newRefresh).isNotEqualTo(refresh);

		// 로그아웃하면 그 리프레시 토큰으로 재발급할 수 없다
		mvc.perform(post("/logout").header("Authorization", "Bearer " + access)
						.contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + newRefresh + "\"}"))
				.andExpect(status().isNoContent());
		postJson("/refresh", Map.of("refreshToken", newRefresh)).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
	}

	@Test
	@DisplayName("교체된 리프레시 토큰을 다시 쓰면 거부하고, 그 사용자의 다른 리프레시 토큰도 모두 지운다 (탈취 의심)")
	void refreshTokenReuseRevokesAll() throws Exception {
		signup("user2@example.com");
		String first = JsonPath.read(login("user2@example.com", "Passw0rd!"), "$.refreshToken");
		String second = JsonPath.read(postJson("/refresh", Map.of("refreshToken", first)).andReturn().getResponse().getContentAsString(), "$.refreshToken");

		postJson("/refresh", Map.of("refreshToken", first)).andExpect(status().isUnauthorized());   // 재사용
		postJson("/refresh", Map.of("refreshToken", second)).andExpect(status().isUnauthorized());  // 함께 지워짐
	}

	@Test
	@DisplayName("이메일 중복은 409, 비밀번호 규칙 위반은 400 (필드 오류 포함)")
	void signupValidation() throws Exception {
		signup("dup@example.com");
		postJson("/signup", Map.of("email", "DUP@example.com", "password", "Passw0rd!", "name", "중복"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
		postJson("/signup", Map.of("email", "not-an-email", "password", "weak", "name", "약함"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.errors", hasSize(2)));
	}

	@Test
	@DisplayName("비밀번호를 5번 틀리면 잠기고, 맞는 비밀번호도 잠금 동안은 거부한다 (429 + Retry-After)")
	void lockAfterFiveFailures() throws Exception {
		signup("lock@example.com");
		for (int i = 0; i < 4; i++) {
			postJson("/login", Map.of("email", "lock@example.com", "password", "Wrong0000!"))
					.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		}
		postJson("/login", Map.of("email", "lock@example.com", "password", "Wrong0000!"))
				.andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
		postJson("/login", Map.of("email", "lock@example.com", "password", "Passw0rd!"))
				.andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
	}

	@Test
	@DisplayName("없는 이메일과 틀린 비밀번호는 같은 응답이다 (가입 여부를 알 수 없게)")
	void unknownEmailLooksSameAsWrongPassword() throws Exception {
		postJson("/login", Map.of("email", "nobody@example.com", "password", "Passw0rd!"))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	@DisplayName("설정의 관리자 계정이 만들어지고, 토큰의 역할은 ADMIN")
	void adminBootstrap() throws Exception {
		String access = JsonPath.read(login("admin@example.com", "Adm1n!pass"), "$.accessToken");
		mvc.perform(get("/me").header("Authorization", "Bearer " + access))
				.andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
	}

	@Test
	@DisplayName("토큰 없이, 또는 망가진 토큰으로 /me를 부르면 401 공통 에러 응답")
	void meRequiresValidToken() throws Exception {
		mvc.perform(get("/me")).andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer"))
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mvc.perform(get("/me").header("Authorization", "Bearer abc.def.ghi")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("JWKS는 로그인 없이 공개키만 준다 (개인키 값 d 없음)")
	void jwksIsPublic() throws Exception {
		mvc.perform(get("/.well-known/jwks.json"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "max-age=300, public"))
				.andExpect(jsonPath("$.keys[0].kty").value("RSA"))
				.andExpect(jsonPath("$.keys[0].alg").value("RS256"))
				.andExpect(jsonPath("$.keys[0].kid").exists())
				.andExpect(jsonPath("$.keys[0].d").doesNotExist());
	}

	private void signup(String email) throws Exception {
		postJson("/signup", Map.of("email", email, "password", "Passw0rd!", "name", "유저")).andExpect(status().isCreated());
	}

	private String login(String email, String password) throws Exception {
		return postJson("/login", Map.of("email", email, "password", password))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private ResultActions postJson(String path, Map<String, String> body) throws Exception {
		StringBuilder json = new StringBuilder("{");
		body.forEach((k, v) -> json.append(json.length() > 1 ? "," : "").append('"').append(k).append("\":\"").append(v).append('"'));
		return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.append('}').toString()));
	}
}
