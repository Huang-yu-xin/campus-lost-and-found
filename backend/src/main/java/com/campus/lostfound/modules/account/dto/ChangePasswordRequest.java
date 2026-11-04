package com.campus.lostfound.modules.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "请输入原密码")
        @Size(max = 64, message = "原密码不能超过 64 个字符")
        String oldPassword,

        @NotBlank(message = "请输入新密码")
        @Size(min = 8, max = 64, message = "新密码为 8～64 位")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).+$", message = "新密码需同时包含字母和数字")
        String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[***]";
    }
}
