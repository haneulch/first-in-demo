package com.firstindemo.apply.message;

/**
 * 접수 API 응답 DTO.
 *
 * @param status QUEUED(접수 완료) 또는 SOLD_OUT(마감)
 */
public record ApplyResponse(String status) {

    public static final ApplyResponse QUEUED = new ApplyResponse("QUEUED");
    public static final ApplyResponse SOLD_OUT = new ApplyResponse("SOLD_OUT");
}
