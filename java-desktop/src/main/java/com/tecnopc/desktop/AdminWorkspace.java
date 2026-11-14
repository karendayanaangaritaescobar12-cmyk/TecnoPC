package com.tecnopc.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tecnopc.ui.DesktopShell;
import static com.tecnopc.ui.DesktopShell.*;
import javafx.application.Application;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.math.BigDecimal;
import java.util.*;

public final class AdminWorkspace extends Application {
    private DesktopShell ui;
    private TableView<JsonNode> selectedTable;
    private String current="Inicio";
    @Override public void start(Stage stage) {
        ui=new DesktopShell("admin","Administración");
        ui.nav("⌂   Inicio",this::dashboard);
        ui.nav("▤   Ventas",()->data("Ventas","Compras registradas y su estado","/api/admin/compras",new Column("Número","numero"),new Column("Cliente","cliente_nombre"),new Column("Total","total",true),new Column("Estado","estado"),new Column("Fecha","creado_en")));
        ui.nav("▦   Inventario",this::inventory);
        ui.nav("♙   Equipo",()->data("Equipo","Personal registrado en el negocio","/api/admin/empleados",new Column("ID","id"),new Column("Nombre","nombres"),new Column("Apellido","apellidos"),new Column("Cargo","cargo"),new Column("Estado","estado")));
        ui.nav("⚿   Usuarios y roles",this::users);
        ui.nav("⚒   Servicio técnico",()->data("Servicio técnico","Órdenes y seguimiento del taller","/api/tecnico/reparaciones",new Column("Código","codigo_ingreso"),new Column("Cliente","cliente"),new Column("Equipo","equipo"),new Column("Estado","estado"),new Column("Técnico","tecnico_usuario")));
        ui.nav("⚠   Alertas de stock",this::stockAlerts);
        ui.nav("✉   Avisos a clientes",this::notifications);
        ui.onConnect(this::dashboard);dashboard();ui.show(stage,"TecnoPC — Administración",getParameters()==null?List.of():getParameters().getRaw());
    }
    private void stockAlerts(){data("Alertas de stock","Productos que requieren reposición","/api/admin/alertas",new Column("Producto","nombre"),new Column("Stock","stock"),new Column("Mínimo","stock_minimo"));}
    private void inventory(){
        data("Inventario","Productos disponibles en el catálogo y configurador","/api/admin/productos",new Column("ID","id"),new Column("Producto","nombre"),new Column("Categoría","categoria"),new Column("SKU","sku"),new Column("Stock","stock"),new Column("Mínimo","stock_minimo"),new Column("Precio sin IVA","precio",true));
        ui.prepend(new HBox(12,primary("＋ Agregar producto",()->productDialog(null)),secondary("Editar producto",()->{var item=selection();if(item!=null)productDialog(item);}),secondary("Actualizar inventario",this::inventory)));
    }
    private void productDialog(JsonNode item){
        boolean editing=item!=null;
        var dialog=new Dialog<ObjectNode>();dialog.setTitle(editing?"Editar producto":"Agregar producto");dialog.initOwner(ui.getScene().getWindow());
        var name=new TextField(editing?item.path("nombre").asText():"");name.setId("producto-nombre");
        var category=new ComboBox<String>();category.getItems().addAll("pc","laptop","cpu","gpu","ram","ssd","mb","psu","case");category.setValue(editing?item.path("categoria").asText():"pc");
        var sku=new TextField(editing?item.path("sku").asText(""):"");
        var specs=new TextField(editing?item.path("especificaciones").asText(""):"");
        var stock=new TextField(editing?item.path("stock").asText():"0");
        var minimum=new TextField(editing?item.path("stock_minimo").asText():"2");
        var cost=new TextField(editing?item.path("costo").asText():"0.00");
        var price=new TextField(editing?item.path("precio").asText():"");price.setPromptText("Ejemplo: 250000.00");
        var error=text("","error");error.setWrapText(true);
        var form=new VBox(12,field("Nombre (máximo 80 caracteres)",name),field("Categoría",category),text("pc: escritorio · laptop: portátil · cpu: procesador · gpu: gráfica\nram: memoria · ssd: almacenamiento · mb: placa · psu: fuente · case: gabinete","muted"),field("SKU único (opcional)",sku),field("Especificaciones: modelo, socket, DDR… (máximo 160)",specs),new HBox(12,field("Existencias",stock),field("Stock mínimo",minimum)),new HBox(12,field("Costo COP",cost),field("Precio COP sin IVA",price)),error);form.setPrefWidth(530);
        dialog.getDialogPane().setContent(scroll(form));dialog.getDialogPane().setPrefHeight(680);
        var save=new ButtonType("Guardar producto",ButtonBar.ButtonData.OK_DONE);dialog.getDialogPane().getButtonTypes().addAll(save,ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(save).addEventFilter(javafx.event.ActionEvent.ACTION,e->{try{productPayload(name,category,sku,specs,stock,minimum,cost,price,item);}catch(IllegalArgumentException ex){error.setText(ex.getMessage());e.consume();}});
        dialog.setResultConverter(button->button==save?productPayload(name,category,sku,specs,stock,minimum,cost,price,item):null);
        dialog.showAndWait().ifPresent(payload->ui.request(editing?"PUT":"POST","/api/admin/productos"+(editing?"/"+item.path("id").asLong():""),payload,r->{inventory();ui.status(editing?"Producto actualizado.":"Producto creado. Ya está disponible en el catálogo.");}));
    }
    private ObjectNode productPayload(TextField name,ComboBox<String> category,TextField sku,TextField specs,TextField stock,TextField minimum,TextField cost,TextField price,JsonNode item){
        if(name.getText().isBlank()||name.getText().trim().length()>80||sku.getText().length()>30||specs.getText().length()>160)throw new IllegalArgumentException("Revisa el nombre y los límites de texto.");
        try{
            int count=Integer.parseInt(stock.getText().trim()),min=Integer.parseInt(minimum.getText().trim());
            BigDecimal c=new BigDecimal(cost.getText().trim()),p=new BigDecimal(price.getText().trim());
            if(count<0||count>1000000||min<0||min>1000000||c.signum()<0||p.signum()<=0||c.scale()>2||p.scale()>2||c.compareTo(new BigDecimal("9999999999.99"))>0||p.compareTo(new BigDecimal("9999999999.99"))>0)throw new NumberFormatException();
            var data=ui.json.createObjectNode();data.put("nombre",name.getText().trim());data.put("categoria",category.getValue());data.put("sku",sku.getText().trim());data.put("especificaciones",specs.getText().trim());data.put("stock",count);data.put("stockMinimo",min);data.put("costo",c);data.put("precio",p);if(item!=null)data.put("stockAnterior",item.path("stock").asInt());return data;
        }catch(NumberFormatException ex){throw new IllegalArgumentException("Usa existencias enteras entre 0 y 1000000 y precios válidos con hasta dos decimales, separados con punto.");}
    }
    private void dashboard() {
        current="Inicio";
        var metrics=row(metric("Ventas registradas","—","Compras del negocio"),metric("Productos","—","Catálogo e inventario"),metric("Stock bajo","—","Pendientes de reposición"),metric("Personal activo","—","Equipo de trabajo"));
        VBox chartCard=card("Actividad de ventas",empty("Sin datos cargados","Conecta tu cuenta para ver las compras recientes."));
        VBox alerts=card("Atención del negocio",text("Consulta las alertas de inventario y los avisos pendientes desde la navegación lateral.","muted"),secondary("Ver alertas de stock",this::stockAlerts));
        HBox lower=row(chartCard,alerts);chartCard.setPrefWidth(620);alerts.setPrefWidth(290);
        ui.page("Panel del administrador","Una vista clara del estado de tu negocio",scroll(new VBox(20,metrics,lower)));
        if(!ui.credentialsPresent())return;
        ui.request("GET","/api/admin/resumen",null,r->{if(!current.equals("Inicio"))return;metrics.getChildren().setAll(metric("Ventas registradas",r.path("compras").asText("0"),"Compras del negocio"),metric("Productos",r.path("productos").asText("0"),"Catálogo e inventario"),metric("Stock bajo",r.path("stockBajo").asText("0"),"Pendientes de reposición"),metric("Personal activo",r.path("empleadosActivos").asText("0"),"Equipo de trabajo"));});
        ui.request("GET","/api/admin/compras",null,r->{if(!current.equals("Inicio"))return;chartCard.getChildren().setAll(text("Actividad de ventas","card-title"),text("Importes de las compras recientes cargadas","muted"));if(r.isEmpty()){chartCard.getChildren().add(empty("Aún no hay ventas","Las compras registradas aparecerán en este panel."));return;}TreeMap<String,BigDecimal> amounts=new TreeMap<>();r.forEach(x->{if(x.path("estado").asText().equals("registrada")){String date=x.path("creado_en").asText();amounts.merge(date.substring(0,Math.min(10,date.length())),x.path("total").decimalValue(),BigDecimal::add);}});BarChart<String,Number> chart=new BarChart<>(new CategoryAxis(),new NumberAxis());chart.setLegendVisible(false);chart.setAnimated(false);chart.setPrefHeight(330);var series=new XYChart.Series<String,Number>();amounts.forEach((date,total)->series.getData().add(new XYChart.Data<>(date,total)));chart.getData().add(series);chartCard.getChildren().add(chart);});
        ui.request("GET","/api/admin/alertas",null,r->{if(!current.equals("Inicio"))return;alerts.getChildren().setAll(text("Alertas de inventario","card-title"));if(r.isEmpty())alerts.getChildren().add(text("✓ No hay productos con stock bajo.","muted"));else r.forEach(x->alerts.getChildren().add(card("",text("⚠  "+x.path("nombre").asText(),"field-label"),text(x.path("stock").asText()+" unidades · mínimo "+x.path("stock_minimo").asText(),"muted"))));});
    }
    private void data(String title,String note,String endpoint,Column... columns) {
        current=title;selectedTable=table(null,columns);
        var content=new VBox(16,row(text("Registros del negocio","card-title"),secondary("↻ Actualizar",()->{if(title.equals("Inventario"))inventory();else data(title,note,endpoint,columns);})),searchable(selectedTable,"Buscar en los registros…"));VBox.setVgrow(content.getChildren().getLast(),Priority.ALWAYS);
        ui.page(title,note,card("",content));
        if(ui.credentialsPresent())ui.request("GET",endpoint,null,r->{if(!current.equals(title))return;selectedTable=table(r,columns);content.getChildren().set(1,searchable(selectedTable,"Buscar en los registros…"));VBox.setVgrow(content.getChildren().getLast(),Priority.ALWAYS);});
    }
    private void users() {
        data("Usuarios y roles","Cuentas individuales y permisos de acceso","/api/admin/usuarios",new Column("ID","id"),new Column("Usuario","usuario"),new Column("Rol","rol"),new Column("Activa","activo"),new Column("Creación","creado_en"));
        ui.prepend(new HBox(10,primary("＋ Crear cuenta",this::createUser),secondary("Editar permisos",this::editUser),secondary("Cambiar contraseña",this::changePassword)));
    }
    private JsonNode selection() {if(selectedTable==null||selectedTable.getSelectionModel().getSelectedItem()==null){ui.error("Selecciona primero una fila de la tabla.");return null;}return selectedTable.getSelectionModel().getSelectedItem();}
    private void createUser() {
        var username=new TextField();var password=new PasswordField();var role=new ComboBox<String>();role.getItems().addAll("VENDEDOR","TECNICO","ADMIN");role.setValue("VENDEDOR");
        ObjectNode result=accountDialog("Crear cuenta",username,password,role,null);if(result!=null)ui.request("POST","/api/admin/usuarios",result,r->users());
    }
    private void editUser() {
        var selected=selection();if(selected==null)return;var role=new ComboBox<String>();role.getItems().addAll("VENDEDOR","TECNICO","ADMIN");role.setValue(selected.path("rol").asText());var active=new CheckBox("Cuenta activa");active.setSelected(selected.path("activo").asBoolean());
        ObjectNode result=accountDialog("Permisos de "+selected.path("usuario").asText(),null,null,role,active);if(result!=null)ui.request("PATCH","/api/admin/usuarios/"+selected.path("id").asLong(),result,r->users());
    }
    private void changePassword() {
        var selected=selection();if(selected==null)return;var password=new PasswordField();ObjectNode result=accountDialog("Contraseña de "+selected.path("usuario").asText(),null,password,null,null);if(result!=null)ui.request("PUT","/api/admin/usuarios/"+selected.path("id").asLong()+"/clave",result,r->users());
    }
    private ObjectNode accountDialog(String name,TextField username,PasswordField password,ComboBox<String> role,CheckBox active) {
        Dialog<ObjectNode> dialog=new Dialog<>();dialog.setTitle(name);dialog.setHeaderText(name);dialog.initOwner(ui.getScene().getWindow());VBox form=new VBox(14);form.setMinWidth(350);
        if(username!=null)form.getChildren().add(field("Usuario (3–60 caracteres)",username));if(password!=null)form.getChildren().add(field("Contraseña (8+ caracteres)",password));if(role!=null)form.getChildren().add(field("Rol",role));if(active!=null)form.getChildren().add(active);var error=text("","error");form.getChildren().add(error);dialog.getDialogPane().setContent(form);
        var save=new ButtonType("Guardar",ButtonBar.ButtonData.OK_DONE);dialog.getDialogPane().getButtonTypes().addAll(save,ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(save).addEventFilter(javafx.event.ActionEvent.ACTION,e->{if(username!=null&&!username.getText().matches("[a-zA-Z0-9._-]{3,60}")){error.setText("Revisa el nombre de usuario.");e.consume();}if(password!=null&&(password.getText().length()<8||password.getText().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)){error.setText("La clave necesita 8+ caracteres y hasta 72 bytes.");e.consume();}});
        dialog.setResultConverter(button->{if(button!=save)return null;var data=ui.json.createObjectNode();if(username!=null)data.put("usuario",username.getText());if(password!=null)data.put("password",password.getText());if(role!=null)data.put("rol",role.getValue());if(active!=null)data.put("activo",active.isSelected());return data;});return dialog.showAndWait().orElse(null);
    }
    private void notifications() {
        data("Avisos a clientes","Aceptado significa recibido por el proveedor; no confirma entrega al cliente","/api/admin/notificaciones",new Column("ID","id"),new Column("Canal","canal"),new Column("Evento","evento"),new Column("Referencia","referencia"),new Column("Estado","estado"),new Column("Intentos","intentos"),new Column("Error","ultimo_error"));
        ui.prepend(secondary("Reintentar aviso seleccionado",()->{var selected=selection();if(selected==null)return;if(!confirm("Reintentar aviso","Revisa el proveedor antes de continuar: un envío ambiguo podría duplicarse. ¿Reencolar este aviso?"))return;var payload=ui.json.createObjectNode().put("confirmarPosibleDuplicado",true);ui.request("POST","/api/admin/notificaciones/"+selected.path("id").asText()+"/reintentar",payload,r->notifications());}));
    }
    public static void main(String[] args){launch(args);}
}
