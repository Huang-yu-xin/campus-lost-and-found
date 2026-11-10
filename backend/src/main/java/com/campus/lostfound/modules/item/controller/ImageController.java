package com.campus.lostfound.modules.item.controller;

import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.modules.account.security.AccountUserDetails;
import com.campus.lostfound.modules.item.dto.ItemViews.Image;
import com.campus.lostfound.modules.item.service.ImageService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ImageController {
    private final ImageService service;
    public ImageController(ImageService service) { this.service = service; }

    @PostMapping(value = "/api/v1/uploads/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Image> upload(@AuthenticationPrincipal AccountUserDetails user,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(service.upload(user.userId(), file));
    }

    @GetMapping("/api/v1/images/{imageId}")
    public ResponseEntity<byte[]> read(@PathVariable long imageId,
            @AuthenticationPrincipal AccountUserDetails user) {
        boolean admin = user != null && user.getAuthorities().stream()
                .anyMatch(role -> role.getAuthority().equals("ROLE_ADMIN"));
        var content = service.read(imageId, user == null ? null : user.userId(), admin);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(content.mediaType())).body(content.bytes());
    }
}
