package com.tecnopc.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PaymentController {
    private final PaymentService payments;
    private final String secret;
    private final CheckoutService checkout;

    public PaymentController(PaymentService payments, @Value("${MP_WEBHOOK_SECRET:}") String secret,CheckoutService checkout) {
        this.payments = payments;
        this.secret = secret;
        this.checkout=checkout;
    }
    record Item(@JsonProperty("producto_id") @NotNull @Min(1) Long productoId,
                @NotNull @Min(1) @Max(100) Integer cantidad) {}
    record Checkout(@JsonProperty("cliente_nombre") @NotBlank @Size(min = 2, max = 80) String clienteNombre,
                    @NotEmpty @Size(max = 30) List<@NotNull @Valid Item> items,
                    @Valid NotificationContact contacto) {
        Checkout(String clienteNombre,List<Item> items) { this(clienteNombre,items,null); }
    }

    @PostMapping({"/api/public/pagos","/api/public/v2/pagos"})
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> start(@Valid @RequestBody Checkout request,@RequestHeader(name="Idempotency-Key",required=false) String key) {
        IdempotencyService.key(key);
        if (secret.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Notificaciones de pago no configuradas");
        return checkout.start(request,key);
    }

    @PostMapping("/api/public/pagos/webhook")
    public Map<String, Object> notification(@RequestParam(name = "data.id", required = false) String id,
        @RequestHeader(name = "x-request-id", required = false) String requestId,
        @RequestHeader(name = "x-signature", required = false) String signature) {
        if (secret.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Notificaciones de pago no configuradas");
        if (!WebhookSignature.valid(secret, id, requestId, signature))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Firma invalida");
        return payments.notifyPayment(id);
    }
}
