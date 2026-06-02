package org.hoohoot.odoo.command;

import org.hoohoot.odoo.command.articles.ApplySupplierCoefficientCommand;
import org.hoohoot.odoo.command.articles.UpdateInternalReferencesCommand;
import picocli.CommandLine.Command;

@Command(
        name = "articles",
        description = "Gérer les articles (produits)",
        mixinStandardHelpOptions = true,
        subcommands = {
                UpdateInternalReferencesCommand.class,
                ApplySupplierCoefficientCommand.class
        }
)
public class ArticlesCommand {
}
