package com.tecnopc.admin;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Persist the order first; never hold its SQL transaction across a provider call. */
@Service
public class CheckoutService {
    private final IdempotencyService idempotency;private final PaymentService payments;private final JdbcTemplate jdbc;
    private final org.springframework.transaction.support.TransactionTemplate transition;
    public CheckoutService(IdempotencyService idempotency,PaymentService payments,JdbcTemplate jdbc,org.springframework.transaction.PlatformTransactionManager manager){this.idempotency=idempotency;this.payments=payments;this.jdbc=jdbc;this.transition=new org.springframework.transaction.support.TransactionTemplate(manager);}
    public Map<String,Object> start(PaymentController.Checkout request,String key){
        var saved=idempotency.execute("checkout","public",key,request,()->{
            var pending=payments.createPending(request);
            jdbc.update("INSERT INTO preferencias_checkout(referencia) VALUES (?)",pending.reference());
            return Map.of("referencia",pending.reference(),"total",pending.total());
        });
        String reference=saved.get("referencia").toString();
        BigDecimal total=new BigDecimal(saved.get("total").toString());
        var pending=new PaymentService.Pending(reference,total);
        int claimed=transition.execute(status->jdbc.update("UPDATE preferencias_checkout SET estado='creando',iniciada_en=? WHERE referencia=? AND estado='nueva'",
                Timestamp.from(Instant.now()),reference));
        if(claimed==1){
            try {
                var result=payments.paymentLink(pending);
                transition.executeWithoutResult(status->jdbc.update("UPDATE preferencias_checkout SET estado='lista',enlace=? WHERE referencia=? AND estado='creando'",result.get("url_pago"),reference));
            }catch(org.springframework.web.server.ResponseStatusException | DataAccessException uncertain){
                // A timeout may have happened after the provider created its preference.
                // Do not automatically call it a second time on a retry.
                transition.executeWithoutResult(status->{
                    jdbc.update("UPDATE preferencias_checkout SET estado='revision' WHERE referencia=? AND estado='creando'",reference);
                    jdbc.update("UPDATE pagos_online SET estado='revision' WHERE referencia=? AND estado='pendiente'",reference);
                });
            }
        }
        var preference=transition.execute(status->{
            jdbc.update("UPDATE preferencias_checkout SET estado='revision' WHERE referencia=? AND estado='creando' AND iniciada_en<?",
                    reference,Timestamp.from(Instant.now().minusSeconds(90)));
            var row=jdbc.queryForList("SELECT estado,enlace FROM preferencias_checkout WHERE referencia=? FOR UPDATE",reference).getFirst();
            if("revision".equals(row.get("estado")))jdbc.update("UPDATE pagos_online SET estado='revision' WHERE referencia=? AND estado='pendiente'",reference);
            return row;
        });
        String state=preference.get("estado").toString();
        String link=state.equals("lista")?Objects.toString(preference.get("enlace"),""):"";
        return Map.of("referencia",reference,"total",total,"url_pago",link,"estado",state.equals("revision")?"revision":"pendiente",
                "mensaje",state.equals("revision")?"El pedido requiere revisión. No se creó un segundo pedido ni se confirmó un pago.":link.isBlank()?"El enlace de pago se está preparando. Conserva el comprobante y actualízalo.":"Pedido registrado");
    }
}
