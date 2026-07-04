package com.firstindemo.exception.code;

import com.firstindemo.code.Status;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 에러 코드.
 * HTTP 상태와 메시지 템플릿을 한 곳에서 관리한다.
 * 성공/도메인 상태({@link Status})와는 구분된다 — 여기는 에러 전용.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

  EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 이벤트: %s"),
  DUPLICATE_EVENT(HttpStatus.CONFLICT, "이미 존재하는 이벤트: %s"),
  INVALID_STOCK(HttpStatus.BAD_REQUEST, "stock은 1 이상이어야 합니다"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다"),
  ;

  private final HttpStatus status;
  private final String messageTemplate;

  public String formatMessage(Object... args) {
    return args.length == 0 ? messageTemplate : messageTemplate.formatted(args);
  }
}
