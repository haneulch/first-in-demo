package com.firstindemo.event;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 이벤트 저장소.
 */
@RequiredArgsConstructor
@Repository
public class EventRepository {

  private final JdbcTemplate jdbc;

  /**
   * 이벤트 저장. event_id가 중복이면 {@link org.springframework.dao.DuplicateKeyException}.
   */
  public void insert(String eventId, int stock) {
    jdbc.update(
      "INSERT INTO event (event_id, stock) VALUES (?, ?)",
      eventId, stock
    );
  }

  /**
   * 이벤트의 당첨자 수(stock) 조회.
   */
  public Optional<Integer> findStock(String eventId) {
    return jdbc.queryForList(
        "SELECT stock FROM event WHERE event_id = ?",
        Integer.class,
        eventId
      ).stream()
      .findFirst();
  }
}
