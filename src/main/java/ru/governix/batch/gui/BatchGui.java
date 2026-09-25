package ru.governix.batch.gui;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.governix.batch.GovernixBatch;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI со списком скриптов.
 */
public class BatchGui implements Listener {

    private static final LegacyComponentSerializer L = LegacyComponentSerializer.legacyAmpersand();
    private static final String TITLE_MAIN = "§8Скрипты GovernixBatch";
    private static final String TITLE_CONFIRM = "§8Подтверждение";

    private static final ConcurrentHashMap<UUID, String> pendingConfirm = new ConcurrentHashMap<>();

    private final GovernixBatch plugin;

    public BatchGui(GovernixBatch plugin) {
        this.plugin = plugin;
    }

    // ===================== OPEN MAIN =====================

    public static void openMain(GovernixBatch plugin, Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, L.deserialize(TITLE_MAIN));
        render(plugin, player, inv);
        player.openInventory(inv);
    }

    private static void render(GovernixBatch plugin, Player player, Inventory inv) {
        inv.clear();

        List<File> scripts = plugin.listScripts();

        // Info-блок
        inv.setItem(0, item(Material.BOOK,
                plugin.msgRaw("gui-info-name"),
                fillLore(plugin.msgList("gui-info-lore"),
                        "{scripts}", String.valueOf(scripts.size()),
                        "{status}", plugin.isActive(player.getUniqueId())
                                ? "§aактивен" : "§cвыключен",
                        "{executed}", String.valueOf(plugin.getCount(player.getUniqueId())))
                        .toArray(new String[0])));

        // Сессия
        boolean active = plugin.isActive(player.getUniqueId());
        if (active) {
            inv.setItem(4, item(Material.LIME_DYE,
                    plugin.msgRaw("gui-session-on-name"),
                    fillLore(plugin.msgList("gui-session-on-lore"),
                            "{executed}", String.valueOf(plugin.getCount(player.getUniqueId())))
                            .toArray(new String[0])));
        } else {
            inv.setItem(4, item(Material.GRAY_DYE,
                    plugin.msgRaw("gui-session-off-name"),
                    plugin.msgList("gui-session-off-lore").toArray(new String[0])));
        }

        // Обновить
        inv.setItem(8, item(Material.SUNFLOWER,
                plugin.msgRaw("gui-refresh-name"),
                plugin.msgList("gui-refresh-lore").toArray(new String[0])));

        // Скрипты
        if (scripts.isEmpty()) {
            inv.setItem(22, item(Material.BARRIER,
                    plugin.msgRaw("gui-empty-name"),
                    plugin.msgList("gui-empty-lore").toArray(new String[0])));
        } else {
            int[] slots = buildSlots();
            SimpleDateFormat df = new SimpleDateFormat("dd.MM HH:mm");

            for (int i = 0; i < scripts.size() && i < slots.length; i++) {
                File f = scripts.get(i);
                int lines = plugin.countCommands(f);
                String size = formatSize(f.length());
                String modified;
                try {
                    FileTime ft = Files.getLastModifiedTime(f.toPath());
                    modified = df.format(new Date(ft.toMillis()));
                } catch (IOException e) {
                    modified = "?";
                }

                String icon = pickIcon(plugin, f.getName());
                Material mat = Material.matchMaterial(icon);
                if (mat == null) mat = Material.PAPER;

                List<String> lore = fillLore(plugin.msgList("gui-script-lore"),
                        "{lines}", String.valueOf(lines),
                        "{size}", size,
                        "{modified}", modified);

                inv.setItem(slots[i], item(mat,
                        "§e" + f.getName().replace(".txt", ""),
                        lore.toArray(new String[0])));
            }
        }

        // Выход
        inv.setItem(49, item(Material.BARRIER,
                plugin.msgRaw("gui-close-name"),
                plugin.msgList("gui-close-lore").toArray(new String[0])));
    }

    private static int[] buildSlots() {
        List<Integer> list = new ArrayList<>();
        for (int row = 2; row <= 5; row++) {
            for (int col = 0; col < 7; col++) {
                list.add(row * 9 + col + 1);
            }
        }
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    // ===================== CONFIRM =====================

    private static void openConfirm(GovernixBatch plugin, Player player, File script) {
        int lines = plugin.countCommands(script);

        Inventory inv = Bukkit.createInventory(null, 27, L.deserialize(TITLE_CONFIRM));

        inv.setItem(11, item(Material.LIME_DYE,
                plugin.msgRaw("confirm-yes-name"),
                fillLore(plugin.msgList("confirm-yes-lore"),
                        "{file}", script.getName(),
                        "{lines}", String.valueOf(lines))
                        .toArray(new String[0])));

        inv.setItem(15, item(Material.RED_DYE,
                plugin.msgRaw("confirm-no-name"),
                plugin.msgList("confirm-no-lore").toArray(new String[0])));

        inv.setItem(13, item(Material.PAPER,
                "§7" + script.getName(),
                "§7Команд: §f" + lines));

        pendingConfirm.put(player.getUniqueId(), script.getName());
        plugin.addPendingConfirm(player.getUniqueId());
        player.openInventory(inv);
    }

    // ===================== CLICK =====================

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;

        String title = LegacyComponentSerializer.legacySection().serialize(event.getView().title());

        if (title.equals(TITLE_MAIN)) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            handleMainClick(player, event.getSlot(), event.isShiftClick(), event.isRightClick());
        } else if (title.equals(TITLE_CONFIRM)) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            handleConfirmClick(player, event.getSlot());
        }
    }

    private void handleMainClick(Player player, int slot, boolean shift, boolean right) {
        if (slot == 0) return;

        if (slot == 4) {
            if (plugin.isActive(player.getUniqueId())) {
                int done = plugin.getCount(player.getUniqueId());
                plugin.stop(player.getUniqueId());
                player.sendMessage(L.deserialize(plugin.msg("prefix")
                        + "§c§l▸ §cРежим §fBATCH §cвыключен. §7Выполнено: §f" + done));
            } else {
                plugin.start(player.getUniqueId());
                player.sendMessage(L.deserialize(plugin.msg("prefix")
                        + "§a§l▸ §aРежим §fBATCH §aвключён."));
            }
            openMain(plugin, player);
            return;
        }

        if (slot == 8) {
            openMain(plugin, player);
            return;
        }

        if (slot == 49) {
            player.closeInventory();
            return;
        }

        List<File> scripts = plugin.listScripts();
        int[] slots = buildSlots();
        int idx = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) { idx = i; break; }
        }
        if (idx < 0 || idx >= scripts.size()) return;

        File script = scripts.get(idx);

        // Shift+ЛКМ — удалить
        if (shift && !right) {
            if (!player.hasPermission("governixbatch.admin")) {
                player.sendMessage(L.deserialize(plugin.msg("prefix") + plugin.msg("no-perm")));
                return;
            }
            if (script.delete()) {
                player.sendMessage(L.deserialize(plugin.msg("prefix")
                        + plugin.msg("script-deleted").replace("{file}", script.getName())));
            }
            openMain(plugin, player);
            return;
        }

        // ПКМ — предпросмотр
        if (right && !shift) {
            player.closeInventory();
            preview(player, script);
            return;
        }

        // ЛКМ — запуск
        if (!shift) {
            int count = plugin.countCommands(script);
            if (count >= plugin.getConfirmThreshold()) {
                openConfirm(plugin, player, script);
            } else {
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.runScript(player, script.getName()));
            }
        }
    }

    private void handleConfirmClick(Player player, int slot) {
        String file = pendingConfirm.get(player.getUniqueId());
        pendingConfirm.remove(player.getUniqueId());
        plugin.removePendingConfirm(player.getUniqueId());
        player.closeInventory();

        if (slot == 11) {
            if (file == null) return;
            Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.runScript(player, file));
        } else if (slot == 15) {
            Bukkit.getScheduler().runTask(plugin, () -> openMain(plugin, player));
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = LegacyComponentSerializer.legacySection().serialize(event.getView().title());
        if (title.equals(TITLE_CONFIRM)) {
            pendingConfirm.remove(player.getUniqueId());
            plugin.removePendingConfirm(player.getUniqueId());
        }
    }

    // ===================== PREVIEW =====================

    private void preview(Player player, File file) {
        player.sendMessage(L.deserialize(plugin.msgRaw("preview-header")
                .replace("{file}", file.getName())));

        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            int shown = 0;
            int max = 15;
            for (String line : lines) {
                if (shown >= max) {
                    player.sendMessage(L.deserialize(plugin.msgRaw("preview-more")
                            .replace("{count}", String.valueOf(lines.size() - max))));
                    break;
                }
                if (line.length() > 80) line = line.substring(0, 77) + "...";
                player.sendMessage(L.deserialize("§7" + line));
                shown++;
            }
        } catch (IOException e) {
            player.sendMessage(L.deserialize("§cОшибка: " + e.getMessage()));
        }

        player.sendMessage(L.deserialize("§8§m-------------------------------"));
        Bukkit.getScheduler().runTaskLater(plugin, () -> openMain(plugin, player), 5L);
    }

    // ===================== HELPERS =====================

    private static ItemStack item(Material mat, String name, String... lore) {
        ItemStack is = new ItemStack(mat);
        ItemMeta meta = is.getItemMeta();
        if (meta == null) return is;
        meta.setDisplayName(name);
        if (lore.length > 0) {
            List<String> l = new ArrayList<>();
            for (String s : lore) l.add(s);
            meta.setLore(l);
        }
        is.setItemMeta(meta);
        return is;
    }

    private static List<String> fillLore(List<String> src, String... pairs) {
        List<String> out = new ArrayList<>();
        for (String s : src) {
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                s = s.replace(pairs[i], pairs[i + 1]);
            }
            out.add(s);
        }
        return out;
    }

    private static String pickIcon(GovernixBatch plugin, String fileName) {
        String lower = fileName.toLowerCase();
        for (var e : plugin.getScriptIcons().entrySet()) {
            if (lower.contains(e.getKey())) return e.getValue();
        }
        return "PAPER";
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
