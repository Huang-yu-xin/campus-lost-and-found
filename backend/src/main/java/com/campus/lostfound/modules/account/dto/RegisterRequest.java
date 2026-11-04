package com.campus.lostfound.modules.account.dto;

import com.campus.lostfound.common.validation.CodePointLength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。身份三项须与虚拟名册一致；不接受 role 等其他字段（未知字段统一 400）。
 * 文本字段先去首尾空白再校验，密码保持原样。
 */
public record RegisterRequest(
        @NotBlank(message = "请输入学号")
        @Size(max = 32, message = "学号不能超过 32 位")
        @Pattern(regexp = "^[0-9]+$", message = "学号只能是数字")
        String studentId,

        @NotBlank(message = "请输入姓名")
        @Size(min = 2, max = 10, message = "姓名为 2～10 个汉字")
        @Pattern(regexp = "^[\\u3400-\\u9fff]+$", message = "姓名只能是汉字")
        String realName,

        @NotBlank(message = "请输入校园卡号")
        @Size(max = 32, message = "校园卡号不能超过 32 个字符")
        @Pattern(regexp = "^[\\x21-\\x7E]+$", message = "校园卡号只能包含英文字母、数字和符号")
        String campusCardNo,

        @NotBlank(message = "请输入邮箱")
        @Size(max = 254, message = "邮箱不能超过 254 个字符")
        @Pattern(regexp = "^[\\x21-\\x7E]+$", message = "邮箱只能包含英文字母、数字和符号")
        @Email(message = "邮箱格式不正确")
        String email,

        @NotBlank(message = "请输入验证码")
        @Pattern(regexp = "^[0-9]{6}$", message = "验证码为 6 位数字")
        String emailCode,

        @NotBlank(message = "请输入密码")
        @Size(min = 8, max = 64, message = "密码为 8～64 位")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).+$", message = "密码需同时包含字母和数字")
        String password,

        @CodePointLength(min = 1, max = 30, message = "昵称为 1～30 个字符")
        String nickname) {

    public RegisterRequest {
        studentId = strip(studentId);
        realName = strip(realName);
        campusCardNo = strip(campusCardNo);
        email = strip(email);
        emailCode = strip(emailCode);
        nickname = strip(nickname);
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    /** 日志或调试输出时不暴露密码和验证码。 */
    @Override
    public String toString() {
        return "RegisterRequest[studentId=" + studentId + "]";
    }
}
