package com.firstindemo.apply;

import com.firstindemo.apply.message.ApplyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

/**
 * 접수 API.
 * POST /events/{eventId}/apply — 게이트 통과 시 202 QUEUED, 마감 시 200 SOLD_OUT.
 */
@RequiredArgsConstructor
@RestController
public class ApplyController {

  private final ApplyService applyService;

  @PostMapping("/events/{eventId}/apply")
  public ResponseEntity<ApplyResponse> apply(
    @PathVariable String eventId,
    @RequestParam(required = false) String userId) {

    // userId가 비어있으면 UUID로 자동 생성 (데모 편의)
    String resolvedUserId = Optional.ofNullable(userId)
      .orElseGet(() -> UUID.randomUUID().toString());

    boolean accepted = applyService.apply(eventId, resolvedUserId);

    if (accepted) {
      return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(ApplyResponse.QUEUED);
    }
    return ResponseEntity.ok(ApplyResponse.SOLD_OUT);
  }
}
