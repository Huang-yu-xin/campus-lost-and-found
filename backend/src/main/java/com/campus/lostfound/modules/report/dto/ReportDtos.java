package com.campus.lostfound.modules.report.dto;

import com.campus.lostfound.modules.claim.dto.ClaimDtos.MessageView;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;

/**
 * 举报接口的对外数据结构，字段与 openapi.yaml 的 Report / ReportRequest / ReportConversation 对齐：
 * 编号以字符串传输，时间为带时区的 UTC 时间。
 */
public final class ReportDtos {

    /** 合同 Id：正整数字符串；位数上限保证能转成 long。 */
    private static final String ID = "[1-9][0-9]{0,17}";

    private ReportDtos() {
    }

    /** 提交举报：claimId 与 clueId 最多填一个；都不填表示只举报公开物品。 */
    public record CreateReportRequest(
            @Pattern(regexp = ID, message = "编号格式不正确") String itemId,
            @Pattern(regexp = ID, message = "编号格式不正确") String claimId,
            @Pattern(regexp = ID, message = "编号格式不正确") String clueId,
            String reason) {
    }

    /** 处理举报：处置动作与举报结论同一事务提交。 */
    public record ResolveReportRequest(Long expectedVersion, String action, String reason) {
    }

    public record ReportView(
            String id,
            String itemId,
            String claimId,
            String clueId,
            String reason,
            String status,
            String action,
            String resolution,
            long version,
            Instant createdAt,
            Instant resolvedAt) {
    }

    /** 管理员经举报受限读取的会话：凭证正文与分页留言，不含联系方式。 */
    public record ReportConversation(
            String kind,
            String contextId,
            String evidenceText,
            List<MessageView> items,
            int page,
            int pageSize,
            long total) {
    }
}
