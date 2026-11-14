const { chromium } = require('../.tools/ui-testing/node_modules/playwright');
(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    await page.route('**/api/**', route => route.fulfill({ json: [] }));
    await page.goto('http://127.0.0.1:8082/');
    await page.screenshot({ path: 'ui-previews/logo-tienda.png' });
    const href = await page.locator('link[rel="icon"][type="image/svg+xml"]').getAttribute('href');
    const icon = await page.request.get(new URL(href, page.url()).href);
    if (!icon.ok() || !(await icon.text()).includes('#008cff')) throw new Error('Icono de pestaña no disponible');
    for (const path of ['/images/tecnopc-icon-32.png', '/images/tecnopc-icon-180.png', '/favicon.ico']) {
      const response = await page.request.get('http://127.0.0.1:8082' + path);
      if (!response.ok()) throw new Error('Recurso no disponible: ' + path);
    }
    console.log('Logo e iconos de pestaña disponibles; vista previa guardada.');
  } finally { await browser.close(); }
})();
