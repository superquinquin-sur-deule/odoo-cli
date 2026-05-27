package org.hoohoot.odoo.command;

import org.hoohoot.odoo.command.barcoderules.ListCommand;
import org.hoohoot.odoo.command.barcoderules.TestCommand;
import picocli.CommandLine.Command;

@Command(
        name = "barcode-rules",
        description = "Gérer les règles de code-barres",
        mixinStandardHelpOptions = true,
        subcommands = {
                ListCommand.class,
                TestCommand.class
        }
)
public class BarcodeRulesCommand {
}
