package org.hoohoot.odoo;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import org.hoohoot.odoo.command.ArticlesCommand;
import org.hoohoot.odoo.command.CooperatorsCommand;
import org.hoohoot.odoo.command.CreneauxCommand;
import org.hoohoot.odoo.command.UpdateCommand;
import picocli.CommandLine.Command;

@TopCommand
@Command(
        name = "odoo",
        mixinStandardHelpOptions = true,
        version = "odoo-cli 1.0.0",
        description = "CLI pour interagir avec une instance Odoo",
        subcommands = {
                ArticlesCommand.class,
                CooperatorsCommand.class,
                CreneauxCommand.class,
                UpdateCommand.class
        }
)
public class OdooCli {
}
