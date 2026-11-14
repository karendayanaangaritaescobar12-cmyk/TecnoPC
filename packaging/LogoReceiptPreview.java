import com.fasterxml.jackson.databind.ObjectMapper;
import com.tecnopc.vendedor.ReceiptRenderer;
import javax.imageio.ImageIO;
import java.io.File;

/** Renders sample data only; does not connect to the service or record a sale. */
class LogoReceiptPreview {
    public static void main(String[] args) throws Exception {
        var data=new ObjectMapper().readTree("""
            {"venta":{"numero":"EJEMPLO-LOGO","creado_en":"2026-11-13","cliente_nombre":"Cliente de ejemplo","cliente_documento":"","vendedor_usuario":"vendedor","estado":"Ejemplo","subtotal":1000000,"iva":190000,"total":1190000},"items":[{"nombre_producto":"Computador de ejemplo","cantidad":1,"precio_unitario":1000000,"subtotal":1000000}]}
            """);
        ImageIO.write(ReceiptRenderer.pages(data).getFirst(),"png",new File("ui-previews/logo-recibo.png"));
    }
}
