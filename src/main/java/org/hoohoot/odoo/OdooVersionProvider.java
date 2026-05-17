package org.hoohoot.odoo;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import picocli.CommandLine.IVersionProvider;

@Dependent
public class OdooVersionProvider implements IVersionProvider {

    @Inject
    @ConfigProperty(name = "quarkus.application.version", defaultValue = "dev")
    String version;

    @Override
    public String[] getVersion() {
        return new String[]{"odoo-cli " + version};
    }
}
