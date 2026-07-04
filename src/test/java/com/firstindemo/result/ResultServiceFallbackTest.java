package com.firstindemo.result;

import com.firstindemo.code.Status;
import com.firstindemo.event.EventService;
import com.firstindemo.judge.WinnerRepository;
import com.firstindemo.result.message.ResultResponse;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결과 조회 캐시 miss fallback 테스트.
 *
 * <p>임베디드 Hazelcast의 캐시 유실(재시작)은 장애가 아니라 캐시 미스로 강등된다는
 * 설계 주장을 검증한다 — 캐시 miss 시 DB(원장) fallback 3분기.</p>
 */
@Import(TestChannelBinderConfiguration.class)
@SpringBootTest(properties = {
  "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
  "spring.datasource.driver-class-name=org.h2.Driver",
  // FirstInFlowTest와 동일 설정 유지 — 컨텍스트 재사용
  "spring.cloud.stream.bindings.applyIn-in-0.destination=apply-queue-test-inbox",
  "firstin.gate-multiplier=3"
})
class ResultServiceFallbackTest {

  private static final int STOCK = 100;

  @Autowired
  private EventService eventService;

  @Autowired
  private ResultService resultService;

  @Autowired
  private WinnerRepository winnerRepository;

  @Autowired
  private HazelcastInstance hz;

  @DisplayName("캐시_유실후_당첨자는_DB_fallback으로_WIN_반환_및_재캐싱")
  @Test
  void 캐시_유실후_당첨자는_DB_fallback으로_WIN_반환_및_재캐싱() {
    String eventId = "fb-win-event";
    eventService.create(eventId, STOCK);
    winnerRepository.batchInsert(eventId, List.of("winner-1"));

    // 재시작 모사: 결과 캐시 유실
    cache().remove(eventId + ":winner-1");

    ResultResponse response = resultService.getResult(eventId, "winner-1");

    assertThat(response.status()).isEqualTo(Status.WIN);
    assertThat(response.retryAfter()).as("WIN이면 retryAfter 없음").isNull();
    assertThat(cache().get(eventId + ":winner-1"))
      .as("fallback 결과가 캐시에 재적재")
      .isEqualTo("WIN");
  }

  @DisplayName("캐시_유실후_재고_소진이면_비당첨자에게_LOSE_반환_및_캐싱")
  @Test
  void 캐시_유실후_재고_소진이면_비당첨자에게_LOSE_반환_및_캐싱() {
    String eventId = "fb-lose-event";
    eventService.create(eventId, STOCK);
    winnerRepository.batchInsert(eventId, users(STOCK)); // 재고 전량 소진

    ResultResponse response = resultService.getResult(eventId, "not-a-winner");

    assertThat(response.status()).isEqualTo(Status.LOSE);
    assertThat(response.retryAfter()).as("LOSE면 retryAfter 없음").isNull();
    assertThat(cache().get(eventId + ":not-a-winner"))
      .as("확정된 낙첨 결과가 캐시에 적재")
      .isEqualTo("LOSE");
  }

  @DisplayName("판정_미완료면_PENDING과_retryAfter를_반환한다")
  @Test
  void 판정_미완료면_PENDING과_retryAfter를_반환한다() {
    String eventId = "fb-pending-event";
    eventService.create(eventId, STOCK);
    winnerRepository.batchInsert(eventId, users(10)); // 재고 미소진

    ResultResponse response = resultService.getResult(eventId, "still-waiting");

    assertThat(response.status()).isEqualTo(Status.PENDING);
    assertThat(response.retryAfter()).as("서버가 통제하는 폴링 간격").isEqualTo(3);
    assertThat(cache().get(eventId + ":still-waiting"))
      .as("미확정 결과는 캐싱하지 않음")
      .isNull();
  }

  private IMap<String, String> cache() {
    return hz.getMap("result-cache");
  }

  private List<String> users(int count) {
    return IntStream.range(0, count).mapToObj(i -> "user-" + i).toList();
  }
}
