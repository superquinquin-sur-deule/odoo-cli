package org.hoohoot.odoo.config;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "odoo")
public interface OdooConfig {

    String url();

    String database();

    String login();

    String password();

    int timeoutSeconds();
}
