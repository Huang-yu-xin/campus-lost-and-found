package com.campus.lostfound.modules.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SendCodeRequest(
        @NotBlank(message = "请输入邮箱")
        @Size(max = 254, message = "邮箱不能超过 254 个字符")
        @Pattern(regexp = "^[\\x21-\\x7E]+$", message = "邮箱只能包含英文字母、数字和符号")
        @Email(message = "邮箱格式不正确")
        String email) {

    public SendCodeRequest {
        email = email == null ? null : email.strip();
    }
}
