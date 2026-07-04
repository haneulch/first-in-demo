package com.firstindemo;

import com.firstindemo.apply.ApplyService;
import com.firstindemo.code.Status;
import com.firstindemo.judge.JudgeService;
import com.firstindemo.judge.WinnerRepository;
import com.firstindemo.messaging.ApplyMessage;
import com.firstindemo.result.ResultService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 선착순 전체 흐름 통합 테스트.
 *
 * <p>10,000건 동시 접수 → 게이트 300명 통과 → 판정 → 당첨 100명 검증</p>
 *
 * <ul>
 *   <li>DB: H2 인메모리 (PostgreSQL 호환 모드)</li>
 *   <li>캐시: Hazelcast 임베디드</li>
 *   <li>메시징: Spring Cloud Stream 테스트 바인더 (브로커 불필요)</li>
 * </ul>
 */
@Import(TestChannelBinderConfiguration.class)
@SpringBootTest(properties = {
  "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
  "spring.datasource.driver-class-name=org.h2.Driver",
  // 테스트 바인더가 발행 메시지를 배치 컨슈머로 루프백하지 않도록 입력 destination 분리
  "spring.cloud.stream.bindings.applyIn-in-0.destination=apply-queue-test-inbox",
  "firstin.stock=100",
  "firstin.gate-multiplier=3"
})
class FirstInFlowTest {

  private static final int STOCK = 100;

  @Autowired
  private ApplyService applyService;

  @Autowired
  private JudgeService judgeService;

  @Autowired
  private WinnerRepository winnerRepository;

  @Autowired
  private ResultService resultService;

  @Autowired
  private JdbcTemplate jdbc;

  @DisplayName("만건_접수시_게이트_300명_통과_당첨_100명")
  @Test
  void 만건_접수시_게이트_300명_통과_당첨_100명() throws Exception {
    String eventId = "flow-event";
    int totalRequests = 10_000;

    // ── 1단계: 10,000건 동시 접수 ──────────────────────────
    ExecutorService executor = Executors.newFixedThreadPool(50);
    CountDownLatch latch = new CountDownLatch(totalRequests);
    AtomicInteger acceptedCount = new AtomicInteger(0);
    List<String> acceptedUsers = Collections.synchronizedList(new ArrayList<>());
    AtomicReference<Throwable> firstError = new AtomicReference<>();

    for (int i = 0; i < totalRequests; i++) {
      final String userId = "user-" + i;
      executor.submit(() -> {
        try {
          if (applyService.apply(eventId, userId)) {
            acceptedCount.incrementAndGet();
            acceptedUsers.add(userId);
          }
        } catch (Throwable t) {
          firstError.compareAndSet(null, t);
        } finally {
          latch.countDown();
        }
      });
    }
    latch.await();
    executor.shutdown();

    // 스레드 안에서 삼켜진 예외를 표면화한다
    assertThat(firstError.get())
      .as("접수 중 예외 없음")
      .isNull();

    // ── 2단계: 게이트 통과 = 정확히 300명 ──────────────────
    assertThat(acceptedCount.get())
      .as("게이트 통과자 수 (stock=%d × multiplier=%d)", STOCK, 3)
      .isEqualTo(300);

    Long applyLogCount = jdbc.queryForObject(
      "SELECT COUNT(*) FROM apply_log WHERE event_id = ?",
      Long.class, eventId
    );
    assertThat(applyLogCount)
      .as("접수 로그(apply_log) 건수")
      .isEqualTo(300);

    // ── 3단계: 게이트 통과한 사용자로 메시지 구성 ───────────
    List<ApplyMessage> messages = acceptedUsers.stream()
      .map(userId -> new ApplyMessage(eventId, userId))
      .toList();
    assertThat(messages)
      .as("판정 대상 메시지 수")
      .hasSize(300);

    // ── 4단계: 배치 판정 ────────────────────────────────
    judgeService.judgeBatch(messages);

    // ── 5단계: 당첨자 = 정확히 100명 ──────────────────────
    long winnerCount = winnerRepository.countWinners(eventId);
    assertThat(winnerCount)
      .as("당첨자 수 (stock=%d)", STOCK)
      .isEqualTo(STOCK);
  }

  @DisplayName("당첨자는_큐_도착_순서_기준_앞_100명이다")
  @Test
  void 당첨자는_큐_도착_순서_기준_앞_100명이다() {
    String eventId = "order-event";

    // 300건이 큐 도착 순서대로 판정된다
    judgeService.judgeBatch(messages(eventId, 0, 300));

    // 당첨자 수가 아니라 "누가" 당첨됐는지 검증 — 앞 100명과 정확히 일치해야 한다
    List<String> expectedWinners = IntStream.range(0, STOCK)
      .mapToObj(i -> "user-" + i)
      .toList();
    assertThat(winnerRepository.getWinners(eventId))
      .as("당첨자는 도착 순서 기준 앞 %d명", STOCK)
      .containsExactlyInAnyOrderElementsOf(expectedWinners);

    // 101번째 이후 도착자는 전원 낙첨
    assertThat(resultService.getResult(eventId, "user-" + STOCK).status())
      .isEqualTo(Status.LOSE);
  }

  @DisplayName("동일_메시지_재전달시_중복_당첨_없고_기당첨자_결과_유지")
  @Test
  void 동일_메시지_재전달시_중복_당첨_없고_기당첨자_결과_유지() {
    String eventId = "redelivery-event";
    List<ApplyMessage> batch = messages(eventId, 0, 300);

    judgeService.judgeBatch(batch);
    assertThat(winnerRepository.countWinners(eventId)).isEqualTo(STOCK);

    // 워커 재시작 시나리오: ack되지 않은 동일 배치가 재전달된다
    judgeService.judgeBatch(batch);

    assertThat(winnerRepository.countWinners(eventId))
      .as("재전달 후에도 당첨자 수 불변")
      .isEqualTo(STOCK);

    // 기당첨자의 결과가 LOSE로 덮어써지지 않는다
    for (String winner : winnerRepository.getWinners(eventId)) {
      assertThat(resultService.getResult(eventId, winner).status())
        .as("기당첨자 %s의 결과", winner)
        .isEqualTo(Status.WIN);
    }
  }

  @DisplayName("기당첨자_중복_메시지는_재고_슬롯을_소모하지_않는다")
  @Test
  void 기당첨자_중복_메시지는_재고_슬롯을_소모하지_않는다() {
    String eventId = "slot-event";

    // 1차 배치: user-0 ~ user-49 당첨 (재고 50 소진)
    judgeService.judgeBatch(messages(eventId, 0, 50));
    assertThat(winnerRepository.countWinners(eventId)).isEqualTo(50);

    // 2차 배치: 기당첨자 50명 재전달 + 신규 150명
    List<ApplyMessage> second = new ArrayList<>();
    second.addAll(messages(eventId, 0, 50));
    second.addAll(messages(eventId, 50, 200));
    judgeService.judgeBatch(second);

    // 기당첨자 재전달이 슬롯을 소모했다면 100명을 채우지 못한다
    assertThat(winnerRepository.countWinners(eventId))
      .as("남은 재고 50개가 신규 사용자에게 정확히 배분")
      .isEqualTo(STOCK);
  }

  @DisplayName("동일_사용자_연타시_1건만_유효")
  @Test
  void 동일_사용자_연타시_1건만_유효() {
    String eventId = "spam-event";

    // 연타 사용자 1명(5회) + 신규 199명 = 총 204건
    List<ApplyMessage> batch = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      batch.add(new ApplyMessage(eventId, "spammer"));
    }
    batch.addAll(messages(eventId, 0, 199));
    judgeService.judgeBatch(batch);

    // 연타 5건 중 1건만 유효 — 재고는 정확히 100명으로 채워진다
    assertThat(winnerRepository.countWinners(eventId)).isEqualTo(STOCK);

    long spammerRows = winnerRepository.getWinners(eventId).stream()
      .filter("spammer"::equals)
      .count();
    assertThat(spammerRows)
      .as("연타 사용자의 당첨 기록 수")
      .isEqualTo(1);
  }

  private List<ApplyMessage> messages(String eventId, int fromInclusive, int toExclusive) {
    return IntStream.range(fromInclusive, toExclusive)
      .mapToObj(i -> new ApplyMessage(eventId, "user-" + i))
      .toList();
  }
}
