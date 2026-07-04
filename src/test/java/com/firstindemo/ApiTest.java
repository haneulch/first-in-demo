package com.firstindemo;

import com.firstindemo.judge.JudgeService;
import com.firstindemo.messaging.ApplyMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API HTTP 계층 테스트.
 *
 * <p>상태 코드, 응답 JSON 형태, userId 자동 생성을 검증한다.
 * 게이트 마감을 싸게 재현하기 위해 재고 2개 × 배수 1로 설정한다.</p>
 */
@Import(TestChannelBinderConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
  "spring.datasource.url=jdbc:h2:mem:apidb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
  "spring.datasource.driver-class-name=org.h2.Driver",
  // 테스트 바인더가 발행 메시지를 배치 컨슈머로 루프백하지 않도록 입력 destination 분리
  "spring.cloud.stream.bindings.applyIn-in-0.destination=apply-queue-test-inbox",
  "firstin.stock=2",
  "firstin.gate-multiplier=1"
})
class ApiTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JudgeService judgeService;

  @DisplayName("접수_게이트_통과시_202_QUEUED")
  @Test
  void 접수_게이트_통과시_202_QUEUED() throws Exception {
    mockMvc.perform(post("/events/{eventId}/apply", "api-queued-event").param("userId", "u1"))
      .andExpect(status().isAccepted())
      .andExpect(jsonPath("$.status").value("QUEUED"));
  }

  @DisplayName("접수_게이트_마감시_200_SOLD_OUT")
  @Test
  void 접수_게이트_마감시_200_SOLD_OUT() throws Exception {
    String eventId = "api-soldout-event"; // 게이트 한도 = 2 × 1 = 2건

    mockMvc.perform(post("/events/{eventId}/apply", eventId).param("userId", "u1"))
      .andExpect(status().isAccepted());
    mockMvc.perform(post("/events/{eventId}/apply", eventId).param("userId", "u2"))
      .andExpect(status().isAccepted());

    mockMvc.perform(post("/events/{eventId}/apply", eventId).param("userId", "u3"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("SOLD_OUT"));
  }

  @DisplayName("접수시_userId_생략하면_자동_생성되어_접수된다")
  @Test
  void 접수시_userId_생략하면_자동_생성되어_접수된다() throws Exception {
    mockMvc.perform(post("/events/{eventId}/apply", "api-anon-event"))
      .andExpect(status().isAccepted())
      .andExpect(jsonPath("$.status").value("QUEUED"));
  }

  @DisplayName("결과_조회_판정전이면_PENDING과_retryAfter")
  @Test
  void 결과_조회_판정전이면_PENDING과_retryAfter() throws Exception {
    mockMvc.perform(get("/events/{eventId}/result", "api-pending-event").param("userId", "nobody"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("PENDING"))
      .andExpect(jsonPath("$.retryAfter").value(3));
  }

  @DisplayName("결과_조회_당첨자는_WIN_retryAfter_없음")
  @Test
  void 결과_조회_당첨자는_WIN_retryAfter_없음() throws Exception {
    String eventId = "api-win-event";
    judgeService.judgeBatch(List.of(new ApplyMessage(eventId, "w1")));

    mockMvc.perform(get("/events/{eventId}/result", eventId).param("userId", "w1"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("WIN"))
      .andExpect(jsonPath("$.retryAfter").doesNotExist());
  }

  @DisplayName("결과_조회_낙첨자는_LOSE_retryAfter_없음")
  @Test
  void 결과_조회_낙첨자는_LOSE_retryAfter_없음() throws Exception {
    String eventId = "api-lose-event"; // 재고 2 → 세 번째 메시지는 낙첨
    judgeService.judgeBatch(List.of(
      new ApplyMessage(eventId, "w1"),
      new ApplyMessage(eventId, "w2"),
      new ApplyMessage(eventId, "l1")
    ));

    mockMvc.perform(get("/events/{eventId}/result", eventId).param("userId", "l1"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("LOSE"))
      .andExpect(jsonPath("$.retryAfter").doesNotExist());
  }

  @DisplayName("이벤트_상태_조회_판정_완료_여부와_당첨자_수")
  @Test
  void 이벤트_상태_조회_판정_완료_여부와_당첨자_수() throws Exception {
    String eventId = "api-status-event";
    judgeService.judgeBatch(List.of(
      new ApplyMessage(eventId, "w1"),
      new ApplyMessage(eventId, "w2")
    ));

    mockMvc.perform(get("/events/{eventId}/status", eventId))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.eventId").value(eventId))
      .andExpect(jsonPath("$.stock").value(2))
      .andExpect(jsonPath("$.winners").value(2))
      .andExpect(jsonPath("$.completed").value(true));
  }

  @DisplayName("당첨자_목록_조회")
  @Test
  void 당첨자_목록_조회() throws Exception {
    String eventId = "api-winners-event";
    judgeService.judgeBatch(List.of(
      new ApplyMessage(eventId, "w1"),
      new ApplyMessage(eventId, "w2")
    ));

    mockMvc.perform(get("/events/{eventId}/winners", eventId))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.length()").value(2))
      .andExpect(jsonPath("$[*].userId").exists());
  }
}
