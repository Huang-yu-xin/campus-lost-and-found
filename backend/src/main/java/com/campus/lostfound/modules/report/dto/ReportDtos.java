package com.campus.lostfound.modules.report.dto;

import java.time.LocalDateTime;

/** 举报接口的对外数据结构，字段与 openapi.yaml 的 Report / ResolveReportRequest 对齐。 */
public final class ReportDtos {

    private ReportDtos() {
    }

    /** 提交举报：claimId 与 clueId 最多填一个；都不填表示只举报公开物品。 */
    public record CreateReportRequest(Long itemId, Long claimId, Long clueId, String reason) {
    }

    /** 处理举报：处置动作与举报结论同一事务提交。 */
    public record ResolveReportRequest(Long expectedVersion, String action, String reason) {
    }

    public record ReportView(
            long id,
            long itemId,
            Long claimId,
            Long clueId,
            String reason,
            String status,
            String action,
            String resolution,
            long version,
            LocalDateTime createdAt,
            LocalDateTime resolvedAt) {
    }
}
