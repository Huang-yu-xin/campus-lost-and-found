package com.campus.lostfound.modules.report.controller;

import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.common.api.PageData;
import com.campus.lostfound.modules.report.dto.ReportDtos.CreateReportRequest;
import com.campus.lostfound.modules.report.dto.ReportDtos.ReportView;
import com.campus.lostfound.modules.report.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 用户侧举报接口（FR-27 提交、FR-22 我的举报）。 */
@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReportView> create(@Valid @RequestBody CreateReportRequest request,
                                          Authentication authentication) {
        return ApiResponse.ok(service.create(userId(authentication), request));
    }

    @GetMapping("/me/reports")
    public ApiResponse<PageData<ReportView>> mine(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            Authentication authentication) {
        return ApiResponse.ok(service.listMine(userId(authentication), page, pageSize));
    }

    private static long userId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }
}
