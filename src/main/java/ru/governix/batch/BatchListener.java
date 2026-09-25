package ru.governix.batch;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

public class BatchListener implements Listener {

    private final GovernixBatch plugin;

    public BatchListener(GovernixBatch plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isActive(player.getUniqueId())) return;

        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (text.isEmpty()) return;

        List<String> commands = plugin.splitCommands(text);

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String line : commands) {
                if (line.isEmpty()) continue;

                if (line.equalsIgnoreCase("end")
                        || line.equalsIgnoreCase("stop")
                        || line.equalsIgnoreCase("стоп")
                        || line.equalsIgnoreCase("выход")) {
                    int done = plugin.getCount(player.getUniqueId());
                    plugin.stop(player.getUniqueId());
                    player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                            .legacyAmpersand().deserialize(
                                    "§c§l▸ §cРежим §fBATCH §cвыключен. §7Выполнено: §f" + done));
                    return;
                }

                if (line.startsWith("/")) line = line.substring(1);

                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
                    plugin.increment(player.getUniqueId());
                    if (plugin.isVerbose()) {
                        player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacyAmpersand().deserialize("§8§l▸ §7" + line));
                    }
                } catch (Exception ex) {
                    player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                            .legacyAmpersand().deserialize("§c✖ §7" + line + " §8— " + ex.getMessage()));
                }
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.stop(event.getPlayer().getUniqueId());
        plugin.removePendingConfirm(event.getPlayer().getUniqueId());
    }
}
