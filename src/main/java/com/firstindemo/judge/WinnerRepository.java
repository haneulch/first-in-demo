package com.firstindemo.judge;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 당첨 원장 저장소.
 * batch INSERT + ON CONFLICT DO NOTHING으로 중복 방지.
 */
@RequiredArgsConstructor
@Repository
public class WinnerRepository {

  private final JdbcTemplate jdbc;

  /**
   * 배치 INSERT. (event_id, user_id) 유니크 제약에 의해 중복은 무시된다.
   *
   * @param eventId 이벤트 ID
   * @param userIds 당첨 대상 사용자 ID 목록
   * @return 실제 INSERT된 건수
   */
  public int batchInsert(String eventId, List<String> userIds) {
    if (userIds.isEmpty()) {
      return 0;
    }

    // 배치 INSERT with ON CONFLICT DO NOTHING
    int[] results = jdbc.batchUpdate(
      "INSERT INTO winner (event_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
      userIds.stream()
        .map(userId -> new Object[]{eventId, userId})
        .toList()
    );

    return (int) Arrays.stream(results).filter(r -> r > 0).count();
  }

  /**
   * 현재 당첨자 조회.
   */
  public List<String> getWinners(String eventId) {
    return jdbc.queryForList(
      "SELECT user_id FROM winner WHERE event_id = ?",
      String.class,
      eventId
    );
  }

  /**
   * 현재 당첨자 수 조회.
   */
  public long countWinners(String eventId) {
    Long count = jdbc.queryForObject(
      "SELECT COUNT(*) FROM winner WHERE event_id = ?",
      Long.class,
      eventId
    );
    return Optional.ofNullable(count).orElse(0L);
  }

  /**
   * 특정 사용자의 당첨 여부 조회.
   */
  public boolean isWinner(String eventId, String userId) {
    Long count = jdbc.queryForObject(
      "SELECT COUNT(*) FROM winner WHERE event_id = ? AND user_id = ?",
      Long.class,
      eventId, userId
    );
    return count != null && count > 0;
  }
}
