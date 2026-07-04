package com.firstindemo.exception;

import com.firstindemo.exception.code.ErrorCode;

/**
 * 에러 응답 공통 형태. 모든 에러는 {code, message}로 내려간다.
 */
public record ErrorResponse(String code, String message) {

  public static ErrorResponse of(BusinessException e) {
    return new ErrorResponse(e.getErrorCode().name(), e.getMessage());
  }

  public static ErrorResponse of(ErrorCode errorCode) {
    return new ErrorResponse(errorCode.name(), errorCode.formatMessage());
  }
}
