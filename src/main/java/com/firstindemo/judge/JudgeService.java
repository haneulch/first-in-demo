package com.firstindemo.judge;

import com.firstindemo.messaging.ApplyMessage;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 판정 서비스.
 * 배치로 받은 메시지를 재고 기준으로 판정하고, 결과를 DB와 캐시에 기록한다.
 */
@Service
public class JudgeService {

  private static final Logger log = LoggerFactory.getLogger(JudgeService.class);

  private final WinnerRepository winnerRepository;
  private final IMap<String, String> resultCache;
  private final int stock;

  public JudgeService(WinnerRepository winnerRepository,
                      HazelcastInstance hz,
                      @Value("${firstin.stock}") int stock) {
    this.winnerRepository = winnerRepository;
    this.resultCache = hz.getMap("result-cache");
    this.stock = stock;
  }

  /**
   * 배치 판정.
   * 현재 당첨자 수를 기준으로 재고 내 메시지만 당첨 처리하고,
   * 나머지는 낙첨 처리한다.
   *
   * @param messages 배치로 소비된 메시지 목록 (큐 도착 순서 보장)
   */
  public void judgeBatch(List<ApplyMessage> messages) {
    if (messages.isEmpty()) {
      return;
    }

    String eventId = messages.getFirst().eventId();

    // 현재 당첨자 수 조회
    long currentWinners = winnerRepository.countWinners(eventId);
    long remaining = stock - currentWinners;

    List<String> winnersToInsert = new ArrayList<>();
    List<String> losers = new ArrayList<>();

    for (ApplyMessage msg : messages) {
      if (remaining > 0 && !winnersToInsert.contains(msg.userId())) {
        winnersToInsert.add(msg.userId());
        remaining--;
      } else {
        losers.add(msg.userId());
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
