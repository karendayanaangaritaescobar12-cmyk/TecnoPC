const { chromium, launchOptions, baseUrl, artifactPath } = require("./browser-test-utils.cjs");
const assert = require("node:assert/strict");
const fixture = {
  id: 1,
  nombre: "PC de prueba",
  categoria: "pc",
  precio: 1000000,
  stock: 3,
  especificaciones: "Equipo de prueba",
};
(async () => {
  const browser = await chromium.launch(launchOptions);
  let checks = 0;
  const test = async (name, fn) => {
    await fn();
    checks++;
    console.log("OK " + name);
  };
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    const errors = [];
    page.on("pageerror", (error) => errors.push(error.message));
    let reads = 0,
      fail = false,
      payments = 0;
    let releaseFirstRead;
    const firstRead = new Promise((resolve) => {
      releaseFirstRead = resolve;
    });
    await page.addInitScript(() => {
      window.cspFailures = [];
      document.addEventListener("securitypolicyviolation", (event) =>
        window.cspFailures.push(event.violatedDirective),
      );
    });
    await page.route("**/api/public/productos", async (route) => {
      reads++;
      if (reads === 1) await firstRead;
      else await new Promise((resolve) => setTimeout(resolve, 300));
      return route.fulfill(
        fail
          ? {
              status: 503,
              headers: { "Retry-After": "5" },
              json: { mensaje: "Datos temporalmente no disponibles" },
            }
          : { json: [fixture] },
      );
    });
    await page.route("**/api/public/v2/pagos", async (route) => {
      payments++;
      await new Promise((resolve) => setTimeout(resolve, 200));
      return route.fulfill({
        status: 503,
        headers: { "Retry-After": "5" },
        json: { mensaje: "Servicio temporalmente ocupado" },
      });
    });
    await page.goto(baseUrl + "/#catalogo", { waitUntil: "domcontentloaded" });
    await test("Veinte cargas simultáneas del catálogo comparten una sola lectura", async () => {
      await page.evaluate(async () => {
        const { loadCatalog } = await import("/js/app.js");
        window.catalogBatch = Promise.all(Array.from({ length: 20 }, () => loadCatalog()));
      });
      releaseFirstRead();
      await page.evaluate(() => window.catalogBatch);
      assert.equal(reads, 1);
      assert.equal(await page.locator("#catalog-products .product-card").count(), 1);
    });
    await page.locator('#catalog-products [data-add="1"]').click();
    await page.clock.install();
    await test("Una caída conserva el carrito y evita reintentos durante Retry-After", async () => {
      fail = true;
      await page.evaluate(async () => (await import("/js/app.js")).loadCatalog());
      const before = reads;
      await page.evaluate(async () => {
        const { loadCatalog } = await import("/js/app.js");
        return Promise.all(Array.from({ length: 20 }, () => loadCatalog()));
      });
      assert.equal(reads, before);
      assert.equal(
        await page.evaluate(() => JSON.parse(localStorage.getItem("tecnopc_cart_v2"))[0].id),
        1,
      );
      assert.match(await page.locator("#catalog-products").textContent(), /Espera/);
      await page.clock.fastForward(5000);
      fail = false;
      await page.evaluate(async () => (await import("/js/app.js")).loadCatalog());
      assert.equal(reads, before + 1);
      assert.equal(await page.locator("#catalog-products .product-card").count(), 1);
    });
    await test("Dos envíos simultáneos de checkout hacen un solo POST sin reintento automático", async () => {
      await page.goto(baseUrl + "/#compras");
      await page.locator('#checkout-form [name="cliente_nombre"]').fill("Cliente de prueba");
      await page.evaluate(() => {
        const form = document.querySelector("#checkout-form");
        form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
        form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
      });
      await page
        .locator("#checkout-message")
        .filter({ hasText: "Servicio temporalmente ocupado" })
        .waitFor();
      assert.equal(payments, 1);
      await page.clock.fastForward(6000);
      assert.equal(payments, 1);
      assert.equal(
        await page.evaluate(() => JSON.parse(localStorage.getItem("tecnopc_cart_v2")).length),
        1,
      );
    });
    await test("El catálogo limita el DOM a sesenta productos y permite avanzar de página", async () => {
      await page.route("**/api/public/productos", (route) =>
        route.fulfill({
          json: Array.from({ length: 145 }, (_, i) => ({
            ...fixture,
            id: i + 1,
            nombre: `Computador ${String(i + 1).padStart(3, "0")}`,
          })),
        }),
      );
      await page.goto(baseUrl + "/#catalogo");
      await page.reload();
      await page.locator("#catalog-products .product-card").first().waitFor();
      assert.equal(await page.locator("#catalog-products .product-card").count(), 60);
      await page.locator('#catalog-pagination [data-catalog-page="1"]').click();
      assert.equal(await page.locator("#catalog-products .product-card").count(), 60);
      assert.match(await page.locator("#catalog-products").textContent(), /Computador 061/);
      await page.locator('#catalog-pagination [data-catalog-page="2"]').click();
      assert.equal(await page.locator("#catalog-products .product-card").count(), 25);
      await page.screenshot({ path: artifactPath("resiliencia-catalogo.png") });
    });
    await test("El chat mantiene un historial visual acotado y CSP no bloquea la interfaz", async () => {
      await page.route("**/api/public/chatbot", (route) =>
        route.fulfill({ json: { respuesta: "Prueba" } }),
      );
      await page.locator("#chat-toggle").click();
      for (let i = 0; i < 45; i++) {
        await page.locator("#chat-input").fill("Prueba " + i);
        await page.locator("#chat-form button").click();
        await page.waitForFunction(() => !document.querySelector("#chat-form button").disabled);
      }
      assert.equal(await page.locator("#chat-messages > *").count(), 80);
      assert.deepEqual(await page.evaluate(() => window.cspFailures), []);
      assert.deepEqual(errors, []);
    });
    const privatePage = await browser.newPage();
    await privatePage.addInitScript(() => {
      Storage.prototype.setItem = () => {
        throw new DOMException("Storage unavailable", "SecurityError");
      };
    });
    await privatePage.route("**/api/public/productos", (route) =>
      route.fulfill({ json: [fixture] }),
    );
    await test("La tienda sigue funcionando cuando el navegador no permite guardar almacenamiento", async () => {
      await privatePage.goto(baseUrl + "/#catalogo");
      await privatePage.locator('#catalog-products [data-add="1"]').click();
      assert.equal(await privatePage.locator("#cart-count").textContent(), "1");
    });
    console.log(
      `${checks} comprobaciones de resiliencia del navegador aprobadas; pedidos simulados.`,
    );
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
