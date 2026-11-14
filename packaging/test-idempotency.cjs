const {chromium}=require('../.tools/ui-testing/node_modules/playwright');
const assert=require('node:assert/strict');
const ref='TPC-PAY-'+'d'.repeat(32),url='https://sandbox.mercadopago.com.co/checkout/v1/redirect?pref_id=example';
const fixture={id:1,nombre:'PC de prueba',categoria:'pc',precio:100,stock:3,especificaciones:'Prueba'};
const receipt={referencia:ref,cliente:'Cliente de prueba',fecha:'2026-11-13T18:00:00Z',estado:'pendiente',numero_venta:null,subtotal:100,iva:19,total:119,items:[{nombre:'PC de prueba',cantidad:1,precio:100,importe:100}]};
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});let checks=0;
 const test=async(name,fn)=>{await fn();checks++;console.log('OK '+name);};
 async function pageFor(){
  const page=await browser.newPage();
  await page.route('**/api/public/productos',route=>route.fulfill({json:[fixture]}));
  await page.route('**/api/public/pagos/recibo',route=>route.fulfill({json:receipt}));
  await page.goto('http://127.0.0.1:8082/#catalogo');await page.locator('#catalog-products [data-add="1"]').click();
  await page.goto('http://127.0.0.1:8082/#compras');await page.locator('#checkout-form [name="cliente_nombre"]').fill('Cliente de prueba');return page;
 }
 try{
  await test('Una respuesta perdida se recupera tras recargar usando la misma clave y un solo pedido',async()=>{
   const page=await pageFor();const operations=new Map(),keys=[];
   await page.route('**/api/public/v2/pagos',async route=>{
    const key=route.request().headers()['idempotency-key'];assert.match(key,/^[0-9a-f-]{36}$/);keys.push(key);
    if(!operations.has(key)){operations.set(key,ref);return route.abort('failed');}
    return route.fulfill({status:201,json:{referencia:operations.get(key),total:119,url_pago:url,estado:'pendiente'}});
   });
   await page.locator('#checkout-button').click();await page.locator('#checkout-message').filter({hasText:'Se perdió la conexión'}).waitFor();
   const saved=await page.evaluate(()=>sessionStorage.getItem('tecnopc-checkout-intent-v1'));assert.equal(saved.includes('Cliente de prueba'),false);
   await page.reload();await page.locator('#checkout-form [name="cliente_nombre"]').fill('Cliente de prueba');
   await page.locator('#checkout-button').click();await page.locator('#order-receipt-paper .cr-products').waitFor();
   assert.equal(keys.length,2);assert.equal(keys[0],keys[1]);assert.equal(operations.size,1);
   assert.equal(await page.evaluate(()=>sessionStorage.getItem('tecnopc-checkout-intent-v1')),null);await page.close();
  });
  await test('Cambiar datos de una compra incierta no genera automáticamente una clave nueva',async()=>{
   const page=await pageFor();let requests=0;
   await page.route('**/api/public/v2/pagos',route=>{requests++;return route.abort('failed');});
   await page.locator('#checkout-button').click();await page.locator('#checkout-message').filter({hasText:'Se perdió la conexión'}).waitFor();
   await page.locator('#checkout-form [name="cliente_nombre"]').fill('Otro cliente');await page.locator('#checkout-button').click();
   await page.locator('#checkout-message').filter({hasText:'intento anterior con otros datos'}).waitFor();assert.equal(requests,1);
   const saved=await page.evaluate(()=>sessionStorage.getItem('tecnopc-checkout-intent-v1'));
   page.once('dialog',dialog=>dialog.dismiss());await page.locator('#reset-checkout-attempt').click();
   assert.equal(await page.evaluate(()=>sessionStorage.getItem('tecnopc-checkout-intent-v1')),saved);await page.close();
  });
  await test('Un pedido en preparación conserva referencia y recupera el enlace sin recrear checkout',async()=>{
   const page=await pageFor();let creates=0,ready=false;
   await page.route('**/api/public/v2/pagos',route=>{creates++;return route.fulfill({status:201,json:{referencia:ref,total:119,url_pago:'',estado:'pendiente'}});});
   await page.route('**/api/public/pagos/recibo',route=>route.fulfill({json:{...receipt,url_pago:ready?url:''}}));
   await page.locator('#checkout-button').click();await page.locator('#order-receipt-paper .cr-products').waitFor();
   assert.equal(await page.locator('#order-payment-link').isVisible(),false);ready=true;
   await page.locator('#refresh-order-receipt').click();await page.locator('#order-payment-link').waitFor();
   assert.equal(await page.locator('#order-payment-link').getAttribute('href'),url);assert.equal(creates,1);await page.close();
  });
  await test('Un pedido en revisión no muestra enlace ni confirma un pago inexistente',async()=>{
   const page=await pageFor();await page.route('**/api/public/v2/pagos',route=>route.fulfill({status:201,json:{referencia:ref,total:119,url_pago:'',estado:'revision'}}));
   await page.route('**/api/public/pagos/recibo',route=>route.fulfill({json:{...receipt,estado:'revision'}}));
   await page.locator('#checkout-button').click();await page.locator('.cr-status').filter({hasText:'Revisión del pedido'}).waitFor();
   assert.equal(await page.locator('#order-payment-link').isVisible(),false);await page.close();
  });
  await test('Si no se puede persistir la clave, el navegador impide enviar la compra',async()=>{
   const page=await pageFor();let creates=0;
   await page.route('**/api/public/v2/pagos',route=>{creates++;return route.fulfill({status:500});});
   await page.evaluate(()=>{Storage.prototype.setItem=()=>{throw new DOMException('blocked','SecurityError');};});
   await page.locator('#checkout-button').click();await page.locator('#checkout-message').filter({hasText:'No se pudo guardar el intento'}).waitFor();
   assert.equal(creates,0);await page.close();
  });
  await test('Un servidor antiguo no provoca una compra sin protección ni un fallback a la ruta vieja',async()=>{
   const page=await pageFor();let legacy=0;
   await page.route('**/api/public/pagos',route=>{legacy++;return route.fulfill({status:201,json:{referencia:ref,url_pago:url,estado:'pendiente'}});});
   await page.route('**/api/public/v2/pagos',route=>route.fulfill({status:401,body:''}));
   await page.locator('#checkout-button').click();await page.locator('#checkout-message').filter({hasText:'El servidor todavía no admite esta versión'}).waitFor();
   assert.equal(legacy,0);assert.equal(await page.evaluate(()=>sessionStorage.getItem('tecnopc-order-receipt')),null);await page.close();
  });
  console.log(`${checks} comprobaciones de idempotencia web aprobadas; pasarela simulada.`);
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
