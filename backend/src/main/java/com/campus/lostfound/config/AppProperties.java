package com.campus.lostfound.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 项目自定义配置 app.*。值来自本机环境变量，校验失败时应用拒绝启动。
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @NotNull Storage storage,
        @Valid @NotNull Verification verification,
        @Valid @NotNull Mail mail) {

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
