package so.alaz.slate.command

import io.papermc.paper.command.brigadier.Commands
import so.alaz.slate.command.sub.AnchorCommand
import so.alaz.slate.command.sub.AuditCommand
import so.alaz.slate.command.sub.BackupCommand
import so.alaz.slate.command.sub.CratesCommand
import so.alaz.slate.command.sub.CrateProbabilityCommand
import so.alaz.slate.command.sub.CurrencyAdminCommand
import so.alaz.slate.command.sub.DebugCommand
import so.alaz.slate.command.sub.HealthCommand
import so.alaz.slate.command.sub.HelpCommand
import so.alaz.slate.command.sub.KeyCommand
import so.alaz.slate.command.sub.MigrateCommand
import so.alaz.slate.command.sub.PlayerKeysCommand
import so.alaz.slate.command.sub.PlayerCurrencyCommand
import so.alaz.slate.command.sub.ReloadCommand
import so.alaz.slate.command.sub.RewardProbabilityCommand
import so.alaz.slate.command.sub.RestoreCommand
import so.alaz.slate.command.sub.SessionCommand
import so.alaz.slate.command.sub.TierProbabilityCommand
import so.alaz.slate.command.sub.VersionCommand
import so.alaz.slate.command.sub.AdminPityCommand
import so.alaz.slate.command.sub.AdminEditorCommand

object SlateCommands {
    fun register(commands: Commands) {
        commands.register(
            Commands.literal("slate")
                .then(ReloadCommand.node())
                .then(HelpCommand.node())
                .then(VersionCommand.node())
                .then(HealthCommand.node())
                .then(KeyCommand.node())
                .then(AnchorCommand.node())
                .then(DebugCommand.node())
                .then(CrateProbabilityCommand.node())
                .then(RewardProbabilityCommand.node())
                .then(TierProbabilityCommand.node())
                .then(SessionCommand.node())
                .then(AdminPityCommand.node())
                .then(MigrateCommand.node())
                .then(BackupCommand.node())
                .then(RestoreCommand.node())
                .then(AuditCommand.node())
                .then(CurrencyAdminCommand.node())
                .then(AdminEditorCommand.node())
                .build(),
            "Slate admin commands",
            listOf("sla"),
        )

        commands.register(
            CratesCommand.node(),
            "Slate player crate commands",
            emptyList(),
        )

        commands.register(
            PlayerKeysCommand.node(),
            "Slate player key commands",
            emptyList(),
        )

        commands.register(
            PlayerCurrencyCommand.node(),
            "Slate player currency command",
            emptyList(),
        )
    }
}
