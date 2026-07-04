package com.firstindemo.code;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum Status {
  WIN,
  LOSE,
  PENDING,
  ;

  private static final Map<String, Status> map = Arrays.stream(values())
    .collect(Collectors.toMap(Status::name, Function.identity()));

  public static Status of(String name) {
    return map.get(name);
  }
}
