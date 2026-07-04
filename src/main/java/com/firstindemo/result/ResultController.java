package com.firstindemo.result;

import com.firstindemo.result.message.EventStatus;
import com.firstindemo.result.message.ResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결과 조회 API.
 * GET /events/{eventId}/result — 개인 결과 (WIN / LOSE / PENDING + retryAfter)
 * GET /events/{eventId}/status — 이벤트 공유 상태 (판정 진행 중 / 완료)
 */
@RequiredArgsConstructor
@RestController
public class ResultController {

  private final ResultService resultService;

  @GetMapping("/events/{eventId}/result")
  public ResponseEntity<ResultResponse> result(
    @PathVariable String eventId,
    @RequestParam String userId) {
    ResultResponse response = resultService.getResult(eventId, userId);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/events/{eventId}/status")
  public ResponseEntity<EventStatus> eventStatus(@PathVariable String eventId) {
    return ResponseEntity.ok(resultService.getEventStatus(eventId));
  }
}
