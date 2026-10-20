package com.platform.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;

class JwtKeysTest {

	@TempDir
	Path dir;

	@Test
	@DisplayName("폴더의 PEM을 모두 읽고, 활성 키로 서명하며, 공개키 목록에는 개인키 값이 없다")
	void loadsKeysFromDirectory() throws Exception {
		writePem("2026-10-20", JwtKeys.generate("a"));
		writePem("2026-11-20", JwtKeys.generate("b"));
		// Secret을 폴더로 마운트하면 생기는 숨김 항목은 무시한다
		Files.createDirectory(dir.resolve("..data"));

		JwtKeys keys = JwtKeys.load(dir.toString(), "2026-11-20");

		assertThat(keys.activeKey().getKeyID()).isEqualTo("2026-11-20");
		assertThat(keys.activeKey().isPrivate()).isTrue();
		assertThat(keys.publicKeys().getKeys()).extracting(JWK::getKeyID).containsExactly("2026-10-20", "2026-11-20");
		assertThat(keys.publicKeys().getKeys()).noneMatch(JWK::isPrivate);
		assertThat(keys.publicKeys().toString()).doesNotContain("\"d\"");
	}

	@Test
	@DisplayName("키가 하나뿐이면 활성 키 id를 정하지 않아도 그 키를 쓴다")
	void singleKeyWithoutActiveKid() throws Exception {
		writePem("only", JwtKeys.generate("x"));
		assertThat(JwtKeys.load(dir.toString(), "").activeKey().getKeyID()).isEqualTo("only");
	}

	@Test
	@DisplayName("활성 키 id에 해당하는 파일이 없으면 시작하지 않는다")
	void failsWhenActiveKidMissing() throws Exception {
		writePem("2026-10-20", JwtKeys.generate("a"));
		assertThatThrownBy(() -> JwtKeys.load(dir.toString(), "nope")).hasMessageContaining("nope");
	}

	@Test
	@DisplayName("폴더를 정하지 않으면 임시 키를 만든다 (개발·테스트)")
	void ephemeralKeyWithoutDirectory() {
		assertThat(JwtKeys.load("", "").activeKey().getKeyID()).startsWith("dev-");
	}

	private void writePem(String kid, RSAKey key) throws Exception {
		String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(key.toPrivateKey().getEncoded());
		Files.writeString(dir.resolve(kid + ".pem"), "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n");
	}
}
