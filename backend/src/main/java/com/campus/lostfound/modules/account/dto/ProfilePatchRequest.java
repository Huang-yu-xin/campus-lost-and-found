package com.campus.lostfound.modules.account.dto;

import com.campus.lostfound.common.validation.CodePointLength;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH /me：只修改请求里出现的字段。没出现的字段保持不变；phone 显式传 null 表示清空手机号。
 * Java record 区分不了“没传”和“传了 null”，所以这里用 setter 记录每个字段是否出现。
 */
public class ProfilePatchRequest {

    @NotNull(message = "缺少 expectedVersion")
    @Min(value = 1, message = "expectedVersion 至少为 1")
    private Long expectedVersion;

    @CodePointLength(min = 1, max = 30, message = "昵称为 1～30 个字符")
    private String nickname;

    @Size(max = 20, message = "手机号不能超过 20 个字符")
    @Pattern(regexp = "^\\+?[0-9 -]{6,20}$", message = "手机号格式不正确")
    private String phone;

    private boolean nicknamePresent;
    private boolean phonePresent;

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname == null ? null : nickname.strip();
        this.nicknamePresent = true;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone == null ? null : phone.strip();
        this.phonePresent = true;
    }

    public boolean hasNickname() {
        return nicknamePresent;
    }

    public boolean hasPhone() {
        return phonePresent;
    }

    @JsonIgnore
    @AssertTrue(message = "至少要修改昵称或手机号中的一项")
    public boolean isAnyFieldPresent() {
        return nicknamePresent || phonePresent;
    }

    @JsonIgnore
    @AssertTrue(message = "昵称不能为空")
    public boolean isNicknameNotNullWhenPresent() {
        return !nicknamePresent || nickname != null;
    }
}
