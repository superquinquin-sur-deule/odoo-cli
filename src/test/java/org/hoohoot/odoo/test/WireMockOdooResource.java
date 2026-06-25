package org.hoohoot.odoo.test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
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

    private static final String FTOP_COUNTER_EVENTS_JSON = """
            [
              {"id":67,"name":"Attended","type":"ftop","point_qty":1.0,"partner_id":[1689,"ARDOUIN PERO, Joëlle"],"shift_id":[244,"DMar. - 15:45 26/05/2026 15:45"],"ignored":false,"reason_ids":[],"create_date":"2026-05-25 16:14:15"},
              {"id":68,"name":"Attended","type":"ftop","point_qty":1.0,"partner_id":[1816,"BELVA, Frederic"],"shift_id":[244,"DMar. - 15:45 26/05/2026 15:45"],"ignored":false,"reason_ids":[],"create_date":"2026-05-26 16:08:14"},
              {"id":70,"name":"Présent","type":"ftop","point_qty":1.0,"partner_id":[1879,"LONGUEVAL, Bertrande"],"shift_id":[235,"DLun. - 15:45 25/05/2026 15:45"],"ignored":false,"reason_ids":[],"create_date":"2026-05-26 19:45:45"}
            ]
            """;

    private static final String FTOP_COUNTER_EVENTS_PARTNER_FILTER_JSON = """
            [
              {"id":67,"name":"Attended","type":"ftop","point_qty":1.0,"partner_id":[1689,"ARDOUIN PERO, Joëlle"],"shift_id":[244,"DMar. - 15:45 26/05/2026 15:45"],"ignored":false,"reason_ids":[],"create_date":"2026-05-25 16:14:15"},
              {"id":70,"name":"Présent","type":"ftop","point_qty":1.0,"partner_id":[1879,"LONGUEVAL, Bertrande"],"shift_id":[235,"DLun. - 15:45 25/05/2026 15:45"],"ignored":false,"reason_ids":[],"create_date":"2026-05-26 19:45:45"}
            ]
            """;

    private static final String PRODUCT_TEMPLATE_READ_JSON = """
            [
              {"id":18099,"name":"Ail sec BIO","default_code":"OLD","barcode_base":0,"barcode_rule_id":false,"categ_id":[200,"Ancienne"]},
              {"id":18100,"name":"Aillet botte BIO","default_code":false,"barcode_base":0,"barcode_rule_id":false,"categ_id":[200,"Ancienne"]}
            ]
            """;

    private static final String FTOP_PARTNERS_DISPLAY_JSON = """
            [
              {"id":1689,"name":"ARDOUIN PERO, Joëlle","display_ftop_points":3.0},
              {"id":1816,"name":"BELVA, Frederic","display_ftop_points":1.0},
              {"id":1879,"name":"LONGUEVAL, Bertrande","display_ftop_points":1.0}
            ]
            """;

    private static final String FTOP_PARTNERS_DISPLAY_PARTNER_FILTER_JSON = """
            [
              {"id":1689,"name":"ARDOUIN PERO, Joëlle","display_ftop_points":3.0},
              {"id":1879,"name":"LONGUEVAL, Bertrande","display_ftop_points":1.0}
            ]
            """;

    private static final String COEFFICIENT_FOUND_JSON = """
            [
              {"id":500,"name":"Surtaxe carburant 2026","value":0.02,"operation_type":"multiplier"}
            ]
            """;

    private static final String SUPPLIERINFO_FOR_COEFF_JSON = """
            [
              {"id":2001,"product_tmpl_id":[101,"Produit A"]},
              {"id":2002,"product_tmpl_id":[102,"Produit B"]},
              {"id":2003,"product_tmpl_id":[103,"Produit C"]}
            ]
            """;

    private static final String PRODUCT_TEMPLATE_COEFF_JSON = """
            [
              {"id":101,"name":"Produit A","base_price":10.0,"list_price":12.0,"coeff1_id":false,"coeff2_id":false,"coeff3_id":false,"coeff4_id":false,"coeff5_id":false,"coeff6_id":false,"coeff7_id":false,"coeff8_id":false,"coeff9_id":[300,"Marge fonctionnement 25%"]},
              {"id":102,"name":"Produit B","base_price":20.0,"list_price":24.0,"coeff1_id":false,"coeff2_id":[200,"Transport Beyaert"],"coeff3_id":false,"coeff4_id":false,"coeff5_id":false,"coeff6_id":false,"coeff7_id":false,"coeff8_id":false,"coeff9_id":[300,"Marge fonctionnement 25%"]},
              {"id":103,"name":"Produit C","base_price":30.0,"list_price":36.0,"coeff1_id":false,"coeff2_id":false,"coeff3_id":false,"coeff4_id":[500,"Surtaxe carburant 2026"],"coeff5_id":false,"coeff6_id":false,"coeff7_id":false,"coeff8_id":false,"coeff9_id":[300,"Marge fonctionnement 25%"]}
            ]
            """;

    private static final String SUPPLIER_BY_NAME_JSON = """
            [
              {"id":328,"name":"ALVEUS GmbH"}
            ]
            """;

    private static final String MEMBERS_SINCE_JSON = """
            [
              {"id":2595,"name":"GRAS, Pierre-Louis","email":"plmp.gras@wanadoo.fr ","create_date":"2026-06-06 09:00:54"},
              {"id":2594,"name":"FLORET, Faustine","email":"faust.floret@gmail.com","create_date":"2026-06-06 08:56:40"},
              {"id":2596,"name":"Madonna","email":"madonna@example.com","create_date":"2026-06-07 10:00:00"}
            ]
            """;

    private static final String BINOME_MISSING_EMAIL_JSON = """
            [
              {"id":2300,"name":"RENARD, Fanny","email":false,"parent_id":[2050,"292 - FEUTRY, Simon"]},
              {"id":9001,"name":"NOMATCH, Person","email":false,"parent_id":[3000,"500 - SOLO, Jean"]},
              {"id":9002,"name":"AMBIGU, Two","email":false,"parent_id":[3001,"501 - DUO, Marie"]}
            ]
            """;

    private static final String BINOME_EMAIL_SOURCES_JSON = """
            [
              {"id":2051,"name":"RENARD, Fanny","email":"fanny_renard@outlook.com"},
              {"id":9003,"name":"AMBIGU, Two","email":"a@example.com"},
              {"id":9004,"name":"AMBIGU, Two","email":"b@example.com"}
            ]
            """;

    private static final String PRODUCTS_CREATED_SINCE_JSON = """
            [
              {"id":34125,"name":"Produit Créé A","create_date":"2026-05-01 07:55:53","create_uid":[28,"Marie Martin"],"product_variant_ids":[33747]},
              {"id":34039,"name":"Produit Créé B","create_date":"2026-05-12 15:21:11","create_uid":[28,"Marie Martin"],"product_variant_ids":[33700]}
            ]
            """;

    private static final String POS_ORDER_LINE_QTY_JSON = """
            [
              {"product_id_count":40,"qty":125.219,"product_id":[33747,"Produit Créé A"]},
              {"product_id_count":12,"qty":39.0,"product_id":[33700,"Produit Créé B"]}
            ]
            """;

    private static final String PRODUCTS_ARCHIVED_SINCE_JSON = """
            [
              {"id":18184,"name":"Produit Archivé A","create_date":"2026-01-28 15:21:01","write_date":"2026-05-25 09:39:22","write_uid":[28,"Marie Martin"]},
              {"id":24781,"name":"Produit Archivé B","create_date":"2026-01-28 17:23:46","write_date":"2026-05-25 09:17:24","write_uid":[28,"Marie Martin"]}
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
        },
        FTOP_COUNTER_EVENTS_READ {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("shift.counter.event")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + FTOP_COUNTER_EVENTS_JSON + "}")));
            }
        },
        NO_COUNTER_EVENTS {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("shift.counter.event")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + EMPTY_ARRAY + "}")));
            }
        },
        SHIFT_COUNTER_EVENT_WRITE {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("shift.counter.event")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("write")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":true}")));
            }
        },
        FTOP_COUNTER_EVENTS_PARTNER_FILTER {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("shift.counter.event")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .withRequestBody(matchingJsonPath("$.params.args[5][0][?(@[0] == 'partner_id')][1]", equalTo("in")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + FTOP_COUNTER_EVENTS_PARTNER_FILTER_JSON + "}")));
            }
        },
        SHIFT_COUNTER_EVENT_CREATE {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("shift.counter.event")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("create")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":999}")));
            }
        },
        FTOP_PARTNERS_DISPLAY_READ {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("res.partner")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .withRequestBody(matchingJsonPath("$.params.args[5][0][0][0]", equalTo("display_ftop_points")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + FTOP_PARTNERS_DISPLAY_JSON + "}")));
            }
        },
        FTOP_PARTNERS_DISPLAY_PARTNER_FILTER {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("res.partner")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .withRequestBody(matchingJsonPath("$.params.args[5][0][?(@[0] == 'id')][1]", equalTo("in")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + FTOP_PARTNERS_DISPLAY_PARTNER_FILTER_JSON + "}")));
            }
        },
        NO_FTOP_PARTNERS {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("res.partner")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .withRequestBody(matchingJsonPath("$.params.args[5][0][0][0]", equalTo("display_ftop_points")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + EMPTY_ARRAY + "}")));
            }
        },
        COEFFICIENT_FOUND {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("product.coefficient")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + COEFFICIENT_FOUND_JSON + "}")));
            }
        },
        COEFFICIENT_NOT_FOUND {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("product.coefficient")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + EMPTY_ARRAY + "}")));
            }
        },
        COEFFICIENT_CREATE {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("product.coefficient")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("create")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":500}")));
            }
        },
        SUPPLIERINFO_FOR_COEFF {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("product.supplierinfo")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + SUPPLIERINFO_FOR_COEFF_JSON + "}")));
            }
        },
        NO_SUPPLIERINFO {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("product.supplierinfo")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + EMPTY_ARRAY + "}")));
            }
        },
        PRODUCT_TEMPLATE_COEFF_READ {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("product.template")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + PRODUCT_TEMPLATE_COEFF_JSON + "}")));
            }
        },
        SUPPLIER_BY_NAME {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("res.partner")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .withRequestBody(matchingJsonPath("$.params.args[5][0][?(@[0] == 'supplier')][2]", equalTo("true")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + SUPPLIER_BY_NAME_JSON + "}")));
            }
        },
        SUPPLIER_BY_NAME_NONE {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("res.partner")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .withRequestBody(matchingJsonPath("$.params.args[5][0][?(@[0] == 'supplier')][2]", equalTo("true")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + EMPTY_ARRAY + "}")));
            }
        },
        MEMBERS_SINCE {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("res.partner")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .withRequestBody(matchingJsonPath("$.params.args[5][0][?(@[0] == 'is_member')][2]", equalTo("true")))
                        .withRequestBody(matchingJsonPath("$.params.args[5][0][?(@[0] == 'create_date')][1]", equalTo(">=")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + MEMBERS_SINCE_JSON + "}")));
            }
        },
        BREVO_CONTACT_CREATED {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/v3/contacts")
                        .withHeader("api-key", equalTo("test-key"))
                        .withRequestBody(matchingJsonPath("$.email"))
                        .withRequestBody(matchingJsonPath("$.attributes.NOM"))
                        .withRequestBody(matchingJsonPath("$.listIds[0]", equalTo("42")))
                        .withRequestBody(matchingJsonPath("$.updateEnabled", equalTo("false")))
                        .willReturn(aResponse()
                                .withStatus(201)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"id\":123}")));
            }
        },
        BREVO_CONTACT_DUPLICATE {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/v3/contacts")
                        .withHeader("api-key", equalTo("test-key"))
                        .withRequestBody(matchingJsonPath("$.email", equalTo("faust.floret@gmail.com")))
                        .withRequestBody(matchingJsonPath("$.attributes.NOM", equalTo("FLORET")))
                        .withRequestBody(matchingJsonPath("$.attributes.PRENOM", equalTo("Faustine")))
                        .willReturn(aResponse()
                                .withStatus(400)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"code\":\"duplicate_parameter\",\"message\":\"Contact already exist\"}")));
            }
        },
        BINOME_CONTACTS_MISSING_EMAIL {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("res.partner")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .withRequestBody(matchingJsonPath("$.params.args[5][0][?(@[0] == 'is_associated_people')][2]", equalTo("true")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + BINOME_MISSING_EMAIL_JSON + "}")));
            }
        },
        BINOME_NO_MISSING_EMAIL {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("res.partner")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .withRequestBody(matchingJsonPath("$.params.args[5][0][?(@[0] == 'is_associated_people')][2]", equalTo("true")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + EMPTY_ARRAY + "}")));
            }
        },
        BINOME_EMAIL_SOURCES {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("res.partner")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .withRequestBody(matchingJsonPath("$.params.args[5][0][?(@[0] == 'name')][1]", equalTo("in")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + BINOME_EMAIL_SOURCES_JSON + "}")));
            }
        },
        PARTNER_EMAIL_WRITE {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("res.partner")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("write")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":true}")));
            }
        },
        PRODUCTS_CREATED_SINCE {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("product.template")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .withRequestBody(matchingJsonPath("$.params.args[5][0][0][0]", equalTo("create_date")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + PRODUCTS_CREATED_SINCE_JSON + "}")));
            }
        },
        PRODUCTS_ARCHIVED_SINCE {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("product.template")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("search_read")))
                        .withRequestBody(matchingJsonPath("$.params.args[5][0][0][0]", equalTo("active")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + PRODUCTS_ARCHIVED_SINCE_JSON + "}")));
            }
        },
        POS_ORDER_LINE_QTY {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/jsonrpc")
                        .withRequestBody(matchingJsonPath("$.params.args[3]", equalTo("pos.order.line")))
                        .withRequestBody(matchingJsonPath("$.params.args[4]", equalTo("read_group")))
                        .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":" + POS_ORDER_LINE_QTY_JSON + "}")));
            }
        },
        BREVO_EMAIL_SENT {
            @Override
            void register(WireMockServer s) {
                s.stubFor(post("/v3/smtp/email")
                        .withHeader("api-key", equalTo("test-key"))
                        .withRequestBody(matchingJsonPath("$.sender.email"))
                        .withRequestBody(matchingJsonPath("$.to[0].email"))
                        .withRequestBody(matchingJsonPath("$.attachment[0].content"))
                        .willReturn(aResponse()
                                .withStatus(201)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"messageId\":\"<abc@brevo>\"}")));
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

        return Map.of(
                "odoo.url", server.baseUrl(),
                "brevo.url", server.baseUrl(),
                "brevo.api-key", "test-key");
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }
}
