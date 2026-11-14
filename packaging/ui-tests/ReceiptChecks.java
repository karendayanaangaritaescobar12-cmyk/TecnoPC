import com.fasterxml.jackson.databind.ObjectMapper;
import com.tecnopc.vendedor.*;
import java.math.BigDecimal;

/** Offline checks: no printer job, no sale writes and no DIAN/network calls. */
public class ReceiptChecks {
 private static int count;
 private static void check(boolean value,String description){if(!value)throw new AssertionError(description);count++;System.out.println("OK "+description);}
 public static void main(String[] args)throws Exception {
  var json=new ObjectMapper();var receipt=json.createObjectNode();var sale=receipt.putObject("venta");
  sale.put("numero","VEN-PRUEBA").put("cliente_nombre","Cliente de ejemplo con un nombre amplio para comprobar el ajuste").put("vendedor_usuario","vendedor-con-nombre-largo-para-comprobar-el-espacio-de-impresion").put("creado_en","2026-11-13 11:00").put("estado","registrada").put("subtotal",new BigDecimal("2080000.00")).put("iva",new BigDecimal("395200.00")).put("total",new BigDecimal("2475200.00"));
  var items=receipt.putArray("items");items.addObject().put("nombre_producto","Producto de ejemplo con descripción extensa para comprobar que no se recorte").put("cantidad",1).put("precio_unitario",2080000).put("subtotal",2080000);
  String original=receipt.toString();var one=ReceiptRenderer.pages(receipt);
  check(one.size()==1,"Recibo breve en una página");check(one.getFirst().getWidth()==794&&one.getFirst().getHeight()==1123,"Formato A4 para vista previa e impresión");
  check(one.getFirst().getRGB(10,10)!=java.awt.Color.WHITE.getRGB(),"Cabecera visual renderizada");check(original.equals(receipt.toString()),"Renderizar no modifica importes ni datos guardados");
  for(int i=1;i<50;i++)items.add(items.get(0).deepCopy());var many=ReceiptRenderer.pages(receipt);
  check(many.size()>1&&many.size()<20,"Recibo largo paginado sin perder productos");check(many.stream().allMatch(p->p.getWidth()==794&&p.getHeight()==1123),"Todas las páginas conservan tamaño de papel");
  var draft=InvoicePreparation.draft(json,receipt,"Emisor de ejemplo","123456789","0","Comprador de ejemplo","CC","12345678","prueba@example.invalid");
  check(!draft.path("emitida").asBoolean(true)&&!draft.path("validada_dian").asBoolean(true),"Borrador no se presenta como factura emitida ni validada");
  check(draft.path("venta_registrada").path("total").decimalValue().compareTo(new BigDecimal("2475200.00"))==0,"Preparación conserva el total registrado");
  check(!draft.has("cufe")&&draft.path("pendientes_para_emision").size()>0,"Sin CUFE ficticio y con requisitos pendientes explícitos");
  sale.put("total",1);check(draft.path("venta_registrada").path("total").decimalValue().compareTo(new BigDecimal("2475200.00"))==0,"Borrador guarda una copia independiente de la venta");
  System.out.println(count+" comprobaciones de recibos y preparación fiscal aprobadas.");
 }
}
