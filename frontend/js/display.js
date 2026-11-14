export const esc = (value) =>
  String(value ?? "").replace(
    /[&<>"']/g,
    (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char],
  );
export const money = (value) => "$" + Math.round(Number(value) || 0).toLocaleString("es-CO");
export const totalPrice = (product) => Math.round(Number(product.precio) * 119) / 100;
