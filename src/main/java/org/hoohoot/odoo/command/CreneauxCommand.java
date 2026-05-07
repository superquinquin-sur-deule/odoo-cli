package org.hoohoot.odoo.command;

import org.hoohoot.odoo.command.creneaux.ConfirmServicesCommand;
import org.hoohoot.odoo.command.creneaux.CreateServicesCommand;
import org.hoohoot.odoo.command.creneaux.ListCommand;
import picocli.CommandLine.Command;

@Command(
        name = "creneaux",
        description = "Gérer les créneaux (shift templates)",
        mixinStandardHelpOptions = true,
        subcommands = {
                ListCommand.class,
                CreateServicesCommand.class,
                ConfirmServicesCommand.class
        }
)
public class CreneauxCommand {
}
