package net.alexxiconify.alexxautowarn;

import com.google.common.collect.ImmutableList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AutoWarnCommand implements CommandExecutor, TabCompleter {
    private static final Pattern ZONE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,32}$");
    private static final NamespacedKey WAND_KEY = NamespacedKey.fromString("alexxautowarn:selection_wand");
    
    private static final String ERR_NO_PERM = "error.no-permission";
    private static final String ERR_PLAYER_ONLY = "error.player-only";
    private static final String ERR_ZONE_NOT_FOUND = "error.zone-not-found";
    private static final String ERR_INVALID_MATERIAL = "error.invalid-material";
    private static final String ZONE_VAR = "zone";
    private static final String MATERIAL_VAR = "material";
    private static final String ACTION_VAR = "action";

    private final AlexxAutoWarn.Settings settings;
    private final ZoneManager zoneManager;
    private final AlexxAutoWarn plugin;
    private final Map<UUID, Vector> pos1 = new ConcurrentHashMap<>();
    private final Map<UUID, Vector> pos2 = new ConcurrentHashMap<>();

    public AutoWarnCommand(AlexxAutoWarn plugin) {
        this.plugin = plugin;
        this.settings = plugin.getSettings();
        this.zoneManager = plugin.getZoneManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        if (!hasPermission(sender, sub)) return true;

        switch (sub) {
            case "wand" -> handleWand(sender);
            case "pos1", "pos2" -> handlePos(sender, sub);
            case "define" -> handleDefine(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "defaultaction" -> handleDefaultAction(sender, args);
            case "setaction" -> handleSetAction(sender, args);
            case "removeaction" -> handleRemoveAction(sender, args);
            case "priority" -> handlePriority(sender, args);
            case "banned" -> handleBanned(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private boolean hasPermission(CommandSender sender, String sub) {
        String perm = "autowarn." + (sub.equals("pos1") || sub.equals("pos2") ? "pos" : sub);
        if (!sender.hasPermission(perm)) {
            sender.sendMessage(settings.getMessage(ERR_NO_PERM));
            return false;
        }
        return true;
    }

    private void handleWand(CommandSender sender) {
        if (sender instanceof Player p) {
            giveSelectionWand(p);
        } else {
            sender.sendMessage(settings.getMessage(ERR_PLAYER_ONLY));
        }
    }

    private void handlePos(CommandSender sender, String sub) {
        if (sender instanceof Player p) {
            Vector loc = p.getLocation().toVector().toBlockVector();
            if (sub.equals("pos1")) {
                pos1.put(p.getUniqueId(), loc);
                p.sendMessage(settings.getMessage("command.pos-set", Placeholder.unparsed("pos", "1"), Placeholder.unparsed("coords", formatVector(loc))));
            } else {
                pos2.put(p.getUniqueId(), loc);
                p.sendMessage(settings.getMessage("command.pos-set", Placeholder.unparsed("pos", "2"), Placeholder.unparsed("coords", formatVector(loc))));
            }
        } else {
            sender.sendMessage(settings.getMessage(ERR_PLAYER_ONLY));
        }
    }

    private void handleDefine(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(settings.getMessage(ERR_PLAYER_ONLY));
            return;
        }
        if (args.length != 2) {
            p.sendMessage(settings.getMessage("error.usage.define"));
            return;
        }
        String name = args[1].toLowerCase();
        if (!ZONE_NAME_PATTERN.matcher(name).matches()) {
            p.sendMessage(settings.getMessage("error.invalid-zone-name"));
            return;
        }
        Vector p1 = pos1.get(p.getUniqueId());
        Vector p2 = pos2.get(p.getUniqueId());
        if (p1 == null || p2 == null) {
            p.sendMessage(settings.getMessage("error.define-no-selection"));
            return;
        }
        zoneManager.addOrUpdateZone(new Zone(name, p.getWorld(), p1, p2, Zone.Action.ALERT, new EnumMap<>(Material.class), 0));
        p.sendMessage(settings.getMessage("command.define-success", Placeholder.unparsed(ZONE_VAR, name)));
        pos1.remove(p.getUniqueId());
        pos2.remove(p.getUniqueId());
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(settings.getMessage("error.usage.remove"));
            return;
        }
        String name = args[1].toLowerCase();
        if (zoneManager.removeZone(name)) {
            sender.sendMessage(settings.getMessage("command.remove-success", Placeholder.unparsed(ZONE_VAR, name)));
        } else {
            sender.sendMessage(settings.getMessage(ERR_ZONE_NOT_FOUND, Placeholder.unparsed(ZONE_VAR, name)));
        }
    }

    private void handleList(CommandSender sender) {
        Collection<Zone> zones = zoneManager.getAllZones();
        if (zones.isEmpty()) {
            sender.sendMessage(settings.getMessage("command.list-empty"));
        } else {
            sender.sendMessage(settings.getMessage("command.list-header", Placeholder.unparsed("count", String.valueOf(zones.size()))));
            zones.forEach(z -> sender.sendMessage(Component.text(" - " + z.getName()).color(NamedTextColor.GRAY)));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(settings.getMessage("error.usage.info"));
            return;
        }
        Zone z = zoneManager.getZone(args[1]);
        if (z == null) {
            sender.sendMessage(settings.getMessage(ERR_ZONE_NOT_FOUND, Placeholder.unparsed(ZONE_VAR, args[1])));
        } else {
            sendZoneInfo(sender, z);
        }
    }

    private void handleDefaultAction(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(settings.getMessage("error.usage.defaultaction"));
            return;
        }
        updateZone(sender, args[1], z -> {
            try {
                return new Zone(z.getName(), plugin.getServer().getWorld(z.getWorldName()), z.getMin(), z.getMax(),
                        Zone.Action.valueOf(args[2].trim().toUpperCase()), z.getMaterialActions(), z.getPriority());
            } catch (IllegalArgumentException e) {
                sender.sendMessage(settings.getMessage("error.invalid-action"));
                return null;
            }
        }, "command.defaultaction-success", ACTION_VAR, args[2].toUpperCase());
    }

    private void handleSetAction(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage(settings.getMessage("error.usage.setaction"));
            return;
        }
        updateZone(sender, args[1], z -> {
            try {
                Material mat = Material.valueOf(args[2].toUpperCase());
                if (!mat.isBlock()) throw new IllegalArgumentException();
                Zone.Action act = Zone.Action.valueOf(args[3].trim().toUpperCase());
                Map<Material, Zone.Action> actions = new EnumMap<>(z.getMaterialActions());
                actions.put(mat, act);
                return new Zone(z.getName(), plugin.getServer().getWorld(z.getWorldName()), z.getMin(), z.getMax(),
                        z.getDefaultAction(), actions, z.getPriority());
            } catch (IllegalArgumentException e) {
                sender.sendMessage(settings.getMessage("error.invalid-material-or-action"));
                return null;
            }
        }, "command.setaction-success", MATERIAL_VAR, args[2].toUpperCase(), ACTION_VAR, args[3].toUpperCase());
    }

    private void handleRemoveAction(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(settings.getMessage("error.usage.removeaction"));
            return;
        }
        updateZone(sender, args[1], z -> {
            try {
                Material mat = Material.valueOf(args[2].toUpperCase());
                if (!z.getMaterialActions().containsKey(mat)) {
                    sender.sendMessage(settings.getMessage("error.no-material-action"));
                    return null;
                }
                Map<Material, Zone.Action> actions = new EnumMap<>(z.getMaterialActions());
                actions.remove(mat);
                return new Zone(z.getName(), plugin.getServer().getWorld(z.getWorldName()), z.getMin(), z.getMax(),
                        z.getDefaultAction(), actions, z.getPriority());
            } catch (IllegalArgumentException e) {
                sender.sendMessage(settings.getMessage(ERR_INVALID_MATERIAL));
                return null;
            }
        }, "command.removeaction-success", MATERIAL_VAR, args[2].toUpperCase());
    }

    private void handlePriority(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(settings.getMessage("error.usage.priority"));
            return;
        }
        updateZone(sender, args[1], z -> {
            try {
                int prio = Integer.parseInt(args[2]);
                return new Zone(z.getName(), plugin.getServer().getWorld(z.getWorldName()), z.getMin(), z.getMax(),
                        z.getDefaultAction(), z.getMaterialActions(), prio);
            } catch (NumberFormatException e) {
                sender.sendMessage(settings.getMessage("error.invalid-priority"));
                return null;
            }
        }, "command.priority-success", "priority", args[2]);
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage(settings.getMessage("command.reload-success"));
    }

    private void handleBanned(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(settings.getMessage("error.usage.banned"));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "add" -> {
                if (args.length != 3) { sender.sendMessage(settings.getMessage("error.usage.banned-add")); return; }
                try {
                    Material m = Material.valueOf(args[2].toUpperCase());
                    if (settings.getGloballyBannedMaterials().contains(m)) {
                        sender.sendMessage(settings.getMessage("error.material-already-banned"));
                    } else {
                        settings.addGloballyBannedMaterial(m);
                        sender.sendMessage(settings.getMessage("command.banned-add-success", Placeholder.unparsed(MATERIAL_VAR, m.name())));
                    }
                } catch (IllegalArgumentException e) { sender.sendMessage(settings.getMessage(ERR_INVALID_MATERIAL)); }
            }
            case "remove" -> {
                if (args.length != 3) { sender.sendMessage(settings.getMessage("error.usage.banned-remove")); return; }
                try {
                    Material m = Material.valueOf(args[2].toUpperCase());
                    if (!settings.getGloballyBannedMaterials().contains(m)) {
                        sender.sendMessage(settings.getMessage("error.material-not-banned"));
                    } else {
                        settings.removeGloballyBannedMaterial(m);
                        sender.sendMessage(settings.getMessage("command.banned-remove-success", Placeholder.unparsed(MATERIAL_VAR, m.name())));
                    }
                } catch (IllegalArgumentException e) { sender.sendMessage(settings.getMessage(ERR_INVALID_MATERIAL)); }
            }
            case "list" -> {
                Set<Material> banned = settings.getGloballyBannedMaterials();
                if (banned.isEmpty()) {
                    sender.sendMessage(settings.getMessage("command.banned-list-empty"));
                } else {
                    sender.sendMessage(settings.getMessage("command.banned-list-header", Placeholder.unparsed("count", String.valueOf(banned.size()))));
                    banned.forEach(m -> sender.sendMessage(Component.text(" - " + m.name()).color(NamedTextColor.GRAY)));
                }
            }
            default -> sender.sendMessage(settings.getMessage("error.usage.banned"));
        }
    }

    private interface ZoneUpdater { Zone update(Zone z); }

    private void updateZone(CommandSender sender, String zoneName, ZoneUpdater updater, String successMsgKey, String... placeholders) {
        Zone z = zoneManager.getZone(zoneName);
        if (z == null) {
            sender.sendMessage(settings.getMessage(ERR_ZONE_NOT_FOUND, Placeholder.unparsed(ZONE_VAR, zoneName)));
            return;
        }
        if (plugin.getServer().getWorld(z.getWorldName()) == null) {
            sender.sendMessage(Component.text("Error: World not found.").color(NamedTextColor.RED));
            return;
        }
        Zone newZone = updater.update(z);
        if (newZone != null) {
            zoneManager.addOrUpdateZone(newZone);
            List<Placeholder> pl = new ArrayList<>();
            pl.add(Placeholder.unparsed(ZONE_VAR, zoneName));
            for (int i = 0; i < placeholders.length; i += 2) {
                pl.add(Placeholder.unparsed(placeholders[i], placeholders[i + 1]));
            }
            sender.sendMessage(settings.getMessage(successMsgKey, pl.toArray(new Placeholder[0])));
        }
    }

    private void giveSelectionWand(Player player) {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.displayName(settings.getMessage("wand.name"));
            meta.lore(Arrays.asList(settings.getMessage("wand.lore1"), settings.getMessage("wand.lore2")));
            meta.getPersistentDataContainer().set(WAND_KEY, PersistentDataType.STRING, "autowarn_wand");
            wand.setItemMeta(meta);
        }
        player.getInventory().addItem(wand);
        player.sendMessage(settings.getMessage("command.wand-given"));
    }

    private void sendZoneInfo(CommandSender sender, Zone zone) {
        sender.sendMessage(settings.getMessage("command.info-header", Placeholder.unparsed(ZONE_VAR, zone.getName())));
        sender.sendMessage(Component.text("  World: ").append(Component.text(zone.getWorldName()).color(NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  Min: ").append(Component.text(formatVector(zone.getMin())).color(NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  Max: ").append(Component.text(formatVector(zone.getMax())).color(NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  Default Action: ").append(Component.text(zone.getDefaultAction().name()).color(NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  Priority: ").append(Component.text(String.valueOf(zone.getPriority())).color(NamedTextColor.GRAY)));

        Map<Material, Zone.Action> actions = zone.getMaterialActions();
        if (!actions.isEmpty()) {
            sender.sendMessage(Component.text("  Material Actions:").color(NamedTextColor.GOLD));
            actions.forEach((m, a) -> sender.sendMessage(Component.text("    - " + m.name() + ": " + a.name()).color(NamedTextColor.GRAY)));
        } else {
            sender.sendMessage(Component.text("  No specific material actions defined.").color(NamedTextColor.GRAY));
        }
    }

    private String formatVector(Vector vec) {
        return String.format("%d, %d, %d", vec.getBlockX(), vec.getBlockY(), vec.getBlockZ());
    }

    private void sendHelp(CommandSender sender) {
        String[] keys = {"wand", "pos", "define", "remove", "list", "info", "setaction", "removeaction", "defaultaction", "priority", "banned", "reload"};
        sender.sendMessage(settings.getMessage("command.help-header"));
        for (String key : keys) sender.sendMessage(settings.getMessage("command.help." + key));
    }

    public NamespacedKey getWandKey() { return WAND_KEY; }
    public void setPos1(UUID uuid, Vector pos) { pos1.put(uuid, pos); }
    public void setPos2(UUID uuid, Vector pos) { pos2.put(uuid, pos); }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(args[0], ImmutableList.of("wand", "pos1", "pos2", "define", "remove", "list", "info", "defaultaction", "setaction", "removeaction", "priority", "banned", "reload"));
        } else if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "remove", "info", "defaultaction", "setaction", "removeaction", "priority" -> 
                    filter(args[1], zoneManager.getAllZones().stream().map(Zone::getName).toList());
                case "banned" -> filter(args[1], ImmutableList.of("add", "remove", "list"));
                default -> Collections.emptyList();
            };
        } else if (args.length == 3) {
            return switch (args[0].toLowerCase()) {
                case "defaultaction" -> filter(args[2], Stream.of(Zone.Action.values()).map(Enum::name).toList());
                case "setaction", "removeaction" -> filter(args[2], Arrays.stream(Material.values()).filter(Material::isBlock).map(Enum::name).toList());
                case "banned" -> {
                    if ("add".equalsIgnoreCase(args[1])) yield filter(args[2], Arrays.stream(Material.values()).filter(Material::isItem).map(Enum::name).toList());
                    if ("remove".equalsIgnoreCase(args[1])) yield filter(args[2], settings.getGloballyBannedMaterials().stream().map(Enum::name).toList());
                    yield Collections.emptyList();
                }
                default -> Collections.emptyList();
            };
        } else if (args.length == 4 && "setaction".equalsIgnoreCase(args[0])) {
            return filter(args[3], Stream.of(Zone.Action.values()).map(Enum::name).toList());
        }
        return Collections.emptyList();
    }

    private List<String> filter(String input, List<String> options) {
        String lower = input.toLowerCase();
        return options.stream().filter(s -> s.toLowerCase().startsWith(lower)).sorted().collect(Collectors.toList());
    }
}