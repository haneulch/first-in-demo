package com.firstindemo.judge;

import com.firstindemo.messaging.ApplyMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Spring Cloud Stream 함수형 컨슈머.
 * 배치 모드로 메시지를 소비하여 JudgeService에 위임한다.
 * Single Active Consumer로 컨슈머 하나만 활성화되어 순서가 보장된다.
 */
@RequiredArgsConstructor
@Component
public class ApplyConsumer {

  private static final Logger log = LoggerFactory.getLogger(ApplyConsumer.class);

  private final JudgeService judgeService;

  /**
   * Spring Cloud Stream 배치 컨슈머 빈.
   * application.yaml의 applyIn-in-0 바인딩에 연결된다.
   */
  @Bean
  public Consumer<List<ApplyMessage>> applyIn() {
    return messages -> {
      log.info("배치 수신: {}건", messages.size());
      judgeService.judgeBatch(messages);
    };
  }
}
