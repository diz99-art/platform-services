package com.platform.auth.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;

import lombok.extern.slf4j.Slf4j;

/**
 * 토큰 서명 키 모음.
 * - 활성 키(active): 새 토큰을 서명한다. 개인키를 쓰므로 이 서버 밖으로 나가지 않는다
 * - 전체 키의 공개키: JWKS 주소로 공개한다. 다른 서비스는 이것만으로 서명을 검증한다 (개인키 없이)
 * 키 교체 중에는 폴더에 키가 두 개 있다: 새 키로 서명하고, 옛 키의 공개키는 남겨서 이미 나간 토큰도 검증되게 한다 (docs/auth.md)
 */
@Slf4j
public final class JwtKeys {

	private static final String PEM_SUFFIX = ".pem";

	private final RSAKey activeKey;
	private final JWKSet allKeys;

	private JwtKeys(RSAKey activeKey, List<JWK> keys) {
		this.activeKey = activeKey;
		this.allKeys = new JWKSet(keys);
	}

	/** 서명에 쓰는 키 (개인키 포함) */
	public RSAKey activeKey() {
		return activeKey;
	}

	/** JWKS로 공개할 공개키 목록 (개인키 값은 빠진다) */
	public JWKSet publicKeys() {
		return allKeys.toPublicJWKSet();
	}

	/**
	 * dir이 비어 있으면 임시 키를 만든다 (개발·테스트). 다시 켜면 키가 바뀌어 전에 받은 토큰은 모두 무효가 된다.
	 * dir이 있으면 그 안의 *.pem(PKCS#8 개인키)을 모두 읽는다. 파일 이름이 키 id(kid)다.
	 */
	public static JwtKeys load(String dir, String activeKid) {
		if (dir == null || dir.isBlank()) {
			RSAKey key = generate("dev-" + UUID.randomUUID().toString().substring(0, 8));
			log.warn("서명 키 폴더가 설정되지 않아 임시 키({})를 만들었습니다. 개발·테스트용입니다.", key.getKeyID());
			return new JwtKeys(key, List.of(key));
		}
		List<JWK> keys = new ArrayList<>();
		// Secret을 폴더로 마운트하면 ..data 같은 숨김 항목이 같이 생긴다 → 이름이 ..로 시작하지 않는 일반 파일만
		try (Stream<Path> files = Files.list(Path.of(dir))) {
			for (Path file : files.filter(f -> f.getFileName().toString().endsWith(PEM_SUFFIX))
					.filter(f -> !f.getFileName().toString().startsWith(".."))
					.filter(Files::isRegularFile).sorted().toList()) {
				String kid = file.getFileName().toString().replace(PEM_SUFFIX, "");
				keys.add(fromPem(kid, Files.readString(file, StandardCharsets.US_ASCII)));
			}
		} catch (IOException e) {
			throw new IllegalStateException("서명 키 폴더를 읽을 수 없습니다: " + dir, e);
		}
		if (keys.isEmpty()) {
			throw new IllegalStateException("서명 키 폴더에 *.pem 파일이 없습니다: " + dir);
		}
		String kid = (activeKid == null || activeKid.isBlank()) && keys.size() == 1 ? keys.getFirst().getKeyID() : activeKid;
		RSAKey active = (RSAKey) keys.stream().filter(k -> k.getKeyID().equals(kid)).findFirst()
				.orElseThrow(() -> new IllegalStateException("활성 키 id(" + activeKid + ")에 해당하는 파일이 없습니다. 있는 키: "
						+ keys.stream().map(JWK::getKeyID).toList()));
		log.info("서명 키 {}개를 읽었습니다. 활성 키: {}, 전체: {}", keys.size(), active.getKeyID(), keys.stream().map(JWK::getKeyID).toList());
		return new JwtKeys(active, keys);
	}

	/** PKCS#8 PEM 개인키 → RSAKey(JWK). 공개키는 개인키 안의 modulus와 public exponent로 만든다 */
	static RSAKey fromPem(String kid, String pem) {
		try {
			String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
			KeyFactory factory = KeyFactory.getInstance("RSA");
			RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) factory.generatePrivate(
					new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
			RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
					new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
			return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(kid)
					.keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build();
		} catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
			throw new IllegalStateException("키 " + kid + "를 읽을 수 없습니다 (PKCS#8 PEM, RSA여야 합니다)", e);
		}
	}

	static RSAKey generate(String kid) {
		try {
			return new RSAKeyGenerator(2048).keyID(kid).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).generate();
		} catch (JOSEException e) {
			throw new IllegalStateException(e);
		}
	}
}
