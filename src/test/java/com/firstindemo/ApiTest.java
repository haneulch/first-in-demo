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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API HTTP 계층 테스트.
 *
 * <p>이벤트 생성, 상태 코드, 응답 JSON 형태, userId 자동 생성을 검증한다.
 * 게이트 마감을 싸게 재현하기 위해 재고 2개 × 배수 1 이벤트를 사용한다.</p>
 */
@Import(TestChannelBinderConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
  "spring.datasource.url=jdbc:h2:mem:apidb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
  "spring.datasource.driver-class-name=org.h2.Driver",
  // 테스트 바인더가 발행 메시지를 배치 컨슈머로 루프백하지 않도록 입력 destination 분리
  "spring.cloud.stream.bindings.applyIn-in-0.destination=apply-queue-test-inbox",
  "firstin.gate-multiplier=1"
})
class ApiTest {

  private static final int STOCK = 2;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JudgeService judgeService;

  // ── 이벤트 생성 ─────────────────────────────────────────

  @DisplayName("이벤트_생성시_201과_지정한_당첨자_수를_반환한다")
  @Test
  void 이벤트_생성시_201과_지정한_당첨자_수를_반환한다() throws Exception {
    mockMvc.perform(post("/events")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"eventId\":\"api-create-event\",\"stock\":50}"))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.eventId").value("api-create-event"))
      .andExpect(jsonPath("$.stock").value(50));
  }

  @DisplayName("이벤트_생성시_eventId_생략하면_자동_생성된다")
  @Test
  void 이벤트_생성시_eventId_생략하면_자동_생성된다() throws Exception {
    mockMvc.perform(post("/events")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"stock\":10}"))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.eventId").isNotEmpty())
      .andExpect(jsonPath("$.stock").value(10));
  }

  @DisplayName("이벤트_중복_생성시_409")
  @Test
  void 이벤트_중복_생성시_409() throws Exception {
    createEvent("api-dup-event", STOCK);

    mockMvc.perform(post("/events")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"eventId\":\"api-dup-event\",\"stock\":5}"))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("DUPLICATE_EVENT"))
      .andExpect(jsonPath("$.message").isNotEmpty());
  }

  @DisplayName("이벤트_생성시_stock이_1_미만이면_400")
  @Test
  void 이벤트_생성시_stock이_1_미만이면_400() throws Exception {
    mockMvc.perform(post("/events")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"eventId\":\"api-invalid-event\",\"stock\":0}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_STOCK"));
  }

  @DisplayName("존재하지_않는_이벤트_접수시_404")
  @Test
  void 존재하지_않는_이벤트_접수시_404() throws Exception {
    mockMvc.perform(post("/events/{eventId}/apply", "no-such-event").param("userId", "u1"))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
  }

  @DisplayName("존재하지_않는_이벤트_상태_조회시_404")
  @Test
  void 존재하지_않는_이벤트_상태_조회시_404() throws Exception {
    mockMvc.perform(get("/events/{eventId}/status", "no-such-event"))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
  }

  // ── 접수 ───────────────────────────────────────────────

  @DisplayName("접수_게이트_통과시_202_QUEUED")
  @Test
  void 접수_게이트_통과시_202_QUEUED() throws Exception {
    createEvent("api-queued-event", STOCK);

    mockMvc.perform(post("/events/{eventId}/apply", "api-queued-event").param("userId", "u1"))
      .andExpect(status().isAccepted())
      .andExpect(jsonPath("$.status").value("QUEUED"));
  }

  @DisplayName("접수_게이트_마감시_200_SOLD_OUT")
  @Test
  void 접수_게이트_마감시_200_SOLD_OUT() throws Exception {
    String eventId = "api-soldout-event"; // 게이트 한도 = 2 × 1 = 2건
    createEvent(eventId, STOCK);

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
    createEvent("api-anon-event", STOCK);

    mockMvc.perform(post("/events/{eventId}/apply", "api-anon-event"))
      .andExpect(status().isAccepted())
      .andExpect(jsonPath("$.status").value("QUEUED"));
  }

  // ── 결과 조회 ───────────────────────────────────────────

  @DisplayName("결과_조회_판정전이면_PENDING과_retryAfter")
  @Test
  void 결과_조회_판정전이면_PENDING과_retryAfter() throws Exception {
    createEvent("api-pending-event", STOCK);

    mockMvc.perform(get("/events/{eventId}/result", "api-pending-event").param("userId", "nobody"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("PENDING"))
      .andExpect(jsonPath("$.retryAfter").value(3));
  }

  @DisplayName("결과_조회_당첨자는_WIN_retryAfter_없음")
  @Test
  void 결과_조회_당첨자는_WIN_retryAfter_없음() throws Exception {
    String eventId = "api-win-event";
    createEvent(eventId, STOCK);
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
    createEvent(eventId, STOCK);
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
    createEvent(eventId, STOCK);
    judgeService.judgeBatch(List.of(
      new ApplyMessage(eventId, "w1"),
      new ApplyMessage(eventId, "w2")
    ));

    mockMvc.perform(get("/events/{eventId}/status", eventId))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.eventId").value(eventId))
      .andExpect(jsonPath("$.stock").value(STOCK))
      .andExpect(jsonPath("$.winners").value(2))
      .andExpect(jsonPath("$.completed").value(true));
  }

  @DisplayName("당첨자_목록_조회")
  @Test
  void 당첨자_목록_조회() throws Exception {
    String eventId = "api-winners-event";
    createEvent(eventId, STOCK);
    judgeService.judgeBatch(List.of(
      new ApplyMessage(eventId, "w1"),
      new ApplyMessage(eventId, "w2")
    ));

    mockMvc.perform(get("/events/{eventId}/winners", eventId))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.length()").value(2))
      .andExpect(jsonPath("$[*].userId").exists());
  }

  private void createEvent(String eventId, int stock) throws Exception {
    mockMvc.perform(post("/events")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"eventId\":\"%s\",\"stock\":%d}".formatted(eventId, stock)))
      .andExpect(status().isCreated());
  }
}
