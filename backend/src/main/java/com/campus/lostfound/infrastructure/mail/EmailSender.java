package com.campus.lostfound.infrastructure.mail;

import java.time.Duration;

/**
 * 发送验证码邮件。开发环境用 DevFileEmailSender 写入本机收件箱（模拟发送）；
 * SMTP 模式使用配置的邮件服务。
 * 实现类不得把验证码写进普通日志。
 */
public interface EmailSender {

    /**
     * @param to 收件邮箱（已规范化）
     * @param purposeLabel 用途说明，例如“注册”“修改邮箱”
     * @param code 6 位验证码明文，只出现在邮件内容里
     * @param validFor 有效期
     */
    void sendVerificationCode(String to, String purposeLabel, String code, Duration validFor);
}
