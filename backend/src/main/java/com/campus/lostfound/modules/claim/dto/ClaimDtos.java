package com.campus.lostfound.modules.claim.dto;

import com.campus.lostfound.modules.item.dto.ItemViews.ItemRecord;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 认领相关的对外数据结构，字段与 openapi.yaml 中 Claim / ClaimDetail 对齐：
 * 编号以字符串输出，时间为带时区的 UTC 时间。
 */
public final class ClaimDtos {

    private ClaimDtos() {
    }

    /** 提交认领申请的请求体：只有特征说明一个字段。 */
    public record CreateClaimRequest(String description) {
    }

    /** 驳回认领的请求体：原因必填，1~500 字。 */
    public record RejectClaimRequest(@NotNull @Positive Long expectedVersion,
            @NotBlank @Size(max = 500) String reason) {
    }

    /** 发送认领留言的请求体：纯文本，1~1000 字。 */
    public record SendMessageRequest(String content) {
    }

    /** 认领留言视图；sender 只含公开摘要，不含联系方式。 */
    public record MessageView(String id, UserBrief sender, String content, Instant createdAt) {
    }

    /** 自己的联系方式授权设置，不含真实手机号和邮箱。 */
    public record ContactPermissionView(
            String claimId,
            boolean phoneVisible,
            boolean emailVisible,
            boolean effective,
            long version,
            Instant updatedAt) {
    }

    /** 设置授权：expectedVersion 首次为 0，两个开关都为 false 表示撤回。 */
    public record ContactPermissionRequest(
            long expectedVersion,
            boolean phoneVisible,
            boolean emailVisible) {
    }

    /** 对方已授权且实际存在的联系方式；未授权或为空的字段省略，不返回 null 占位。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CounterpartyContactView(String claimId, String phone, String email) {
    }

    /** 用户摘要，contract 中的 Publisher 结构。 */
    public record UserBrief(String id, String nickname) {
    }

    /** 认领申请（不含联系方式）。 */
    public record ClaimView(
            String id,
            String itemId,
            UserBrief applicant,
            UserBrief publisher,
            String description,
            String status,
            String reason,
            long version,
            Instant createdAt,
            Instant updatedAt) {
    }

    /** 认领私有详情：申请 + 相关物品记录；归档后双方仍可查看，因此不依赖公开详情接口。 */
    public record ClaimDetailView(ClaimView claim, ItemRecord item) {
    }
}
