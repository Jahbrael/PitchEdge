package com.betai.api;

import com.betai.api.dto.UserSavedBatchResponse;
import com.betai.security.CustomUserDetails;
import com.betai.service.UserHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/me/history")
@RequiredArgsConstructor
public class UserHistoryController {

    private final UserHistoryService userHistoryService;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<UserSavedBatchResponse>> getHistory(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(userHistoryService.getUserHistory(user.getId()));
    }
}
