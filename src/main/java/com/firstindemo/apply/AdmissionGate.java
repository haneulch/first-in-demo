package com.firstindemo.apply;

import com.firstindemo.event.EventService;
import com.firstindemo.exception.BusinessException;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 조기 차단 게이트.
 * 이벤트별 재고(stock)의 gate-multiplier배를 넘는 접수는 큐에 넣지 않고 즉시 "마감" 응답한다.
 * 게이트는 의도적으로 느슨하다 — 정확한 카운터가 아닌 근사치.
 * 최종 판정은 워커가 큐 순서와 DB 기준으로 내린다.
 */
@Component
public class AdmissionGate {

  private final IMap<String, Long> counterMap;
  private final EventService eventService;
  private final int multiplier;

  public AdmissionGate(HazelcastInstance hz,
                       EventService eventService,
                       @Value("${firstin.gate-multiplier}") int multiplier) {
    this.counterMap = hz.getMap("gate-counter");
    this.eventService = eventService;
    this.multiplier = multiplier;
  }

  /**
   * 게이트 통과 여부를 판단한다.
   * 카운터를 원자적으로 증가시키고, 이벤트별 한도(stock × multiplier) 이하이면 통과.
   *
   * @param eventId 이벤트 ID
   * @return true이면 접수 허용, false이면 마감
   * @throws BusinessException {@code EVENT_NOT_FOUND} — 이벤트가 존재하지 않으면
   */
  public boolean tryPass(String eventId) {
    long gateLimit = (long) eventService.getStockOrThrow(eventId) * multiplier;

    // 카운터가 없으면 0L로 초기화 후 증가
    long current = counterMap.compute(eventId, (key, val) -> {
      long prev = (val != null) ? val : 0L;
      return prev + 1;
    });
    return current <= gateLimit;
  }
}
