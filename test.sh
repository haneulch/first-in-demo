# demo.sh — 재고 100개에 동시 요청 1000발
hey -n 1000 -c 200 -m POST http://localhost:8080/apply
curl http://localhost:8080/admin/stats   # 당첨 정확히 100명 확인