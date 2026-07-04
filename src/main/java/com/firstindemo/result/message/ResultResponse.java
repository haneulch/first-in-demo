package com.firstindemo.result.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.firstindemo.code.Status;

import static com.firstindemo.code.Status.*;

/**
 * 결과 조회 API 응답 DTO.
 *
 * @param status     WIN / LOSE / PENDING
 * @param retryAfter PENDING 상태일 때 폴링 간격 (초). WIN/LOSE이면 null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResultResponse(Status status, Integer retryAfter) {

  public static ResultResponse win() {
    return new ResultResponse(WIN, null);
  }

  public static ResultResponse lose() {
    return new ResultResponse(LOSE, null);
  }

  public static ResultResponse pending(int retryAfterSeconds) {
    return new ResultResponse(PENDING, retryAfterSeconds);
  }
}
