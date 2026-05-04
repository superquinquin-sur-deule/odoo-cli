package org.hoohoot.odoo.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hoohoot.odoo.config.OdooConfig;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class OdooClient {

    private static final Logger LOG = Logger.getLogger(OdooClient.class);

    @Inject
    OdooConfig config;

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpClient http;
    private Integer uid;

    @PostConstruct
    void init() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.timeoutSeconds()))
                .build();
    }

    public int authenticate() {
        if (uid != null) {
            return uid;
        }
        ObjectNode params = mapper.createObjectNode();
        params.put("service", "common");
        params.put("method", "login");
        params.putPOJO("args", List.of(config.database(), config.login(), config.password()));

        JsonNode result = call(params);
        if (result.isNull() || !result.isInt() || result.intValue() == 0) {
            throw new OdooException("Odoo authentication failed for user '" + config.login()
                    + "' on database '" + config.database() + "'");
        }
        uid = result.intValue();
        LOG.debugf("Authenticated to Odoo as uid=%d", uid);
        return uid;
    }

    public JsonNode executeKw(String model, String method, List<?> args) {
        return executeKw(model, method, args, Map.of());
    }

    public JsonNode executeKw(String model, String method, List<?> args, Map<String, ?> kwargs) {
        int currentUid = authenticate();
        ObjectNode params = mapper.createObjectNode();
        params.put("service", "object");
        params.put("method", "execute_kw");
        params.putPOJO("args", List.of(
                config.database(),
                currentUid,
                config.password(),
                model,
                method,
                args,
                kwargs
        ));
        return call(params);
    }

    public JsonNode searchRead(String model, List<?> domain, List<String> fields) {
        return executeKw(model, "search_read", List.of(domain), Map.of("fields", fields));
    }

    private JsonNode call(ObjectNode params) {
        ObjectNode body = mapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("method", "call");
        body.set("params", params);

        try {
            String json = mapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(config.url() + "/jsonrpc"))
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new OdooException("Odoo HTTP error " + resp.statusCode() + ": " + resp.body());
            }
            JsonNode root = mapper.readTree(resp.body());
            if (root.has("error") && !root.get("error").isNull()) {
                JsonNode err = root.get("error");
                String msg = err.path("data").path("message").asText(err.toString());
                throw new OdooException("Odoo RPC error: " + msg);
            }
            return root.path("result");
        } catch (OdooException e) {
            throw e;
        } catch (Exception e) {
            throw new OdooException("Odoo RPC call failed: " + e.getMessage(), e);
        }
    }
}
