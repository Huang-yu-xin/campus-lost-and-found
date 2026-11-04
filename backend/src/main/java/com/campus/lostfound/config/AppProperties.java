package com.campus.lostfound.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 项目自定义配置 app.*。值来自本机环境变量，校验失败时应用拒绝启动。
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @NotNull Storage storage,
        @Valid @NotNull Verification verification,
        @Valid @NotNull Mail mail,
        @Valid @DefaultValue Security security,
        @Valid Admin admin) {

    /** 登录失败限流：同一账号连续失败达到次数后临时锁定，到期自动解锁，不永久锁号。 */
    public record Security(
            @DefaultValue("5") @Min(1) int loginMaxFailures,
            @DefaultValue("15m") @NotNull Duration loginLockDuration) {
    }

    /**
     * 预置管理员。account 为空时不初始化；配置后启动时若该账号不存在则自动创建，已存在则不改动。
     * 密码只放本机配置，不写进源码和仓库。
     */
    public record Admin(String account, String password, String email, @DefaultValue("管理员") String nickname) {

        private static final Pattern ACCOUNT = Pattern.compile("^[\\x21-\\x7E]{1,32}$");
        private static final Pattern STRONG_PASSWORD = Pattern.compile("^(?=.*[A-Za-z])(?=.*[0-9]).{8,64}$");

        public boolean enabled() {
            return account != null && !account.isBlank();
        }

        @AssertTrue(message = "管理员账号需为 1～32 个 ASCII 可见字符")
        public boolean isAccountValid() {
            return !enabled() || ACCOUNT.matcher(account).matches();
        }

        @AssertTrue(message = "管理员密码需为 8～64 位且同时包含字母和数字")
        public boolean isPasswordStrong() {
            return !enabled() || (password != null && STRONG_PASSWORD.matcher(password).matches());
        }

        @AssertTrue(message = "配置管理员时需要同时配置邮箱")
        public boolean isEmailPresent() {
            return !enabled() || (email != null && !email.isBlank());
        }
    }

    /** 图片上传目录，指向仓库 runtime 下的本机目录，不得对外静态开放。 */
    public record Storage(@NotBlank(message = "需要通过 APP_UPLOAD_DIR 配置上传目录") String uploadDir) {
    }

    /** 验证码摘要使用的 HMAC 密钥，随机生成，不提交 Git。 */
    public record Verification(
            @NotBlank(message = "需要通过 APP_CODE_HMAC_KEY 配置验证码密钥")
            @Size(min = 32, message = "验证码密钥至少 32 个字符")
            String hmacKey) {
    }

    public record Mail(@NotNull MailMode mode, String devMailDir) {

        @AssertTrue(message = "dev-file 模式需要通过 APP_DEV_MAIL_DIR 配置开发收件箱目录")
        public boolean isDevMailDirConfigured() {
            return mode != MailMode.DEV_FILE || (devMailDir != null && !devMailDir.isBlank());
        }
    }

    public enum MailMode {
        /** 验证码写入本机开发收件箱，仅用于虚拟数据和演示，需标注“模拟发送”。 */
        DEV_FILE,
        SMTP
    }
}
