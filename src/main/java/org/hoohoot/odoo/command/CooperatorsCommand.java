package org.hoohoot.odoo.command;

import org.hoohoot.odoo.command.cooperators.ExportPartsCommand;
import org.hoohoot.odoo.command.cooperators.FixBinomeEmailsCommand;
import org.hoohoot.odoo.command.cooperators.ListCommand;
import org.hoohoot.odoo.command.cooperators.ResetFtopCounterCommand;
import org.hoohoot.odoo.command.cooperators.SyncBrevoCommand;
import picocli.CommandLine.Command;

@Command(
        name = "cooperators",
        description = "Gérer les coopérateurs",
        mixinStandardHelpOptions = true,
        subcommands = {
                ListCommand.class,
                ExportPartsCommand.class,
                ResetFtopCounterCommand.class,
                SyncBrevoCommand.class,
                FixBinomeEmailsCommand.class
        }
)
public class CooperatorsCommand {
}
