package com.platform.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.platform.auth.controller.dto.StrongPassword;

class StrongPasswordTest {

	private final StrongPassword.Validator validator = new StrongPassword.Validator();

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({
			"Passw0rd!, true",
			"short1!, false",        // 8자 미만
			"password1, false",      // 특수문자 없음
			"Password!, false",      // 숫자 없음
			"12345678!, false",      // 영문 없음
			"'Pass w0rd!', false",   // 공백
	})
	void rules(String password, boolean valid) {
		assertThat(validator.isValid(password, null)).isEqualTo(valid);
	}
}
