const { chromium } = require("../.tools/ui-testing/node_modules/playwright");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const reference = "TPC-PAY-" + "a".repeat(32);
const payment = "https://sandbox.mercadopago.com.co/checkout/v1/redirect?pref_id=ejemplo";
(async () => {
  const browser = await chromium.launch({ channel: "chrome", headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1500 } });
    const errors = []; page.on("pageerror", error => errors.push(error.message));
    let creations = 0, checks = 0, fail = false;
    let receipt = { marca: "TecnoPC", referencia: reference, cliente: "Cliente de ejemplo", fecha: "2026-11-13T16:00:00Z", estado: "pendiente", numero_venta: null, subtotal: 2080000, iva: 395200, total: 2475200, items: [{ nombre: "Computador de escritorio (ejemplo)", cantidad: 1, precio: 2000000, importe: 2000000 }, { nombre: "Memoria USB (ejemplo)", cantidad: 2, precio: 40000, importe: 80000 }] };
    const test = async (name, fn) => { await fn(); checks++; console.log("OK " + name); };
    await page.route("**/api/public/productos", route => route.fulfill({ json: [{ id: 9001, nombre: "Computador de prueba", categoria: "pc", especificaciones: "Producto ficticio de prueba", stock: 3, precio: 2080000 }] }));
    await page.route("**/api/public/v2/pagos", route => { creations++; return route.fulfill({ status: 201, json: { referencia: reference, total: 2475200, estado: "pendiente", url_pago: payment } }); });
    await page.route("**/api/public/pagos/recibo", route => fail ? route.fulfill({ status: 503, json: { mensaje: "Consulta no disponible" } }) : route.fulfill({ json: receipt }));
    await page.goto("http://127.0.0.1:8082/#catalogo");
    await page.locator('#catalog-products [data-add="9001"]').click();
    await page.goto("http://127.0.0.1:8082/#compras");
    await page.locator('#checkout-form [name="cliente_nombre"]').fill("Cliente de ejemplo");
    await page.locator("#checkout-button").click();
    await page.locator("#order-receipt-paper .cr-products").waitFor();
    await test("Pedido muestra comprobante con marca, logo, detalle e importes guardados", async () => {
      assert.equal(await page.locator(".cr-header img").getAttribute("alt"), "TecnoPC");
      assert.equal(await page.locator(".cr-products tbody tr").count(), 2);
      assert.match(await page.locator(".cr-total").textContent(), /2\.475\.200,00/);
      assert.equal(await page.locator(".cr-header img").evaluate(img => img.complete && img.naturalWidth > 0), true);
    });
    await test("Pedido pendiente no se presenta como pagado y conserva acceso al pago", async () => {
      assert.match(await page.locator(".cr-status").textContent(), /Pendiente de pago/);
      assert.match(await page.locator(".cr-status").textContent(), /no están reservados/);
      assert.equal(await page.locator("#order-payment-link").getAttribute("href"), payment);
      assert.equal(await page.locator("#order-payment-link").getAttribute("rel"), "noopener noreferrer");
    });
    fs.mkdirSync("ui-previews", { recursive: true });
    await page.locator(".customer-receipt").screenshot({ path: "ui-previews/pedido-web-pendiente-ejemplo.png" });
    await test("Reabrir un comprobante no crea pedidos duplicados", async () => {
      await page.locator("#close-order-receipt").click(); await page.locator("#checkout-button").click();
      await page.locator(".cr-products").waitFor(); assert.equal(creations, 1);
    });
    await test("Solo la respuesta del servidor cambia el recibo a pago confirmado", async () => {
      receipt = { ...receipt, estado: "aprobado", numero_venta: "MP-EJEMPLO-001" };
      await page.locator("#refresh-order-receipt").click(); await page.locator(".cr-status.is-paid").waitFor();
      assert.match(await page.locator(".cr-document").textContent(), /RECIBO DE COMPRA/);
      assert.equal(await page.locator("#order-payment-link").isVisible(), false);
    });
    await page.locator(".customer-receipt").screenshot({ path: "ui-previews/pedido-web-pagado-ejemplo.png" });
    await test("Impresión muestra el recibo y oculta controles y tienda", async () => {
      await page.emulateMedia({ media: "print" });
      assert.equal(await page.locator(".receipt-actions").isVisible(), false);
      assert.equal(await page.locator("main").isVisible(), false);
      assert.equal(await page.locator(".customer-receipt").isVisible(), true);
      await page.pdf({ path: "ui-previews/pedido-web-pagado-ejemplo.pdf", printBackground: true, preferCSSPageSize: true });
      await page.emulateMedia({ media: "screen" });
    });
    await test("Comprobante móvil sin desbordamiento horizontal", async () => {
      await page.setViewportSize({ width: 390, height: 844 });
      assert.equal(await page.evaluate(() => { const d = document.querySelector("#order-receipt-dialog"); return d.scrollWidth <= d.clientWidth + 1 && document.documentElement.scrollWidth <= innerWidth; }), true);
      await page.screenshot({ path: "ui-previews/pedido-web-movil-ejemplo.png" });
    });
    await test("Fallos al actualizar no inventan estados ni recrean el pedido", async () => {
      await page.clock.install();
      fail = true; await page.locator("#refresh-order-receipt").click();
      await page.locator("#order-receipt-status").filter({ hasText: "Consulta no disponible" }).waitFor();
      assert.equal(creations, 1); assert.match(await page.locator(".cr-status").textContent(), /Pago confirmado/); fail = false;
      await page.clock.fastForward(5000);
    });
    await test("Texto de cliente y productos se escapa sin ejecutar HTML", async () => {
      receipt = { ...receipt, cliente: '<img src=x onerror="window.receiptXss=1">' };
      await page.locator("#refresh-order-receipt").click();
      await page.locator(".cr-customer").filter({ hasText: "<img" }).waitFor();
      assert.equal(await page.locator(".customer-receipt img").count(), 1); assert.equal(await page.evaluate(() => window.receiptXss), undefined);
    });
    await test("Recargar conserva únicamente la referencia y enlace en esta pestaña", async () => {
      await page.reload(); await page.locator("#resume-order").click(); await page.locator(".cr-products").waitFor();
      const stored = await page.evaluate(() => sessionStorage.getItem("tecnopc-order-receipt"));
      assert.equal(JSON.parse(stored).referencia, reference); assert.equal(stored.includes("cliente"), false); assert.equal(creations, 1);
    });
    await test("El estado de preparación listo se comunica al cliente sin mensaje manual", async () => {
      receipt = { ...receipt, cliente: "Cliente de ejemplo", preparacion: "listo" };
      await page.locator("#refresh-order-receipt").click();
      await page.locator(".cr-status").filter({ hasText: "Listo para recoger" }).waitFor();
      assert.match(await page.locator(".cr-status").textContent(), /Presenta la referencia/);
    });
    assert.deepEqual(errors, []); console.log(`${checks} comprobaciones del recibo web aprobadas. Todos los pedidos y pagos fueron simulados.`);
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exit(1); });
