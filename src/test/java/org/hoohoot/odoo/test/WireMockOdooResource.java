package org.hoohoot.odoo.test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;

public class WireMockOdooResource implements QuarkusTestResourceLifecycleManager {

    private static final String PARTNERS = """
            [
              {"id":1,"name":"Doe, Alice","email":"alice@example.com","street":"1 rue","zip":"75001","city":"Paris","total_partner_owned_share":1},
              {"id":2,"name":"Smith, Bob","email":"bob@example.com","street":false,"zip":false,"city":false,"total_partner_owned_share":1},
              {"id":3,"name":"Jones, Carol","email":"alice@example.com","street":false,"zip":false,"city":false,"total_partner_owned_share":1},
              {"id":4,"name":"Brown, Dave","email":false,"street":false,"zip":false,"city":false,"total_partner_owned_share":1}
            ]
            """;

    private static final String INVOICES = """
            [
              {"partner_id":[1,"Doe, Alice"],"amount_total_signed":100,"date_invoice":"2020-01-01"},
              {"partner_id":[2,"Smith, Bob"],"amount_total_signed":50,"date_invoice":"2020-01-01"},
              {"partner_id":[3,"Jones, Carol"],"amount_total_signed":75,"date_invoice":"2020-01-01"},
              {"partner_id":[4,"Brown, Dave"],"amount_total_signed":25,"date_invoice":"2020-01-01"}
            ]
            """;

    private static final String TEMPLATES = """
            [
              {"id":1,"name":"Mon AM","week_name":"A","shift_type_id":[1,"Standard"],"start_datetime_tz":"2026-01-05 09:00:00","end_datetime_tz":"2026-01-05 12:00:00","duration":3,"seats_min":2,"seats_max":5,"seats_reserved":1},
              {"id":2,"name":"Mon PM","week_name":"A","shift_type_id":[1,"Standard"],"start_datetime_tz":"2026-01-05 13:00:00","end_datetime_tz":"2026-01-05 16:00:00","duration":3,"seats_min":2,"seats_max":5,"seats_reserved":3},
              {"id":3,"name":"Tue AM","week_name":"A","shift_type_id":[1,"Standard"],"start_datetime_tz":"2026-01-06 09:00:00","end_datetime_tz":"2026-01-06 12:00:00","duration":3,"seats_min":0,"seats_max":5,"seats_reserved":0}
            ]
            """;

    private WireMockServer server;

    @Override
    public Map<String, String> start() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();

        server.stubFor(post("/jsonrpc")
                .withRequestBody(matchingJsonPath("$.params.method", equalTo("login")))
                .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":1}")));

        server.stubFor(post("/jsonrpc")
                .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("res.partner")))
                .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + PARTNERS + "}")));

        server.stubFor(post("/jsonrpc")
                .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("account.invoice")))
                .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + INVOICES + "}")));

        server.stubFor(post("/jsonrpc")
                .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("shift.template")))
                .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + TEMPLATES + "}")));

        return Map.of("odoo.url", server.baseUrl());
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop();
        }
    }
}
