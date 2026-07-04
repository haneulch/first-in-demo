package com.firstindemo.infra;

import com.hazelcast.core.HazelcastInstance;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 기동 시 DB의 접수 건수로 게이트 카운터를 복구한다.
 * 캐시 유실(재시작)에 대한 방어 로직.
 * 모든 이벤트에 대해 카운터를 복구한다.
 */
@RequiredArgsConstructor
@Component
public class CounterRecovery implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(CounterRecovery.class);

  private final JdbcTemplate jdbc;
  private final HazelcastInstance hz;

  @Override
  public void run(ApplicationArguments args) {
    List<Map<String, Object>> rows = jdbc.queryForList(
      "SELECT event_id, COUNT(*) AS cnt FROM apply_log GROUP BY event_id"
    );

    for (Map<String, Object> row : rows) {
      String eventId = (String) row.get("event_id");
      long count = ((Number) row.get("cnt")).longValue();
      CacheName.GATE_COUNTER.getMap(hz).put(eventId, count);
      log.info("게이트 카운터 복구 완료: eventId={}, count={}", eventId, count);
    }

    if (rows.isEmpty()) {
      log.info("복구할 게이트 카운터 없음");
    }
  }
}
