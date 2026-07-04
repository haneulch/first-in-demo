package com.firstindemo.event;

import com.firstindemo.exception.BusinessException;
import com.firstindemo.exception.code.ErrorCode;
import com.firstindemo.event.message.CreateEventRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

/**
 * 이벤트 생성 API.
 * POST /events — 이벤트를 생성하고 당첨자 수(stock)를 지정한다.
 */
@RequiredArgsConstructor
@RestController
public class EventController {

  private final EventService eventService;

  @PostMapping("/events")
  public ResponseEntity<Event> create(@RequestBody CreateEventRequest request) {
    if (request.stock() == null || request.stock() < 1) {
      throw new BusinessException(ErrorCode.INVALID_STOCK);
    }

    // eventId가 비어있으면 UUID로 자동 생성
    String eventId = Optional.ofNullable(request.eventId())
      .filter(id -> !id.isBlank())
      .orElseGet(() -> UUID.randomUUID().toString());

    Event event = eventService.create(eventId, request.stock());
    return ResponseEntity.status(HttpStatus.CREATED).body(event);
  }
}
