package com.campus.lostfound.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class CodePointLengthValidatorTest {

    private record Nickname(@CodePointLength(min = 1, max = 3) String value) {
    }

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void countsCodePointsAfterStripping() {
        // 3 个 emoji 在 Java String 里占 6 个 char，但只算 3 个字符
        assertThat(validator.validate(new Nickname("😀😀😀"))).isEmpty();
        assertThat(validator.validate(new Nickname("  校园卡  "))).isEmpty();
        assertThat(validator.validate(new Nickname("四个汉字"))).hasSize(1);
        assertThat(validator.validate(new Nickname("   "))).hasSize(1);
        assertThat(validator.validate(new Nickname(null))).isEmpty();
    }
}
