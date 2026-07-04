package com.firstindemo.result.message;

public record EventStatus(String eventId, int stock, long winners, boolean completed) {
}
