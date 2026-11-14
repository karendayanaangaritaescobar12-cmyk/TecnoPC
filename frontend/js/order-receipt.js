import { api } from "./api.js";
import { esc } from "./display.js";
import { validPaymentUrl } from "./payment-url.js";

/* Private order reference stays in this browser tab, never in a URL or localStorage. */
(() => {
  const key = "tecnopc-order-receipt";
  const dialog = document.querySelector("#order-receipt-dialog");
  const paper = document.querySelector("#order-receipt-paper");
  const status = document.querySelector("#order-receipt-status");
  const resume = document.querySelector("#resume-order");
  const pay = document.querySelector("#order-payment-link");
  const print = document.querySelector("#print-order-receipt");
  const refresh = document.querySelector("#refresh-order-receipt");
  const validReference = (value) => /^TPC-PAY-[a-fA-F0-9]{32}$/.test(String(value || ""));
  const receiptMoney = (value) =>
    new Intl.NumberFormat("es-CO", {
      style: "currency",
      currency: "COP",
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(Number(value));
  let order = null,
    loading = false,
    lastRefresh = 0,
    receipt = null;
  try {
    const saved = JSON.parse(sessionStorage.getItem(key));
    if (saved && validReference(saved.referencia))
      order = {
        referencia: saved.referencia,
        url_pago: validPaymentUrl(saved.url_pago) ? saved.url_pago : "",
      };
  } catch {
    /* Private/restricted browsing: keep the receipt in memory. */
  }
  function remember() {
    try {
      if (order) sessionStorage.setItem(key, JSON.stringify(order));
      else sessionStorage.removeItem(key);
    } catch {}
    resume.hidden = !order;
    const button = document.querySelector("#checkout-button");
    button.textContent = order
      ? "Ver comprobante de mi pedido"
      : "Crear pedido y ver comprobante →";
    if (order) button.disabled = false;
  }
  function paymentLink() {
    pay.hidden =
      !order?.url_pago ||
      receipt?.estado === "aprobado" ||
      ["cancelado", "rechazado", "revision"].includes(receipt?.estado);
    if (!pay.hidden) pay.href = order.url_pago;
    else pay.removeAttribute("href");
  }
  function render(data) {
    const approved = data.estado === "aprobado";
    const states = {
      pendiente: [
        "Pendiente de pago",
        "Tu pedido está registrado. El pago aún no ha sido confirmado y los productos no están reservados.",
      ],
      aprobado: [
        "Pago confirmado",
        "Gracias por tu compra. El proveedor confirmó el pago y la venta quedó registrada.",
      ],
      rechazado: [
        "Pago rechazado",
        "El proveedor rechazó el pago. Contacta al equipo antes de intentar otro pedido.",
      ],
      cancelado: [
        "Pedido cancelado",
        "Este pago figura cancelado. No es un comprobante de compra pagada.",
      ],
      revision: [
        "Revisión del pedido",
        "El pedido requiere revisión del equipo. Este comprobante no confirma una compra completada; contacta a TecnoPC.",
      ],
    };
    const ready = approved && data.preparacion === "listo";
    const [label, detail] = (ready
      ? [
          "Listo para recoger",
          "Tu pedido está listo para entregar en TecnoPC. Presenta la referencia del pedido al recogerlo.",
        ]
      : states[data.estado]) || [
      "Estado por confirmar",
      "No se ha podido confirmar el estado del pedido.",
    ];
    const timestamp = new Intl.DateTimeFormat("es-CO", {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: "America/Bogota",
    }).format(new Date(data.fecha));
    paper.innerHTML = `<article class="customer-receipt" aria-label="Comprobante del pedido TecnoPC">
      <header class="cr-header"><div><img src="images/logo-tecnopc.svg" alt="TecnoPC" width="230" height="50"><p>Computadores · Componentes · Servicio técnico</p></div><div class="cr-document"><span>${approved ? "RECIBO DE COMPRA" : "COMPROBANTE DE PEDIDO"}</span><strong>${esc(data.numero_venta || "Pedido web")}</strong><small>${esc(timestamp)} · Colombia</small></div></header>
      <div class="cr-body"><div class="cr-status ${approved ? "is-paid" : ""}"><strong>${esc(label)}</strong><span>${esc(detail)}</span></div>
      <section class="cr-customer"><div><span>PREPARADO PARA</span><strong>${esc(data.cliente)}</strong><small>Compra en la tienda web de TecnoPC</small></div><div><span>REFERENCIA PRIVADA DEL PEDIDO</span><strong class="cr-reference">${esc(data.referencia)}</strong><small>Consérvala para consultar este comprobante.</small></div></section>
      <h3>Detalle de tu pedido</h3><p class="cr-caption">Valores en pesos colombianos (COP). Precios unitarios e importes sin IVA.</p>
      <table class="cr-products"><thead><tr><th>Producto</th><th>Cant.</th><th>Precio unitario</th><th>Importe</th></tr></thead><tbody>${data.items.map((item) => `<tr><td>${esc(item.nombre)}</td><td>${Number(item.cantidad)}</td><td>${receiptMoney(Number(item.precio))}</td><td>${receiptMoney(Number(item.importe))}</td></tr>`).join("")}</tbody></table>
      <div class="cr-totals"><div><span>Subtotal sin IVA</span><strong>${receiptMoney(Number(data.subtotal))}</strong></div><div><span>IVA</span><strong>${receiptMoney(Number(data.iva))}</strong></div><div class="cr-total"><span>Total del pedido</span><strong>${receiptMoney(Number(data.total))}</strong></div></div>
      <section class="cr-thanks"><h3>${approved ? "Gracias por confiar en TecnoPC" : "Tu próximo equipo empieza aquí"}</h3><p>${approved ? "Conserva este recibo como referencia de tu compra. Para consultas, ten a mano la referencia del pedido." : "Puedes guardar este comprobante antes de continuar al pago seguro. Actualiza el estado al regresar de Mercado Pago."}</p></section>
      <footer class="cr-footer"><p>Este documento no sustituye una factura electrónica validada por la DIAN.</p><p>${approved ? "Importes y estado consultados en el registro del pedido." : "Un pedido pendiente no acredita un pago realizado ni una reserva de existencias."} La referencia permite consultar el comprobante: no la publiques.</p></footer></div></article>`;
    document.querySelector("#order-receipt-heading").textContent = approved
      ? "Tu recibo de compra TecnoPC"
      : "Tu comprobante de pedido TecnoPC";
    status.textContent = "Comprobante actualizado desde la tienda.";
    print.disabled = false;
    paymentLink();
  }
  async function update() {
    if (!order || loading) return;
    loading = true;
    refresh.disabled = true;
    status.textContent = "Consultando los detalles de tu pedido…";
    try {
      const data = await api("pagos/recibo", {
        method: "POST",
        body: JSON.stringify({ referencia: order.referencia }),
      });
      if (
        data.referencia !== order.referencia ||
        !Array.isArray(data.items) ||
        !data.items.length ||
        !Number.isFinite(Number(data.total)) ||
        Number(data.total) <= 0 ||
        Number.isNaN(Date.parse(data.fecha))
      )
        throw new Error("El comprobante no contiene todos los datos necesarios.");
      receipt = data;
      if (validPaymentUrl(data.url_pago)) {
        order.url_pago = data.url_pago;
        remember();
      }
      render(data);
      lastRefresh = Date.now();
    } catch (error) {
      status.textContent = `${error.message} Tu pedido no se ha vuelto a crear. Conserva la referencia ${order.referencia} y vuelve a actualizar.`;
      paymentLink();
    } finally {
      loading = false;
      refresh.disabled = false;
    }
  }
  async function open() {
    if (!order) return;
    if (!dialog.open) dialog.showModal();
    if (!receipt) {
      paper.replaceChildren();
      print.disabled = true;
    }
    paymentLink();
    await update();
  }
  window.TecnoPCOrderReceipt = {
    hasOrder: () => Boolean(order),
    open,
    async created(result, link) {
      if (!validReference(result.referencia) || (link && !validPaymentUrl(link)))
        throw new Error("No se recibió una referencia de pedido válida.");
      order = { referencia: result.referencia, url_pago: link };
      receipt = null;
      remember();
      await open();
    },
  };
  resume.addEventListener("click", open);
  refresh.addEventListener("click", update);
  print.addEventListener("click", () => {
    if (receipt) window.print();
  });
  document.querySelector("#close-order-receipt").addEventListener("click", () => dialog.close());
  document.querySelector("#forget-order-receipt").addEventListener("click", () => {
    if (
      !confirm(
        "¿Olvidar el comprobante en este navegador? Guarda antes un PDF si lo necesitas. Esto no cancela el pedido ni devuelve un pago.",
      )
    )
      return;
    order = null;
    receipt = null;
    remember();
    paper.replaceChildren();
    pay.removeAttribute("href");
    dialog.close();
  });
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden && dialog.open && order && Date.now() - lastRefresh > 5000) update();
  });
  remember();
})();
