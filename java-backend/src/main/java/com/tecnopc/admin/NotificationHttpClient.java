package com.tecnopc.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NotificationHttpClient {
    private final ObjectMapper json;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public NotificationHttpClient(ObjectMapper json) { this.json=json; }
    public record Response(int status,String body) {}

    public Response send(URI endpoint,Map<String,String> headers,Object body) throws IOException,InterruptedException {
        var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(20))
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        headers.forEach(request::header);
        var response=client.send(request.build(),HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(),response.body());
    }
}
