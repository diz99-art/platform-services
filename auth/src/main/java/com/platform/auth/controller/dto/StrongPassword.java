package com.platform.auth.controller.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/** 비밀번호 규칙: 8~64자, 영문·숫자·특수문자를 각각 1개 이상, 공백 없음 */
@Documented
@Constraint(validatedBy = StrongPassword.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

	String message() default "비밀번호는 8~64자이고 영문·숫자·특수문자를 각각 1개 이상 포함해야 합니다 (공백 불가).";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<StrongPassword, String> {

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			if (value == null) {
				return true; // 비어 있는지는 @NotBlank가 본다
			}
			return value.length() >= 8 && value.length() <= 64
					&& value.chars().noneMatch(Character::isWhitespace)
					&& value.chars().anyMatch(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))
					&& value.chars().anyMatch(Character::isDigit)
					&& value.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
		}
	}
}
