package org.hoohoot.odoo.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hoohoot.odoo.config.BrevoConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@ApplicationScoped
public class BrevoClient {

    public enum Status { CREATED, DUPLICATE, ERROR }

    public record ContactResult(Status status, String message) {
    }

    @Inject
    BrevoConfig config;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * Crée un contact dans Brevo et l'ajoute à la liste donnée (updateEnabled=false :
     * un contact déjà existant n'est pas modifié et renvoie DUPLICATE).
     */
    public ContactResult createContact(String email, String nom, String prenom, int listId) {
        ObjectNode body = mapper.createObjectNode();
        body.put("email", email);
        ObjectNode attributes = body.putObject("attributes");
        attributes.put("NOM", nom);
        attributes.put("PRENOM", prenom);
        body.putPOJO("listIds", List.of(listId));
        body.put("updateEnabled", false);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(config.url() + "/v3/contacts"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("api-key", config.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() / 100 == 2) {
                return new ContactResult(Status.CREATED, "");
            }
            JsonNode error = mapper.readTree(resp.body());
            if ("duplicate_parameter".equals(error.path("code").asText())) {
                return new ContactResult(Status.DUPLICATE, error.path("message").asText(""));
            }
            return new ContactResult(Status.ERROR,
                    "HTTP " + resp.statusCode() + " : " + resp.body());
        } catch (Exception e) {
            return new ContactResult(Status.ERROR, e.getMessage());
        }
    }
}
