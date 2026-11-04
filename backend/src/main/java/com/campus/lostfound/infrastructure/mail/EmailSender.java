package com.campus.lostfound.infrastructure.mail;

import java.time.Duration;

/**
 * 发送验证码邮件。开发环境用 DevFileEmailSender 写入本机收件箱（模拟发送）；
 * 需要真实发送时新增 SMTP 实现并在 MailConfig 中切换，业务代码不用改。
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
