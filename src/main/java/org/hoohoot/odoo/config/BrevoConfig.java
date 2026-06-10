package org.hoohoot.odoo.config;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "brevo")
public interface BrevoConfig {
    String url();
    String apiKey();
}
