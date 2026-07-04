package com.firstindemo.apply;

import com.firstindemo.messaging.ApplyMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 접수 서비스.
 * 게이트를 통과한 요청을 큐에 발행하고, 접수 로그를 DB에 기록한다.
 */
@RequiredArgsConstructor
@Service
public class ApplyService {

  private static final Logger log = LoggerFactory.getLogger(ApplyService.class);

  private final AdmissionGate gate;
  private final StreamBridge streamBridge;
  private final JdbcTemplate jdbc;

  /**
   * 접수 처리.
   *
   * @param eventId 이벤트 ID
   * @param userId  사용자 ID
   * @return true이면 접수 완료(큐에 발행됨), false이면 마감
   */
  public boolean apply(String eventId, String userId) {
    if (!gate.tryPass(eventId)) {
      log.debug("게이트 차단: eventId={}, userId={}", eventId, userId);
      return false;
    }

    ApplyMessage message = new ApplyMessage(eventId, userId);
    streamBridge.send("applyOut-out-0", message);

    // 접수 로그 기록 (게이트 카운터 복구용)
    jdbc.update(
      "INSERT INTO apply_log (event_id, user_id) VALUES (?, ?)",
      eventId, userId
    );

    log.debug("접수 완료: eventId={}, userId={}", eventId, userId);
    return true;
  }
}
