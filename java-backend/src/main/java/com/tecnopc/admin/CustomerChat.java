package com.tecnopc.admin;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.regex.Pattern;
import java.text.NumberFormat;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Local rules and database catalog; never discloses customers or order details. */
@Service
public class CustomerChat {
    private final JdbcTemplate jdbc;
    public CustomerChat(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> answer(String message) {
        return answer(message,List.of());
    }
    public Map<String,Object> answer(String message,List<String> history) {
        String text = Normalizer.normalize(message, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        String context=Normalizer.normalize(String.join(" ",history==null?List.of():history)+" "+message,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);
        if(matches(text,"password|contrasena|token|clave de admin|datos de clientes|ignora.*instrucciones"))return reply("No compartas contraseñas, tokens ni datos bancarios. Solo uso información pública del catálogo y de nuestros servicios.","Ver catálogo","#catalogo");
        if(matches(text,"seguimiento|estado.*reparacion|tpc-[0-9]"))return reply("Consulta tu equipo en Seguimiento con el código entregado por el taller. Escríbelo en ese formulario, no en el chat.","Seguir mi reparación","#seguimiento");
        if(matches(text,"promocion|descuento|oferta"))return reply("Consulta las promociones publicadas. No tengo descuentos adicionales verificados para prometerte.","Ver promociones","#promociones");
        if (text.matches("(?s).*(cmp-|ven-|factura|pedido|mi compra).*"))
            return reply("Para consultar datos de una compra, contacta al personal por un canal privado.", "", "");
        if (matches(text,"servicio|repar|diagnost|mantenimiento|no prende|no enciende|virus|pantalla azul|calienta|lento|limpieza"))
            return reply("Podemos recibir tu solicitud de servicio técnico para revisar el equipo y cotizar el trabajo. ¿Qué marca/modelo tienes, qué falla presenta y desde cuándo ocurre? No puedo confirmar diagnóstico, costo ni fecha de entrega sin revisión. Si hay humo u olor a quemado, apágalo y no abras la fuente de poder.", "Solicitar servicio", "#servicio");
        if (text.contains("garantia"))
            return reply("La cobertura y duracion de la garantia dependen del producto. Confirmalas con nuestro personal antes de comprar.", "", "");
        if (text.matches("(?s).*(horario|direccion|ubicacion|telefono|contacto).*"))
            return reply("Deja una solicitud para que el equipo te contacte. No hay horarios ni direccion comercial confirmados en el sistema.", "Contacto", "#servicio");
        if (text.matches("(?s).*(pago|tarjeta|transferencia|cuotas|financi).*"))
            return reply("Agrega productos al carrito y continua en Mercado Pago. El pago solo se confirma cuando el proveedor lo verifica.", "Ir al carrito", "#compras");
        if (text.contains("iva"))
            return reply("Los precios mostrados incluyen IVA del 19 %. La cotizacion detalla subtotal, IVA y total.", "", "");
        if(matches(context,"recom|armar|ensambl|configur|personaliz|cotiz|gamer|gaming|jugar|diseno|edicion|oficina|estudi|programa|presupuesto|comprar|catalogo|producto|precio|stock|disponib|computador|portatil|laptop|procesador|memoria|grafica|ssd|\\bpc\\b|\\bram\\b|fuente|gabinete|placa"))return recommend(text,context);
        if (text.matches("(?s).*(catalogo|producto|precio|stock|disponib|computador|portatil|laptop|procesador|memoria|grafica|ssd).*")) {
            var products = jdbc.queryForList("SELECT nombre,precio,stock FROM productos WHERE stock>0 ORDER BY nombre LIMIT 8");
            String catalog = products.stream().map(p -> p.get("nombre") + " - COP " +
                new BigDecimal(p.get("precio").toString()).multiply(new BigDecimal("1.19"))
                    .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + " IVA incluido; disponibles: " + p.get("stock"))
                .collect(Collectors.joining("\n"));
            return reply(catalog.isEmpty() ? "No hay productos disponibles en este momento." : "Algunos productos disponibles:\n" + catalog,
                "Ver catalogo", "#catalogo");
        }
        return reply("Hola. Puedo orientarte sobre catalogo, armado de PC, pagos y servicio tecnico. No compartas contrasenas ni datos bancarios en este chat.", "Ver catalogo", "#catalogo");
    }

    private Map<String,Object> recommend(String text,String context){
        boolean build=matches(context,"armar|ensambl|configur|personaliz|componentes|cotiz");
        boolean gaming=matches(context,"gamer|gaming|jugar|juegos|fortnite|valorant|minecraft|warzone");
        boolean design=matches(context,"diseno|edicion|render|arquitect|video|autocad|blender");
        boolean office=matches(context,"oficina|estudi|excel|trabajo|programar|programacion");
        boolean laptop=matches(context,"portatil|laptop|movilidad");
        BigDecimal budget=budget(text);if(budget==null)budget=budget(context);
        String target=build?"#armar":"#catalogo",label=build?"Armar mi PC":"Ver catálogo";
        if(build&&!gaming&&!design&&!office)return reply("Te ayudo a armar una PC. ¿La usarás para estudiar/oficina, programar, jugar o diseño/edición? Dime tu presupuesto total en COP y si debe incluir monitor y periféricos.",label,target);
        if(budget==null)return reply("Para recomendarte opciones reales necesito tu presupuesto en COP, por ejemplo 2500000 o 2,5 millones. ¿Prefieres portátil o escritorio y para qué programas o juegos lo usarás?",label,target);
        List<Map<String,Object>> products;
        try{products=jdbc.queryForList("SELECT id,nombre,categoria,especificaciones,precio,stock FROM productos WHERE stock>0 ORDER BY precio,id LIMIT 2000");}
        catch(org.springframework.dao.DataAccessException ex){return reply("Ahora no logro consultar las existencias. No confirmaré modelos ni precios sin el catálogo. Puedes dejar una cotización para que el equipo revise opciones con tu presupuesto de "+money(budget)+".","Solicitar cotización","#armar");}
        String advice=gaming?" Confirma los juegos, resolución y FPS deseados; no puedo garantizar rendimiento solo por el precio.":design?" Confirma programas y tamaño de proyectos para revisar los requisitos del fabricante.":office?" Para estudio/oficina, revisa SSD, memoria suficiente para tus programas y posibilidad de ampliación.":"";
        if(products.isEmpty())return reply("Tu presupuesto es "+money(budget)+". No hay productos con stock registrado; no voy a inventar modelos ni precios. Deja una cotización para confirmar disponibilidad."+advice,"Solicitar cotización","#armar");
        StringBuilder answer=new StringBuilder("Presupuesto total: "+money(budget)+" con IVA.\n");
        if(build){
            BigDecimal total=BigDecimal.ZERO;var missing=new java.util.ArrayList<String>();
            var names=Map.of("cpu","procesador","mb","placa madre","ram","memoria RAM","ssd","almacenamiento","gpu","gráfica","psu","fuente","case","gabinete");
            answer.append("Candidatos de menor precio para revisar con el técnico:\n");
            for(String category:List.of("cpu","mb","ram","ssd","gpu","psu","case")){
                var found=products.stream().filter(p->category.equals(p.get("categoria"))).findFirst();
                if(found.isEmpty()){missing.add(names.get(category));continue;}
                answer.append(productLine(found.get()));total=total.add(publicPrice(found.get()));
            }
            answer.append("Suma de los candidatos: ").append(money(total)).append(" con IVA. ");
            if(total.compareTo(budget)>0)answer.append("Supera tu presupuesto: hay que ajustar la propuesta. ");
            if(!missing.isEmpty())answer.append("Faltan existencias de: ").append(String.join(", ",missing)).append(". ");
            answer.append("Esta lista prioriza precio; no es una configuración compatible validada. El técnico debe verificar socket, DDR, BIOS, conectores, potencia, refrigeración y dimensiones. No incluye monitor, periféricos, sistema operativo ni mano de obra no cotizados.");
        }else{
            String category=laptop?"laptop":category(context);BigDecimal max=budget;
            var options=products.stream().filter(p->category==null?Set.of("pc","laptop").contains(p.get("categoria")):category.equals(p.get("categoria"))).filter(p->publicPrice(p).compareTo(max)<=0).limit(3).toList();
            if(options.isEmpty())answer.append("No encontré opciones de ese tipo dentro del presupuesto y con stock. Solicita una cotización para revisar alternativas.");
            else{answer.append("Opciones disponibles por categoría y precio:\n");options.forEach(p->answer.append(productLine(p)));answer.append("Revisa las especificaciones con el equipo antes de elegir; el precio no garantiza rendimiento.");}
        }
        return reply(answer+advice+"\nEl stock y los precios pueden cambiar. No se han reservado productos.",label,target);
    }
    private String category(String t){if(matches(t,"procesador|\\bcpu\\b"))return "cpu";if(matches(t,"grafica|\\bgpu\\b"))return "gpu";if(matches(t,"memoria|\\bram\\b"))return "ram";if(matches(t,"ssd|disco|almacenamiento"))return "ssd";if(matches(t,"placa|motherboard"))return "mb";if(matches(t,"fuente"))return "psu";if(matches(t,"gabinete"))return "case";if(matches(t,"escritorio"))return "pc";return null;}
    private static boolean matches(String text,String pattern){return Pattern.compile(pattern).matcher(text).find();}
    private BigDecimal publicPrice(Map<String,Object> p){return new BigDecimal(p.get("precio").toString()).multiply(new BigDecimal("1.19")).setScale(2,java.math.RoundingMode.HALF_UP);}
    private String productLine(Map<String,Object> p){return "• "+p.get("nombre")+" — "+money(publicPrice(p))+" IVA incluido. "+Objects.toString(p.get("especificaciones"),"")+"\n";}
    private String money(BigDecimal n){return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO")).format(n)+" COP";}
    static BigDecimal budget(String t){
        var m=Pattern.compile("(?<![a-z0-9])([0-9]+(?:[.,][0-9]+)?)\\s*(millones|millon|mill|m|k|mil)(?![a-z])").matcher(t);
        if(m.find())return new BigDecimal(m.group(1).replace(',','.')).multiply(new BigDecimal(m.group(2).equals("mil")||m.group(2).equals("k")?"1000":"1000000"));
        m=Pattern.compile("(?<![a-z0-9])([0-9]{1,3}(?:[.,][0-9]{3}){1,3}|[0-9]{4,10})(?![a-z0-9])").matcher(t);BigDecimal found=null;
        while(m.find()){var n=new BigDecimal(m.group(1).replace(".","").replace(",",""));if(n.compareTo(new BigDecimal("10000"))>=0)found=n;}return found;
    }

    private Map<String, Object> reply(String answer, String label, String target) {
        return target.isEmpty() ? Map.of("respuesta", answer) :
            Map.of("respuesta", answer, "accion", Map.of("texto", label, "destino", target));
    }
}
