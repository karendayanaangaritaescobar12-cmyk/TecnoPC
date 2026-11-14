import assert from "node:assert/strict";
import test from "node:test";
import { createApiClient } from "../../frontend/js/api.js";

const json = (data, status = 200, headers = {}) =>
  new Response(JSON.stringify(data), { status, headers });

test("shared client sends JSON and preserves checkout idempotency headers", async () => {
  let calls = 0;
  const api = createApiClient({
    fetchRequest: async (url, options) => {
      calls++;
      assert.equal(url, "/api/public/v2/pagos");
      assert.equal(options.method, "POST");
      assert.equal(options.headers["Content-Type"], "application/json");
      assert.equal(options.headers["Idempotency-Key"], "same-attempt");
      assert.equal(options.body, '{"items":[]}');
      return json({ referencia: "test-only" });
    },
  });
  assert.deepEqual(
    await api("v2/pagos", {
      method: "POST",
      headers: { "Idempotency-Key": "same-attempt" },
      body: '{"items":[]}',
    }),
    { referencia: "test-only" },
  );
  assert.equal(calls, 1);
});

test("simultaneous catalog reads share a request but later reads refresh", async () => {
  let calls = 0;
  let release;
  const gate = new Promise((resolve) => {
    release = resolve;
  });
  const api = createApiClient({
    fetchRequest: async () => {
      calls++;
      await gate;
      return json([{ id: calls }]);
    },
  });
  const first = api("productos");
  const second = api("productos");
  assert.equal(calls, 1);
  release();
  assert.deepEqual(await first, [{ id: 1 }]);
  assert.deepEqual(await second, [{ id: 1 }]);
  assert.deepEqual(await api("productos"), [{ id: 2 }]);
  assert.equal(calls, 2);
});

test("checkout POSTs are not combined or retried automatically", async () => {
  let calls = 0;
  const api = createApiClient({
    fetchRequest: async () => {
      calls++;
      return json({ ok: true });
    },
  });
  await Promise.all([api("v2/pagos", { method: "POST" }), api("v2/pagos", { method: "POST" })]);
  assert.equal(calls, 2);
});

for (const status of [429, 503]) {
  test(`HTTP ${status} respects Retry-After and recovers without affecting other routes`, async () => {
    let now = 1_000;
    let calls = 0;
    const api = createApiClient({
      now: () => now,
      fetchRequest: async () => {
        calls++;
        return calls === 1
          ? json({ mensaje: "Ocupado" }, status, { "Retry-After": "2" })
          : json([]);
      },
    });
    await assert.rejects(api("productos"), /Espera 2 segundos/);
    await assert.rejects(api("productos"), /Espera 2 segundos/);
    assert.equal(calls, 1);
    await api("seguimiento", { method: "POST" });
    now = 3_000;
    assert.deepEqual(await api("productos"), []);
    assert.equal(calls, 3);
  });
}

test("server errors retain their status for checkout recovery", async () => {
  const api = createApiClient({ fetchRequest: async () => json({ mensaje: "Conflicto" }, 409) });
  await assert.rejects(api("v2/pagos", { method: "POST" }), (error) => {
    assert.equal(error.status, 409);
    assert.equal(error.message, "Conflicto");
    return true;
  });
});

test("invalid JSON does not pass as a successful response", async () => {
  const api = createApiClient({ fetchRequest: async () => new Response("<html>error</html>") });
  await assert.rejects(api("productos"), /respuesta válida/);
});

test("failed catalog reads do not remain stuck as pending requests", async () => {
  let calls = 0;
  const api = createApiClient({
    fetchRequest: async () => {
      if (++calls === 1) throw new TypeError("offline");
      return json([]);
    },
  });
  await assert.rejects(api("productos"), /Comprueba tu conexión/);
  assert.deepEqual(await api("productos"), []);
  assert.equal(calls, 2);
});

test("network failure after a POST warns that its outcome is unknown", async () => {
  const api = createApiClient({
    fetchRequest: async () => {
      throw new TypeError("offline");
    },
  });
  await assert.rejects(api("v2/pagos", { method: "POST" }), /Conserva el intento/);
});

for (const method of ["GET", "POST"]) {
  test(`${method} timeout cancels fetch and explains recovery`, async () => {
    const api = createApiClient({
      fetchRequest: async (_url, { signal }) =>
        new Promise((_resolve, reject) => {
          signal.addEventListener(
            "abort",
            () => reject(new DOMException("aborted", "AbortError")),
            { once: true },
          );
        }),
    });
    await assert.rejects(
      api("productos", { method }, 5),
      method === "GET" ? /Conservamos tu carrito/ : /Comprueba el estado/,
    );
  });
}
