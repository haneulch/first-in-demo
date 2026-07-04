package com.firstindemo.infra;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Hazelcast 캐시 맵 이름.
 * 맵 이름 문자열을 한 곳에서 관리한다 — 선언(HazelcastConfig)과 사용(hz.getMap)이 항상 일치한다.
 */
@Getter
@RequiredArgsConstructor
public enum CacheName {

  /** 조기 차단 게이트 카운터. eventId → 접수 시도 수 */
  GATE_COUNTER("gate-counter"),

  /** 판정 결과 캐시. "eventId:userId" → WIN/LOSE */
  RESULT_CACHE("result-cache"),

  /** 이벤트별 당첨자 수 캐시. eventId → stock */
  EVENT_STOCK("event-stock"),
  ;

  private final String mapName;

  /**
   * 이 이름의 Hazelcast 맵을 얻는다.
   */
  public <K, V> IMap<K, V> getMap(HazelcastInstance hz) {
    return hz.getMap(mapName);
  }
}
