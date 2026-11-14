package com.tecnopc.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

@Service
@EnableConfigurationProperties(NotificationSettings.class)
public class NotificationSender {
    private final NotificationSettings settings;
    private final NotificationHttpClient http;
    private final ObjectMapper json;
    public NotificationSender(NotificationSettings settings,NotificationHttpClient http,ObjectMapper json) {
        this.settings=settings;this.http=http;this.json=json;
    }
    public record Job(String id,String lease,String channel,String destination,String event,String reference,int attempts) {}
    public static class SendFailure extends RuntimeException {
        final String code;final boolean retry;final boolean ambiguous;
        SendFailure(String code,boolean retry,boolean ambiguous) { super(code);this.code=code;this.retry=retry;this.ambiguous=ambiguous; }
    }
    public Set<String> readyChannels() {
        Set<String> result=new HashSet<>();
        var email=settings.email();var wa=settings.whatsapp();
        if(email!=null && email.enabled() && present(email.apiKey()) && present(email.from()) && !email.from().matches("(?s).*[\\r\\n].*")) result.add("correo");
        if(wa!=null && wa.enabled() && present(wa.token()) && present(wa.phoneNumberId()) && wa.phoneNumberId().matches("[0-9]+") &&
            present(wa.template()) && wa.template().matches("[a-z0-9_]+") && present(wa.apiVersion()) && wa.apiVersion().matches("v[0-9]+\\.[0-9]+") && present(wa.language())) result.add("whatsapp");
        return result;
    }

    public String send(Job job) {
        if(!readyChannels().contains(job.channel())) throw new SendFailure("CANAL_NO_CONFIGURADO",true,false);
        String message=switch(job.event()) {
            case "solicitud_registrada" -> "Tu solicitud fue registrada. El equipo de TecnoPC te contactara.";
            case "compra_registrada" -> "Tu compra fue registrada correctamente. Aun se encuentra en preparacion.";
            case "compra_lista" -> "Tu pedido está listo para recoger en TecnoPC. Conserva esta referencia para la entrega.";
            case "servicio_recibido" -> "Tu equipo fue recibido en el taller. Conserva el codigo de seguimiento.";
            case "servicio_listo" -> "Tu equipo está listo para recoger en TecnoPC. Presenta tu código de ingreso para la entrega.";
            default -> throw new SendFailure("EVENTO_DESCONOCIDO",false,false);
        };
        try {
            NotificationHttpClient.Response response;
            if(job.channel().equals("correo")) {
                response=http.send(URI.create("https://api.resend.com/emails"),
                    Map.of("Authorization","Bearer "+settings.email().apiKey(),"Idempotency-Key","tecnopc/"+job.id()),
                    Map.of("from",settings.email().from(),"to",List.of(job.destination()),"subject","TecnoPC - "+job.reference(),
                        "text",message+"\nReferencia: "+job.reference()));
            } else {
                var wa=settings.whatsapp();
                response=http.send(URI.create("https://graph.facebook.com/"+wa.apiVersion()+"/"+wa.phoneNumberId()+"/messages"),
                    Map.of("Authorization","Bearer "+wa.token()),
                    Map.of("messaging_product","whatsapp","to",job.destination().replace("+",""),"type","template",
                        "template",Map.of("name",wa.template(),"language",Map.of("code",wa.language()),
                            "components",List.of(Map.of("type","body","parameters",List.of(
                                Map.of("type","text","text",message),Map.of("type","text","text",job.reference())))))));
            }
            if(response.status()<200 || response.status()>=300) {
                boolean ambiguous=job.channel().equals("whatsapp") && response.status()>=500;
                throw new SendFailure("HTTP_"+response.status(),response.status()==429 || (response.status()>=500 && !ambiguous),ambiguous);
            }
            var body=json.readTree(response.body());
            if(body==null || !body.isObject()) throw new SendFailure("RESPUESTA_AMBIGUA",false,true);
            String id=job.channel().equals("correo")?body.path("id").asText():body.path("messages").path(0).path("id").asText();
            if(id.isBlank() || id.length()>150) throw new SendFailure("RESPUESTA_AMBIGUA",false,true);
            return id;
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();throw new SendFailure("ENVIO_INTERRUMPIDO",!job.channel().equals("whatsapp"),job.channel().equals("whatsapp"));
        } catch(IOException ex) {
            throw new SendFailure("CONEXION_AMBIGUA",!job.channel().equals("whatsapp"),job.channel().equals("whatsapp"));
        }
    }
    private boolean present(String value) { return value!=null && !value.isBlank(); }
}
