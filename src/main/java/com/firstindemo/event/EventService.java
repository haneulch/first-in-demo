package com.firstindemo.event;

import com.firstindemo.exception.BusinessException;
import com.firstindemo.exception.code.ErrorCode;
import com.firstindemo.infra.CacheName;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 이벤트 서비스.
 * 이벤트를 생성하고, 이벤트별 당첨자 수(stock)를 캐시 우선으로 조회한다.
 * 캐시 유실 시 DB(event 테이블)가 원장으로서 fallback을 제공한다.
 */
@Service
public class EventService {

  private final EventRepository eventRepository;
  private final IMap<String, Integer> stockCache;

  public EventService(EventRepository eventRepository, HazelcastInstance hz) {
    this.eventRepository = eventRepository;
    this.stockCache = CacheName.EVENT_STOCK.getMap(hz);
  }

  /**
   * 이벤트 생성.
   *
   * @throws BusinessException {@code DUPLICATE_EVENT} — eventId가 이미 존재하면
   */
  public Event create(String eventId, int stock) {
    try {
      eventRepository.insert(eventId, stock);
    } catch (DuplicateKeyException e) {
      throw new BusinessException(ErrorCode.DUPLICATE_EVENT, eventId);
    }
    stockCache.put(eventId, stock);
    return new Event(eventId, stock);
  }

  /**
   * 이벤트의 당첨자 수(stock)를 캐시 우선으로 조회한다.
   * 캐시 miss 시 DB를 조회하고 캐시에 재적재한다.
   */
  public Optional<Integer> findStock(String eventId) {
    Integer cached = stockCache.get(eventId);
    if (cached != null) {
      return Optional.of(cached);
    }
    Optional<Integer> stock = eventRepository.findStock(eventId);
    stock.ifPresent(s -> stockCache.put(eventId, s));
    return stock;
  }

  /**
   * 이벤트의 당첨자 수(stock)를 조회한다. 이벤트가 없으면 예외.
   *
   * @throws BusinessException {@code EVENT_NOT_FOUND} — 이벤트가 존재하지 않으면
   */
  public int getStockOrThrow(String eventId) {
    return findStock(eventId)
      .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND, eventId));
  }
}
