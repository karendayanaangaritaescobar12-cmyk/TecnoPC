package com.tecnopc.vendedor;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import static com.tecnopc.ui.DesktopShell.money;

/** One paper layout for screen preview and printing; amounts come from the saved sale. */
public final class ReceiptRenderer {
    private static final Color INK=new Color(22,39,62),BLUE=new Color(0,105,232),MUTED=new Color(92,111,135),LINE=new Color(222,230,239);
    public static final int WIDTH=794,HEIGHT=1123;
    private ReceiptRenderer(){}
    public static List<BufferedImage> pages(JsonNode data){
        var result=new ArrayList<BufferedImage>();var sale=data.path("venta");
        var items=new ArrayList<JsonNode>();data.path("items").forEach(items::add);
        int index=0,page=1;
        do {
            var image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);g.fillRect(0,0,WIDTH,HEIGHT);
            int y=header(g,sale,page);
            while(index<items.size()){
                var item=items.get(index);g.setFont(font(14,false));var name=wrap(g,item.path("nombre_producto").asText(),280);
                int height=Math.max(52,name.size()*20+22);
                // Reserve the final totals block when it fits; continue on a fresh page otherwise.
                if(y+height>900)break;
                g.setColor(index%2==0?new Color(247,250,254):Color.WHITE);g.fillRect(44,y,706,height);
                g.setColor(INK);int rowY=y+25;for(String line:name){g.drawString(line,58,rowY);rowY+=20;}
                right(g,item.path("cantidad").asText(),420,y+25);
                amount(g,money(item.path("precio_unitario").decimalValue()),580,y+25,145,14,false);
                amount(g,money(item.path("subtotal").decimalValue()),736,y+25,145,14,false);
                g.setColor(LINE);g.drawLine(44,y+height,750,y+height);y+=height;index++;
            }
            boolean last=index==items.size();
            if(last&&y+204<=1015){totals(g,sale,y+25);}else if(last){
                footer(g,sale,page++,false);g.dispose();result.add(image);
                image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_RGB);g=image.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setColor(Color.WHITE);g.fillRect(0,0,WIDTH,HEIGHT);header(g,sale,page);totals(g,sale,340);
            }
            footer(g,sale,page++,last);g.dispose();result.add(image);
        }while(index<items.size());
        return List.copyOf(result);
    }
    private static int header(Graphics2D g,JsonNode sale,int page){
        g.setColor(INK);g.fillRect(0,0,WIDTH,134);g.setColor(BLUE);g.fillRect(0,134,WIDTH,5);
        try(var logo=com.tecnopc.ui.DesktopShell.class.getResourceAsStream("tecnopc-icon.png")){
            if(logo==null)throw new IllegalStateException("Falta el logo de TecnoPC");
            g.drawImage(javax.imageio.ImageIO.read(logo),44,25,52,52,null);
        }catch(java.io.IOException ex){throw new IllegalStateException("No se pudo leer el logo de TecnoPC",ex);}
        g.setColor(Color.WHITE);g.setFont(font(35,true));g.drawString("Tecno",106,65);g.setColor(new Color(0,140,255));g.drawString("PC",212,65);
        g.setColor(new Color(195,211,231));g.setFont(font(12,false));g.drawString("Computadores · Componentes · Servicio técnico",44,94);
        g.setFont(font(13,true));g.setColor(Color.WHITE);right(g,"RECIBO DE VENTA",750,46);
        g.setFont(font(15,true));right(g,sale.path("numero").asText(),750,72);
        g.setFont(font(12,false));g.setColor(new Color(195,211,231));right(g,"Fecha: "+sale.path("creado_en").asText().replace('T',' '),750,97);
        g.setColor(new Color(243,247,252));g.fillRoundRect(44,163,706,100,12,12);
        label(g,"CLIENTE",60,185);g.setFont(font(15,true));g.setColor(INK);
        var names=wrap(g,sale.path("cliente_nombre").asText(),425);int y=209;for(String name:names){g.drawString(name,60,y);y+=18;}
        g.setFont(font(12,false));g.setColor(MUTED);g.drawString("Documento: "+value(sale,"cliente_documento"),60,248);
        label(g,"ATENDIDO POR",550,185);g.setFont(font(13,true));g.setColor(INK);String seller=value(sale,"vendedor_usuario");while(seller.length()>1&&g.getFontMetrics().stringWidth(seller)>184)seller=seller.substring(0,seller.length()-2)+"…";g.drawString(seller,550,209);
        g.setFont(font(12,false));g.setColor(MUTED);g.drawString("Estado: "+value(sale,"estado"),550,234);
        g.setFont(font(12,false));g.setColor(MUTED);g.drawString(page==1?"Detalle de la venta · Valores en pesos colombianos (COP)":"Detalle de la venta · Continuación",44,291);
        g.setColor(INK);g.fillRoundRect(44,308,706,42,6,6);g.setColor(Color.WHITE);g.setFont(font(12,true));
        g.drawString("PRODUCTO",58,333);right(g,"CANT.",420,333);right(g,"UNIT. SIN IVA",580,333);right(g,"IMPORTE SIN IVA",736,333);return 350;
    }
    private static void totals(Graphics2D g,JsonNode sale,int y){
        g.setFont(font(13,false));g.setColor(MUTED);g.drawString("Subtotal",475,y+20);right(g,money(sale.path("subtotal").decimalValue()),735,y+20);
        g.drawString("IVA registrado",475,y+50);right(g,money(sale.path("iva").decimalValue()),735,y+50);
        g.setColor(BLUE);g.fillRoundRect(448,y+69,302,59,9,9);g.setColor(Color.WHITE);g.setFont(font(13,true));g.drawString("TOTAL",466,y+105);amount(g,money(sale.path("total").decimalValue()),734,y+106,207,23,true);
        g.setColor(INK);g.setFont(font(16,true));g.drawString("Gracias por confiar en TecnoPC",44,y+164);
        g.setFont(font(12,false));g.setColor(MUTED);g.drawString("Conserva este recibo como referencia de tu compra.",44,y+187);
    }
    private static void footer(Graphics2D g,JsonNode sale,int page,boolean last){
        g.setColor(LINE);g.drawLine(44,1053,750,1053);g.setFont(font(11,false));g.setColor(MUTED);
        g.drawString("Recibo de venta. No sustituye una factura electrónica validada por la DIAN.",44,1076);
        g.drawString(last?"Importes tomados de la venta registrada.":"Continúa en la siguiente página.",44,1096);right(g,"Página "+page,750,1096);
    }
    private static Font font(int size,boolean bold){return new Font("Dialog",bold?Font.BOLD:Font.PLAIN,size);}
    private static void label(Graphics2D g,String text,int x,int y){g.setColor(MUTED);g.setFont(font(11,true));g.drawString(text,x,y);}
    private static String value(JsonNode node,String key){String s=node.path(key).asText("");return s.isBlank()?"No informado":s;}
    private static void right(Graphics2D g,String text,int x,int y){g.drawString(text,x-g.getFontMetrics().stringWidth(text),y);}
    private static void amount(Graphics2D g,String text,int x,int y,int width,int size,boolean bold){g.setFont(font(size,bold));while(size>7&&g.getFontMetrics().stringWidth(text)>width)g.setFont(font(--size,bold));right(g,text,x,y);}
    private static List<String> wrap(Graphics2D g,String text,int width){
        var lines=new ArrayList<String>();StringBuilder line=new StringBuilder();
        for(char c:text.replaceAll("[\\r\\n\\t]"," ").toCharArray()){
            if(line.length()>0&&g.getFontMetrics().stringWidth(line.toString()+c)>width){lines.add(line.toString().strip());line.setLength(0);}line.append(c);
        }if(line.length()>0)lines.add(line.toString().strip());if(lines.isEmpty())lines.add("");return lines;
    }
}
