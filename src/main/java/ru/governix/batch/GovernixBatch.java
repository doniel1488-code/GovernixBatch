package ru.governix.batch;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GovernixBatch extends JavaPlugin {

    /** Игроки в режиме batch: UUID → сколько команд выполнено */
    private final Map<UUID, Integer> activeSessions = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveResource("scripts/example.txt", false);

        getCommand("gbatch").setExecutor((sender, cmd, label, args) -> handle(sender, args));
        getServer().getPluginManager().registerEvents(new BatchListener(this), this);

        getLogger().info("GovernixBatch v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        activeSessions.clear();
        getLogger().info("GovernixBatch disabled!");
    }

    // ===== API для listener =====

    public boolean isActive(UUID uuid) { return activeSessions.containsKey(uuid); }

    public void start(UUID uuid) { activeSessions.put(uuid, 0); }

    public void stop(UUID uuid) { activeSessions.remove(uuid); }

    public int increment(UUID uuid) {
        return activeSessions.merge(uuid, 1, Integer::sum);
    }

    public int getCount(UUID uuid) {
        return activeSessions.getOrDefault(uuid, 0);
    }

    // ===== Команда =====

    private boolean handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("governixbatch.use")) {
            sender.sendMessage("§cНет прав.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§8§m-------------------------");
            sender.sendMessage("§bGovernixBatch §7— утилита массовых команд");
            sender.sendMessage("");
            sender.sendMessage("§e/gbatch start §7— войти в режим");
            sender.sendMessage("§e/gbatch stop §7— выйти");
            sender.sendMessage("§e/gbatch status §7— сколько выполнил");
            sender.sendMessage("§e/gbatch run <файл> §7— выполнить файл из scripts/");
            sender.sendMessage("");
            sender.sendMessage("§7Пока режим активен — всё, что ты пишешь в чат,");
            sender.sendMessage("§7исполняется от имени §fконсоли§7. Без слэша.");
            sender.sendMessage("§8§m-------------------------");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (!(sender instanceof org.bukkit.entity.Player player)) {
                    sender.sendMessage("§cТолько для игроков — консоль и так всё выполняет.");
                    return true;
                }
                if (isActive(player.getUniqueId())) {
                    sender.sendMessage("§cРежим уже активен. §7/gbatch stop");
                    return true;
                }
                start(player.getUniqueId());
                sender.sendMessage("§a§l▸ §aРежим §fBATCH §aвключён.");
                sender.sendMessage("§7Пиши команды в чат без §f/§7 — они выполнятся от консоли.");
                sender.sendMessage("§7Для выхода: §f/gbatch stop");
            }

            case "stop", "end" -> {
                if (!(sender instanceof org.bukkit.entity.Player player)) return true;
                int done = getCount(player.getUniqueId());
                stop(player.getUniqueId());
                sender.sendMessage("§c§l▸ §cРежим §fBATCH §cвыключен. §7Выполнено: §f" + done);
            }

            case "status" -> {
                if (!(sender instanceof org.bukkit.entity.Player player)) return true;
                boolean on = isActive(player.getUniqueId());
                sender.sendMessage("§7Статус: " + (on ? "§aактивен" : "§cвыключен")
                        + " §7| Выполнено: §f" + getCount(player.getUniqueId()));
            }

            case "run" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cИспользование: §f/gbatch run <файл>");
                    return true;
                }
                runScript(sender, args[1]);
            }

            default -> sender.sendMessage("§cНеизвестная подкоманда. §7/gbatch");
        }
        return true;
    }

    private void runScript(CommandSender sender, String fileName) {
        File dir = new File(getDataFolder(), "scripts");
        if (!dir.exists()) dir.mkdirs();

        if (!fileName.endsWith(".txt")) fileName += ".txt";
        File file = new File(dir, fileName);

        if (!file.exists()) {
            sender.sendMessage("§cФайл не найден: §f" + file.getName());
            return;
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            sender.sendMessage("§cОшибка чтения файла: §f" + e.getMessage());
            return;
        }

        int executed = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("/")) line = line.substring(1);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
            executed++;
        }
        sender.sendMessage("§a§l▸ §aВыполнено §f" + executed + " §aкоманд из §f" + file.getName());
    }
}
