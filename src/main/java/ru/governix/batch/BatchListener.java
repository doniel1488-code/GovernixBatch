package ru.governix.batch;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

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

        // Разбиваем по \n на случай многострочной вставки
        String[] lines = text.split("\\r?\\n");

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty()) continue;

                // Локальные команды выхода
                if (line.equalsIgnoreCase("end")
                        || line.equalsIgnoreCase("stop")
                        || line.equalsIgnoreCase("стоп")
                        || line.equalsIgnoreCase("выход")) {
                    int done = plugin.getCount(player.getUniqueId());
                    plugin.stop(player.getUniqueId());
                    player.sendMessage("§c§l▸ §cРежим §fBATCH §cвыключен. §7Выполнено: §f" + done);
                    return;
                }

                if (line.startsWith("/")) line = line.substring(1);

                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
                    plugin.increment(player.getUniqueId());
                    player.sendMessage("§8§l▸ §7" + line);
                } catch (Exception ex) {
                    player.sendMessage("§c✖ §7" + line + " §8— " + ex.getMessage());
                }
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.stop(event.getPlayer().getUniqueId());
    }
}
