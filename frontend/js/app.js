"use strict";
const API = "/api/public";
const IVA = 0.19,
  ENSAMBLE = 80000;
const GROUPS = [
  ["laptop", "Portátiles", "laptop"],
  ["pc", "PC de escritorio", "pc"],
  ["componentes", "Componentes", "gpu"],
  ["perifericos", "Periféricos", "headset"],
];
const PARTS = [
  ["cpu", "Procesador"],
  ["gpu", "Tarjeta gráfica"],
  ["ram", "Memoria RAM"],
  ["ssd", "Almacenamiento"],
  ["mb", "Placa madre"],
  ["psu", "Fuente de poder"],
  ["case", "Gabinete"],
];
const CATEGORIES = {
  pc: "PC de escritorio",
  laptop: "Portátil",
  cpu: "Procesador",
  gpu: "Tarjeta gráfica",
  ram: "Memoria RAM",
  ssd: "Almacenamiento",
  mb: "Placa madre",
  psu: "Fuente de poder",
  case: "Gabinete",
};
const VIEWS = new Set([
  "inicio",
  "catalogo",
  "armar",
  "promociones",
  "servicio",
  "seguimiento",
  "compras",
]);
const state = {
  products: [],
  selection: {},
  categories: new Set(),
  brands: new Set(),
  query: "",
  loaded: false,
  error: "",
  price: 10000000,
};
let cart = [];
try {
  const saved = JSON.parse(localStorage.getItem("tecnopc_cart_v2") || "[]");
  if (Array.isArray(saved))
    cart = saved
      .filter(
        (x) =>
          Number.isSafeInteger(x.id) &&
          x.id > 0 &&
          Number.isInteger(x.quantity) &&
          x.quantity > 0 &&
          x.quantity <= 100,
      )
      .slice(0, 30);
} catch {}
const $ = (selector) => document.querySelector(selector);
const esc = (value) =>
  String(value ?? "").replace(
    /[&<>"']/g,
    (char) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        char
      ],
  );
const money = (value) =>
  "$" + Math.round(Number(value) || 0).toLocaleString("es-CO");
const totalPrice = (product) => Math.round(Number(product.precio) * 119) / 100;
const illustration = (type, classes = "") =>
  `<svg class="${classes}" viewBox="0 0 180 140" aria-hidden="true"><use href="images/devices.svg#${Object.hasOwn(CATEGORIES, type) || type === "headset" ? type : "pc"}"/></svg>`;
const product = (id) => state.products.find((p) => Number(p.id) === Number(id));
const stock = (p) => Math.max(0, Number(p.stock) || 0);
const brand = (p) =>
  String(p.nombre)
    .match(
      /\b(ASUS|MSI|Lenovo|HP|Dell|Acer|Gigabyte|Corsair|Kingston|AMD|Intel|NVIDIA|Logitech|Samsung|Crucial)\b/i,
    )?.[1]
    .toUpperCase() || "Otras marcas";
const group = (p) =>
  p.categoria === "pc" || p.categoria === "laptop"
    ? p.categoria
    : /\b(mouse|teclado|monitor|aud[ií]fonos|headset|webcam)\b/i.test(
          p.nombre + " " + p.especificaciones,
        )
      ? "perifericos"
      : "componentes";
const empty = (title, note, extra = "") =>
  `<div class="empty-state"><span class="empty-symbol">▦</span><h3>${esc(title)}</h3><p>${esc(note)}</p>${extra}</div>`;
function toast(message) {
  $("#toast").textContent = message;
  $("#toast").hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => ($("#toast").hidden = true), 3500);
}
function message(target, text, success = false) {
  target.textContent = text;
  target.classList.toggle("success", success);
}
const pendingReads = new Map();
const apiCooldowns = new Map();
async function api(path, options = {}, timeout) {
  const method = (options.method || "GET").toUpperCase();
  timeout ??= method === "GET" ? 12000 : 35000;
  const key = `${method}:${path}`;
  const remaining = Math.ceil(((apiCooldowns.get(key) || 0) - Date.now()) / 1000);
  if (remaining > 0)
    throw new Error(`El servicio está ocupado. Espera ${remaining} segundos antes de volver a intentar.`);
  if (method === "GET" && pendingReads.has(key)) return pendingReads.get(key);
  const request = performApiRequest(path, options, timeout, key);
  if (method === "GET") pendingReads.set(key, request);
  try { return await request; }
  finally { if (pendingReads.get(key) === request) pendingReads.delete(key); }
}
async function performApiRequest(path, options, timeout, key) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  try {
    const response = await fetch(`${API}/${path}`, {
      ...options,
      headers: {
        "Content-Type": "application/json",
        ...(options.headers || {}),
      },
      signal: controller.signal,
    });
    let data;
    try { data = await response.json(); } catch {}
    if (path === "v2/pagos" && [401,403,404].includes(response.status))
      throw new Error("El servidor todavía no admite esta versión de la tienda. Actualiza el servicio antes de crear el pedido.");
    if (response.status === 429 || response.status === 503) {
      const delay = Math.max(1, Math.min(60, Number(response.headers.get("Retry-After")) || 5));
      apiCooldowns.set(key, Date.now() + delay * 1000);
      throw new Error(`${data?.mensaje || "El servicio no está disponible temporalmente."} Espera ${delay} segundos.`);
    }
    if (!data) throw new Error("El servidor no devolvió una respuesta válida.");
    if (!response.ok) {
      const error = new Error(data.mensaje || "No se pudo completar la solicitud.");
      error.status = response.status; throw error;
    }
    return data;
  } catch (error) {
    if (error.name === "AbortError")
      throw new Error(
        (options.method || "GET").toUpperCase() === "GET"
          ? "El servidor está tardando en responder. Conservamos tu carrito; intenta más tarde."
          : "No se pudo confirmar la respuesta. Comprueba el estado antes de repetir una compra o solicitud.",
      );
    if (error instanceof TypeError)
      throw new Error(
        (options.method || "GET").toUpperCase() === "GET"
          ? "No se pudo conectar con la tienda. Comprueba tu conexión."
          : "Se perdió la conexión y no se pudo confirmar el resultado. Conserva el intento para recuperar la misma operación.",
      );
    throw error;
  } finally {
    clearTimeout(timer);
  }
}
function showView(name) {
  const view = VIEWS.has(name) ? name : "inicio";
  document
    .querySelectorAll("[data-view]")
    .forEach((node) => (node.hidden = node.dataset.view !== view));
  document.querySelectorAll("[data-view-link]").forEach((link) => {
    const active = link.dataset.viewLink === view;
    link.classList.toggle("active", active);
    if (active) link.setAttribute("aria-current", "page");
    else link.removeAttribute("aria-current");
  });
  if (view === "compras") renderCart();
  window.scrollTo({
    top: 0,
    behavior: matchMedia("(prefers-reduced-motion: reduce)").matches
      ? "instant"
      : "smooth",
  });
}
function navigate(view) {
  if (location.hash === `#${view}`) showView(view);
  else location.hash = view;
}
window.addEventListener("hashchange", () => showView(location.hash.slice(1)));
$("#global-search").addEventListener("submit", (event) => {
  event.preventDefault();
  state.query = $("#search").value.trim();
  renderCatalog();
  navigate("catalogo");
});
function card(p) {
  const available = stock(p) > 0;
  return `<article class="product-card"><div class="product-visual">${illustration(p.categoria)}</div><span class="product-category">${esc(CATEGORIES[p.categoria] || "Producto")}</span><h3>${esc(p.nombre)}</h3><p class="specs">${esc(p.especificaciones || "Consulta las características con nuestro equipo.")}</p><div class="product-price">${money(totalPrice(p))}</div><div class="product-bottom"><span class="stock-tag ${available ? "" : "out"}">${available ? "● En stock" : "○ No disponible"}</span><button type="button" class="cart-add" data-add="${Number(p.id)}" ${available ? "" : "disabled"} aria-label="Agregar ${esc(p.nombre)} al carrito">＋</button></div></article>`;
}
function renderHome() {
  $("#home-categories").innerHTML =
    GROUPS.map(
      ([id, label, icon]) =>
        `<a href="#catalogo" class="category-card" data-category="${id}">${illustration(icon)}<strong>${label}</strong><small>Explorar categoría →</small></a>`,
    ).join("") +
    '<a href="#promociones" class="category-card promo-category"><span class="promo-symbol">%</span><strong>Promociones</strong><small>Ver campañas →</small></a>';
  const featured = state.products
    .filter((p) => stock(p) > 0 && ["pc", "laptop"].includes(p.categoria))
    .slice(0, 5);
  $("#featured-products").innerHTML = state.error
    ? empty(
        "No pudimos cargar el catálogo",
        state.error,
        '<button class="btn secondary" data-reload>Volver a intentar</button>',
      )
    : featured.length
      ? featured.map(card).join("")
      : empty(
          "Estamos preparando nuestro catálogo",
          "Los equipos aparecerán aquí cuando estén publicados en la tienda.",
          '<a class="btn secondary" href="#servicio">Consultar con TecnoPC</a>',
        );
}
function renderFilters() {
  $("#category-filters").innerHTML = GROUPS.map(
    ([id, label]) =>
      `<label class="check"><input type="checkbox" data-filter-category="${id}" ${state.categories.has(id) ? "checked" : ""}>${label}<span>${state.products.filter((p) => group(p) === id).length}</span></label>`,
  ).join("");
  const brands = [...new Set(state.products.map(brand))].sort();
  $("#brand-filters").innerHTML = brands.length
    ? brands
        .map(
          (name) =>
            `<label class="check"><input type="checkbox" data-filter-brand="${esc(name)}" ${state.brands.has(name) ? "checked" : ""}>${esc(name)}<span>${state.products.filter((p) => brand(p) === name).length}</span></label>`,
        )
        .join("")
    : '<p class="muted" style="font-size:10px">Las marcas aparecerán con los productos.</p>';
  $("#price-filter").max = String(
    Math.max(10000000, ...state.products.map(totalPrice)),
  );
  $("#price-filter").value = String(state.price);
  $("#price-limit").textContent = money(state.price);
}
function renderCatalog(page = 0) {
  if (!Number.isInteger(page) || page < 0) page = 0;
  let list = state.products.filter(
    (p) =>
      (!state.categories.size || state.categories.has(group(p))) &&
      (!state.brands.size || state.brands.has(brand(p))) &&
      totalPrice(p) <= state.price &&
      (!$("#in-stock").checked || stock(p) > 0) &&
      (!state.query ||
        (p.nombre + " " + p.especificaciones + " " + (p.sku || ""))
          .toLocaleLowerCase("es")
          .includes(state.query.toLocaleLowerCase("es"))),
  );
  const order = $("#sort").value;
  list.sort((a, b) =>
    order === "precio-asc"
      ? totalPrice(a) - totalPrice(b)
      : order === "precio-desc"
        ? totalPrice(b) - totalPrice(a)
        : order === "stock"
          ? stock(b) - stock(a)
          : a.nombre.localeCompare(b.nombre, "es"),
  );
  const pages = Math.max(1, Math.ceil(list.length / 60));
  page = Math.min(page, pages - 1);
  $("#catalog-pagination").innerHTML = !state.error && pages > 1
    ? `<button class="btn secondary" type="button" data-catalog-page="${page - 1}" ${page === 0 ? "disabled" : ""}>Anterior</button><span>Página ${page + 1} de ${pages}</span><button class="btn secondary" type="button" data-catalog-page="${page + 1}" ${page === pages - 1 ? "disabled" : ""}>Siguiente</button>`
    : "";
  $("#product-count").textContent =
    `${list.length} ${list.length === 1 ? "producto encontrado" : "productos encontrados"} · precios con IVA`;
  $("#search-summary").hidden = !state.query;
  $("#search-summary").textContent = `Resultados para «${state.query}»`;
  $("#catalog-products").innerHTML = state.error
    ? empty(
        "No pudimos cargar el catálogo",
        state.error,
        '<button class="btn secondary" data-reload>Volver a intentar</button>',
      )
    : list.length
      ? list.slice(page * 60, (page + 1) * 60).map(card).join("")
      : state.products.length
        ? empty(
            "No encontramos coincidencias",
            "Prueba otra búsqueda o limpia los filtros.",
            '<button class="btn secondary" data-reset-filters>Limpiar filtros</button>',
          )
        : empty(
            "El catálogo aún está vacío",
            "Cuando TecnoPC publique productos, podrás explorarlos y agregarlos a tu carrito.",
          );
}
function resetFilters() {
  state.categories.clear();
  state.brands.clear();
  state.query = "";
  state.price = Math.max(10000000, ...state.products.map(totalPrice));
  $("#search").value = "";
  $("#in-stock").checked = false;
  renderFilters();
  renderCatalog();
}
$("#clear-filters").addEventListener("click", resetFilters);
$("#sort").addEventListener("change", renderCatalog);
$("#in-stock").addEventListener("change", renderCatalog);
$("#price-filter").addEventListener("input", (event) => {
  state.price = Number(event.target.value);
  $("#price-limit").textContent = money(state.price);
  renderCatalog();
});
document.addEventListener("change", (event) => {
  const category = event.target.dataset.filterCategory,
    selectedBrand = event.target.dataset.filterBrand;
  if (category) {
    event.target.checked
      ? state.categories.add(category)
      : state.categories.delete(category);
    renderCatalog();
  }
  if (selectedBrand) {
    event.target.checked
      ? state.brands.add(selectedBrand)
      : state.brands.delete(selectedBrand);
    renderCatalog();
  }
  if (event.target.dataset.part) {
    state.selection[event.target.dataset.part] = event.target.value;
    renderBuilderSummary();
  }
});
document.addEventListener("click", (event) => {
  const button = event.target.closest(
    "[data-add],[data-category],[data-reload],[data-reset-filters],[data-cart-change],[data-remove],[data-catalog-page]",
  );
  if (!button) return;
  if (button.hasAttribute("data-catalog-page")) renderCatalog(Number(button.dataset.catalogPage));
  if (button.dataset.add) addCart(Number(button.dataset.add));
  if (button.dataset.category) {
    state.categories = new Set([button.dataset.category]);
    state.brands.clear();
    state.query = "";
    $("#search").value = "";
    renderFilters();
    renderCatalog();
  }
  if (button.hasAttribute("data-reload")) loadCatalog();
  if (button.hasAttribute("data-reset-filters")) resetFilters();
  if (button.dataset.cartChange) {
    const id = Number(button.dataset.cartChange),
      line = cart.find((x) => x.id === id),
      p = product(id);
    if (line && p) {
      const n = line.quantity + Number(button.dataset.delta);
      if (n > 0 && n <= Math.min(stock(p), 100)) {
        line.quantity = n;
        saveCart();
        renderCart();
      }
    }
  }
  if (button.dataset.remove) {
    cart = cart.filter((x) => x.id !== Number(button.dataset.remove));
    saveCart();
    renderCart();
  }
});
function renderBuilder() {
  $("#builder-parts").innerHTML = PARTS.map(
    ([id, label]) =>
      `<div class="part-row">${illustration(id)}<label>${label}<select data-part="${id}" aria-label="${label}"><option value="">Selecciona ${label.toLowerCase()}</option>${state.products
        .filter((p) => p.categoria === id && stock(p) > 0)
        .map(
          (p) =>
            `<option value="${Number(p.id)}" ${String(state.selection[id]) === String(p.id) ? "selected" : ""}>${esc(p.nombre)} · ${money(Number(p.precio))}</option>`,
        )
        .join("")}</select></label></div>`,
  ).join("");
  renderBuilderSummary();
}
function renderBuilderSummary() {
  const chosen = PARTS.map(([key]) => product(state.selection[key])).filter(
    Boolean,
  );
  const assembly = chosen.length ? ENSAMBLE : 0,
    base = chosen.reduce((sum, p) => sum + Number(p.precio), 0) + assembly;
  $("#builder-selection").innerHTML = chosen.length
    ? chosen
        .map((p) => `<span class="part-chip">✓ ${esc(p.nombre)}</span>`)
        .join("")
    : '<p class="muted" style="text-align:center;font-size:11px">Aún no has seleccionado componentes.</p>';
  $("#builder-totals").innerHTML = totalsMarkup(
    base,
    assembly
      ? `<div class="total-line"><span>Ensamble y pruebas incluido</span><span>${money(assembly)}</span></div>`
      : "",
  );
  const warnings = [];
  const cpu = product(state.selection.cpu),
    board = product(state.selection.mb),
    ram = product(state.selection.ram);
  const socket = (p) =>
    String(p?.especificaciones || "")
      .toUpperCase()
      .match(/\b(AM[45]|LGA\s?\d{3,4})\b/)?.[1]
      .replace(/\s/g, "");
  const memory = (p) =>
    String(p?.especificaciones || "")
      .toUpperCase()
      .match(/\bDDR[345]\b/)?.[0];
  if (socket(cpu) && socket(board) && socket(cpu) !== socket(board))
    warnings.push("El socket del procesador y la placa no coincide.");
  if (memory(ram) && memory(board) && memory(ram) !== memory(board))
    warnings.push("La RAM y la placa indican generaciones DDR distintas.");
  $("#compatibility").classList.toggle("warning", warnings.length > 0);
  $("#compatibility").textContent = warnings.length
    ? warnings.join(" ")
    : "La compatibilidad final, dimensiones y potencia deben ser revisadas por un técnico antes de confirmar la configuración.";
  $("#quote-form button[type=submit]").disabled = !chosen.length;
}
function totalsMarkup(base, extra = "") {
  return `${extra}<div class="total-line"><span>Subtotal sin IVA</span><span>${money(base)}</span></div><div class="total-line"><span>IVA · 19 %</span><span>${money(base * IVA)}</span></div><div class="total-line grand"><span>Total estimado</span><span>${money(base * (1 + IVA))}</span></div>`;
}
function saveCart() {
  try {
    localStorage.setItem("tecnopc_cart_v2", JSON.stringify(cart));
  } catch {}
  $("#cart-count").textContent = String(
    cart.reduce((n, line) => n + line.quantity, 0),
  );
}
function addCart(id) {
  const p = product(id);
  if (!p || !stock(p)) return;
  let line = cart.find((x) => x.id === id);
  const n = (line?.quantity || 0) + 1;
  if (n > Math.min(stock(p), 100)) {
    toast("No hay más unidades disponibles de este producto.");
    return;
  }
  if (!line && cart.length >= 30) {
    toast("El carrito admite hasta 30 productos diferentes.");
    return;
  }
  if (line) line.quantity = n;
  else cart.push({ id, quantity: 1 });
  saveCart();
  toast(`${p.nombre} agregado al carrito`);
  renderCart();
}
function renderCart() {
  const lines = cart
    .map((line) => ({ ...line, p: product(line.id) }))
    .filter((x) => x.p);
  const base = lines.reduce(
    (n, line) => n + Number(line.p.precio) * line.quantity,
    0,
  );
  $("#cart-items").innerHTML = lines.length
    ? lines
        .map(
          (line) =>
            `<article class="cart-item">${illustration(line.p.categoria)}<div><h3>${esc(line.p.nombre)}</h3><p class="muted">${money(totalPrice(line.p))} por unidad · IVA incluido</p><div class="quantity-control"><button type="button" data-cart-change="${line.id}" data-delta="-1" aria-label="Disminuir cantidad" ${line.quantity <= 1 ? "disabled" : ""}>−</button><span>${line.quantity}</span><button type="button" data-cart-change="${line.id}" data-delta="1" aria-label="Aumentar cantidad" ${line.quantity >= Math.min(stock(line.p), 100) ? "disabled" : ""}>＋</button></div></div><div class="cart-item-price">${money(totalPrice(line.p) * line.quantity)}<button type="button" class="remove-item" data-remove="${line.id}">Eliminar</button></div></article>`,
        )
        .join("")
    : empty(
        "Tu carrito está esperando",
        "Explora el catálogo y agrega los productos que te interesan.",
        '<a class="btn secondary" href="#catalogo">Explorar productos</a>',
      );
  $("#cart-totals").innerHTML = totalsMarkup(base);
  $("#checkout-button").disabled =
    !lines.length || lines.some((line) => line.quantity > stock(line.p));
  if (window.TecnoPCOrderReceipt?.hasOrder())
    $("#checkout-button").disabled = false;
  saveCart();
}
function notificationFields() {
  return `<details class="notification-box"><summary>Correo y WhatsApp para avisos (opcional)</summary><div class="notice-fields"><label>Correo electrónico<input name="aviso_correo" type="email" maxlength="254" autocomplete="email" placeholder="cliente@correo.com"></label><label>WhatsApp con código de país<input name="aviso_whatsapp" type="tel" pattern="\\+[1-9][0-9]{7,14}" maxlength="16" placeholder="+573001234567"></label><label class="check"><input name="avisos_correo" type="checkbox"> Quiero recibir por correo avisos del registro y avance de esta solicitud o compra.</label><label class="check"><input name="avisos_whatsapp" type="checkbox"> Autorizo avisos por WhatsApp sobre esta solicitud o compra.</label></div></details>`;
}
document
  .querySelectorAll(".notification-fields")
  .forEach((node) => (node.innerHTML = notificationFields()));
function contact(form) {
  const correo = String(form.get("aviso_correo") || "").trim(),
    whatsapp = String(form.get("aviso_whatsapp") || "").trim(),
    avisosCorreo = form.get("avisos_correo") === "on",
    avisosWhatsapp = form.get("avisos_whatsapp") === "on";
  if (avisosCorreo && !correo)
    throw new Error("Indica tu correo para autorizar los avisos.");
  if (avisosWhatsapp && !whatsapp)
    throw new Error(
      "Indica WhatsApp con código de país para autorizar los avisos.",
    );
  return { correo, whatsapp, avisosCorreo, avisosWhatsapp };
}
async function submitRequest(form, type, detail, target) {
  const button = form.querySelector("[type=submit]");
  if (button.disabled) return;
  button.disabled = true;
  message(target, "");
  try {
    const fields = new FormData(form);
    const result = await api("solicitudes", {
      method: "POST",
      body: JSON.stringify({
        tipo: type,
        nombre: fields.get("nombre").trim(),
        telefono: fields.get("telefono").trim(),
        detalle: detail,
        contacto: contact(fields),
      }),
    });
    message(
      target,
      `Solicitud registrada${result.id ? ` · SOL-${result.id}` : ""}. Nuestro equipo te contactará.`,
      true,
    );
    form.reset();
  } catch (error) {
    message(target, error.message);
  } finally {
    button.disabled = false;
    if (type === "cotizacion") renderBuilderSummary();
  }
}
$("#service-form").addEventListener("submit", (event) => {
  event.preventDefault();
  const fields = new FormData(event.target);
  submitRequest(
    event.target,
    "servicio",
    `${fields.get("equipo")}: ${fields.get("descripcion")}`,
    $("#service-message"),
  );
});
$("#quote-form").addEventListener("submit", (event) => {
  event.preventDefault();
  const chosen = PARTS.map(([key]) => product(state.selection[key])).filter(
    Boolean,
  );
  if (!chosen.length) return;
  submitRequest(
    event.target,
    "cotizacion",
    ("PC personalizada: " + chosen.map((p) => p.nombre).join(" + ")).slice(
      0,
      600,
    ),
    $("#quote-message"),
  );
});
const checkoutIntentStorage = "tecnopc-checkout-intent-v1";
async function prepareCheckoutIntent(payload) {
  if (!globalThis.crypto?.subtle || !globalThis.crypto?.randomUUID)
    throw new Error("Para crear pedidos abre la tienda mediante HTTPS o desde localhost.");
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(JSON.stringify(payload)));
  const fingerprint = [...new Uint8Array(digest)].map(byte => byte.toString(16).padStart(2, "0")).join("");
  let saved;
  try { saved = JSON.parse(sessionStorage.getItem(checkoutIntentStorage) || "null"); }
  catch { throw new Error("No se pudo recuperar el intento de compra. Permite el almacenamiento de esta pestaña antes de continuar."); }
  if (saved) {
    if (!/^[0-9a-f-]{36}$/.test(saved.key) || saved.fingerprint !== fingerprint)
      throw new Error("Hay un intento anterior con otros datos. Revisa su resultado antes de iniciar otra compra o resolver el intento anterior.");
    return saved.key;
  }
  const key = crypto.randomUUID();
  try {
    sessionStorage.setItem(checkoutIntentStorage, JSON.stringify({key, fingerprint}));
    if (!sessionStorage.getItem(checkoutIntentStorage)) throw new Error();
  } catch { throw new Error("No se pudo guardar el intento de compra. Permite el almacenamiento de esta pestaña; no se ha enviado el pedido."); }
  return key;
}
$("#reset-checkout-attempt").addEventListener("click", () => {
  if (!confirm("Comprueba primero si la compra ya quedó registrada. Descartar este intento no cancela pedidos ni pagos y puede permitir crear otro. ¿Ya verificaste el resultado y quieres descartarlo?")) return;
  try { sessionStorage.removeItem(checkoutIntentStorage); $("#reset-checkout-attempt").hidden = true; }
  catch { message($("#checkout-message"), "No se pudo descartar el intento local."); }
});
try { $("#reset-checkout-attempt").hidden = !sessionStorage.getItem(checkoutIntentStorage); } catch {}
$("#checkout-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.target,
    button = $("#checkout-button");
  if (button.disabled) return;
  if (window.TecnoPCOrderReceipt?.hasOrder()) {
    await window.TecnoPCOrderReceipt.open();
    return;
  }
  if (!cart.length) return;
  button.disabled = true;
  message($("#checkout-message"), "");
  try {
    const fields = new FormData(form);
    const payload = {
        cliente_nombre: fields.get("cliente_nombre").trim(),
        contacto: contact(fields),
        items: [...cart].sort((a,b) => a.id-b.id).map((line) => ({
          producto_id: line.id,
          cantidad: line.quantity,
        })),
      };
    const key = await prepareCheckoutIntent(payload);
    $("#reset-checkout-attempt").hidden = false;
    const result = await api("v2/pagos", {method:"POST", headers:{"Idempotency-Key":key}, body:JSON.stringify(payload)});
    const url = result.url_pago ? new URL(result.url_pago) : null;
    if (url && (
      url.protocol !== "https:" ||
      !["www.mercadopago.com.co", "sandbox.mercadopago.com.co"].includes(
        url.hostname,
      ) ||
      url.username ||
      url.password
    ))
      throw new Error("El enlace de pago no es válido.");
    await window.TecnoPCOrderReceipt.created(result, url?.href || "");
    try {
      const saved = JSON.parse(sessionStorage.getItem("tecnopc-order-receipt") || "null");
      if (saved?.referencia === result.referencia) sessionStorage.removeItem(checkoutIntentStorage);
      $("#reset-checkout-attempt").hidden = !sessionStorage.getItem(checkoutIntentStorage);
    } catch {}
  } catch (error) {
    if ([400,422].includes(error.status)) {
      try { sessionStorage.removeItem(checkoutIntentStorage); $("#reset-checkout-attempt").hidden = true; } catch {}
    }
    message($("#checkout-message"), error.message);
  } finally {
    button.disabled = false;
  }
});
const STATUS = {
  recibido: "Recibido",
  diagnostico: "Diagnóstico",
  esperando_autorizacion: "Autorización",
  en_reparacion: "En reparación",
  listo: "Listo para recoger",
  entregado: "Entregado",
  cancelado: "Cancelado",
};
const date = (value) => {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime())
    ? String(value ?? "")
    : new Intl.DateTimeFormat("es-CO", {
        day: "numeric",
        month: "short",
        year: "numeric",
      }).format(parsed);
};
$("#tracking-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const button = event.target.querySelector("button"),
    target = $("#tracking-result");
  const code = $("#tracking-code").value.trim().toUpperCase();
  if (!/^TPC-[0-9]{4}-[A-HJ-NP-Z2-9]{12}$/.test(code)) {
    target.innerHTML = empty(
      "Revisa el código de ingreso",
      "Usa el código completo del comprobante, por ejemplo TPC-2026- seguido de sus 12 caracteres.",
    );
    return;
  }
  button.disabled = true;
  target.innerHTML = empty(
    "Consultando tu equipo",
    "Estamos verificando el estado registrado en el taller.",
  );
  try {
    const info = await api("seguimiento", {
      method: "POST",
      body: JSON.stringify({ codigo: code }),
    });
    const steps = [
      "recibido",
      "diagnostico",
      "esperando_autorizacion",
      "en_reparacion",
      "listo",
      "entregado",
    ];
    const index = steps.indexOf(info.estado);
    const history = Array.isArray(info.historial) ? info.historial : [];
    const descriptions = {
      recibido: "Tu equipo ya está en el taller y está pendiente de revisión.",
      diagnostico: "El equipo está en revisión para identificar el problema.",
      esperando_autorizacion:
        "El taller espera tu autorización para continuar.",
      en_reparacion: "El equipo técnico está trabajando en tu reparación.",
      listo:
        "El trabajo está listo. Contacta al taller para coordinar la recogida.",
      entregado: "El taller registró la entrega del equipo.",
      cancelado:
        "La orden fue cancelada. Contacta al taller para conocer los detalles.",
    };
    target.innerHTML = `<div class="panel"><div class="tracking-top"><div><h2>Estado de la reparación</h2><small>${esc(info.equipo)} · Ingreso ${esc(date(info.fechaIngreso))}</small></div><span class="tracking-code">${esc(info.codigo)}</span></div>${
      info.estado === "cancelado"
        ? ""
        : `<div class="timeline">${steps
            .map((step, i) => {
              const entry = history.find((h) => h.evento === step);
              return `<div class="timeline-step ${i <= index ? "done" : ""} ${i === index ? "current" : ""}"><i>${i < index ? "✓" : "•"}</i>${STATUS[step]}<small>${entry ? esc(date(entry.creado_en)) : ""}</small></div>`;
            })
            .join("")}</div>`
    }<div class="tracking-state"><strong>${esc(STATUS[info.estado] || "Estado actualizado")}</strong>${esc(descriptions[info.estado] || "Consulta al taller para conocer el avance.")}</div><ul class="event-list">${history.map((entry) => `<li><strong>${esc(STATUS[entry.evento] || "Actualización")}</strong><span>${esc(date(entry.creado_en))}</span></li>`).join("")}</ul></div>`;
  } catch (error) {
    target.innerHTML = empty("No pudimos consultar el equipo", error.message);
  } finally {
    button.disabled = false;
  }
});
function setChat(open) {
  $("#chat-panel").hidden = !open;
  $("#chat-toggle").setAttribute("aria-expanded", String(open));
  if (open) $("#chat-input").focus();
  else $("#chat-toggle").focus();
}
$("#chat-toggle").addEventListener("click", () =>
  setChat($("#chat-panel").hidden),
);
$("#chat-close").addEventListener("click", () => setChat(false));
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && !$("#chat-panel").hidden) setChat(false);
});
document.querySelectorAll("[data-chat-route]").forEach((button) =>
  button.addEventListener("click", () => {
    navigate(button.dataset.chatRoute);
    setChat(false);
  }),
);
const session = globalThis.crypto?.randomUUID
  ? crypto.randomUUID().replaceAll("-", "")
  : "session" + Date.now().toString(36) + Math.random().toString(36).slice(2);
function chatBubble(text, type) {
  const node = document.createElement("div");
  node.className = `chat-bubble ${type}`;
  node.textContent = text;
  $("#chat-messages").append(node);
  while ($("#chat-messages").children.length > 80) $("#chat-messages").firstElementChild.remove();
  $("#chat-messages").scrollTop = $("#chat-messages").scrollHeight;
  return node;
}
const chatHistory = [];
$("#chat-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const input = $("#chat-input"),
    text = input.value.trim();
  if (!text || event.target.querySelector("button").disabled) return;
  if (text.length > 500) {
    chatBubble("Resume tu consulta en hasta 500 caracteres.", "bot");
    return;
  }
  chatBubble(text, "user");
  input.value = "";
  const waiting = chatBubble("Consultando información de la tienda…", "bot"),
    button = event.target.querySelector("button");
  button.disabled = true;
  $("#chat-status").textContent = "Consultando…";
  try {
    const result = await api(
      "chatbot",
      {
        method: "POST",
        body: JSON.stringify({
          mensaje: text,
          sesion: session,
          historial: chatHistory.slice(-6),
        }),
      },
      35000,
    );
    chatHistory.push(text);
    if (chatHistory.length > 6) chatHistory.shift();
    waiting.textContent =
      result.respuesta || "Cuéntame un poco más para poder orientarte.";
    const destination = String(result.accion?.destino || "").replace(/^#/, "");
    if (VIEWS.has(destination)) {
      const action = document.createElement("button");
      action.type = "button";
      action.className = "chat-action";
      action.textContent = result.accion.texto || "Continuar";
      action.addEventListener("click", () => {
        navigate(destination);
        setChat(false);
      });
      waiting.append(action);
    }
    $("#chat-status").textContent = "Disponible";
  } catch (error) {
    waiting.textContent = `${error.message} No he podido comprobar productos ni existencias. Puedes consultar el catálogo o solicitar una cotización; no se ha registrado ninguna compra.`;
    $("#chat-status").textContent = "Conexión no disponible";
  } finally {
    button.disabled = false;
    $("#chat-messages").scrollTop = $("#chat-messages").scrollHeight;
  }
});
async function loadCatalog() {
  if (loadCatalog.pending) return loadCatalog.pending;
  loadCatalog.pending = refreshCatalog();
  try { return await loadCatalog.pending; }
  finally { loadCatalog.pending = null; }
}
async function refreshCatalog() {
  state.error = "";
  try {
    const list = await api("productos");
    if (!Array.isArray(list))
      throw new Error("El catálogo no tiene el formato esperado.");
    state.products = list.filter(
      (p) =>
        Number.isSafeInteger(Number(p.id)) &&
        p.id > 0 &&
        Number.isFinite(Number(p.precio)) &&
        Number(p.precio) >= 0,
    );
    state.loaded = true;
    state.price = Math.max(10000000, ...state.products.map(totalPrice));
    cart = cart.filter((line) => Boolean(product(line.id)));
  } catch (error) {
    state.error = error.message;
  }
  renderHome();
  renderFilters();
  renderCatalog();
  renderBuilder();
  renderCart();
}
showView(location.hash.slice(1));
saveCart();
loadCatalog();
