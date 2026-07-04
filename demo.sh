#!/usr/bin/env bash
# demo.sh — 재고 100개 이벤트 생성 후 동시 1,000건 접수 → 당첨 정확히 100명, 중복 0건 확인
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
EVENT_ID="${1:-demo-$(date +%s)}"
STOCK=100
TOTAL=10000
CONCURRENCY=200

echo "▶ 이벤트 생성 (eventId=${EVENT_ID}, 당첨자 수=${STOCK})"
curl -s -X POST "${BASE_URL}/events" \
  -H 'Content-Type: application/json' \
  -d "{\"eventId\":\"${EVENT_ID}\",\"stock\":${STOCK}}"
echo

echo "▶ 동시 접수: ${TOTAL}건 (동시성 ${CONCURRENCY}, eventId=${EVENT_ID})"
seq 1 "$TOTAL" | xargs -P "$CONCURRENCY" -I {} \
  curl -s -o /dev/null -X POST "${BASE_URL}/events/${EVENT_ID}/apply?userId=user-{}"

echo "▶ 판정 완료 대기 (GET /events/${EVENT_ID}/status)"
for _ in $(seq 1 30); do
  STATUS=$(curl -s "${BASE_URL}/events/${EVENT_ID}/status")
  echo "  ${STATUS}"
  if echo "$STATUS" | grep -q '"completed":true'; then
    break
  fi
  sleep 1
done

WINNERS=$(curl -s "${BASE_URL}/events/${EVENT_ID}/winners")
WINNER_COUNT=$(echo "$WINNERS" | grep -o '"userId"' | wc -l | tr -d ' ')
DUP_COUNT=$(echo "$WINNERS" | grep -o '"userId":"[^"]*"' | sort | uniq -d | wc -l | tr -d ' ')

echo "▶ 결과"
echo "  당첨자 수: ${WINNER_COUNT} (기대: ${STOCK})"
echo "  중복 당첨: ${DUP_COUNT}건 (기대: 0)"

if [ "$WINNER_COUNT" -eq "$STOCK" ] && [ "$DUP_COUNT" -eq 0 ]; then
  echo "✅ 성공: 정확히 ${STOCK}명, 중복 0건"
else
  echo "❌ 실패"
  exit 1
fi
