package org.hoohoot.odoo.command;

import org.hoohoot.odoo.command.cooperators.ListCommand;
import org.hoohoot.odoo.command.cooperators.ResetFtopCounterCommand;
import picocli.CommandLine.Command;

@Command(
        name = "cooperators",
        description = "Gérer les coopérateurs",
        mixinStandardHelpOptions = true,
        subcommands = {
                ListCommand.class,
                ResetFtopCounterCommand.class
        }
)
public class CooperatorsCommand {
}
