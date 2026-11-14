import com.tecnopc.ui.DesktopShell;
import com.tecnopc.desktop.AdminWorkspace;
import com.tecnopc.vendedor.SalesWorkspace;
import com.tecnopc.tecnico.WorkshopWorkspace;
import javafx.application.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.Stage;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.io.*;

public class DesktopUiChecks {
 static final AtomicInteger unhandled=new AtomicInteger(); static int checks=0; static final AtomicInteger writes=new AtomicInteger();
 static <T>T fx(Callable<T> action)throws Exception{var result=new AtomicReference<T>();var error=new AtomicReference<Throwable>();var done=new CountDownLatch(1);Platform.runLater(()->{try{result.set(action.call());}catch(Throwable e){error.set(e);}finally{done.countDown();}});if(!done.await(12,TimeUnit.SECONDS))throw new IllegalStateException("UI timeout");if(error.get()!=null)throw new RuntimeException(error.get());return result.get();}
 static void waitFor(Callable<Boolean> condition)throws Exception{for(int i=0;i<60;i++){if(fx(condition))return;Thread.sleep(100);}throw new IllegalStateException("UI condition timeout");}
 static void check(boolean result,String name){if(!result)throw new AssertionError(name);checks++;System.out.println("OK "+name);}
 static Button button(Parent root,String text){return root.lookupAll(".button").stream().filter(n->n instanceof Button b&&b.getText().equals(text)).map(Button.class::cast).findFirst().orElseThrow(()->new IllegalStateException("Button "+text));}
 static void shot(Stage stage,String file)throws Exception{fx(()->{stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();var image=stage.getScene().snapshot(null);int w=(int)image.getWidth(),h=(int)image.getHeight();int[] pixels=new int[w*h];image.getPixelReader().getPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,w);var png=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);png.setRGB(0,0,w,h,pixels,0,w);javax.imageio.ImageIO.write(png,"png",new File(file));return null;});}
 public static void main(String[] args)throws Exception {
  Thread.setDefaultUncaughtExceptionHandler((t,e)->{unhandled.incrementAndGet();e.printStackTrace();});
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",exchange->{if(!("Basic "+java.util.Base64.getEncoder().encodeToString("cuenta_prueba:solo-prueba-no-real".getBytes(StandardCharsets.UTF_8))).equals(exchange.getRequestHeaders().getFirst("Authorization"))){exchange.sendResponseHeaders(401,-1);exchange.close();return;}String route=exchange.getRequestURI().getPath();if(route.equals("/api/admin/denegado")){exchange.sendResponseHeaders(403,-1);exchange.close();return;}if(!exchange.getRequestMethod().equals("GET"))writes.incrementAndGet();String body=switch(route){
   case "/api/admin/resumen" -> "{\"productos\":2,\"stockBajo\":1,\"compras\":2,\"empleadosActivos\":1}";
   case "/api/admin/compras" -> "[{\"numero\":\"VENTA-PRUEBA\",\"cliente_nombre\":\"Cliente de prueba\",\"estado\":\"registrada\",\"total\":119000,\"creado_en\":\"2026-11-13\"}]";
   case "/api/admin/usuarios" -> "[{\"id\":1,\"usuario\":\"usuario_prueba\",\"rol\":\"TECNICO\",\"activo\":true}]";
   case "/api/vendedor/productos" -> "[{\"id\":1,\"nombre\":\"Producto simulado\",\"categoria\":\"ssd\",\"stock\":5,\"precio\":100000}]";
   case "/api/vendedor/ventas/VEN-PRUEBA/recibo" -> "{\"venta\":{\"numero\":\"VEN-PRUEBA\",\"cliente_nombre\":\"Cliente simulado\",\"vendedor_usuario\":\"cuenta_prueba\",\"estado\":\"registrada\",\"creado_en\":\"2026-11-13\",\"subtotal\":100000,\"iva\":19000,\"total\":119000},\"items\":[{\"nombre_producto\":\"Producto simulado\",\"cantidad\":1,\"precio_unitario\":100000,\"subtotal\":100000}]}";
   case "/api/tecnico/solicitudes" -> "[{\"id\":1,\"nombre\":\"Cliente simulado\",\"telefono\":\"0000000000\",\"detalle\":\"Descripcion de prueba\"}]";
   case "/api/tecnico/reparaciones" -> "[{\"id\":1,\"codigo_ingreso\":\"TPC-2026-ABCDEFGH2345\",\"cliente\":\"Cliente simulado\",\"equipo\":\"Portatil de prueba\",\"estado\":\"recibido\",\"diagnostico\":null,\"trabajo_realizado\":null,\"presupuesto\":null,\"autorizado\":false}]";
   default -> "[]";
  };byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
  Platform.startup(()->Platform.setImplicitExit(false));
  try{
   int index=0;for(Application app:new Application[]{new AdminWorkspace(),new SalesWorkspace(),new WorkshopWorkspace()}){
    int kind=index++;Stage stage=fx(()->{var s=new Stage();app.start(s);return s;});DesktopShell ui=(DesktopShell)fx(()->stage.getScene().getRoot());
    check(fx(()->!ui.getLeft().isVisible()),"Navegación oculta antes de iniciar sesión "+kind);
    check(fx(()->{ui.applyCss();ui.layout();var pane=ui.lookup(".connection-pane");var bounds=pane.localToScene(pane.getBoundsInLocal());return Math.abs((bounds.getMinX()+bounds.getMaxX())/2-stage.getScene().getWidth()/2)<3&&bounds.getWidth()<=481;}),"Acceso centrado y ancho limitado "+kind);
    shot(stage,"ui-previews/acceso-centrado-"+kind+".png");
    fx(()->{ui.server.setText("http://127.0.0.1:"+server.getAddress().getPort());ui.user.setText("cuenta_prueba");ui.password.setText("incorrecta");button(ui,"Conectar").fire();return null;});
    waitFor(()->ui.lookupAll(".status-text").stream().anyMatch(n->n instanceof Label l&&l.getText().contains("incorrect")));
    check(fx(()->!ui.getLeft().isVisible()),"Clave incorrecta mantiene bloqueo "+kind);
    fx(()->{ui.server.setText("http://127.0.0.1:"+server.getAddress().getPort());ui.user.setText("cuenta_prueba");ui.password.setText("solo-prueba-no-real");button(ui,"Conectar").fire();return null;});
    waitFor(()->ui.lookupAll(".connection-state").stream().anyMatch(n->n instanceof Label l&&l.getText().contains("conectada")));
    check(fx(()->!ui.password.getText().isBlank()),"Conexion autenticada simulada de rol "+kind);
    check(fx(()->ui.getLeft().isVisible()),"Acceso correcto muestra navegación "+kind);check(fx(()->!ui.lookup(".connection-pane").isVisible()),"Acceso oculto durante sesión activa "+kind);fx(()->{stage.setIconified(true);return null;});Thread.sleep(100);fx(()->{stage.setIconified(false);return null;});check(fx(()->ui.getLeft().isVisible()),"Minimizar y restaurar conserva sesión "+kind);if(kind==0){fx(()->{ui.request("GET","/api/admin/denegado",null,r->{});return null;});waitFor(()->ui.lookupAll(".status-text").stream().anyMatch(n->n instanceof Label l&&l.getText().contains("no permite")));check(fx(()->ui.getLeft().isVisible()),"Respuesta 403 conserva sesión autenticada");}
    if(kind==0){fx(()->{button(ui,"Inventario").fire();return null;});check(fx(()->button(ui,"＋ Agregar producto").isVisible()),"Administrador muestra Agregar producto");Platform.runLater(()->button(ui,"＋ Agregar producto").fire());waitFor(()->javafx.stage.Window.getWindows().stream().anyMatch(w->w.getScene()!=null&&w.getScene().lookup("#producto-nombre")!=null));check(true,"Formulario de nuevo producto disponible");fx(()->{var w=javafx.stage.Window.getWindows().stream().filter(x->x.getScene()!=null&&x.getScene().lookup("#producto-nombre")!=null).findFirst().orElseThrow();var pane=(DialogPane)w.getScene().getRoot();((Button)pane.lookupButton(ButtonType.CANCEL)).fire();return null;});fx(()->{button(ui,"Usuarios y roles").fire();return null;});waitFor(()->ui.lookupAll(".table-view").stream().anyMatch(n->n instanceof TableView t&&t.getItems().size()==1));check(true,"Administrador muestra usuarios en tabla");fx(()->{button(ui,"Inicio").fire();return null;});Thread.sleep(400);}
    if(kind==1){check(fx(()->button(ui,"Imprimir recibo").isVisible()),"Botón Imprimir recibo visible en punto de venta");check(fx(()->button(ui,"Recibos").isVisible()),"Sección Recibos disponible");waitFor(()->ui.lookupAll(".combo-box").stream().anyMatch(n->n instanceof ComboBox c&&c.getItems().size()==1));fx(()->{var picker=(ComboBox)ui.lookupAll(".combo-box").stream().filter(n->n instanceof ComboBox c&&c.getItems().size()==1).findFirst().orElseThrow();picker.getSelectionModel().selectFirst();button(ui,"＋ Agregar").fire();return null;});check(fx(()->ui.lookupAll(".table-view").stream().anyMatch(n->n instanceof TableView t&&t.getItems().size()==1)),"Vendedor agrega producto al carrito");check(fx(()->ui.lookupAll(".total-value").stream().anyMatch(n->n instanceof Label l&&l.getText().contains("119.000"))),"Vendedor calcula total con IVA");}
    if(kind==2){fx(()->{button(ui,"Diagnóstico").fire();return null;});waitFor(()->ui.lookupAll(".combo-box").stream().anyMatch(n->n instanceof ComboBox c&&c.getItems().size()==1));fx(()->{var picker=(ComboBox)ui.lookupAll(".combo-box").stream().filter(n->n instanceof ComboBox c&&c.getItems().size()==1).findFirst().orElseThrow();picker.getSelectionModel().selectFirst();return null;});check(fx(()->ui.lookupAll(".combo-box").stream().anyMatch(n->n instanceof ComboBox c&&c.getItems().contains("diagnostico")&&!c.getItems().contains("listo"))),"Tecnico solo ofrece transiciones permitidas");}
    if(kind==1){
     fx(()->{button(ui,"Facturación").fire();return null;});
     check(fx(()->ui.lookupAll(".label").stream().anyMatch(n->n instanceof Label l&&l.getText().contains("MODO DEMOSTRACIÓN"))),"Facturación anuncia modo demostración");
     fx(()->{button(ui,"Recibos").fire();ui.lookupAll(".text-field").stream().filter(n->n instanceof TextField t&&t.getPromptText().contains("Número de venta;")).map(TextField.class::cast).findFirst().orElseThrow().setText("VEN-PRUEBA");button(ui,"Ver e imprimir recibo").fire();return null;});
     waitFor(()->javafx.stage.Window.getWindows().stream().anyMatch(w->w.getScene()!=null&&w.getScene().lookup(".receipt-page")!=null));
     check(true,"Recibo renderizado desde venta guardada simulada");
     var receiptWindow=fx(()->javafx.stage.Window.getWindows().stream().filter(w->w.getScene()!=null&&w.getScene().lookup(".receipt-page")!=null).findFirst().orElseThrow());
     Platform.runLater(()->button(receiptWindow.getScene().getRoot(),"Preparar factura electrónica").fire());
     waitFor(()->javafx.stage.Window.getWindows().stream().anyMatch(w->w instanceof Stage st&&st.getTitle().equals("Preparación de factura electrónica")));
     var fiscalWindow=fx(()->javafx.stage.Window.getWindows().stream().filter(w->w instanceof Stage st&&st.getTitle().equals("Preparación de factura electrónica")).findFirst().orElseThrow());
     check(fx(()->fiscalWindow.getScene().getRoot().lookupAll(".label").stream().anyMatch(n->n instanceof Label l&&l.getText().contains("NO EMITE FACTURAS"))),"Borrador fiscal no promete emisión");
     fx(()->{var pane=(DialogPane)fiscalWindow.getScene().getRoot();((Button)pane.lookupButton(ButtonType.CLOSE)).fire();return null;});
     fx(()->{var pane=(DialogPane)receiptWindow.getScene().getRoot();((Button)pane.lookupButton(ButtonType.CLOSE)).fire();button(ui,"Nueva venta").fire();return null;});
    }
    shot(stage,"ui-previews/"+new String[]{"admin-con-datos-pruebas","vendedor-con-datos-pruebas","tecnico-con-datos-pruebas"}[kind]+".png");
    fx(()->{button(ui,"Salir").fire();return null;});check(fx(()->ui.password.getText().isEmpty()),"Salir borra clave de rol "+kind);check(fx(()->ui.lookupAll(".card-title").stream().anyMatch(n->n instanceof Label l&&l.getText().equals("Sesión cerrada"))),"Salir retira datos visibles de rol "+kind);fx(()->{stage.close();return null;});
   }
   check(unhandled.get()==0,"Sin excepciones en el hilo de interfaz");check(writes.get()==0,"Pruebas sin escrituras ni envios reales");System.out.println(checks+" comprobaciones de escritorio aprobadas.");
  }finally{server.stop(0);Platform.exit();}
 }
}
