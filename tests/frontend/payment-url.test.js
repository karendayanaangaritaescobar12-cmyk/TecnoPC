import assert from "node:assert/strict";
import test from "node:test";
import { validPaymentUrl } from "../../frontend/js/payment-url.js";
import { esc, money, totalPrice } from "../../frontend/js/display.js";

test("real and sandbox payment hosts are accepted over HTTPS", () => {
  assert.equal(validPaymentUrl("https://www.mercadopago.com.co/checkout?pref_id=test"), true);
  assert.equal(validPaymentUrl("https://sandbox.mercadopago.com.co/checkout"), true);
});

for (const url of [
  "http://www.mercadopago.com.co/checkout",
  "https://www.mercadopago.com.co.attacker.example/checkout",
  "https://attacker.example/?next=https://www.mercadopago.com.co",
  "https://user:password@www.mercadopago.com.co/checkout",
  "javascript:alert(1)",
  "//www.mercadopago.com.co/checkout",
  "",
  null,
]) {
  test(`unsafe payment address is rejected: ${String(url)}`, () => {
    assert.equal(validPaymentUrl(url), false);
  });
}

test("product names are escaped before insertion into HTML", () => {
  assert.equal(
    esc("<img src=\"x\" onerror='alert(1)'>&"),
    "&lt;img src=&quot;x&quot; onerror=&#39;alert(1)&#39;&gt;&amp;",
  );
  assert.equal(esc(null), "");
});

test("display preserves Colombian money and existing tax calculation", () => {
  assert.equal(money(1_000), "$1.000");
  assert.equal(money("invalid"), "$0");
  assert.equal(totalPrice({ precio: 100 }), 119);
});
