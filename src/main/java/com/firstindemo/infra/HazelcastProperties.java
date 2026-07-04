package com.firstindemo.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Hazelcast 관련 외부 설정 프로퍼티.
 * application.yaml의 {@code firstin.hazelcast} 하위 값들과 바인딩된다.
 * 캐시 맵 이름은 설정이 아니라 {@link CacheName}에서 관리한다.
 */
@ConfigurationProperties(prefix = "firstin.hazelcast")
public record HazelcastProperties(
  @DefaultValue("firstin-hz")
  String instanceName,

  @DefaultValue("127.0.0.1")
  List<String> members,

  @DefaultValue
  ResultCache resultCache
) {

  public record ResultCache(
    @DefaultValue("600")
    int ttlSeconds
  ) {
  }
}
