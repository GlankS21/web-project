#!/usr/bin/env bash
# End-to-end smoke test of a running stack (docker compose up -d --build --wait), through the website
# like a browser would, plus the API documentation on the gateway.
#
#   ADMIN_EMAIL=admin@shop.local ADMIN_PASSWORD=... scripts/smoke-test.sh
#
# It creates data (a category, a product, a customer, orders): run it against a local or test stack,
# never production. Online payments need PAYMENT_PROVIDER=mock (the default): they are paid with
# YooKassa test cards in the mock card form. Needs bash, curl and node.
set -u

BASE=${FRONTEND_URL:-http://localhost:3000}
GATEWAY=${GATEWAY_URL:-http://localhost:8080}
ADMIN_EMAIL=${ADMIN_EMAIL:?set ADMIN_EMAIL}
ADMIN_PASSWORD=${ADMIN_PASSWORD:?set ADMIN_PASSWORD}
ORIGIN="Origin: $BASE"
JSON='content-type: application/json'
DIR=$(mktemp -d)
trap 'rm -rf "$DIR"' EXIT
PASS=0
FAIL=0

check() { # name expected actual
  if [ "$2" = "$3" ]; then
    PASS=$((PASS + 1)); echo "  ok   $1"
  else
    FAIL=$((FAIL + 1)); echo "  FAIL $1: expected [$2] got [$3]"
  fi
}
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
# Value at a dotted path of the JSON read from stdin ("data.items.length"); empty when absent
json() {
  node -e '
    let s = "";
    process.stdin.on("data", (d) => (s += d)).on("end", () => {
      let v;
      try { v = process.argv[1].split(".").reduce((o, k) => (o == null ? undefined : o[k]), JSON.parse(s)); } catch {}
      console.log(v ?? "");
    });' "$1"
}
# Bodies with non-ASCII text are sent from files: curl on Windows re-encodes command-line arguments
body() { printf '%s' "$2" > "$DIR/$1.json"; echo "@$DIR/$1.json"; }
# Provider id of the mock payment of an order once it waits for the card (empty after 30 s)
wait_for_mock_payment() {
  local id=
  for _ in $(seq 1 30); do
    id=$(curl -s -b "$C" "$BASE/api/payments/orders/$1" | node -e '
      let s = "";
      process.stdin.on("data", (d) => (s += d)).on("end", () => {
        try {
          const p = JSON.parse(s).data;
          const charge = p.status === "PENDING" && p.confirmationToken
            ? p.transactions.findLast((t) => t.type === "CHARGE" && t.status === "PENDING") : null;
          console.log(charge?.providerId ?? "");
        } catch { console.log(""); }
      });')
    [ -n "$id" ] && break
    sleep 1
  done
  echo "$id"
}
# pay_with_card <mock payment> <card number> [3-D Secure code]
pay_with_card() {
  local code=${3:+",\"threeDSecureCode\":\"$3\""}
  curl -s -b "$C" -X POST -H "$ORIGIN" -H "$JSON" \
    -d "{\"cardNumber\":\"$2\",\"expiry\":\"12/30\",\"cvc\":\"123\"$code}" "$BASE/api/payments/mock/$1/pay"
}
# Status of an order once it reaches the expected one or is cancelled (30 s at most)
wait_for_order() {
  local status=
  for _ in $(seq 1 30); do
    status=$(curl -s -b "$C" "$BASE/api/orders/$1" | json data.status)
    { [ "$status" = "$2" ] || [ "$status" = CANCELLED ]; } && break
    sleep 1
  done
  echo "$status"
}

echo "== API documentation (gateway)"
for service in auth-service user-service product-service inventory-service cart-service order-service \
               payment-service notification-service; do
  check "$service OpenAPI" true "$(curl -s "$GATEWAY/docs/$service/v3/api-docs" | json openapi | grep -q '^3\.' && echo true)"
done
check "Swagger UI" 200 "$(code -L "$GATEWAY/swagger-ui.html")"

echo "== public pages"
check "home" 200 "$(code "$BASE/")"
check "product list" 200 "$(code "$BASE/products")"
check "health" 200 "$(code "$BASE/api/health")"
check "unknown product" 404 "$(code "$BASE/products/no-such-product")"
check "X-Frame-Options" DENY "$(curl -sI "$BASE/" | tr -d '\r' | awk -F': ' 'tolower($1)=="x-frame-options"{print $2}')"

echo "== signed out"
check "/cart redirects to login" "307 $BASE/login?next=%2Fcart" "$(curl -s -o /dev/null -w '%{http_code} %{redirect_url}' "$BASE/cart")"
check "/admin redirects" 307 "$(code "$BASE/admin")"
check "API needs a session" 401 "$(code "$BASE/api/cart")"
check "refresh is not relayed" 404 "$(code -X POST -H "$ORIGIN" -H "$JSON" -d '{}' "$BASE/api/auth/refresh")"
check "path traversal rejected" 404 "$(code --path-as-is "$BASE/api/products/../auth/refresh")"
check "cross-origin POST rejected" 403 "$(code -X POST -H 'Origin: https://evil.example' -H "$JSON" -d '{}' "$BASE/api/auth/login")"
check "cross-site POST rejected" 403 "$(code -X POST -H 'Sec-Fetch-Site: cross-site' -H "$JSON" -d '{}' "$BASE/api/auth/login")"

echo "== administrator"
A=$DIR/admin.jar
check "login" 200 "$(code -c "$A" -X POST -H "$ORIGIN" -H "$JSON" \
  --data-binary "$(body admin-login "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")" "$BASE/api/auth/login")"
check "HttpOnly session cookies" 2 "$(grep -c '^#HttpOnly_.*shop_[ar]t' "$A")"
for page in "" /orders /products /products/new /categories /inventory /accounts /notifications; do
  check "page /admin$page" 200 "$(code -b "$A" "$BASE/admin$page")"
done
SUFFIX=$(date +%s)
CATEGORY=$(curl -s -b "$A" -X POST -H "$ORIGIN" -H "$JSON" -d "{\"name\":\"Smoke $SUFFIX\"}" "$BASE/api/categories" | json data.id)
check "category created" true "$([ -n "$CATEGORY" ] && echo true)"
PRODUCT=$(curl -s -b "$A" -X POST -H "$ORIGIN" -H "$JSON" --data-binary "$(body product \
  "{\"sku\":\"SMOKE-$SUFFIX\",\"name\":\"Тестовые наушники $SUFFIX\",\"price\":19990,\"categoryId\":$CATEGORY,\"status\":\"ACTIVE\",\"images\":[{\"url\":\"https://example.com/a.jpg\"}]}")" \
  "$BASE/api/products")
PRODUCT_ID=$(echo "$PRODUCT" | json data.id)
SLUG=$(echo "$PRODUCT" | json data.slug)
check "product created" true "$([ -n "$PRODUCT_ID" ] && echo true)"
check "stock counted" 200 "$(code -b "$A" -X PUT -H "$ORIGIN" -H "$JSON" -d '{"quantityOnHand":5,"reason":"Smoke test"}' "$BASE/api/inventory/$PRODUCT_ID")"
check "product edit page" 200 "$(code -b "$A" "$BASE/admin/products/$PRODUCT_ID")"
check "stock page" 200 "$(code -b "$A" "$BASE/admin/inventory/$PRODUCT_ID")"

echo "== customer"
C=$DIR/customer.jar
check "register" 201 "$(code -c "$C" -X POST -H "$ORIGIN" -H "$JSON" --data-binary "$(body register \
  "{\"email\":\"smoke.$SUFFIX@example.com\",\"password\":\"Secret123\",\"firstName\":\"Иван\",\"lastName\":\"Петров\"}")" \
  "$BASE/api/auth/register")"
check "no admin pages" "307 $BASE/" "$(curl -s -b "$C" -o /dev/null -w '%{http_code} %{redirect_url}' "$BASE/admin")"
check "no admin API" 403 "$(code -b "$C" "$BASE/api/orders/admin")"
check "product page" 200 "$(code -b "$C" "$BASE/products/$SLUG")"
check "add to cart" 200 "$(code -b "$C" -X POST -H "$ORIGIN" -H "$JSON" -d "{\"productId\":$PRODUCT_ID,\"quantity\":2}" "$BASE/api/cart/items")"
check "cart page" 200 "$(code -b "$C" "$BASE/cart")"
check "checkout page" 200 "$(code -b "$C" "$BASE/checkout")"
ADDRESS='{"recipientName":"Иван Петров","phoneNumber":"+79161234567","addressLine1":"ул. Тверская, д. 1, кв. 10","city":"Москва","postalCode":"125009","countryCode":"RU"}'
check "address saved" 201 "$(code -b "$C" -X POST -H "$ORIGIN" -H "$JSON" --data-binary "$(body address "$ADDRESS")" "$BASE/api/users/me/addresses")"
KEY=$(node -e 'console.log(crypto.randomUUID())')
ORDER=$(body order "{\"items\":[{\"productId\":$PRODUCT_ID,\"quantity\":2}],\"shippingAddress\":$ADDRESS,\"paymentMethod\":\"ONLINE\"}")
ORDER_ID=$(curl -s -b "$C" -X POST -H "$ORIGIN" -H "$JSON" -H "Idempotency-Key: $KEY" --data-binary "$ORDER" "$BASE/api/orders" | json data.id)
check "order placed" 36 "${#ORDER_ID}"
check "same Idempotency-Key, same order" "$ORDER_ID" \
  "$(curl -s -b "$C" -X POST -H "$ORIGIN" -H "$JSON" -H "Idempotency-Key: $KEY" --data-binary "$ORDER" "$BASE/api/orders" | json data.id)"
check "payment page" 200 "$(code -b "$C" "$BASE/orders/$ORDER_ID/payment")"
MOCK_PAYMENT=$(wait_for_mock_payment "$ORDER_ID")
check "saga: stock reserved, payment waits for the card" true "$([ -n "$MOCK_PAYMENT" ] && echo true)"
check "declined test card" "FAILED insufficient_funds" "$(pay_with_card "$MOCK_PAYMENT" 5555555555554600 | json data.payment.status) \
$(curl -s -b "$C" "$BASE/api/payments/orders/$ORDER_ID" | json data.failureReason)"
check "order still awaits payment" AWAITING_PAYMENT "$(curl -s -b "$C" "$BASE/api/orders/$ORDER_ID" | json data.status)"
check "another card" PENDING "$(curl -s -b "$C" -X POST -H "$ORIGIN" "$BASE/api/payments/orders/$ORDER_ID/retry" | json data.status)"
MOCK_PAYMENT=$(wait_for_mock_payment "$ORDER_ID")
check "3-D Secure asked" true "$(pay_with_card "$MOCK_PAYMENT" 5555555555554477 | json data.threeDSecureRequired)"
check "3-D Secure confirmed" SUCCEEDED "$(pay_with_card "$MOCK_PAYMENT" 5555555555554477 123 | json data.payment.status)"
check "saga: order paid" PAID "$(wait_for_order "$ORDER_ID" PAID)"
check "two attempts recorded" 2 "$(curl -s -b "$C" "$BASE/api/payments/orders/$ORDER_ID" | json data.transactions.length)"
check "order page" 200 "$(code -b "$C" "$BASE/orders/$ORDER_ID?placed=1")"
sleep 2
check "ordered product left the cart" 0 "$(curl -s -b "$C" "$BASE/api/cart" | json data.items.length)"
for page in /orders /account /account/addresses /account/notifications /account/password; do
  check "page $page" 200 "$(code -b "$C" "$BASE$page")"
done
check "admin ships the order" SHIPPED "$(curl -s -b "$A" -X PATCH -H "$ORIGIN" -H "$JSON" \
  -d '{"status":"SHIPPED","note":"RU123"}' "$BASE/api/orders/admin/$ORDER_ID/status" | json data.status)"
check "admin order page" 200 "$(code -b "$A" "$BASE/admin/orders/$ORDER_ID")"

echo "== paid order cancelled by the administrator: refund and stock back"
AVAILABLE=$(curl -s "$BASE/api/inventory/availability?productIds=$PRODUCT_ID" | json data.0.availableQuantity)
KEY=$(node -e 'console.log(crypto.randomUUID())')
ORDER_ID=$(curl -s -b "$C" -X POST -H "$ORIGIN" -H "$JSON" -H "Idempotency-Key: $KEY" --data-binary "$ORDER" "$BASE/api/orders" | json data.id)
MOCK_PAYMENT=$(wait_for_mock_payment "$ORDER_ID")
check "paid" SUCCEEDED "$(pay_with_card "$MOCK_PAYMENT" 5555555555554444 | json data.payment.status)"
check "saga: second order paid" PAID "$(wait_for_order "$ORDER_ID" PAID)"
check "admin cancels" CANCELLED "$(curl -s -b "$A" -X POST -H "$ORIGIN" -H "$JSON" -d '{"reason":"Smoke test"}' \
  "$BASE/api/orders/$ORDER_ID/cancel" | json data.status)"
REFUND=
for _ in $(seq 1 30); do
  REFUND=$(curl -s -b "$C" "$BASE/api/payments/orders/$ORDER_ID" | json data.status)
  [ "$REFUND" = REFUNDED ] && break
  sleep 1
done
check "payment refunded" REFUNDED "$REFUND"
check "refund recorded on the order" true "$(sleep 2; [ -n "$(curl -s -b "$C" "$BASE/api/orders/$ORDER_ID" | json data.refundedAt)" ] && echo true)"
check "sold units back in stock" "$AVAILABLE" "$(curl -s "$BASE/api/inventory/availability?productIds=$PRODUCT_ID" | json data.0.availableQuantity)"

echo "== token refresh: expired access token, five concurrent requests"
grep -v shop_at "$C" > "$DIR/refresh.jar"
for i in 1 2 3 4 5; do
  (code -b "$DIR/refresh.jar" -c "$DIR/r$i.jar" "$BASE/account" > "$DIR/r$i.code") &
done
wait
check "all served" "200 200 200 200 200" "$(for i in 1 2 3 4 5; do printf '%s ' "$(cat "$DIR/r$i.code")"; done | sed 's/ $//')"
check "one rotated refresh token shared" 1 "$(cat "$DIR"/r?.jar | grep shop_rt | awk '{print $7}' | sort -u | wc -l | tr -d ' ')"
check "session not revoked" 200 "$(code -b "$DIR/r3.jar" "$BASE/api/users/me")"

echo "== logout"
check "logout" 200 "$(code -b "$C" -c "$C" -X POST -H "$ORIGIN" "$BASE/api/auth/logout")"
check "signed out" 401 "$(code -b "$C" "$BASE/api/cart")"

echo "== rate limit per browser address (needs TRUSTED_PROXY_SECRET, TRUSTED_PROXY_HOPS=0)"
# Documentation range (RFC 5737), a different address on each run
NET="198.51.100"
IP1="$NET.$((SUFFIX % 250 + 1))"
IP2="$NET.$(((SUFFIX + 1) % 250 + 1))"
LIMITED=0
for _ in $(seq 1 12); do
  [ "$(code -X POST -H "$ORIGIN" -H "X-Forwarded-For: $IP1" -H "$JSON" -d '{"email":"nobody@example.com","password":"Wrong1234"}' "$BASE/api/auth/login")" = 429 ] && LIMITED=1
done
check "one address limited" 1 "$LIMITED"
check "other address unaffected" 401 "$(code -X POST -H "$ORIGIN" -H "X-Forwarded-For: $IP2" -H "$JSON" -d '{"email":"nobody@example.com","password":"Wrong1234"}' "$BASE/api/auth/login")"

echo
echo "passed=$PASS failed=$FAIL"
[ "$FAIL" = 0 ]
