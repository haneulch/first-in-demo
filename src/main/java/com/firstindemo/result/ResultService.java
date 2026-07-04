package com.firstindemo.result;

import com.firstindemo.code.Status;
import com.firstindemo.event.EventService;
import com.firstindemo.infra.CacheName;
import com.firstindemo.judge.WinnerRepository;
import com.firstindemo.result.dto.EventStatus;
import com.firstindemo.result.dto.EventWinner;
import com.firstindemo.result.message.ResultResponse;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 결과 조회 서비스.
 * 캐시 우선 조회 → 캐시 miss 시 DB fallback.
 */
@Service
public class ResultService {

  private final IMap<String, String> resultCache;
  private final WinnerRepository winnerRepository;
  private final EventService eventService;

  public ResultService(HazelcastInstance hz,
                       WinnerRepository winnerRepository,
                       EventService eventService) {
    this.resultCache = CacheName.RESULT_CACHE.getMap(hz);
    this.winnerRepository = winnerRepository;
    this.eventService = eventService;
  }

  /**
   * 사용자의 당첨 결과를 조회한다.
   *
   * @param eventId 이벤트 ID
   * @param userId  사용자 ID
   * @return WIN / LOSE / PENDING(retryAfter=3)
   */
  public ResultResponse getResult(String eventId, String userId) {
    String cacheKey = eventId + ":" + userId;

    // 1차: 캐시 조회
    String cached = resultCache.get(cacheKey);
    if (cached != null) {
      return switch (Status.of(cached)) {
        case WIN -> ResultResponse.win();
        case LOSE -> ResultResponse.lose();
        default -> ResultResponse.pending(3);
      };
    }

    // 2차: DB fallback
    if (winnerRepository.isWinner(eventId, userId)) {
      // 캐시에 다시 넣기
      resultCache.put(cacheKey, "WIN");
      return ResultResponse.win();
    }

    // 재고 소진 여부로 LOSE/PENDING 판단 (이벤트가 없으면 404)
    int stock = eventService.getStockOrThrow(eventId);
    long winners = winnerRepository.countWinners(eventId);
    if (winners >= stock) {
      resultCache.put(cacheKey, "LOSE");
      return ResultResponse.lose();
    }

    // 아직 판정 미완료
    return ResultResponse.pending(3);
  }


  /**
   * 이벤트 당첨자 조회
   *
   * @param eventId 이벤트 ID
   * @return 현재 당첨자
   */
  public List<EventWinner> getWinners(String eventId) {
    eventService.getStockOrThrow(eventId); // 이벤트가 없으면 404
    List<String> winners = winnerRepository.getWinners(eventId);
    return EventWinner.of(winners);
  }

  /**
   * 이벤트 상태 조회.
   *
   * @param eventId 이벤트 ID
   * @return 판정 완료 여부와 현재 당첨자 수
   */
  public EventStatus getEventStatus(String eventId) {
    int stock = eventService.getStockOrThrow(eventId);
    long winners = winnerRepository.countWinners(eventId);
    boolean completed = winners >= stock;
    return new EventStatus(eventId, stock, winners, completed);
  }
}
