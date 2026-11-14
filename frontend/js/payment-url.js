/** Only accept HTTPS checkout pages from the configured payment provider. */
export function validPaymentUrl(value) {
  try {
    const url = new URL(value);
    return (
      url.protocol === "https:" &&
      ["www.mercadopago.com.co", "sandbox.mercadopago.com.co"].includes(url.hostname) &&
      !url.username &&
      !url.password
    );
  } catch {
    return false;
  }
}
