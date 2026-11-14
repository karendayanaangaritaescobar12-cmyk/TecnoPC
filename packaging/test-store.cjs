const { chromium, launchOptions, baseUrl, artifactPath } = require("./browser-test-utils.cjs");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const fixtures = [
  {
    id: 1,
    nombre: "Portátil ASUS de prueba",
    categoria: "laptop",
    especificaciones: "16 GB RAM · SSD 512 GB",
    precio: 2000000,
    stock: 5,
  },
  {
    id: 2,
    nombre: "PC TecnoPC de prueba",
    categoria: "pc",
    especificaciones: "Equipo de escritorio · 32 GB RAM",
    precio: 3000000,
    stock: 3,
  },
  {
    id: 3,
    nombre: "Procesador AMD de prueba",
    categoria: "cpu",
    especificaciones: "Socket AM5 · 65 W",
    precio: 900000,
    stock: 9,
  },
  {
    id: 4,
    nombre: "Placa ASUS de prueba",
    categoria: "mb",
    especificaciones: "Socket AM4 · DDR4",
    precio: 600000,
    stock: 4,
  },
  {
    id: 5,
    nombre: "Memoria Kingston de prueba",
    categoria: "ram",
    especificaciones: "DDR5 · 16 GB",
    precio: 250000,
    stock: 8,
  },
  {
    id: 6,
    nombre: "SSD Samsung de prueba",
    categoria: "ssd",
    especificaciones: "1 TB NVMe",
    precio: 300000,
    stock: 8,
  },
  {
    id: 7,
    nombre: "Tarjeta gráfica MSI de prueba",
    categoria: "gpu",
    especificaciones: "8 GB GDDR6",
    precio: 1800000,
    stock: 2,
  },
  {
    id: 8,
    nombre: "Fuente Corsair de prueba",
    categoria: "psu",
    especificaciones: "650 W",
    precio: 350000,
    stock: 4,
  },
  {
    id: 9,
    nombre: "Gabinete de prueba",
    categoria: "case",
    especificaciones: "ATX",
    precio: 300000,
    stock: 4,
  },
  {
    id: 10,
    nombre: "Portátil Lenovo agotado",
    categoria: "laptop",
    especificaciones: "8 GB RAM",
    precio: 1200000,
    stock: 0,
  },
];
(async () => {
  const browser = await chromium.launch(launchOptions);
  const errors = [];
  let checks = 0;
  const test = async (name, fn) => {
    await fn();
    checks++;
    console.log("OK " + name);
  };
  const dir = path.dirname(artifactPath("placeholder"));
  fs.mkdirSync(dir, { recursive: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1050 } });
  page.on("pageerror", (error) => errors.push(error.message));
  await test("Carga inicial y estado vacío o desconectado sin desbordamiento", async () => {
    await page.goto(baseUrl + "/");
    await page.locator("#featured-products .empty-state").waitFor();
    assert.equal(
      await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
      true,
    );
    await page.screenshot({ path: path.join(dir, "tienda-inicio.png"), fullPage: true });
  });
  await page.route("**/api/public/productos", (route) => route.fulfill({ json: fixtures }));
  const posts = [];
  await page.route("**/api/public/solicitudes", async (route) => {
    posts.push(route.request().postDataJSON());
    await route.fulfill({ status: 201, json: { id: 42, mensaje: "Solicitud registrada" } });
  });
  await page.route("**/api/public/seguimiento", (route) =>
    route.fulfill({
      json: {
        codigo: "TPC-2026-ABCDEFGH2345",
        equipo: "Portátil de prueba",
        estado: "en_reparacion",
        fechaIngreso: "2026-11-10T12:00:00Z",
        ultimaActualizacion: "2026-11-13T12:00:00Z",
        historial: [
          { evento: "recibido", creado_en: "2026-11-10T12:00:00Z" },
          { evento: "diagnostico", creado_en: "2026-11-11T12:00:00Z" },
          { evento: "en_reparacion", creado_en: "2026-11-13T12:00:00Z" },
        ],
      },
    }),
  );
  await page.route("**/api/public/chatbot", (route) => {
    posts.push(route.request().postDataJSON());
    return route.fulfill({
      json: {
        respuesta: "Te ayudo a configurar tu PC.",
        accion: { texto: "Ir al configurador", destino: "#armar" },
      },
    });
  });
  await page.route("**/api/public/v2/pagos", (route) =>
    route.fulfill({ status: 503, json: { mensaje: "Pasarela no configurada para la prueba" } }),
  );
  await page.reload();
  await page.locator("#featured-products .product-card").first().waitFor();
  await test("Categoría abre y filtra catálogo", async () => {
    await page.locator("[data-category=laptop]").click();
    await page.locator("#catalogo").waitFor({ state: "visible" });
    assert.equal(await page.locator("#catalog-products .product-card").count(), 2);
  });
  await test("Disponibilidad excluye agotados", async () => {
    await page.locator("#in-stock").check();
    assert.equal(await page.locator("#catalog-products .product-card").count(), 1);
  });
  await test("Búsqueda global funciona", async () => {
    await page.locator("#clear-filters").click();
    await page.locator("#search").fill("Samsung");
    await page.locator("#global-search button").click();
    assert.equal(await page.locator("#catalog-products .product-card").count(), 1);
    await page.locator("#clear-filters").click();
  });
  await test("Ordenación de precios funciona", async () => {
    await page.locator("#sort").selectOption("precio-asc");
    assert.match(
      await page.locator("#catalog-products .product-card h3").first().textContent(),
      /Kingston/,
    );
  });
  await page.screenshot({ path: path.join(dir, "catalogo-muestra-pruebas.png"), fullPage: true });
  await test("Carrito suma unidades y cantidades", async () => {
    await page.locator('#catalog-products [data-add="1"]').click();
    await page.locator('#catalog-products [data-add="1"]').click();
    assert.equal(await page.locator("#cart-count").textContent(), "2");
    await page.locator(".cart-link").click();
    await page.locator("#compras").waitFor({ state: "visible" });
    assert.equal(await page.locator("#cart-items .cart-item").count(), 1);
    assert.equal(await page.locator(".quantity-control span").textContent(), "2");
  });
  await test("Checkout muestra error sin confirmar pagos ficticios", async () => {
    await page.locator("#checkout-form input[name=cliente_nombre]").fill("Cliente de prueba");
    await page.locator("#checkout-button").click();
    await page.getByText(/Pasarela no configurada para la prueba/).waitFor();
    assert.equal(await page.locator("#checkout-button").isDisabled(), false);
  });
  await test("Configurador detecta incompatibilidades indicadas", async () => {
    await page.goto(baseUrl + "/#armar");
    await page.locator("[data-part=cpu]").selectOption("3");
    await page.locator("[data-part=mb]").selectOption("4");
    await page.locator("[data-part=ram]").selectOption("5");
    assert.match(await page.locator("#compatibility").textContent(), /socket/);
    assert.match(await page.locator("#compatibility").textContent(), /DDR/);
  });
  await test("Cotización conserva componentes y envía datos correctos", async () => {
    await page.locator("#quote-form [name=nombre]").fill("Ana de prueba");
    await page.locator("#quote-form [name=telefono]").fill("3001234567");
    await page.locator("#quote-form button[type=submit]").click();
    await page.locator("#quote-message.success").waitFor();
    assert.equal(posts.at(-1).tipo, "cotizacion");
    assert.match(posts.at(-1).detalle, /AMD/);
  });
  await page.screenshot({
    path: path.join(dir, "configurador-muestra-pruebas.png"),
    fullPage: true,
  });
  await test("Solicitud valida consentimiento sin destinatario", async () => {
    await page.goto(baseUrl + "/#servicio");
    await page.locator("#service-form [name=nombre]").fill("Ana de prueba");
    await page.locator("#service-form [name=telefono]").fill("3001234567");
    await page.locator("#service-form [name=descripcion]").fill("El equipo no enciende");
    await page.locator("#service-form summary").click();
    await page.locator("#service-form [name=avisos_correo]").check();
    await page.locator("#service-form button[type=submit]").click();
    await page.getByText("Indica tu correo para autorizar los avisos.", { exact: true }).waitFor();
  });
  await test("Solicitud de servicio envía canales autorizados", async () => {
    await page.locator("#service-form [name=aviso_correo]").fill("cliente@example.com");
    await page.locator("#service-form button[type=submit]").click();
    await page.locator("#service-message.success").waitFor();
    assert.equal(posts.at(-1).contacto.avisosCorreo, true);
    assert.equal(posts.at(-1).contacto.correo, "cliente@example.com");
  });
  await test("Seguimiento mantiene código seguro y etapas reales", async () => {
    await page.goto(baseUrl + "/#seguimiento");
    await page.locator("#tracking-code").fill("TPC-2026-ABCDEFGH2345");
    await page.locator("#tracking-form button").click();
    await page.locator(".timeline-step.current").waitFor();
    assert.equal(
      (await page.locator(".timeline-step.current").textContent()).includes("En reparación"),
      true,
    );
  });
  await page.screenshot({
    path: path.join(dir, "seguimiento-muestra-pruebas.png"),
    fullPage: true,
  });
  await test("Chat abre y navega al configurador", async () => {
    await page.locator("#chat-toggle").click();
    await page.locator("#chat-input").fill("Quiero armar una PC");
    await page.locator("#chat-form button").click();
    await page.getByRole("button", { name: "Ir al configurador", exact: true }).click();
    await page.locator("#armar").waitFor({ state: "visible" });
    await page.locator("#chat-panel").waitFor({ state: "hidden" });
  });
  await test("Chat conserva contexto de recomendaciones", async () => {
    await page.locator("#chat-toggle").click();
    await page.locator("#chat-input").fill("Para jugar con 2 millones");
    await page.locator("#chat-form button").click();
    await page.locator("#chat-form button").waitFor({ state: "visible" });
    await page.waitForFunction(() => !document.querySelector("#chat-form button").disabled);
    assert.deepEqual(posts.at(-1).historial, ["Quiero armar una PC"]);
    assert.equal(posts.at(-1).mensaje, "Para jugar con 2 millones");
    await page.locator("#chat-close").click();
  });
  await test("Promociones no inventa descuentos", async () => {
    await page.goto(baseUrl + "/#promociones");
    await page.getByRole("heading", { name: "No hay promociones publicadas por ahora" }).waitFor();
  });
  await test("Vista móvil sin desbordamiento en todas las secciones", async () => {
    await page.setViewportSize({ width: 390, height: 844 });
    for (const view of [
      "inicio",
      "catalogo",
      "armar",
      "servicio",
      "seguimiento",
      "compras",
      "promociones",
    ]) {
      await page.goto(baseUrl + "/#" + view);
      await page.waitForTimeout(150);
      assert.equal(
        await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
        true,
        view,
      );
    }
    await page.goto(baseUrl + "/");
    await page.screenshot({
      path: path.join(dir, "tienda-movil-muestra-pruebas.png"),
      fullPage: true,
    });
  });
  await test("Nombres de producto se escapan como texto", async () => {
    await page.route("**/api/public/productos", (route) =>
      route.fulfill({
        json: [{ ...fixtures[0], nombre: "<img src=x onerror=window.productXss=1>" }],
      }),
    );
    await page.goto(baseUrl + "/#catalogo");
    await page.reload();
    await page
      .getByRole("heading", { name: "<img src=x onerror=window.productXss=1>", exact: true })
      .waitFor();
    assert.equal(await page.evaluate(() => window.productXss), undefined);
    assert.equal(await page.locator("#catalog-products .product-card img").count(), 0);
  });
  assert.deepEqual(errors, []);
  console.log(
    `${checks} comprobaciones web aprobadas. Solo GET al catálogo real; todos los POST fueron simulados.`,
  );
  await browser.close();
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
