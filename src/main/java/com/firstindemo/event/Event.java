package com.firstindemo.event;

/**
 * 이벤트. 이벤트별로 당첨자 수(stock)를 지정한다.
 */
public record Event(String eventId, int stock) {
}
