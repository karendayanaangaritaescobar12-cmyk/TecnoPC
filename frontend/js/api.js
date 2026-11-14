/** Shared HTTP behavior: timeouts, duplicate reads and Retry-After. Never retries POSTs. */
export function createApiClient({
  fetchRequest = (...args) => fetch(...args),
  now = () => Date.now(),
} = {}) {
  const API = "/api/public";
  const pendingReads = new Map();
  const apiCooldowns = new Map();
  async function api(path, options = {}, timeout) {
    const method = (options.method || "GET").toUpperCase();
    timeout ??= method === "GET" ? 12000 : 35000;
    const key = `${method}:${path}`;
    const remaining = Math.ceil(((apiCooldowns.get(key) || 0) - now()) / 1000);
    if (remaining > 0)
      throw new Error(
        `El servicio está ocupado. Espera ${remaining} segundos antes de volver a intentar.`,
      );
    if (method === "GET" && pendingReads.has(key)) return pendingReads.get(key);
    const request = performApiRequest(path, options, timeout, key);
    if (method === "GET") pendingReads.set(key, request);
    try {
      return await request;
    } finally {
      if (pendingReads.get(key) === request) pendingReads.delete(key);
    }
  }
  async function performApiRequest(path, options, timeout, key) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeout);
    try {
      const response = await fetchRequest(`${API}/${path}`, {
        ...options,
        headers: {
          "Content-Type": "application/json",
          ...(options.headers || {}),
        },
        signal: controller.signal,
      });
      let data;
      try {
        data = await response.json();
      } catch {}
      if (path === "v2/pagos" && [401, 403, 404].includes(response.status))
        throw new Error(
          "El servidor todavía no admite esta versión de la tienda. Actualiza el servicio antes de crear el pedido.",
        );
      if (response.status === 429 || response.status === 503) {
        const delay = Math.max(1, Math.min(60, Number(response.headers.get("Retry-After")) || 5));
        apiCooldowns.set(key, now() + delay * 1000);
        throw new Error(
          `${data?.mensaje || "El servicio no está disponible temporalmente."} Espera ${delay} segundos.`,
        );
      }
      if (!data) throw new Error("El servidor no devolvió una respuesta válida.");
      if (!response.ok) {
        const error = new Error(data.mensaje || "No se pudo completar la solicitud.");
        error.status = response.status;
        throw error;
      }
      return data;
    } catch (error) {
      if (error.name === "AbortError")
        throw new Error(
          (options.method || "GET").toUpperCase() === "GET"
            ? "El servidor está tardando en responder. Conservamos tu carrito; intenta más tarde."
            : "No se pudo confirmar la respuesta. Comprueba el estado antes de repetir una compra o solicitud.",
          { cause: error },
        );
      if (error instanceof TypeError)
        throw new Error(
          (options.method || "GET").toUpperCase() === "GET"
            ? "No se pudo conectar con la tienda. Comprueba tu conexión."
            : "Se perdió la conexión y no se pudo confirmar el resultado. Conserva el intento para recuperar la misma operación.",
          { cause: error },
        );
      throw error;
    } finally {
      clearTimeout(timer);
    }
  }

  return api;
}

export const api = createApiClient();
