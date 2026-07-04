#!/usr/bin/env bash
# loadtest.sh — 시스템 부하 체크
#
# 측정 항목:
#   - 처리량(RPS), 응답 지연 분포(p50/p95/p99/max)
#   - 게이트 차단율(202 QUEUED vs 200 SOLD_OUT), 오류율
#   - 판정 완료 시간(부하 종료 → completed:true)
#   - 앱 컨테이너 CPU/메모리 피크, RabbitMQ 큐 적체 피크
#
# 사용법:
#   ./loadtest.sh                          # 기본: 10,000건 / 동시성 200 / stock 100
#   TOTAL=50000 CONCURRENCY=1000 STOCK=300 ./loadtest.sh
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TOTAL="${TOTAL:-10000}"
CONCURRENCY="${CONCURRENCY:-200}"
STOCK="${STOCK:-100}"
EVENT_ID="load-$(date +%s)"

WORK_DIR=$(mktemp -d)
MONITOR_PID=""
stop_monitor() {
  if [ -n "$MONITOR_PID" ]; then
    kill "$MONITOR_PID" 2>/dev/null || true
    wait "$MONITOR_PID" 2>/dev/null || true
    MONITOR_PID=""
  fi
}
trap 'stop_monitor; rm -rf "$WORK_DIR"' EXIT

now_ms() { perl -MTime::HiRes=time -e 'printf "%d", time()*1000'; }

echo "══════════════════════════════════════════════════"
echo " 부하 테스트: ${TOTAL}건 / 동시성 ${CONCURRENCY} / stock ${STOCK}"
echo "══════════════════════════════════════════════════"

# ── 0) 헬스 체크 ─────────────────────────────────────
if ! curl -sf "${BASE_URL}/actuator/health" >/dev/null 2>&1; then
  echo "❌ 앱 응답 없음 (${BASE_URL})"
  echo "   docker compose -f src/main/resources/compose.yaml up -d --build"
  exit 1
fi

# ── 1) 이벤트 생성 ───────────────────────────────────
CREATED=$(curl -s -X POST "${BASE_URL}/events" \
  -H 'Content-Type: application/json' \
  -d "{\"eventId\":\"${EVENT_ID}\",\"stock\":${STOCK}}")
echo "▶ 이벤트 생성: ${CREATED}"

# ── 2) 자원·큐 모니터링 시작 (1초 간격, 백그라운드) ──
APP_CONTAINER=$(docker ps --format '{{.Names}}' 2>/dev/null | grep -m1 'app' || true)
(
  while :; do
    if [ -n "$APP_CONTAINER" ]; then
      docker stats --no-stream --format '{{.CPUPerc}} {{.MemUsage}}' "$APP_CONTAINER" \
        2>/dev/null >> "$WORK_DIR/stats.log" || true
    fi
    curl -s -u guest:guest "http://localhost:15672/api/queues" 2>/dev/null \
      | grep -o '"messages":[0-9]*' | head -1 | cut -d: -f2 >> "$WORK_DIR/queue.log" || true
    sleep 1
  done
) &
MONITOR_PID=$!

# ── 3) 부하 발사 ─────────────────────────────────────
echo "▶ 부하 발사 중..."
START_MS=$(now_ms)
seq 1 "$TOTAL" | xargs -P "$CONCURRENCY" -I{} \
  curl -s -o /dev/null -w '%{time_total} %{http_code}\n' \
    -X POST "${BASE_URL}/events/${EVENT_ID}/apply?userId=user-{}" \
  > "$WORK_DIR/results.log"
END_MS=$(now_ms)
LOAD_MS=$((END_MS - START_MS))

# ── 4) 판정 완료 대기 ────────────────────────────────
echo "▶ 판정 완료 대기..."
JUDGE_START_MS=$(now_ms)
COMPLETED=false
for _ in $(seq 1 120); do
  if curl -s "${BASE_URL}/events/${EVENT_ID}/status" | grep -q '"completed":true'; then
    COMPLETED=true
    break
  fi
  sleep 1
done
JUDGE_MS=$(( $(now_ms) - JUDGE_START_MS ))

stop_monitor

# ── 5) 정합성 확인 ───────────────────────────────────
WINNERS=$(curl -s "${BASE_URL}/events/${EVENT_ID}/winners")
WINNER_COUNT=$(echo "$WINNERS" | grep -o '"userId"' | wc -l | tr -d ' ')
DUP_COUNT=$(echo "$WINNERS" | grep -o '"userId":"[^"]*"' | sort | uniq -d | wc -l | tr -d ' ')

# ── 6) 집계 ─────────────────────────────────────────
RESPONSES=$(wc -l < "$WORK_DIR/results.log" | tr -d ' ')
QUEUED=$(awk '$2 == 202' "$WORK_DIR/results.log" | wc -l | tr -d ' ')
SOLD_OUT=$(awk '$2 == 200' "$WORK_DIR/results.log" | wc -l | tr -d ' ')
ERRORS=$((RESPONSES - QUEUED - SOLD_OUT))

LATENCY=$(awk '{print $1}' "$WORK_DIR/results.log" | sort -n | awk '
  { a[NR] = $1; sum += $1 }
  END {
    if (NR == 0) { print "-"; exit }
    printf "avg %.0fms | p50 %.0fms | p95 %.0fms | p99 %.0fms | max %.0fms",
      sum/NR*1000, a[int(NR*0.50)]*1000, a[int(NR*0.95)]*1000, a[int(NR*0.99)]*1000, a[NR]*1000
  }')

PEAK_CPU=$(awk '{gsub(/%/,"",$1); if ($1+0 > m) m = $1+0} END {printf "%.1f%%", m}' "$WORK_DIR/stats.log" 2>/dev/null || echo "-")
PEAK_MEM=$(awk '{print $2}' "$WORK_DIR/stats.log" 2>/dev/null | sort -h | tail -1 || echo "-")
PEAK_QUEUE=$(sort -n "$WORK_DIR/queue.log" 2>/dev/null | tail -1 || echo "-")

RPS=$(( TOTAL * 1000 / (LOAD_MS > 0 ? LOAD_MS : 1) ))

echo ""
echo "══════════════ 결과 ══════════════"
echo " [처리량]"
echo "   접수 ${TOTAL}건 완료: $((LOAD_MS / 1000)).$((LOAD_MS % 1000 / 100))초 (약 ${RPS} RPS)"
echo "   판정 완료까지: $((JUDGE_MS / 1000)).$((JUDGE_MS % 1000 / 100))초 (completed=${COMPLETED})"
echo " [응답 지연]"
echo "   ${LATENCY}"
echo " [게이트]"
echo "   통과(202 QUEUED): ${QUEUED}건 / 차단(200 SOLD_OUT): ${SOLD_OUT}건 / 오류: ${ERRORS}건"
echo "   차단율: $(( SOLD_OUT * 100 / (RESPONSES > 0 ? RESPONSES : 1) ))%"
echo " [시스템]"
echo "   앱 컨테이너 피크: CPU ${PEAK_CPU} / MEM ${PEAK_MEM:-측정 안 됨}"
echo "   큐 적체 피크: ${PEAK_QUEUE:-측정 안 됨} 메시지"
echo " [정합성]"
echo "   당첨자: ${WINNER_COUNT}명 (기대 ${STOCK}) / 중복: ${DUP_COUNT}건 (기대 0)"
echo "═════════════════════════════════"

if [ "$ERRORS" -eq 0 ] && [ "$WINNER_COUNT" -eq "$STOCK" ] && [ "$DUP_COUNT" -eq 0 ] && [ "$COMPLETED" = true ]; then
  echo "✅ 성공: 오류 0건, 정확히 ${STOCK}명 당첨, 중복 0건"
else
  echo "❌ 실패"
  exit 1
fi
