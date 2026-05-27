package org.hoohoot.odoo.test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;

public class WireMockOdooResource implements QuarkusTestResourceLifecycleManager {

    private static final String STUBS_PROPERTY = "wiremock.test.stubs";

    private static final String PARTNERS = """
            [
              {"id":1,"name":"Doe, Alice","email":"alice@example.com","street":"1 rue","zip":"75001","city":"Paris","total_partner_owned_share":1,"cooperative_state":"up_to_date","is_associated_people":false},
              {"id":2,"name":"Smith, Bob","email":"bob@example.com","street":false,"zip":false,"city":false,"total_partner_owned_share":1,"cooperative_state":"alert","is_associated_people":false},
              {"id":3,"name":"Jones, Carol","email":"alice@example.com","street":false,"zip":false,"city":false,"total_partner_owned_share":1,"cooperative_state":"unsubscribed","is_associated_people":false},
              {"id":4,"name":"Brown, Dave","email":false,"street":false,"zip":false,"city":false,"total_partner_owned_share":1,"cooperative_state":"up_to_date","is_associated_people":true}
            ]
            """;

    private static final String INVOICES = """
            [
              {"partner_id":[1,"Doe, Alice"],"amount_total_signed":100,"date_invoice":"2020-01-15"},
              {"partner_id":[2,"Smith, Bob"],"amount_total_signed":50,"date_invoice":"2021-06-10"},
              {"partner_id":[3,"Jones, Carol"],"amount_total_signed":75,"date_invoice":"2019-03-20"},
              {"partner_id":[4,"Brown, Dave"],"amount_total_signed":25,"date_invoice":"2022-11-05"}
            ]
            """;

    private static final String BINOMES = """
            [
              {"id":5,"name":"Smith, Eve","email":"eve@example.com","street":"2 rue","zip":"75002","city":"Paris","parent_id":[2,"Smith, Bob"]}
            ]
            """;

    private static final String TEMPLATES = """
            [
              {"id":1,"name":"Mon AM","week_name":"A","shift_type_id":[1,"Standard"],"start_datetime_tz":"2026-01-05 09:00:00","end_datetime_tz":"2026-01-05 12:00:00","duration":3,"seats_min":2,"seats_max":5,"seats_reserved":1},
              {"id":2,"name":"Mon PM","week_name":"A","shift_type_id":[1,"Standard"],"start_datetime_tz":"2026-01-05 13:00:00","end_datetime_tz":"2026-01-05 16:00:00","duration":3,"seats_min":2,"seats_max":5,"seats_reserved":3},
              {"id":3,"name":"Tue AM","week_name":"A","shift_type_id":[1,"Standard"],"start_datetime_tz":"2026-01-06 09:00:00","end_datetime_tz":"2026-01-06 12:00:00","duration":3,"seats_min":0,"seats_max":5,"seats_reserved":0}
            ]
            """;

    private static final String DRAFT_SHIFTS = """
            [
              {"id":101,"name":"Mon AM 06/04","state":"draft","date_begin":"2026-04-20 09:00:00"},
              {"id":102,"name":"Mon PM 06/04","state":"draft","date_begin":"2026-04-20 13:00:00"}
            ]
            """;

    private static final String CONFIRMED_SHIFTS_WITH_TICKETS = """
            [
              {"id":201,"name":"Mon AM 20/04","shift_template_id":[1,"Mon AM"],"shift_ticket_ids":[301,302]},
              {"id":202,"name":"Mon PM 20/04","shift_template_id":[2,"Mon PM"],"shift_ticket_ids":[303,304]}
            ]
            """;

    private static final String SHIFT_TICKETS_JSON = """
            [
              {"id":301,"shift_id":[201,"Mon AM 20/04"],"shift_type":"standard","seats_max":8,"seats_reserved":6},
              {"id":302,"shift_id":[201,"Mon AM 20/04"],"shift_type":"ftop","seats_max":4,"seats_reserved":1},
              {"id":303,"shift_id":[202,"Mon PM 20/04"],"shift_type":"standard","seats_max":10,"seats_reserved":10},
              {"id":304,"shift_id":[202,"Mon PM 20/04"],"shift_type":"ftop","seats_max":5,"seats_reserved":2}
            ]
            """;

    private static final String SHIFT_TEMPLATE_TICKETS_JSON = """
            [
              {"shift_template_id":[1,"Mon AM"],"seats_max":8},
              {"shift_template_id":[2,"Mon PM"],"seats_max":10}
            ]
            """;

    private static final String EMPTY_ARRAY = "[]";

    private static final String DRAFT_SHIFTS_WITH_TICKETS_JSON = """
            [
              {"id":203,"name":"Tue AM 27/04","shift_template_id":[1,"Mon AM"],"shift_ticket_ids":[305,306]}
            ]
            """;

    private static final String DRAFT_SHIFT_TICKETS_JSON = """
            [
              {"id":305,"shift_id":[203,"Tue AM 27/04"],"shift_type":"standard","seats_max":8,"seats_reserved":3},
              {"id":306,"shift_id":[203,"Tue AM 27/04"],"shift_type":"ftop","seats_max":4,"seats_reserved":0}
            ]
            """;

    private static final String SHIFTS_FOR_ALERT_JSON = """
            [
              {"id":401,"name":"CMar. - 13:15","state":"draft","date_begin":"2026-05-19 11:15:00","date_end":"2026-05-19 14:00:00","seats_min":4,"seats_max":4,"seats_reserved":1,"shift_template_id":[58,"CMar. - 13:15"]},
              {"id":402,"name":"CMar. - 15:45","state":"draft","date_begin":"2026-05-19 13:45:00","date_end":"2026-05-19 16:30:00","seats_min":5,"seats_max":5,"seats_reserved":0,"shift_template_id":[59,"CMar. - 15:45"]},
              {"id":403,"name":"CMar. - 18:15","state":"confirm","date_begin":"2026-05-19 16:15:00","date_end":"2026-05-19 19:00:00","seats_min":3,"seats_max":4,"seats_reserved":3,"shift_template_id":[60,"CMar. - 18:15"]},
              {"id":404,"name":"Volants CMar.","state":"draft","date_begin":"2026-05-19 20:00:00","date_end":"2026-05-19 22:00:00","seats_min":0,"seats_max":4,"seats_reserved":0,"shift_template_id":[61,"Volants CMar."]}
            ]
            """;

    private static final String IR_MODEL_DATA_JSON = """
            [
              {"id":1,"module":"__export__","name":"product_template_3802","model":"product.template","res_id":18099},
              {"id":2,"module":"__export__","name":"product_template_34039_7d5bebe8","model":"product.template","res_id":18100},
              {"id":3,"module":"__export__","name":"product_category_146","model":"product.category","res_id":201}
            ]
            """;

    private static final String BARCODE_RULE_JSON = """
            [{"id":118,"name":"Price Look Up Codes (PLU Codes)"}]
            """;

    private static final String BARCODE_RULES_LIST_JSON = """
            [
              {"id":22,"name":"Meal Voucher Payment","type":"meal_voucher_payment","encoding":"any","pattern":"...........{NNNDD}........","sequence":1,"create_date":"2026-01-23 12:10:24","transform_expr":false,"barcode_nomenclature_id":[1,"Default Nomenclature"]},
              {"id":7,"name":"Customer Barcodes","type":"client","encoding":"any","pattern":"042","sequence":2,"create_date":"2026-01-23 10:06:10","transform_expr":false,"barcode_nomenclature_id":[1,"Default Nomenclature"]},
              {"id":6,"name":"Cashier Barcodes","type":"cashier","encoding":"ean13","pattern":"041","sequence":3,"create_date":"2026-01-23 10:06:10","transform_expr":"value * 2","barcode_nomenclature_id":[1,"Default Nomenclature"]},
              {"id":5,"name":"Location barcodes","type":"location","encoding":"any","pattern":"414","sequence":4,"create_date":"2026-01-22 10:05:50","transform_expr":"value / 6.55957","barcode_nomenclature_id":[1,"Default Nomenclature"]}
            ]
            """;

    private static final String PRODUCT_TEMPLATE_READ_JSON = """
            [
              {"id":18099,"name":"Ail sec BIO","default_code":"OLD","barcode_base":0,"barcode_rule_id":false,"categ_id":[200,"Ancienne"]},
              {"id":18100,"name":"Aillet botte BIO","default_code":false,"barcode_base":0,"barcode_rule_id":false,"categ_id":[200,"Ancienne"]}
            ]
            """;

    public enum Stub {
        PARTNERS {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("res.partner")))
                        .withRequestBody(matchingJsonPath("$.params.args[5][0][0][0]", equalTo("is_member")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + WireMockOdooResource.PARTNERS + "}")));
            }
        },
        BINOMES {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("res.partner")))
                        .withRequestBody(matchingJsonPath("$.params.args[5][0][0][0]", equalTo("parent_id")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + WireMockOdooResource.BINOMES + "}")));
            }
        },
        INVOICES {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("account.invoice")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + WireMockOdooResource.INVOICES + "}")));
            }
        },
        SHIFT_TEMPLATES {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("shift.template")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + TEMPLATES + "}")));
            }
        },
        CREATE_SHIFTS_WIZARD {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("create.shifts.wizard")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("create")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":99}")));
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("create.shifts.wizard")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("create_shifts")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":true}")));
            }
        },
        DRAFT_SHIFTS {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("shift.shift")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + WireMockOdooResource.DRAFT_SHIFTS + "}")));
            }
        },
        BUTTON_CONFIRM {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("shift.shift")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("button_confirm")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":true}")));
            }
        },
        IR_MODEL_DATA_LOOKUP {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("ir.model.data")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + IR_MODEL_DATA_JSON + "}")));
            }
        },
        BARCODE_RULE_LOOKUP {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("barcode.rule")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + BARCODE_RULE_JSON + "}")));
            }
        },
        BARCODE_RULES_LIST {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("barcode.rule")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .withRequestBody(matchingJsonPath("$.params.args[5][0][0][0]", equalTo("barcode_nomenclature_id")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + BARCODE_RULES_LIST_JSON + "}")));
            }
        },
        BARCODE_NOMENCLATURE_DEFAULT {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("barcode.nomenclature")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":[{\"id\":1,\"name\":\"Default Nomenclature\"}]}")));
            }
        },
        PRODUCT_TEMPLATE_READ {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("product.template")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + PRODUCT_TEMPLATE_READ_JSON + "}")));
            }
        },
        PRODUCT_TEMPLATE_WRITE {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("product.template")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("write")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":true}")));
            }
        },
        CONFIRMED_SHIFTS_WITH_TICKETS {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("shift.shift")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + WireMockOdooResource.CONFIRMED_SHIFTS_WITH_TICKETS + "}")));
            }
        },
        NO_SHIFTS {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("shift.shift")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + EMPTY_ARRAY + "}")));
            }
        },
        SHIFT_TICKETS_READ {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("shift.ticket")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + SHIFT_TICKETS_JSON + "}")));
            }
        },
        SHIFT_TEMPLATE_TICKETS_READ {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("shift.template.ticket")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + SHIFT_TEMPLATE_TICKETS_JSON + "}")));
            }
        },
        SHIFT_TICKET_WRITE {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("shift.ticket")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("write")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":true}")));
            }
        },
        SHIFTS_STATE_IN_MATCH {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("shift.shift")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .withRequestBody(matchingJsonPath("$.params.args[5][0][0][0]", equalTo("state")))
                        .withRequestBody(matchingJsonPath("$.params.args[5][0][0][1]", equalTo("in")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + DRAFT_SHIFTS_WITH_TICKETS_JSON + "}")));
            }
        },
        DRAFT_SHIFT_TICKETS_READ {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("shift.ticket")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + DRAFT_SHIFT_TICKETS_JSON + "}")));
            }
        },
        SHIFTS_FOR_ALERT {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("shift.shift")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + SHIFTS_FOR_ALERT_JSON + "}")));
            }
        };

        abstract void register(WireMockServer s);
    }

    public static void expect(Stub... stubs) {
        if (stubs.length == 0) {
            System.clearProperty(STUBS_PROPERTY);
            return;
        }
        System.setProperty(STUBS_PROPERTY,
                Arrays.stream(stubs).map(Enum::name).collect(Collectors.joining(",")));
    }

    private WireMockServer server;

    @Override
    public Map<String, String> start() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();

        server.stubFor(post("/jsonrpc")
                .withRequestBody(matchingJsonPath("$.params.method", equalTo("login")))
                .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":1}")));

        String stubsCsv = System.getProperty(STUBS_PROPERTY, "");
        for (String key : stubsCsv.split(",")) {
            String trimmed = key.trim();
            if (!trimmed.isEmpty()) {
                Stub.valueOf(trimmed).register(server);
            }
        }

        return Map.of("odoo.url", server.baseUrl());
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }
}
