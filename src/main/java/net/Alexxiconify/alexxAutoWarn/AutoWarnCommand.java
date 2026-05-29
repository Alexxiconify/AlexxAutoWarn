package net.alexxiconify.alexxautowarn;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class AutoWarnCommand implements CommandExecutor, TabCompleter {
    private static final Pattern ZONE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,32}$");

    private static final String ERR_NO_PERM = "error.no-permission";
    private static final String ERR_PLAYER_ONLY = "error.player-only";
    private static final String ERR_ZONE_NOT_FOUND = "error.zone-not-found";
    private static final String ERR_INVALID_MATERIAL = "error.invalid-material";
    private static final String ZONE_VAR = "zone";
    private static final String MATERIAL_VAR = "material";
    private static final String ACTION_VAR = "action";

    private final AlexxAutoWarn plugin;
    private final Settings settings;
    private final ZoneManager zoneManager;
    private final NamespacedKey wandKey;
    private final Map<UUID, Vector> pos1 = new ConcurrentHashMap<>();
    private final Map<UUID, Vector> pos2 = new ConcurrentHashMap<>();

    public AutoWarnCommand(AlexxAutoWarn plugin) {
        this.plugin = plugin;
        this.settings = plugin.getSettings();
        this.zoneManager = plugin.getZoneManager();
        this.wandKey = plugin.getWandKey();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!hasPermission(sender, sub)) {
            return true;
        }

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
        String perm = "autowarn." + (("pos1".equals(sub) || "pos2".equals(sub)) ? "pos" : sub);
        if (sender.hasPermission(perm)) {
            return true;
        }
        sender.sendMessage(settings.getMessage(ERR_NO_PERM));
        return false;
    }

    private void handleWand(CommandSender sender) {
        if (sender instanceof Player player) {
            giveSelectionWand(player);
            return;
        }
        sender.sendMessage(settings.getMessage(ERR_PLAYER_ONLY));
    }

    private void handlePos(CommandSender sender, String sub) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(settings.getMessage(ERR_PLAYER_ONLY));
            return;
        }

        Vector position = player.getLocation().toVector().toBlockVector();
        if ("pos1".equals(sub)) {
            pos1.put(player.getUniqueId(), position);
            player.sendMessage(settings.getMessage("command.pos-set", Placeholder.unparsed("pos", "1"), Placeholder.unparsed("coords", formatVector(position))));
        } else {
            pos2.put(player.getUniqueId(), position);
            player.sendMessage(settings.getMessage("command.pos-set", Placeholder.unparsed("pos", "2"), Placeholder.unparsed("coords", formatVector(position))));
        }
    }

    private void handleDefine(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(settings.getMessage(ERR_PLAYER_ONLY));
            return;
        }
        if (args.length != 2) {
            sender.sendMessage(settings.getMessage("error.usage.define"));
            return;
        }

        String zoneName = args[1].toLowerCase(Locale.ROOT);
        if (!ZONE_NAME_PATTERN.matcher(zoneName).matches()) {
            sender.sendMessage(settings.getMessage("error.invalid-zone-name"));
            return;
        }

        Vector first = pos1.get(player.getUniqueId());
        Vector second = pos2.get(player.getUniqueId());
        if (first == null || second == null) {
            sender.sendMessage(settings.getMessage("error.define-no-selection"));
            return;
        }

        zoneManager.addOrUpdateZone(new Zone(zoneName, player.getWorld().getName(), first, second, Zone.Action.ALERT, Collections.emptyMap(), 0));
        sender.sendMessage(settings.getMessage("command.define-success", Placeholder.unparsed(ZONE_VAR, zoneName)));
        pos1.remove(player.getUniqueId());
        pos2.remove(player.getUniqueId());
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(settings.getMessage("error.usage.remove"));
            return;
        }

        String zoneName = args[1].toLowerCase(Locale.ROOT);
        if (zoneManager.removeZone(zoneName)) {
            sender.sendMessage(settings.getMessage("command.remove-success", Placeholder.unparsed(ZONE_VAR, zoneName)));
        } else {
            sender.sendMessage(settings.getMessage(ERR_ZONE_NOT_FOUND, Placeholder.unparsed(ZONE_VAR, zoneName)));
        }
    }

    private void handleList(CommandSender sender) {
        Collection<Zone> zones = zoneManager.getAllZones();
        if (zones.isEmpty()) {
            sender.sendMessage(settings.getMessage("command.list-empty"));
            return;
        }

        sender.sendMessage(settings.getMessage("command.list-header", Placeholder.unparsed("count", String.valueOf(zones.size()))));
        zones.forEach(zone -> sender.sendMessage(Component.text(" - " + zone.getName()).color(NamedTextColor.GRAY)));
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(settings.getMessage("error.usage.info"));
            return;
        }

        Zone zone = zoneManager.getZone(args[1]);
        if (zone == null) {
            sender.sendMessage(settings.getMessage(ERR_ZONE_NOT_FOUND, Placeholder.unparsed(ZONE_VAR, args[1])));
            return;
        }

        sendZoneInfo(sender, zone);
    }

    private void handleDefaultAction(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(settings.getMessage("error.usage.defaultaction"));
            return;
        }

        Zone.Action action;
        try {
            action = Zone.Action.valueOf(args[2].trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(settings.getMessage("error.invalid-action"));
            return;
        }

        updateZone(sender, args[1], zone -> new Zone(zone.getName(), zone.getWorldName(), zone.getMin(), zone.getMax(), action, zone.getMaterialActions(), zone.getPriority()),
                "command.defaultaction-success", ACTION_VAR, action.name());
    }

    private void handleSetAction(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage(settings.getMessage("error.usage.setaction"));
            return;
        }

        Material material = Material.matchMaterial(args[2].trim());
        if (material == null) {
            sender.sendMessage(settings.getMessage("error.invalid-material-or-action"));
            return;
        }

        Zone.Action action;
        try {
            action = Zone.Action.valueOf(args[3].trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(settings.getMessage("error.invalid-material-or-action"));
            return;
        }

        updateZone(sender, args[1], zone -> {
            Map<Material, Zone.Action> actions = copyActions(zone);
            actions.put(material, action);
            return new Zone(zone.getName(), zone.getWorldName(), zone.getMin(), zone.getMax(), zone.getDefaultAction(), actions, zone.getPriority());
        }, "command.setaction-success", MATERIAL_VAR, material.name(), ACTION_VAR, action.name());
    }

    private void handleRemoveAction(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(settings.getMessage("error.usage.removeaction"));
            return;
        }

        Material material = Material.matchMaterial(args[2].trim());
        if (material == null) {
            sender.sendMessage(settings.getMessage(ERR_INVALID_MATERIAL));
            return;
        }

        updateZone(sender, args[1], zone -> {
            if (!zone.getMaterialActions().containsKey(material)) {
                sender.sendMessage(settings.getMessage("error.no-material-action"));
                return null;
            }
            Map<Material, Zone.Action> actions = copyActions(zone);
            actions.remove(material);
            return new Zone(zone.getName(), zone.getWorldName(), zone.getMin(), zone.getMax(), zone.getDefaultAction(), actions, zone.getPriority());
        }, "command.removeaction-success", MATERIAL_VAR, material.name());
    }

    private void handlePriority(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(settings.getMessage("error.usage.priority"));
            return;
        }

        int priority;
        try {
            priority = Integer.parseInt(args[2].trim());
        } catch (NumberFormatException ex) {
            sender.sendMessage(settings.getMessage("error.invalid-priority"));
            return;
        }

        updateZone(sender, args[1], zone -> new Zone(zone.getName(), zone.getWorldName(), zone.getMin(), zone.getMax(), zone.getDefaultAction(), zone.getMaterialActions(), priority),
                "command.priority-success", "priority", String.valueOf(priority));
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadPlugin();
        sender.sendMessage(settings.getMessage("command.reload-success"));
    }

    private void handleBanned(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(settings.getMessage("error.usage.banned"));
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length != 3) {
                    sender.sendMessage(settings.getMessage("error.usage.banned-add"));
                    return;
                }

                Material material = Material.matchMaterial(args[2].trim());
                if (material == null) {
                    sender.sendMessage(settings.getMessage(ERR_INVALID_MATERIAL));
                    return;
                }

                if (settings.getGloballyBannedMaterials().contains(material)) {
                    sender.sendMessage(settings.getMessage("error.material-already-banned"));
                    return;
                }

                settings.addGloballyBannedMaterial(material);
                sender.sendMessage(settings.getMessage("command.banned-add-success", Placeholder.unparsed(MATERIAL_VAR, material.name())));
            }
            case "remove" -> {
                if (args.length != 3) {
                    sender.sendMessage(settings.getMessage("error.usage.banned-remove"));
                    return;
                }

                Material material = Material.matchMaterial(args[2].trim());
                if (material == null) {
                    sender.sendMessage(settings.getMessage(ERR_INVALID_MATERIAL));
                    return;
                }

                if (!settings.getGloballyBannedMaterials().contains(material)) {
                    sender.sendMessage(settings.getMessage("error.material-not-banned"));
                    return;
                }

                settings.removeGloballyBannedMaterial(material);
                sender.sendMessage(settings.getMessage("command.banned-remove-success", Placeholder.unparsed(MATERIAL_VAR, material.name())));
            }
            case "list" -> {
                Set<Material> banned = settings.getGloballyBannedMaterials();
                if (banned.isEmpty()) {
                    sender.sendMessage(settings.getMessage("command.banned-list-empty"));
                    return;
                }

                sender.sendMessage(settings.getMessage("command.banned-list-header", Placeholder.unparsed("count", String.valueOf(banned.size()))));
                banned.stream().map(Material::name).sorted().forEach(name -> sender.sendMessage(Component.text(" - " + name).color(NamedTextColor.GRAY)));
            }
            default -> sender.sendMessage(settings.getMessage("error.usage.banned"));
        }
    }

    private void updateZone(CommandSender sender, String zoneName, ZoneUpdater updater, String successMsgKey, String... placeholders) {
        Zone zone = zoneManager.getZone(zoneName);
        if (zone == null) {
            sender.sendMessage(settings.getMessage(ERR_ZONE_NOT_FOUND, Placeholder.unparsed(ZONE_VAR, zoneName)));
            return;
        }

        Zone updated = updater.update(zone);
        if (updated == null) {
            return;
        }

        zoneManager.addOrUpdateZone(updated);
        List<TagResolver> resolved = new java.util.ArrayList<>();
        resolved.add(Placeholder.unparsed(ZONE_VAR, updated.getName()));
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            resolved.add(Placeholder.unparsed(placeholders[i], placeholders[i + 1]));
        }
        sender.sendMessage(settings.getMessage(successMsgKey, resolved.toArray(new TagResolver[0])));
    }

    private void giveSelectionWand(Player player) {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.displayName(settings.getMessage("wand.name"));
            meta.lore(List.of(settings.getMessage("wand.lore1"), settings.getMessage("wand.lore2")));
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.STRING, "autowarn_wand");
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
        if (actions.isEmpty()) {
            sender.sendMessage(Component.text("  No specific material actions defined.").color(NamedTextColor.GRAY));
            return;
        }

        sender.sendMessage(Component.text("  Material Actions:").color(NamedTextColor.GOLD));
        actions.entrySet().stream()
                .sorted((left, right) -> left.getKey().name().compareTo(right.getKey().name()))
                .forEach(entry -> sender.sendMessage(Component.text("    - " + entry.getKey().name() + ": " + entry.getValue().name()).color(NamedTextColor.GRAY)));
    }

    private String formatVector(Vector vector) {
        return String.format("%d, %d, %d", vector.getBlockX(), vector.getBlockY(), vector.getBlockZ());
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(settings.getMessage("command.help-header"));
        for (String key : List.of("wand", "pos", "define", "remove", "list", "info", "setaction", "removeaction", "defaultaction", "priority", "banned", "reload")) {
            sender.sendMessage(settings.getMessage("command.help." + key));
        }
    }

    public NamespacedKey getWandKey() {
        return wandKey;
    }

    public void setPos1(UUID uuid, Vector pos) {
        pos1.put(uuid, pos);
    }

    public void setPos2(UUID uuid, Vector pos) {
        pos2.put(uuid, pos);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("wand", "pos1", "pos2", "define", "remove", "list", "info", "defaultaction", "setaction", "removeaction", "priority", "banned", "reload"));
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "remove", "info", "defaultaction", "setaction", "removeaction", "priority" -> filter(args[1], zoneManager.getAllZones().stream().map(Zone::getName).toList());
                case "banned" -> filter(args[1], List.of("add", "remove", "list"));
                default -> Collections.emptyList();
            };
        }

        if (args.length == 3) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "defaultaction" -> filter(args[2], Arrays.stream(Zone.Action.values()).map(Enum::name).toList());
                case "setaction", "removeaction" -> filter(args[2], Arrays.stream(Material.values()).map(Enum::name).toList());
                case "banned" -> switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "add" -> filter(args[2], Arrays.stream(Material.values()).map(Enum::name).toList());
                    case "remove" -> filter(args[2], settings.getGloballyBannedMaterials().stream().map(Enum::name).toList());
                    default -> Collections.emptyList();
                };
                default -> Collections.emptyList();
            };
        }

        if (args.length == 4 && "setaction".equalsIgnoreCase(args[0])) {
            return filter(args[3], Arrays.stream(Zone.Action.values()).map(Enum::name).toList());
        }

        return Collections.emptyList();
    }

    private List<String> filter(String input, List<String> options) {
        String lower = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().collect(Collectors.toList());
    }

    private Map<Material, Zone.Action> copyActions(Zone zone) {
        Map<Material, Zone.Action> actions = new EnumMap<>(Material.class);
        actions.putAll(zone.getMaterialActions());
        return actions;
    }

    @FunctionalInterface
    private interface ZoneUpdater {
        Zone update(Zone zone);
    }
}