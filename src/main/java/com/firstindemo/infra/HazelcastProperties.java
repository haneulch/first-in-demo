package com.firstindemo.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Hazelcast 관련 외부 설정 프로퍼티.
 * application.yaml의 {@code firstin.hazelcast} 하위 값들과 바인딩된다.
 */
@ConfigurationProperties(prefix = "firstin.hazelcast")
public record HazelcastProperties(
  @DefaultValue("firstin-hz")
  String instanceName,

  @DefaultValue("127.0.0.1")
  List<String> members,

  @DefaultValue
  ResultCache resultCache,

  @DefaultValue
  GateCounter gateCounter
) {

  public record ResultCache(
    @DefaultValue("result-cache")
    String mapName,

    @DefaultValue("600")
    int ttlSeconds
  ) {
  }

  public record GateCounter(
    @DefaultValue("gate-counter")
    String mapName
  ) {
  }
}
