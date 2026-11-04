package com.campus.lostfound.modules.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 登录请求：account 为学号、校园卡号或预置管理员账号。 */
public record LoginRequest(
        @NotBlank(message = "请输入账号")
        @Size(max = 32, message = "账号不能超过 32 个字符")
        @Pattern(regexp = "^[\\x21-\\x7E]+$", message = "账号只能包含英文字母、数字和符号")
        String account,

        @NotBlank(message = "请输入密码")
        @Size(max = 64, message = "密码不能超过 64 个字符")
        String password) {

    /** 日志或调试输出时不暴露密码。 */
    @Override
    public String toString() {
        return "LoginRequest[account=" + account + "]";
    }
}
