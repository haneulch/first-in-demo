// k6 부하 테스트 — 선착순 접수 API
//
// 시나리오: 이벤트 생성(setup) → 목표 RPS로 접수 폭주(constant-arrival-rate)
//          → 판정 완료 대기 + 정합성 검증(teardown)
//
// 측정: RPS 달성률, 지연 분포(p90/p95/p99), 게이트 차단율, 오류율, 판정 완료 시간
//
// 실행:
//   k6 run k6/loadtest.js
//   k6 run -e RATE=2000 -e DURATION=30s -e STOCK=300 k6/loadtest.js
//   k6 run -e BASE_URL=http://other-host:8080 k6/loadtest.js
import http from 'k6/http';
import exec from 'k6/execution';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const STOCK = Number(__ENV.STOCK || 100);
const RATE = Number(__ENV.RATE || 1000);        // 목표 초당 요청 수
const DURATION = __ENV.DURATION || '10s';

export const options = {
  scenarios: {
    apply_burst: {
      executor: 'constant-arrival-rate',        // VU 수가 아니라 도착률을 고정 — 서버가 느려져도 RPS 유지
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Number(__ENV.VUS || 200),
      maxVUs: Number(__ENV.MAX_VUS || 2000),
    },
  },
  thresholds: {
    http_req_failed: ['rate==0'],               // 네트워크/5xx 오류 0
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    apply_unexpected: ['count==0'],             // 200/202 외 응답 0
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const queued = new Counter('apply_queued');           // 202 — 게이트 통과
const soldOut = new Counter('apply_sold_out');        // 200 — 게이트 차단
const unexpected = new Counter('apply_unexpected');   // 그 외
const gateBlockRate = new Rate('gate_block_rate');
const judgeDrainTime = new Trend('judge_drain_time', true); // 판정 완료 대기 시간(ms)

export function setup() {
  const eventId = `k6-${Date.now()}`;
  const res = http.post(
    `${BASE_URL}/events`,
    JSON.stringify({ eventId, stock: STOCK }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (res.status !== 201) {
    throw new Error(`이벤트 생성 실패: ${res.status} ${res.body}`);
  }
  console.log(`이벤트 생성: ${eventId} (stock=${STOCK})`);
  return { eventId };
}

export default function (data) {
  // 전체 테스트에서 유일한 userId — 중복 접수는 별도 시나리오에서 다룬다
  const userId = `user-${exec.scenario.iterationInTest}`;
  const res = http.post(`${BASE_URL}/events/${data.eventId}/apply?userId=${userId}`);

  if (res.status === 202) {
    queued.add(1);
    gateBlockRate.add(false);
  } else if (res.status === 200) {
    soldOut.add(1);
    gateBlockRate.add(true);
  } else {
    unexpected.add(1);
  }

  check(res, { 'apply는 200/202': (r) => r.status === 200 || r.status === 202 });
}

export function teardown(data) {
  // 판정 완료 대기
  const start = Date.now();
  let completed = false;
  for (let i = 0; i < 120; i++) {
    const res = http.get(`${BASE_URL}/events/${data.eventId}/status`);
    if (res.status === 200 && res.json('completed') === true) {
      completed = true;
      break;
    }
    sleep(1);
  }
  const drainMs = Date.now() - start;
  judgeDrainTime.add(drainMs);

  // 정합성 검증: 당첨자 수 == stock, 중복 0
  const winners = http.get(`${BASE_URL}/events/${data.eventId}/winners`).json();
  const ids = winners.map((w) => w.userId);
  const uniqueCount = new Set(ids).size;

  console.log(`판정 완료: ${completed} (대기 ${drainMs}ms)`);
  console.log(`당첨자: ${ids.length}명 (기대 ${STOCK}) / 중복: ${ids.length - uniqueCount}건 (기대 0)`);

  if (!completed) throw new Error('판정이 제한 시간 안에 끝나지 않음');
  if (ids.length !== STOCK) throw new Error(`당첨자 수 불일치: ${ids.length} != ${STOCK}`);
  if (uniqueCount !== ids.length) throw new Error(`중복 당첨 ${ids.length - uniqueCount}건`);
}
