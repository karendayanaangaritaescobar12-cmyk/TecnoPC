package com.tecnopc.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationSenderTest {
    NotificationHttpClient http=mock(NotificationHttpClient.class);
    NotificationSettings settings=new NotificationSettings(new NotificationSettings.Email(true,"not-a-real-key","TecnoPC <avisos@example.com>"),
        new NotificationSettings.Whatsapp(true,"not-a-real-token","123456","v27.0","tecnopc_actualizacion","es"));
    NotificationSender sender=new NotificationSender(settings,http,new ObjectMapper());
    NotificationSender.Job job(String channel) { return new NotificationSender.Job("event-1","lease",channel,channel.equals("correo")?"cliente@example.com":"+573001234567","compra_lista","VEN-1",1); }

    @Test void emailHasStableIdempotencyKeyAndCorrectRecipient() throws Exception {
        when(http.send(any(),anyMap(),any())).thenReturn(new NotificationHttpClient.Response(200,"{\"id\":\"test-email\"}"));
        assertEquals("test-email",sender.send(job("correo")));
        var headers=ArgumentCaptor.forClass(Map.class);var body=ArgumentCaptor.forClass(Object.class);
        verify(http).send(eq(java.net.URI.create("https://api.resend.com/emails")),headers.capture(),body.capture());
        assertEquals("tecnopc/event-1",headers.getValue().get("Idempotency-Key"));
        assertTrue(body.getValue().toString().contains("cliente@example.com"));
    }
    @Test void whatsappUsesApprovedTemplateAndInternationalNumber() throws Exception {
        when(http.send(any(),anyMap(),any())).thenReturn(new NotificationHttpClient.Response(200,"{\"messages\":[{\"id\":\"test-whatsapp\"}]}"));
        assertEquals("test-whatsapp",sender.send(job("whatsapp")));
        var body=ArgumentCaptor.forClass(Object.class);
        verify(http).send(eq(java.net.URI.create("https://graph.facebook.com/v27.0/123456/messages")),anyMap(),body.capture());
        String payload=new ObjectMapper().writeValueAsString(body.getValue());
        assertTrue(payload.contains("573001234567"));assertTrue(payload.contains("tecnopc_actualizacion"));assertTrue(payload.contains("VEN-1"));
    }
    @Test void whatsappNetworkFailureIsAmbiguousInsteadOfBlindRetry() throws Exception {
        when(http.send(any(),anyMap(),any())).thenThrow(new IOException("test"));
        var error=assertThrows(NotificationSender.SendFailure.class,()->sender.send(job("whatsapp")));
        assertTrue(error.ambiguous);assertFalse(error.retry);
    }
    @Test void emailNetworkFailureCanRetryWithSameKey() throws Exception {
        when(http.send(any(),anyMap(),any())).thenThrow(new IOException("test"));
        var error=assertThrows(NotificationSender.SendFailure.class,()->sender.send(job("correo")));
        assertTrue(error.retry);assertFalse(error.ambiguous);
    }
    @Test void disabledChannelsNeverMakeNetworkCalls() {
        var disabled=new NotificationSender(new NotificationSettings(new NotificationSettings.Email(false,"",""),new NotificationSettings.Whatsapp(false,"","","v27.0","","es")),http,new ObjectMapper());
        assertTrue(disabled.readyChannels().isEmpty());
        assertThrows(NotificationSender.SendFailure.class,()->disabled.send(job("correo")));
        verifyNoInteractions(http);
    }
}
