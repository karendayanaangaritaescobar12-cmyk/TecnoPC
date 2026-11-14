package com.tecnopc.vendedor;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.*;
import java.awt.print.*;
import java.util.*;
import static com.tecnopc.ui.DesktopShell.money;

/** Prints persisted server values; this is a sale receipt, not an electronic tax invoice. */
public final class SaleReceipt {
 private SaleReceipt(){}
 public static javafx.scene.Node preview(JsonNode data){
  var pages=new javafx.scene.layout.VBox(18);pages.setStyle("-fx-padding: 18; -fx-background-color: #e5ebf3;");
  for(var page:ReceiptRenderer.pages(data)){
   int w=page.getWidth(),h=page.getHeight();var image=new javafx.scene.image.WritableImage(w,h);
   image.getPixelWriter().setPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),page.getRGB(0,0,w,h,null,0,w),0,w);
   var view=new javafx.scene.image.ImageView(image);view.getStyleClass().add("receipt-page");view.setPreserveRatio(true);view.setFitWidth(650);view.setAccessibleText(format(data));pages.getChildren().add(view);
  }
  var scroll=new javafx.scene.control.ScrollPane(pages);scroll.setFitToWidth(true);scroll.setPrefViewportWidth(700);scroll.setPrefViewportHeight(700);return scroll;
 }
 public static boolean print(JsonNode data,String number)throws PrinterException {
  var pages=ReceiptRenderer.pages(data);var job=PrinterJob.getPrinterJob();job.setJobName("TecnoPC - "+number);
  job.setPrintable((graphics,page,index)->{
   if(index<0||index>=pages.size())return Printable.NO_SUCH_PAGE;
   var image=pages.get(index);Graphics2D g=(Graphics2D)graphics.create();
   try{double scale=Math.min(page.getImageableWidth()/image.getWidth(),page.getImageableHeight()/image.getHeight());g.translate(page.getImageableX()+(page.getImageableWidth()-image.getWidth()*scale)/2,page.getImageableY());g.scale(scale,scale);g.drawImage(image,0,0,null);}finally{g.dispose();}return Printable.PAGE_EXISTS;
  });if(!job.printDialog())return false;job.print();return true;
 }
 public static String format(JsonNode data){
  var v=data.path("venta");StringBuilder out=new StringBuilder("TecnoPC\nRECIBO DE VENTA\n");
  out.append("Número: ").append(v.path("numero").asText()).append("\nFecha: ").append(v.path("creado_en").asText()).append("\nCliente: ").append(v.path("cliente_nombre").asText()).append("\nDocumento: ").append(v.path("cliente_documento").asText("")).append("\nVendedor: ").append(v.path("vendedor_usuario").asText()).append("\nEstado: ").append(v.path("estado").asText()).append("\n\nPRODUCTOS (precios sin IVA)\n");
  for(var item:data.path("items"))out.append(item.path("nombre_producto").asText()).append("\n  ").append(item.path("cantidad").asInt()).append(" x ").append(money(item.path("precio_unitario").decimalValue())).append(" = ").append(money(item.path("subtotal").decimalValue())).append("\n");
  out.append("\nSubtotal: ").append(money(v.path("subtotal").decimalValue())).append("\nIVA: ").append(money(v.path("iva").decimalValue())).append("\nTOTAL COP: ").append(money(v.path("total").decimalValue())).append("\n\nGracias por tu compra.\nEste recibo no sustituye una factura electrónica.\n");return out.toString();
 }
 public static boolean print(String content,String number)throws PrinterException {
  PrinterJob job=PrinterJob.getPrinterJob();job.setJobName("TecnoPC - "+number);
  job.setPrintable((graphics,page,index)->{
   Graphics2D g=(Graphics2D)graphics;g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,10));g.setColor(Color.BLACK);
   FontMetrics metrics=g.getFontMetrics();int columns=Math.max(10,(int)(page.getImageableWidth()/metrics.charWidth('M')));
   var lines=new ArrayList<String>();for(String line:content.split("\\R",-1)){if(line.isEmpty())lines.add("");else for(int start=0;start<line.length();start+=columns)lines.add(line.substring(start,Math.min(line.length(),start+columns)));}
   int perPage=Math.max(1,(int)(page.getImageableHeight()/metrics.getHeight())-2);int start=index*perPage;if(start>=lines.size())return Printable.NO_SUCH_PAGE;
   for(int i=start;i<Math.min(start+perPage,lines.size());i++)g.drawString(lines.get(i),(float)page.getImageableX(),(float)(page.getImageableY()+metrics.getAscent()+(i-start)*metrics.getHeight()));
   g.drawString("Página "+(index+1),(float)page.getImageableX(),(float)(page.getImageableY()+page.getImageableHeight()-2));return Printable.PAGE_EXISTS;
  });if(!job.printDialog())return false;job.print();return true;
 }
}
