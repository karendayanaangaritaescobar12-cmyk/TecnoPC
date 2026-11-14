package com.tecnopc.admin;

import jakarta.validation.constraints.*;

/** Only explicitly selected channels may receive transactional notifications. */
public record NotificationContact(
    @Email @Size(max=254) String correo,
    @Pattern(regexp="^$|\\+[1-9][0-9]{7,14}") String whatsapp,
    boolean avisosCorreo,
    boolean avisosWhatsapp) {
    @AssertTrue(message="Indica un destino para cada canal autorizado")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isConsentConsistent() {
        return (!avisosCorreo || (correo != null && !correo.isBlank())) &&
               (!avisosWhatsapp || (whatsapp != null && !whatsapp.isBlank()));
    }
}
