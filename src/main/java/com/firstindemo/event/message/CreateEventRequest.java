package com.firstindemo.event.message;

/**
 * 이벤트 생성 요청 DTO.
 *
 * @param eventId 이벤트 ID. 생략 시 UUID로 자동 생성
 * @param stock   당첨자 수 (1 이상)
 */
public record CreateEventRequest(String eventId, Integer stock) {
}
