package com.campus.lostfound.modules.clue;

import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.common.api.PageData;
import com.campus.lostfound.modules.clue.ClueService.*;
import com.campus.lostfound.modules.item.dto.ItemViews.ItemRecord;
import com.campus.lostfound.modules.item.dto.VersionRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ClueController {
    public record Content(String content) {}
    private final ClueService service;
    public ClueController(ClueService service) { this.service = service; }
    private static long user(Authentication auth) { return Long.parseLong(auth.getName()); }

    @PostMapping("/items/{itemId}/clues")
    @PreAuthorize("hasRole('STUDENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Clue> create(Authentication auth, @PathVariable long itemId, @RequestBody Content body) {
        return ApiResponse.ok(service.create(user(auth), itemId, body.content()));
    }
    @GetMapping("/clues/{clueId}")
    public ApiResponse<Detail> detail(Authentication auth, @PathVariable long clueId) {
        return ApiResponse.ok(service.detail(user(auth), clueId));
    }
    @GetMapping("/me/clues")
    public ApiResponse<PageData<Clue>> mine(Authentication auth, @RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="10") int pageSize) {
        return ApiResponse.ok(service.list(user(auth), false, null, page, pageSize));
    }
    @GetMapping("/me/received-clues")
    public ApiResponse<PageData<Clue>> received(Authentication auth, @RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="10") int pageSize, @RequestParam(required=false) Long itemId) {
        return ApiResponse.ok(service.list(user(auth), true, itemId, page, pageSize));
    }
    @GetMapping("/items/{itemId}/clues")
    public ApiResponse<PageData<Clue>> item(Authentication auth, @PathVariable long itemId,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="10") int pageSize) {
        return ApiResponse.ok(service.itemClues(user(auth), itemId, page, pageSize));
    }
    @GetMapping("/clues/{clueId}/messages")
    public ApiResponse<PageData<Message>> messages(Authentication auth, @PathVariable long clueId,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="10") int pageSize) {
        return ApiResponse.ok(service.messages(user(auth), clueId, page, pageSize));
    }
    @PostMapping("/clues/{clueId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Message> send(Authentication auth, @PathVariable long clueId, @RequestBody Content body) {
        return ApiResponse.ok(service.send(user(auth), clueId, body.content()));
    }
    @PostMapping("/items/{itemId}/resolve")
    public ApiResponse<ItemRecord> resolve(Authentication auth, @PathVariable long itemId,
            @Valid @RequestBody VersionRequest body) {
        return ApiResponse.ok(service.resolve(user(auth), itemId, body.expectedVersion()));
    }
}
