package com.tecnopc.vendedor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tecnopc.ui.DesktopShell;
import static com.tecnopc.ui.DesktopShell.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Consumer;

/** Preparation only: no signing, numbering, submission or declaration of DIAN validation. */
public final class InvoicePreparation {
 public static final String DIAN="https://micrositios.dian.gov.co/sistema-de-facturacion-electronica/facturacion-gratuita-dian/";
 private InvoicePreparation(){}
 public static ObjectNode draft(ObjectMapper json,JsonNode receipt,String company,String nit,String dv,String buyerName,String buyerType,String buyerDocument,String buyerEmail){
  var draft=json.createObjectNode();draft.put("tipo","BORRADOR_PARA_REVISION");draft.put("modo","demostracion");draft.put("emitida",false);draft.put("validada_dian",false);
  draft.put("advertencia","No es factura electrónica ni XML UBL. Este JSON no es una plantilla importable en la DIAN. No contiene CUFE ni numeración fiscal.");
  draft.putObject("emisor_declarado").put("nombre_razon_social",company).put("nit",nit).put("dv",dv);
  draft.putObject("comprador_declarado").put("nombre_razon_social",buyerName).put("tipo_documento",buyerType).put("numero_documento",buyerDocument).put("correo",buyerEmail);
  draft.set("venta_registrada",receipt.path("venta").deepCopy());draft.set("productos",receipt.path("items").deepCopy());
  var pending=draft.putArray("pendientes_para_emision");
  for(String task:new String[]{"Verificar datos y responsabilidades tributarias de emisor y comprador","Completar datos fiscales adicionales exigidos para el caso","Habilitar el emisor y su modalidad de software ante la DIAN","Configurar numeración autorizada, vigencias y prefijos","Configurar firma digital y ambiente de pruebas","Implementar y probar el proveedor o el conector elegido","Firmar, transmitir, consultar validación y entregar el documento por el canal acordado"})pending.add(task);
  return draft;
 }
 public static void show(DesktopShell ui,JsonNode receipt,Consumer<String> openUrl){
  var dialog=new Dialog<Void>();dialog.setTitle("Preparación de factura electrónica");dialog.initOwner(ui.getScene().getWindow());
  var company=new TextField();var nit=new TextField();var dv=new TextField();
  var buyer=new TextField(receipt.path("venta").path("cliente_nombre").asText());
  var type=new ComboBox<String>();type.getItems().addAll("CC","NIT","CE","PASAPORTE");type.setValue("CC");
  var document=new TextField(receipt.path("venta").path("cliente_documento").asText(""));var email=new TextField();
  var explanation=text("MODO DEMOSTRACIÓN · NO EMITE FACTURAS\nPrepara datos de una venta para revisión. Emitir requiere habilitar cada negocio y configurar el sistema fiscal. Guardar un borrador no transmite información a la DIAN.","muted");explanation.setWrapText(true);
  var error=text("","error");error.setWrapText(true);
  var form=new VBox(13,explanation,text("Emisor del negocio","card-title"),field("Nombre o razón social según RUT",company),new HBox(12,field("NIT sin dígito de verificación",nit),field("DV",dv)),text("Comprador","card-title"),field("Nombre o razón social",buyer),new HBox(12,field("Tipo de documento declarado",type),field("Número de documento",document)),field("Correo (opcional para este borrador)",email),error);form.setPrefWidth(590);
  dialog.getDialogPane().setContent(scroll(form));dialog.getDialogPane().setPrefHeight(720);
  var save=new ButtonType("Guardar borrador",ButtonBar.ButtonData.OTHER);var dian=new ButtonType("Servicio gratuito DIAN",ButtonBar.ButtonData.OTHER);dialog.getDialogPane().getButtonTypes().addAll(dian,save,ButtonType.CLOSE);
  dialog.getDialogPane().lookupButton(dian).addEventFilter(javafx.event.ActionEvent.ACTION,e->{e.consume();try{openUrl.accept(DIAN);}catch(Exception ex){error.setText("No se pudo abrir el navegador. Consulta dian.gov.co.");}});
  dialog.getDialogPane().lookupButton(save).addEventFilter(javafx.event.ActionEvent.ACTION,e->{
   e.consume();if(company.getText().isBlank()||company.getText().length()>200||!nit.getText().matches("[0-9]{5,15}")||!dv.getText().matches("[0-9]")||buyer.getText().isBlank()||buyer.getText().length()>200||!document.getText().matches("[a-zA-Z0-9-]{3,30}")||email.getText().length()>254||(!email.getText().isBlank()&&!email.getText().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))){error.setText("Revisa nombre del emisor, NIT, DV y datos del comprador. La validez fiscal de los datos requiere revisión adicional.");return;}
   var chooser=new FileChooser();chooser.setTitle("Guardar borrador de facturación (no fiscal)");chooser.setInitialFileName("borrador-"+receipt.path("venta").path("numero").asText().replaceAll("[^a-zA-Z0-9-]","_")+".json");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Datos JSON","*.json"));var file=chooser.showSaveDialog(dialog.getDialogPane().getScene().getWindow());if(file==null)return;
   try{var data=draft(ui.json,receipt,company.getText().trim(),nit.getText(),dv.getText(),buyer.getText().trim(),type.getValue(),document.getText(),email.getText().trim());Files.writeString(file.toPath(),ui.json.writerWithDefaultPrettyPrinter().writeValueAsString(data),StandardCharsets.UTF_8);error.setText("Borrador guardado. No se emitió ni transmitió ninguna factura.");ui.status("Borrador no fiscal guardado.");}catch(Exception ex){error.setText("No se pudo guardar. Revisa la carpeta de destino.");}
  });dialog.showAndWait();
 }
}
