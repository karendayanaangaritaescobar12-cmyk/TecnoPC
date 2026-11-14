package com.tecnopc.tecnico;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecnopc.ui.DesktopShell;
import static com.tecnopc.ui.DesktopShell.*;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import java.math.BigDecimal;
import java.util.*;

public final class WorkshopWorkspace extends Application {
    private DesktopShell ui;
    private String current="Recepción";
    private long selectedId;
    private final List<JsonNode> repairs=new ArrayList<>();
    private final TextField equipment=new TextField(),model=new TextField(),serial=new TextField(),accessories=new TextField();
    private final Label code=text("Sin asignar","code-value"),receiptInfo=text("El código se genera al registrar el equipo.","muted");
    private static final Map<String,String> STATES=Map.of("recibido","Recibido","diagnostico","Diagnóstico","esperando_autorizacion","Esperando autorización","en_reparacion","En reparación","listo","Listo para recoger","entregado","Entregado","cancelado","Cancelado");
    private static final Map<String,List<String>> NEXT=Map.of("recibido",List.of("diagnostico","cancelado"),"diagnostico",List.of("esperando_autorizacion","cancelado"),"esperando_autorizacion",List.of("en_reparacion","cancelado"),"en_reparacion",List.of("listo","cancelado"),"listo",List.of("entregado"),"entregado",List.of(),"cancelado",List.of());
    @Override public void start(Stage stage) {
        ui=new DesktopShell("tecnico","Taller técnico");ui.nav("▣   Recepción",this::reception);ui.nav("⚒   Diagnóstico",this::diagnosis);ui.nav("▤   Reparaciones",this::repairList);ui.nav("◷   Historial",this::history);ui.nav("◈   Equipos por estado",this::byState);ui.onDisconnect(()->{repairs.clear();selectedId=0;equipment.clear();model.clear();serial.clear();accessories.clear();code.setText("Sin asignar");receiptInfo.setText("El código se genera al registrar el equipo.");});ui.onConnect(this::reception);reception();ui.show(stage,"TecnoPC — Taller técnico",getParameters()==null?List.of():getParameters().getRaw());
    }
    private ComboBox<JsonNode> selector(List<JsonNode> rows) {
        var combo=new ComboBox<JsonNode>(FXCollections.observableArrayList(rows));combo.setMaxWidth(Double.MAX_VALUE);combo.setPromptText("Selecciona un registro");combo.setConverter(new StringConverter<>(){public String toString(JsonNode x){if(x==null)return "";return x.has("codigo_ingreso")?x.path("codigo_ingreso").asText()+" · "+x.path("cliente").asText()+" · "+x.path("equipo").asText():"SOL-"+x.path("id").asText()+" · "+x.path("nombre").asText();}public JsonNode fromString(String value){return null;}});return combo;
    }
    private void reception() {
        current="Recepción";
        ComboBox<JsonNode> requests=selector(List.of());Label customer=text("Selecciona una solicitud de servicio pendiente.","muted");requests.setOnAction(e->{var value=requests.getValue();if(value!=null)customer.setText(value.path("nombre").asText()+" · "+value.path("telefono").asText()+"\n"+value.path("detalle").asText());});
        equipment.setPromptText("Portátil, PC de escritorio…");model.setPromptText("Marca y modelo");serial.setPromptText("Número de serie (opcional)");accessories.setPromptText("Cargador, estuche y otros accesorios");
        Button receive=primary("✓ Registrar ingreso",()->{});receive.setOnAction(e->{var request=requests.getValue();if(request==null||equipment.getText().isBlank()){ui.error("Selecciona una solicitud y describe el equipo recibido.");return;}var payload=ui.json.createObjectNode().put("solicitudId",request.path("id").asLong()).put("equipo",equipment.getText().trim()).put("marcaModelo",model.getText().trim()).put("serie",serial.getText().trim()).put("accesorios",accessories.getText().trim());receive.setDisable(true);ui.request("POST","/api/tecnico/reparaciones",payload,r->{code.setText(r.path("codigoIngreso").asText());receiptInfo.setText("Orden #"+r.path("id").asText()+" · "+request.path("nombre").asText()+"\nEntrega este código al cliente para su seguimiento.");equipment.clear();model.clear();serial.clear();accessories.clear();requests.getItems().remove(request);},()->receive.setDisable(false));});
        VBox form=card("Datos del equipo",field("Solicitud del cliente",requests),customer,field("Equipo",equipment),row(field("Marca y modelo",model),field("Número de serie",serial)),field("Accesorios recibidos",accessories),receive);form.setPrefWidth(650);
        var copy=secondary("Copiar código",()->{if(code.getText().equals("Sin asignar")){ui.error("Registra primero el ingreso para obtener el código.");return;}ClipboardContent content=new ClipboardContent();content.putString(code.getText());Clipboard.getSystemClipboard().setContent(content);ui.status("Código copiado para entregar al cliente.");});
        VBox ticket=card("Comprobante de recepción",text("◈","empty-symbol"),text("Código de ingreso","field-label"),code,receiptInfo,copy,new Separator(),text("Seguimiento seguro","card-title"),text("El cliente consulta el avance con el código de su comprobante. Los diagnósticos privados permanecen en el taller.","muted"));ticket.setPrefWidth(315);ticket.setMinWidth(285);
        ui.page("Recepción de equipo","Registra el ingreso y entrega al cliente su código de seguimiento",scroll(row(form,ticket)));
        if(ui.credentialsPresent())ui.request("GET","/api/tecnico/solicitudes",null,r->{if(!current.equals("Recepción"))return;r.forEach(requests.getItems()::add);if(r.isEmpty())customer.setText("No hay solicitudes pendientes de recibir.");});
    }
    private void loadRepairs(Runnable after) {ui.request("GET","/api/tecnico/reparaciones",null,r->{repairs.clear();r.forEach(repairs::add);after.run();});}
    private void diagnosis() {
        current="Diagnóstico";ui.page("Diagnóstico y reparación","Actualiza el diagnóstico, la autorización y el trabajo realizado",card("",empty("Selecciona una orden","Conecta tu cuenta para cargar las reparaciones.")));
        if(ui.credentialsPresent())loadRepairs(()->{if(current.equals("Diagnóstico"))diagnosisForm();});else diagnosisForm();
    }
    private void diagnosisForm() {
        var order=selector(repairs);var state=new ComboBox<String>();state.setMaxWidth(Double.MAX_VALUE);state.setConverter(new StringConverter<>(){public String toString(String s){return s==null?"":STATES.getOrDefault(s,s);}public String fromString(String s){return null;}});
        TextArea diagnosis=new TextArea(),work=new TextArea();diagnosis.setPrefRowCount(4);work.setPrefRowCount(4);diagnosis.setPromptText("Describe el diagnóstico técnico…");work.setPromptText("Detalla el trabajo realizado…");TextField budget=new TextField();budget.setPromptText("Valor en COP");CheckBox authorized=new CheckBox("Autorización del cliente registrada");Label summary=text("Selecciona una orden para ver sus datos.","muted"),badge=text("Orden de taller","step-pill");
        order.setOnAction(e->{var data=order.getValue();if(data==null)return;selectedId=data.path("id").asLong();String currentState=data.path("estado").asText();state.getItems().setAll(currentState);state.getItems().addAll(NEXT.getOrDefault(currentState,List.of()));state.setValue(currentState);diagnosis.setText(data.path("diagnostico").asText(""));work.setText(data.path("trabajo_realizado").asText(""));budget.setText(data.path("presupuesto").isNull()?"":data.path("presupuesto").asText(""));authorized.setSelected(data.path("autorizado").asBoolean());summary.setText(data.path("cliente").asText()+"\n"+data.path("equipo").asText()+" · "+data.path("marca_modelo").asText("")+"\n"+data.path("codigo_ingreso").asText());badge.setText(STATES.getOrDefault(currentState,currentState));});
        Button save=primary("✓ Guardar seguimiento",()->{});save.setOnAction(e->{var data=order.getValue();if(data==null||state.getValue()==null){ui.error("Selecciona una orden y su estado.");return;}var payload=ui.json.createObjectNode().put("estado",state.getValue()).put("diagnostico",diagnosis.getText()).put("trabajoRealizado",work.getText()).put("autorizado",authorized.isSelected());try{if(!budget.getText().isBlank()){var value=new BigDecimal(budget.getText().trim());if(value.signum()<0)throw new NumberFormatException();payload.put("presupuesto",value);}}catch(NumberFormatException ex){ui.error("El presupuesto debe ser un número positivo en COP.");return;}save.setDisable(true);ui.request("PATCH","/api/tecnico/reparaciones/"+data.path("id").asLong(),payload,r->{ui.status("Seguimiento guardado. Los avisos autorizados se encolan al quedar listo.");diagnosis();},()->save.setDisable(false));});
        VBox editor=card("Seguimiento técnico",field("Orden de reparación",order),row(field("Estado",state),field("Presupuesto · COP",budget)),field("Diagnóstico",diagnosis),authorized,field("Trabajo realizado",work),new HBox(10,save,secondary("Ver historial",this::history)));editor.setPrefWidth(650);
        VBox detail=card("Orden seleccionada",badge,summary,new Separator(),text("Flujo de trabajo","card-title"),text("Recibido → Diagnóstico → Autorización → Reparación → Listo → Entregado","muted"),text("Antes de iniciar la reparación registra el diagnóstico, el presupuesto y la autorización del cliente.","muted"));detail.setPrefWidth(310);detail.setMinWidth(270);
        ui.page("Diagnóstico y reparación","Los estados disponibles respetan el flujo de trabajo de la orden",scroll(row(editor,detail)));
        repairs.stream().filter(r->r.path("id").asLong()==selectedId).findFirst().ifPresent(order::setValue);
    }
    private void repairList() {
        current="Reparaciones";ui.page("Reparaciones","Órdenes de taller y su estado actual",card("",empty("Sin datos cargados","Conecta tu cuenta para cargar las reparaciones.")));
        if(ui.credentialsPresent())loadRepairs(()->{if(!current.equals("Reparaciones"))return;var array=ui.json.createArrayNode();repairs.forEach(array::add);var list=table(array,new Column("Código","codigo_ingreso"),new Column("Cliente","cliente"),new Column("Equipo","equipo"),new Column("Estado","estado"),new Column("Presupuesto","presupuesto",true));var open=primary("Abrir diagnóstico",()->{var data=list.getSelectionModel().getSelectedItem();if(data==null){ui.error("Selecciona una orden de la tabla.");return;}selectedId=data.path("id").asLong();diagnosis();});ui.page("Reparaciones","Selecciona una orden para continuar su trabajo",card("",new VBox(16,row(secondary("↻ Actualizar",this::repairList),open),searchable(list,"Buscar cliente, código o equipo…"))));});
    }
    private void history() {
        current="Historial";ui.page("Historial del equipo","Registro de actualizaciones y responsables",card("",empty("Selecciona una orden","Carga una reparación para consultar su historial.")));
        if(ui.credentialsPresent())loadRepairs(()->{if(!current.equals("Historial"))return;var order=selector(repairs);VBox events=new VBox();order.setOnAction(e->{var selected=order.getValue();if(selected==null)return;selectedId=selected.path("id").asLong();ui.request("GET","/api/tecnico/reparaciones/"+selectedId+"/eventos",null,r->{if(current.equals("Historial"))events.getChildren().setAll(searchable(table(r,new Column("Fecha","creado_en"),new Column("Responsable","usuario"),new Column("Evento","evento"),new Column("Detalle","detalle")),"Buscar en el historial…"));});});ui.page("Historial del equipo","Registro de actualizaciones y responsables",card("",field("Orden",order),events));repairs.stream().filter(r->r.path("id").asLong()==selectedId).findFirst().ifPresent(order::setValue);});
    }
    private void byState() {
        current="Estados";VBox content=new VBox(16,empty("Sin datos cargados","Conecta tu cuenta para ver la distribución del taller."));ui.page("Equipos por estado","Distribución de las órdenes recientes del taller",scroll(content));if(ui.credentialsPresent())loadRepairs(()->{if(!current.equals("Estados"))return;content.getChildren().clear();for(String state:List.of("recibido","diagnostico","esperando_autorizacion","en_reparacion","listo","entregado","cancelado")){long count=repairs.stream().filter(r->r.path("estado").asText().equals(state)).count();content.getChildren().add(card("",row(text(STATES.get(state),"card-title"),text(Long.toString(count),"code-value"))));}});
    }
    public static void main(String[] args){launch(args);}
}
