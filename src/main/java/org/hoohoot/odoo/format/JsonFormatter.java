package org.hoohoot.odoo.format;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class JsonFormatter {

    private final ObjectMapper mapper = new ObjectMapper();

    public void print(List<Map<String, Object>> rows) {
        try {
            System.out.println(mapper.writeValueAsString(rows));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize rows to JSON", e);
        }
    }
}
