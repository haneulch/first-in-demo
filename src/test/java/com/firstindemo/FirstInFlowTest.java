package com.firstindemo;

import com.firstindemo.apply.ApplyService;
import com.firstindemo.judge.JudgeService;
import com.firstindemo.judge.WinnerRepository;
import com.firstindemo.messaging.ApplyMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 선착순 전체 흐름 통합 테스트.
 *
 * <p>10,000건 동시 접수 → 게이트 300명 통과 → 판정 → 당첨 100명 검증</p>
 *
 * <ul>
 *   <li>DB: H2 인메모리 (PostgreSQL 호환 모드)</li>
 *   <li>캐시: Hazelcast 임베디드</li>
 * </ul>
 */
@SpringBootTest(properties = {
  "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "firstin.stock=100",
  "firstin.gate-multiplier=3"
})
class FirstInFlowTest {

  @Autowired
  private ApplyService applyService;

  @Autowired
  private JudgeService judgeService;

  @Autowired
  private WinnerRepository winnerRepository;

  @Autowired
  private JdbcTemplate jdbc;

  @Test
  void 만건_접수시_게이트_300명_통과_당첨_100명() throws Exception {
    String eventId = "test-event";
    int totalRequests = 10_000;

    // ── 1단계: 10,000건 동시 접수 ──────────────────────────
    ExecutorService executor = Executors.newFixedThreadPool(50);
    CountDownLatch latch = new CountDownLatch(totalRequests);
    AtomicInteger acceptedCount = new AtomicInteger(0);
    List<String> acceptedUsers = Collections.synchronizedList(new ArrayList<>());

    for (int i = 0; i < totalRequests; i++) {
      final String userId = "user-" + i;
      executor.submit(() -> {
        try {
          if (applyService.apply(eventId, userId)) {
            acceptedCount.incrementAndGet();
            acceptedUsers.add(userId);
          }
        } finally {
          latch.countDown();
        }
      });
    }
    latch.await();
    executor.shutdown();

    // ── 2단계: 게이트 통과 = 정확히 300명 ──────────────────
    assertThat(acceptedCount.get())
      .as("게이트 통과자 수 (stock=%d × multiplier=%d)", 100, 3)
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
      .as("당첨자 수 (stock=%d)", 100)
      .isEqualTo(100);
  }
}
