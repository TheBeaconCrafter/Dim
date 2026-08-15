package ac.grim.grimac.command.commands;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.command.BuildableCommand;
import ac.grim.grimac.platform.api.manager.cloud.CloudPlatformCommandArguments;
import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;
import org.jetbrains.annotations.NotNull;

public class GrimInfo implements BuildableCommand {
    @Override
    public void register(CommandManager<Sender> commandManager, CloudPlatformCommandArguments arguments) {
        commandManager.command(
                commandManager.commandBuilder("dim", "grim", "grimac")
                        .literal("info", Description.of("Display Dim version and attribution"))
                        .permission("grim.info")
                        .handler(this::handleInfo)
        );
    }

    private void handleInfo(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        sender.sendMessage(Component.text()
                .append(MessageUtil.miniMessage("%prefix% "))
                .append(Component.text("Dim Version ", NamedTextColor.GRAY))
                .append(Component.text(GrimAPI.INSTANCE.getExternalAPI().getGrimVersion() + " ", NamedTextColor.GOLD))
                .append(Component.text("by ItsBeacon, based on the work of GrimAC", NamedTextColor.GRAY))
                .build());
    }
}
