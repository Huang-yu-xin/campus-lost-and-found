package com.campus.lostfound.modules.report.controller;

import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.common.api.PageData;
import com.campus.lostfound.modules.report.dto.ReportDtos.ReportConversation;
import com.campus.lostfound.modules.report.dto.ReportDtos.ReportView;
import com.campus.lostfound.modules.report.dto.ReportDtos.ResolveReportRequest;
import com.campus.lostfound.modules.report.service.ReportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理员举报接口（FR-22）。全部要求 ADMIN 角色，私有会话只经 conversation 读取并审计。 */
@RestController
@RequestMapping("/api/v1/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private final ReportService service;

    public AdminReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageData<ReportView>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return ApiResponse.ok(service.listAdmin(status, page, pageSize));
    }

    @GetMapping("/{reportId}")
    public ApiResponse<ReportView> detail(@PathVariable("reportId") long reportId) {
        return ApiResponse.ok(service.getAdmin(reportId));
    }

    @GetMapping("/{reportId}/conversation")
    public ApiResponse<ReportConversation> conversation(
            @PathVariable("reportId") long reportId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            Authentication authentication) {
        return ApiResponse.ok(service.conversation(userId(authentication), reportId, page, pageSize));
    }

    @PostMapping("/{reportId}/resolve")
    public ApiResponse<ReportView> resolve(@PathVariable("reportId") long reportId,
                                           @RequestBody ResolveReportRequest request,
                                           Authentication authentication) {
        return ApiResponse.ok(service.resolve(userId(authentication), reportId, request));
    }

    private static long userId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }
}
