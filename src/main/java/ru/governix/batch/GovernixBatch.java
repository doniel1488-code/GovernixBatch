package ru.governix.batch;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.governix.batch.gui.BatchGui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class GovernixBatch extends JavaPlugin {

    private static final LegacyComponentSerializer L = LegacyComponentSerializer.legacyAmpersand();

    private final Map<UUID, Integer> activeSessions = new ConcurrentHashMap<>();
    /** Игроки, у которых открыто окно подтверждения — блокируем повторное открытие */
    private final Set<UUID> pendingConfirm = ConcurrentHashMap.newKeySet();

    private List<String> splitKeywords = new ArrayList<>();
    private String separator = ";;";
    private Pattern splitPattern;
    private int confirmThreshold;
    private boolean verbose;
    private Map<String, String> scriptIcons = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        getCommand("gbatch").setExecutor((sender, cmd, label, args) -> handle(sender, args));

        getServer().getPluginManager().registerEvents(new BatchListener(this), this);
        getServer().getPluginManager().registerEvents(new BatchGui(this), this);

        // Создать папку скриптов
        File dir = new File(getDataFolder(), "scripts");
        if (!dir.exists()) dir.mkdirs();

        getLogger().info("GovernixBatch v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        activeSessions.clear();
        pendingConfirm.clear();
        getLogger().info("GovernixBatch disabled!");
    }

    public void loadConfig() {
        reloadConfig();
        splitKeywords = getConfig().getStringList("split-keywords");
        separator = getConfig().getString("separator", ";;");
        confirmThreshold = getConfig().getInt("confirm-threshold", 5);
        verbose = getConfig().getBoolean("verbose", true);

        scriptIcons = new HashMap<>();
        if (getConfig().isConfigurationSection("script-icons")) {
            for (String key : getConfig().getConfigurationSection("script-icons").getKeys(false)) {
                scriptIcons.put(key.toLowerCase(), getConfig().getString("script-icons." + key));
            }
        }

        if (splitKeywords.isEmpty()) {
            splitKeywords = List.of("lp", "cmi", "litebans", "coreprotect");
        }

        StringBuilder sb = new StringBuilder("(?<=^|\\s)(?=(?:");
        for (int i = 0; i < splitKeywords.size(); i++) {
            if (i > 0) sb.append("|");
            sb.append(Pattern.quote(splitKeywords.get(i)));
        }
        sb.append(")\\s)");
        splitPattern = Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }

    public int reloadAll() {
        loadConfig();
        return splitKeywords.size();
    }

    // ===== API для listener =====

    public boolean isActive(UUID uuid) { return activeSessions.containsKey(uuid); }
    public void start(UUID uuid) { activeSessions.put(uuid, 0); }
    public void stop(UUID uuid) { activeSessions.remove(uuid); }
    public int increment(UUID uuid) { return activeSessions.merge(uuid, 1, Integer::sum); }
    public int getCount(UUID uuid) { return activeSessions.getOrDefault(uuid, 0); }

    public boolean isPendingConfirm(UUID uuid) { return pendingConfirm.contains(uuid); }
    public void addPendingConfirm(UUID uuid) { pendingConfirm.add(uuid); }
    public void removePendingConfirm(UUID uuid) { pendingConfirm.remove(uuid); }

    public int getConfirmThreshold() { return confirmThreshold; }
    public boolean isVerbose() { return verbose; }
    public Map<String, String> getScriptIcons() { return scriptIcons; }

    // ===== Разбивка команд =====

    public List<String> splitCommands(String input) {
        if (input == null || input.isBlank()) return List.of();

        List<String> result = new ArrayList<>();
        String normalized = input.replace("\r", " ").replace("\n", " ").trim();

        List<String> chunks;
        if (separator != null && !separator.isEmpty() && normalized.contains(separator)) {
            chunks = new ArrayList<>();
            for (String part : normalized.split(Pattern.quote(separator))) {
                chunks.add(part.trim());
            }
        } else {
            chunks = List.of(normalized);
        }

        for (String chunk : chunks) {
            if (chunk.isEmpty()) continue;
            String[] parts = splitPattern.split(chunk);
            for (String p : parts) {
                String cmd = p.trim();
                if (!cmd.isEmpty()) result.add(cmd);
            }
        }
        return result;
    }

    // ===== Команда =====

    private boolean handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("governixbatch.use")) {
            sender.sendMessage(L.deserialize(msg("no-perm")));
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                BatchGui.openMain(this, player);
            } else {
                sendConsoleHelp(sender);
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "gui", "menu" -> {
                if (sender instanceof Player player) BatchGui.openMain(this, player);
                else sendConsoleHelp(sender);
            }
            case "start" -> {
                if (!(sender instanceof Player player)) return true;
                if (isActive(player.getUniqueId())) {
                    player.sendMessage(L.deserialize(msg("prefix") + "&cРежим уже активен."));
                    return true;
                }
                start(player.getUniqueId());
                player.sendMessage(L.deserialize(msg("prefix") + "&a§l▸ &aРежим &fBATCH &aвключён."));
                player.sendMessage(L.deserialize("&7Пиши или вставляй команды в чат без &f/&7."));
                player.sendMessage(L.deserialize("&7Для выхода: &f/gbatch stop &7или слово &fend"));
            }
            case "stop", "end" -> {
                if (!(sender instanceof Player player)) return true;
                int done = getCount(player.getUniqueId());
                stop(player.getUniqueId());
                player.sendMessage(L.deserialize(msg("prefix") + "&c§l▸ &cРежим &fBATCH &cвыключен. &7Выполнено: &f" + done));
            }
            case "status" -> {
                if (!(sender instanceof Player player)) return true;
                boolean on = isActive(player.getUniqueId());
                player.sendMessage(L.deserialize(msg("prefix") + "&7Статус: "
                        + (on ? "&aактивен" : "&cвыключен")
                        + " &7| Выполнено: &f" + getCount(player.getUniqueId())));
            }
            case "reload" -> {
                int n = reloadAll();
                sender.sendMessage(L.deserialize(msg("prefix") + msg("reloaded").replace("{count}", String.valueOf(n))));
            }
            case "run" -> {
                if (args.length < 2) {
                    sender.sendMessage(L.deserialize("&cИспользование: &f/gbatch run <файл>"));
                    return true;
                }
                runScript(sender, args[1]);
            }
            default -> {
                if (sender instanceof Player player) BatchGui.openMain(this, player);
                else sendConsoleHelp(sender);
            }
        }
        return true;
    }

    private void sendConsoleHelp(CommandSender s) {
        s.sendMessage("§8§m-------------------------");
        s.sendMessage("§bGovernixBatch §7— утилита массовых команд");
        s.sendMessage("§e/gbatch gui §7— меню скриптов (для игроков)");
        s.sendMessage("§e/gbatch start §7— режим чата");
        s.sendMessage("§e/gbatch stop §7— выйти");
        s.sendMessage("§e/gbatch status §7— статистика");
        s.sendMessage("§e/gbatch run <файл> §7— выполнить скрипт");
        s.sendMessage("§e/gbatch reload §7— перечитать конфиг");
        s.sendMessage("§8§m-------------------------");
    }

    // ===== Запуск скриптов =====

    public List<File> listScripts() {
        File dir = new File(getDataFolder(), "scripts");
        if (!dir.exists()) dir.mkdirs();
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".txt"));
        if (files == null) return List.of();
        List<File> list = new ArrayList<>(Arrays.asList(files));
        list.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    public int countCommands(File file) {
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            int n = 0;
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                n++;
            }
            return n;
        } catch (IOException e) {
            return 0;
        }
    }

    public void runScript(CommandSender sender, String fileName) {
        File dir = new File(getDataFolder(), "scripts");
        if (!dir.exists()) dir.mkdirs();

        if (!fileName.endsWith(".txt")) fileName += ".txt";
        File file = new File(dir, fileName);

        if (!file.exists()) {
            sender.sendMessage(L.deserialize(msg("prefix") + msg("script-not-found").replace("{file}", file.getName())));
            return;
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            sender.sendMessage(L.deserialize(msg("prefix") + "&cОшибка чтения файла: &f" + e.getMessage()));
            return;
        }

        int executed = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("/")) line = line.substring(1);
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
                executed++;
                if (sender instanceof Player p && verbose) {
                    p.sendMessage(L.deserialize("§8§l▸ §7" + line));
                }
            } catch (Exception ex) {
                sender.sendMessage(L.deserialize("§c✖ §7" + line + " §8— " + ex.getMessage()));
            }
        }
        sender.sendMessage(L.deserialize(msg("prefix")
                + msg("script-ran")
                .replace("{count}", String.valueOf(executed))
                .replace("{file}", file.getName())));
    }

    // ===== Сообщения =====

    public String msg(String key) {
        return getConfig().getString("messages." + key, "&cMissing: " + key);
    }

    public String msgRaw(String key) {
        return getConfig().getString("messages." + key, "&cMissing: " + key);
    }

    public List<String> msgList(String key) {
        return getConfig().getStringList("messages." + key);
    }
}
