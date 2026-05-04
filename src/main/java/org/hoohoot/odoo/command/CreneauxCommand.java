package org.hoohoot.odoo.command;

import org.hoohoot.odoo.command.creneaux.ListCommand;
import picocli.CommandLine.Command;

@Command(
        name = "creneaux",
        description = "Gérer les créneaux (shift templates)",
        mixinStandardHelpOptions = true,
        subcommands = {
                ListCommand.class
        }
)
public class CreneauxCommand {
}
