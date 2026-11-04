package com.campus.lostfound.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 文本长度校验：先去掉首尾空白，再按 Unicode 码点计数（一个 emoji 或汉字都算 1 个字符），
 * 与数据库 CHAR_LENGTH 及前端计数一致。不要用 @Size 校验用户输入的文本长度。
 * null 视为通过，是否必填另用 @NotNull / @NotBlank 表达。
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CodePointLengthValidator.class)
public @interface CodePointLength {

    int min() default 0;

    int max() default Integer.MAX_VALUE;

    String message() default "长度需在 {min}～{max} 个字符之间";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
