package com.tecnopc.vendedor;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecnopc.ui.DesktopShell;
import static com.tecnopc.ui.DesktopShell.*;
import javafx.application.Application;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.math.*;
import java.util.*;

public final class SalesWorkspace extends Application {
    private DesktopShell ui;
    private String current="Nueva venta";
    private String lastSale;
    private final SaleIntentStore saleIntents=new SaleIntentStore();
    private final List<Product> products=new ArrayList<>();
    private final javafx.collections.ObservableList<CartLine> cart=FXCollections.observableArrayList();
    private final TextField client=new TextField(),document=new TextField(),email=new TextField(),phone=new TextField();
    private final CheckBox emailConsent=new CheckBox("El cliente autoriza avisos por correo"),phoneConsent=new CheckBox("El cliente autoriza avisos por WhatsApp");
    private final ComboBox<Product> picker=new ComboBox<>();
    private final Spinner<Integer> quantity=new Spinner<>(1,1000,1);
    private final Label subtotal=text("$ 0","field-label"),tax=text("$ 0","field-label"),total=text("$ 0","total-value"),receipt=text("","muted");
    private record Product(long id,String name,int stock,BigDecimal price) {public String toString(){return name+" · "+stock+" disponibles";}}
    private static final class CartLine {final Product product;final IntegerProperty quantity=new SimpleIntegerProperty();CartLine(Product product,int count){this.product=product;this.quantity.set(count);}}
    @Override public void start(Stage stage) {
        ui=new DesktopShell("vendedor","Punto de venta");
        ui.nav("▣   Nueva venta",this::newSale);ui.nav("▦   Productos",this::productList);ui.nav("▤   Mis ventas",this::history);ui.nav("✓   Preparar pedidos",this::orders);
        ui.nav("▤   Recibos",this::receipts);
        ui.nav("▤   Facturación",this::billing);
        ui.onDisconnect(()->{lastSale=null;clear();products.clear();picker.getItems().clear();});ui.onConnect(()->{newSale();loadProducts();});newSale();ui.show(stage,"TecnoPC — Punto de venta",getParameters()==null?List.of():getParameters().getRaw());
    }
    private void newSale() {
        current="Nueva venta";
        TextField search=new TextField();search.setPromptText("Buscar producto por nombre…");search.textProperty().addListener((o,a,b)->picker.getItems().setAll(products.stream().filter(p->p.name().toLowerCase(Locale.ROOT).contains(b.toLowerCase(Locale.ROOT))).toList()));
        picker.setMaxWidth(Double.MAX_VALUE);picker.setPromptText("Selecciona un producto disponible");quantity.setPrefWidth(90);
        Button add=primary("＋ Agregar",this::addProduct);HBox selection=new HBox(10,picker,quantity,add);HBox.setHgrow(picker,Priority.ALWAYS);
        TableView<CartLine> table=new TableView<>(cart);table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);table.setPlaceholder(empty("Tu venta empieza aquí","Conecta tu cuenta y agrega productos al carrito."));table.setPrefHeight(360);
        TableColumn<CartLine,String> name=new TableColumn<>("Producto");name.setCellValueFactory(r->new ReadOnlyStringWrapper(r.getValue().product.name()));name.setMinWidth(170);
        TableColumn<CartLine,String> price=new TableColumn<>("Precio sin IVA");price.setCellValueFactory(r->new ReadOnlyStringWrapper(money(r.getValue().product.price())));
        TableColumn<CartLine,Integer> count=new TableColumn<>("Cantidad");count.setCellValueFactory(r->r.getValue().quantity.asObject());count.setCellFactory(c->new TableCell<>(){@Override protected void updateItem(Integer value,boolean empty){super.updateItem(value,empty);setText(null);setGraphic(null);if(!empty&&getTableRow()!=null&&getTableRow().getItem()!=null){CartLine line=getTableRow().getItem();Spinner<Integer> editor=new Spinner<>(1,Math.min(line.product.stock(),1000),value);editor.setPrefWidth(85);editor.valueProperty().addListener((o,old,n)->{line.quantity.set(n);totals();table.refresh();});setGraphic(editor);}}});
        TableColumn<CartLine,String> sum=new TableColumn<>("Subtotal");sum.setCellValueFactory(r->new ReadOnlyStringWrapper(money(r.getValue().product.price().multiply(BigDecimal.valueOf(r.getValue().quantity.get())))));
        TableColumn<CartLine,String> remove=new TableColumn<>("");remove.setMaxWidth(70);remove.setCellFactory(c->new TableCell<>(){@Override protected void updateItem(String item,boolean empty){super.updateItem(item,empty);setGraphic(null);if(!empty){Button button=new Button("×");button.getStyleClass().add("danger-button");button.setOnAction(e->{cart.remove(getTableRow().getItem());totals();});setGraphic(button);}}});
        table.getColumns().addAll(name,price,count,sum,remove);
        VBox lines=card("Productos de la venta",search,selection,table,secondary("↻ Actualizar disponibilidad",this::loadProducts),secondary("Resolver intento de venta anterior",this::resolveIntent));VBox.setVgrow(table,Priority.ALWAYS);
        client.setPromptText("Nombre completo");document.setPromptText("Opcional");email.setPromptText("cliente@correo.com");phone.setPromptText("+573001234567");emailConsent.setWrapText(true);phoneConsent.setWrapText(true);
        var noticePane=new TitledPane("Avisos al cliente (opcional)",new VBox(12,field("Correo",email),field("WhatsApp",phone),emailConsent,phoneConsent));noticePane.setAnimated(false);noticePane.setExpanded(false);noticePane.getStyleClass().add("connection-pane");
        VBox buyer=card("Datos del cliente",field("Nombre",client),field("Documento",document),noticePane);
        HBox subLine=row(text("Subtotal","muted"),subtotal),taxLine=row(text("IVA · 19 %","muted"),tax);
        Button sell=primary("✓ Registrar venta",()->{});sell.setMaxWidth(Double.MAX_VALUE);sell.setOnAction(e->sell(sell));
        VBox summary=card("Resumen de la venta",subLine,taxLine,new Separator(),text("Total estimado","field-label"),total,sell,secondary("Limpiar venta",()->{if(cart.isEmpty()||confirm("Limpiar venta","¿Descartar el carrito y los datos ingresados?"))clear();}),receipt);
        summary.getChildren().add(secondary("Imprimir recibo",()->{if(lastSale==null)ui.error("Primero registra una venta o busca una anterior en Recibos.");else showReceipt(lastSale);}));
        VBox side=new VBox(18,buyer,summary);side.setPrefWidth(330);side.setMinWidth(290);lines.setPrefWidth(650);
        HBox layout=new HBox(20,lines,side);HBox.setHgrow(lines,Priority.ALWAYS);ui.page("Nueva venta","Selecciona productos, revisa el total y registra la compra",scroll(layout));totals();
    }
    private void loadProducts() {
        ui.request("GET","/api/vendedor/productos",null,data->{products.clear();data.forEach(p->products.add(new Product(p.path("id").asLong(),p.path("nombre").asText(),p.path("stock").asInt(),p.path("precio").decimalValue())));picker.getItems().setAll(products.stream().filter(p->p.stock()>0).toList());});
    }
    private void addProduct() {
        Product p=picker.getValue();if(p==null){ui.error("Selecciona un producto disponible.");return;}int n=quantity.getValue();CartLine existing=cart.stream().filter(l->l.product.id()==p.id()).findFirst().orElse(null);int count=n+(existing==null?0:existing.quantity.get());if(count>p.stock()||count>1000){ui.error("La cantidad supera el inventario disponible.");return;}if(existing==null)cart.add(new CartLine(p,n));else{existing.quantity.set(count);newSale();}totals();
    }
    private BigDecimal base(){return cart.stream().map(l->l.product.price().multiply(BigDecimal.valueOf(l.quantity.get()))).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private void totals(){BigDecimal base=base(),iva=base.multiply(new BigDecimal("0.19")).setScale(2,RoundingMode.HALF_UP);subtotal.setText(money(base));tax.setText(money(iva));total.setText(money(base.add(iva)));}
    private void clear(){cart.clear();client.clear();document.clear();email.clear();phone.clear();emailConsent.setSelected(false);phoneConsent.setSelected(false);receipt.setText("");totals();}
    private void sell(Button button) {
        if(button.isDisabled())return;
        if(client.getText().isBlank()||cart.isEmpty()){ui.error("Indica el nombre del cliente y agrega productos.");return;}
        if((emailConsent.isSelected()&&email.getText().isBlank())||(phoneConsent.isSelected()&&phone.getText().isBlank())){ui.error("Indica el destino de cada canal autorizado.");return;}
        if(!confirm("Confirmar venta","¿Registrar esta venta por un total estimado de "+total.getText()+"? El servidor validará precios y existencias."))return;
        var payload=ui.json.createObjectNode().put("clienteNombre",client.getText().trim()).put("clienteDocumento",document.getText().trim());var items=payload.putArray("items");cart.stream().sorted(Comparator.comparingLong(l->l.product.id())).forEach(l->items.addObject().put("productoId",l.product.id()).put("cantidad",l.quantity.get()));
        payload.putObject("contacto").put("correo",email.getText().trim()).put("whatsapp",phone.getText().trim()).put("avisosCorreo",emailConsent.isSelected()).put("avisosWhatsapp",phoneConsent.isSelected());
        String scope=intentScope(),key;
        try{key=saleIntents.prepare(scope,payload.toString());}catch(Exception ex){ui.error(ex.getMessage());return;}
        button.setDisable(true);ui.request("POST","/api/vendedor/v2/ventas",payload,r->{if(r.path("registrada").asBoolean()){lastSale=r.path("numero").asText();String confirmation="Venta "+lastSale+" registrada · "+money(r.path("total").decimalValue());try{saleIntents.clear(scope);}catch(Exception ex){ui.error("La venta ya está registrada. Anota "+lastSale+" y resuelve el intento local antes de otra venta.");}clear();receipt.setText(confirmation);loadProducts();}else ui.error("El servidor no confirmó la venta. Conserva el intento y comprueba Mis ventas.");},()->button.setDisable(false),key);
    }
    private String intentScope(){return ui.server.getText().trim().replaceAll("/+$","").toLowerCase(Locale.ROOT)+"|"+ui.user.getText().trim().toLowerCase(Locale.ROOT);}
    private void resolveIntent(){
        if(!confirm("Resolver intento anterior","Consulta primero Mis ventas y los recibos para comprobar si la venta ya quedó registrada. Descartar el identificador local no anula una venta y puede permitir otra. ¿Ya comprobaste el resultado y quieres descartar el intento local?"))return;
        try{saleIntents.clear(intentScope());ui.status("Intento local descartado. Las ventas registradas permanecen intactas.");}catch(Exception ex){ui.error(ex.getMessage());}
    }
    private void receipts(){
        current="Recibos";var number=new TextField(lastSale==null?"":lastSale);number.setPromptText("Número de venta; consúltalo en Mis ventas");
        ui.page("Recibos de venta","Consulta e imprime una venta ya registrada",card("Buscar recibo",field("Número de venta",number),primary("Ver e imprimir recibo",()->{if(number.getText().isBlank())ui.error("Indica el número de venta.");else showReceipt(number.getText().trim());}),text("La impresión no registra otra venta ni descuenta existencias.","muted")));
    }
    private void billing(){
        current="Facturación";var number=new TextField(lastSale==null?"":lastSale);number.setPromptText("Número de una venta registrada");
        var info=text("MODO DEMOSTRACIÓN\nPrepara los datos del negocio y del comprador a partir de una venta. La emisión real aún requiere habilitación, numeración, firma y un conector fiscal probado para cada emisor. No se transmite información ni se generan cargos desde esta pantalla.","muted");info.setWrapText(true);
        ui.page("Facturación electrónica","Preparación de datos · Emisión real no activada",card("Preparar una venta para revisión",info,field("Número de venta",number),primary("Preparar factura electrónica",()->{
            String sale=number.getText().trim();if(!sale.matches("[a-zA-Z0-9-]{1,64}")){ui.error("Indica un número de venta válido.");return;}
            ui.request("GET","/api/vendedor/ventas/"+sale+"/recibo",null,data->InvoicePreparation.show(ui,data,getHostServices()::showDocument));
        }),secondary("Información del servicio gratuito DIAN",()->{try{getHostServices().showDocument(InvoicePreparation.DIAN);}catch(Exception ex){ui.error("No se pudo abrir el navegador.");}})));
    }
    private void showReceipt(String number){
        if(!number.matches("[a-zA-Z0-9-]{1,64}")){ui.error("Revisa el número de venta.");return;}
        ui.request("GET","/api/vendedor/ventas/"+number+"/recibo",null,data->{
            var preview=SaleReceipt.preview(data);
            var dialog=new Dialog<Void>();dialog.setTitle("Recibo "+number);dialog.initOwner(ui.getScene().getWindow());dialog.getDialogPane().setContent(preview);
            var print=new ButtonType("Imprimir / PDF",ButtonBar.ButtonData.OTHER);var electronic=new ButtonType("Preparar factura electrónica",ButtonBar.ButtonData.OTHER);dialog.getDialogPane().getButtonTypes().addAll(electronic,print,ButtonType.CLOSE);
            dialog.getDialogPane().lookupButton(print).addEventFilter(javafx.event.ActionEvent.ACTION,e->{e.consume();try{if(SaleReceipt.print(data,number))ui.status("Recibo enviado a la impresora.");}catch(Exception ex){ui.error("No se pudo imprimir. Comprueba la impresora o selecciona Microsoft Print to PDF.");}});
            dialog.getDialogPane().lookupButton(electronic).addEventFilter(javafx.event.ActionEvent.ACTION,e->{e.consume();InvoicePreparation.show(ui,data,getHostServices()::showDocument);});
            dialog.showAndWait();
        });
    }
    private void productList(){current="Productos";ui.page("Productos","Precios sin IVA y existencias disponibles",card("Catálogo del punto de venta",empty("Conecta tu cuenta","Consulta aquí los productos del negocio.")));ui.request("GET","/api/vendedor/productos",null,r->{if(current.equals("Productos"))ui.page("Productos","Precios sin IVA y existencias disponibles",card("",searchable(table(r,new Column("Producto","nombre"),new Column("Categoría","categoria"),new Column("Stock","stock"),new Column("Precio sin IVA","precio",true)),"Buscar producto…")));});}
    private void history(){current="Mis ventas";ui.page("Mis ventas","Historial de compras registradas con tu cuenta",card("",empty("Sin datos cargados","Conecta tu cuenta para consultar tus ventas.")));ui.request("GET","/api/vendedor/mis-ventas",null,r->{if(current.equals("Mis ventas"))ui.page("Mis ventas","Historial de compras registradas con tu cuenta",card("",searchable(table(r,new Column("Número","numero"),new Column("Cliente","cliente_nombre"),new Column("Total","total",true),new Column("Estado","estado"),new Column("Fecha","creado_en")),"Buscar en mis ventas…")));});}
    private void orders() {
        current="Pedidos";ui.page("Pedidos por preparar","Marca un pedido listo para activar el aviso al cliente",card("",empty("Sin datos cargados","Conecta tu cuenta para consultar los pedidos.")));
        ui.request("GET","/api/vendedor/pedidos",null,r->{if(!current.equals("Pedidos"))return;
            var table=table(r,new Column("ID","id"),new Column("Pedido","numero"),new Column("Cliente","cliente_nombre"),new Column("Preparación","preparacion"));
            Button ready=primary("✓ Marcar listo y avisar",()->{var order=table.getSelectionModel().getSelectedItem();if(order==null){ui.error("Selecciona un pedido de la tabla.");return;}if(confirm("Pedido listo","¿Confirmas que "+order.path("numero").asText()+" está listo para recoger?"))ui.request("POST","/api/vendedor/pedidos/"+order.path("id").asLong()+"/listo",ui.json.createObjectNode(),result->orders());});
            ui.page("Pedidos por preparar","Marca un pedido listo para activar el aviso al cliente",card("",new VBox(16,row(secondary("↻ Actualizar",this::orders),ready),searchable(table,"Buscar pedido…"))));
        });
    }
    public static void main(String[] args){launch(args);}
}
