package org.hoohoot.odoo.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hoohoot.odoo.config.BrevoConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

@ApplicationScoped
public class BrevoClient {

    public enum Status { CREATED, DUPLICATE, ERROR }

    public record ContactResult(Status status, String message) {
    }

    public record SendResult(boolean success, String message) {
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

    /**
     * Envoie un email transactionnel (POST /v3/smtp/email) avec une pièce jointe.
     * L'expéditeur doit être un expéditeur validé dans le compte Brevo.
     */
    public SendResult sendTransactionalEmail(String senderEmail, String senderName,
            List<String> recipients, String subject, String htmlContent,
            byte[] attachment, String attachmentName) {
        ObjectNode body = mapper.createObjectNode();

        ObjectNode sender = body.putObject("sender");
        sender.put("email", senderEmail);
        if (senderName != null && !senderName.isBlank()) {
            sender.put("name", senderName);
        }

        ArrayNode to = body.putArray("to");
        for (String recipient : recipients) {
            to.addObject().put("email", recipient);
        }

        body.put("subject", subject);
        body.put("htmlContent", htmlContent);

        ArrayNode attachments = body.putArray("attachment");
        ObjectNode att = attachments.addObject();
        att.put("content", Base64.getEncoder().encodeToString(attachment));
        att.put("name", attachmentName);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(config.url() + "/v3/smtp/email"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("api-key", config.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() / 100 == 2) {
                return new SendResult(true, "");
            }
            return new SendResult(false, "HTTP " + resp.statusCode() + " : " + resp.body());
        } catch (Exception e) {
            return new SendResult(false, e.getMessage());
        }
    }
}
