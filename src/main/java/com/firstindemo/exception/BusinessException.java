package com.firstindemo.exception;

import com.firstindemo.exception.code.ErrorCode;
import lombok.Getter;

/**
 * 비즈니스 예외.
 * {@link ErrorCode}를 실어 던지면 {@link GlobalExceptionHandler}가
 * 코드에 정의된 HTTP 상태와 메시지로 변환한다.
 */
@Getter
public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;

  public BusinessException(ErrorCode errorCode, Object... messageArgs) {
    super(errorCode.formatMessage(messageArgs));
    this.errorCode = errorCode;
  }
}
