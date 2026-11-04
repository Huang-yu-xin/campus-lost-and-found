package com.campus.lostfound.modules.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangeEmailRequest(
        @NotNull(message = "缺少 expectedVersion")
        @Min(value = 1, message = "expectedVersion 至少为 1")
        Long expectedVersion,

        @NotBlank(message = "请输入新邮箱")
        @Size(max = 254, message = "邮箱不能超过 254 个字符")
        @Pattern(regexp = "^[\\x21-\\x7E]+$", message = "邮箱只能包含英文字母、数字和符号")
        @Email(message = "邮箱格式不正确")
        String email,

        @NotBlank(message = "请输入验证码")
        @Pattern(regexp = "^[0-9]{6}$", message = "验证码为 6 位数字")
        String emailCode) {

    public ChangeEmailRequest {
        email = email == null ? null : email.strip();
        emailCode = emailCode == null ? null : emailCode.strip();
    }

    @Override
    public String toString() {
        return "ChangeEmailRequest[expectedVersion=" + expectedVersion + "]";
    }
}
