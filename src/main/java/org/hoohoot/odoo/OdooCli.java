package org.hoohoot.odoo;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import org.hoohoot.odoo.command.CooperatorsCommand;
import picocli.CommandLine.Command;

@TopCommand
@Command(
        name = "odoo",
        mixinStandardHelpOptions = true,
        version = "odoo-cli 1.0.0",
        description = "CLI pour interagir avec une instance Odoo",
        subcommands = {
                CooperatorsCommand.class
        }
)
public class OdooCli {
}
