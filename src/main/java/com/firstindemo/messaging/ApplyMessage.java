package com.firstindemo.messaging;

/**
 * apply와 judge가 공유하는 메시지 계약.
 * 큐를 통해 접수 정보를 전달한다.
 */
public record ApplyMessage(String eventId, String userId) {
}
