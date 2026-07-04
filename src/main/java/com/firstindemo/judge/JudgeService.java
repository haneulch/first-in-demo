package com.firstindemo.judge;

import com.firstindemo.event.EventService;
import com.firstindemo.infra.CacheName;
import com.firstindemo.messaging.ApplyMessage;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 판정 서비스.
 * 배치로 받은 메시지를 재고 기준으로 판정하고, 결과를 DB와 캐시에 기록한다.
 */
@Service
public class JudgeService {

  private static final Logger log = LoggerFactory.getLogger(JudgeService.class);

  private final WinnerRepository winnerRepository;
  private final EventService eventService;
  private final IMap<String, String> resultCache;

  public JudgeService(WinnerRepository winnerRepository,
                      EventService eventService,
                      HazelcastInstance hz) {
    this.winnerRepository = winnerRepository;
    this.eventService = eventService;
    this.resultCache = CacheName.RESULT_CACHE.getMap(hz);
  }

  /**
   * 배치 판정.
   * 현재 당첨자 수를 기준으로 재고 내 메시지만 당첨 처리하고,
   * 나머지는 낙첨 처리한다.
   *
   * <p>재전달·연타로 이미 당첨된 사용자의 중복 메시지는 재고를 소모하지 않고,
   * 기존 WIN 결과를 LOSE로 덮어쓰지 않는다.</p>
   *
   * @param messages 배치로 소비된 메시지 목록 (큐 도착 순서 보장)
   */
  public void judgeBatch(List<ApplyMessage> messages) {
    if (messages.isEmpty()) {
      return;
    }

    String eventId = messages.getFirst().eventId();

    // 이벤트별 당첨자 수(stock) 조회 — 이벤트가 없으면 판정 불가, 배치 폐기
    Integer stock = eventService.findStock(eventId).orElse(null);
    if (stock == null) {
      log.warn("존재하지 않는 이벤트의 메시지 {}건 폐기: eventId={}", messages.size(), eventId);
      return;
    }

    // 기존 당첨자 조회 — 재전달·연타 중복 메시지 식별용
    Set<String> existingWinners = new HashSet<>(winnerRepository.getWinners(eventId));
    long remaining = stock - existingWinners.size();

    List<String> winnersToInsert = new ArrayList<>();
    Set<String> batchWinners = new HashSet<>();
    Set<String> losers = new LinkedHashSet<>();

    for (ApplyMessage msg : messages) {
      String userId = msg.userId();

      // 이미 당첨된 사용자의 중복 메시지 — 재고 소모 없이 건너뛴다
      if (existingWinners.contains(userId) || batchWinners.contains(userId)) {
        continue;
      }

      if (remaining > 0) {
        winnersToInsert.add(userId);
        batchWinners.add(userId);
        remaining--;
      } else {
        losers.add(userId);
      }
    }

    // 당첨자 batch INSERT (중복은 DB 유니크 제약으로 흡수)
    if (!winnersToInsert.isEmpty()) {
      int inserted = winnerRepository.batchInsert(eventId, winnersToInsert);
      log.info("배치 판정: 당첨 시도={}, 실제 INSERT={}", winnersToInsert.size(), inserted);
    }

    // 결과 캐시에 push
    for (String userId : winnersToInsert) {
      resultCache.put(cacheKey(eventId, userId), "WIN");
    }

    for (String userId : losers) {
      resultCache.put(cacheKey(eventId, userId), "LOSE");
    }

    log.info("배치 판정 완료: 총 {}건 (당첨={}, 낙첨={})",
      messages.size(), winnersToInsert.size(), losers.size());
  }

  private String cacheKey(String eventId, String userId) {
    return eventId + ":" + userId;
  }
}
