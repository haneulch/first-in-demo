package com.firstindemo.infra;

import com.firstindemo.apply.AdmissionGate;
import com.firstindemo.event.EventService;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게이트 카운터 복구 테스트.
 *
 * <p>임베디드 Hazelcast는 순수 인메모리라 재시작 시 게이트 카운터가 유실된다.
 * 기동 시 DB의 apply_log 건수로 카운터를 재구성하는 방어 로직을 검증한다.</p>
 */
@Import(TestChannelBinderConfiguration.class)
@SpringBootTest(properties = {
  "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
  "spring.datasource.driver-class-name=org.h2.Driver",
  // FirstInFlowTest와 동일 설정 유지 — 컨텍스트 재사용
  "spring.cloud.stream.bindings.applyIn-in-0.destination=apply-queue-test-inbox",
  "firstin.gate-multiplier=3"
})
class CounterRecoveryTest {

  private static final int STOCK = 100;
  private static final int GATE_LIMIT = 300; // stock 100 × multiplier 3

  @Autowired
  private EventService eventService;

  @Autowired
  private CounterRecovery counterRecovery;

  @Autowired
  private AdmissionGate gate;

  @Autowired
  private JdbcTemplate jdbc;

  @Autowired
  private HazelcastInstance hz;

  @DisplayName("재시작시_apply_log_건수로_게이트_카운터를_복구한다")
  @Test
  void 재시작시_apply_log_건수로_게이트_카운터를_복구한다() {
    String eventId = "recovery-full-event";
    eventService.create(eventId, STOCK);
    insertApplyLogs(eventId, GATE_LIMIT);

    // 재시작 모사: 인메모리 카운터 유실
    hz.getMap("gate-counter").remove(eventId);

    counterRecovery.run(null);

    assertThat(hz.getMap("gate-counter").get(eventId))
      .as("복구된 카운터 값")
      .isEqualTo((long) GATE_LIMIT);

    // 한도가 이미 찼으므로 복구 직후 게이트는 차단한다
    assertThat(gate.tryPass(eventId))
      .as("한도 소진 상태에서 게이트 차단")
      .isFalse();
  }

  @DisplayName("복구된_카운터가_한도_미만이면_잔여분만_통과시킨다")
  @Test
  void 복구된_카운터가_한도_미만이면_잔여분만_통과시킨다() {
    String eventId = "recovery-partial-event";
    eventService.create(eventId, STOCK);
    insertApplyLogs(eventId, GATE_LIMIT - 1);

    hz.getMap("gate-counter").remove(eventId);

    counterRecovery.run(null);

    // 잔여 1자리: 300번째 통과, 301번째 차단
    assertThat(gate.tryPass(eventId)).as("한도 내 마지막 접수").isTrue();
    assertThat(gate.tryPass(eventId)).as("한도 초과 접수").isFalse();
  }

  private void insertApplyLogs(String eventId, int count) {
    jdbc.batchUpdate(
      "INSERT INTO apply_log (event_id, user_id) VALUES (?, ?)",
      IntStream.range(0, count)
        .mapToObj(i -> new Object[]{eventId, "user-" + i})
        .toList()
    );
  }
}
