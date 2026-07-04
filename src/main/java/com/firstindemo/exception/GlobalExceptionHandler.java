package com.firstindemo.exception;

import com.firstindemo.exception.code.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리.
 * BusinessException은 ErrorCode에 정의된 상태/메시지로,
 * 그 외 예외는 500 INTERNAL_ERROR로 변환한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
    return ResponseEntity.status(e.getErrorCode().getStatus())
      .body(ErrorResponse.of(e));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) throws Exception {
    // 프레임워크가 상태 코드를 정의한 예외(파라미터 누락 400, 경로 없음 404 등)는 기본 처리에 위임
    if (e instanceof org.springframework.web.ErrorResponse) {
      throw e;
    }
    log.error("처리되지 않은 예외", e);
    return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
      .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
  }
}
