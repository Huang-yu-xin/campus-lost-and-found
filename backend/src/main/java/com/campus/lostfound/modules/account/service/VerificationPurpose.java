package com.campus.lostfound.modules.account.service;

/** 验证码用途。两种用途的验证码互不通用：注册不绑定用户，修改邮箱绑定当前用户。 */
public enum VerificationPurpose {
    REGISTER("注册"),
    CHANGE_EMAIL("修改邮箱");

    private final String label;

    VerificationPurpose(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
