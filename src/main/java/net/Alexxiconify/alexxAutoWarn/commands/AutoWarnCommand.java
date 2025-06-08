package net.Alexxiconify.alexxAutoWarn.commands;

import com.google.common.collect.ImmutableList;
import net.alexxiconify.alexxAutoWarn.AlexxAutoWarn;
import net.alexxiconify.alexxAutoWarn.managers.ZoneManager;
import net.alexxiconify.alexxAutoWarn.objects.Zone;
import net.alexxiconify.alexxAutoWarn.utils.Settings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
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
import org.bukkit.util.StringUtil;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Handles all logic for the /autowarn command using Paper's command API.
 */
public class AutoWarnCommand implements CommandExecutor, TabCompleter {
 private static final Pattern ZONE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,32}$");
 private final Settings settings;
 private final ZoneManager zoneManager;
 private final NamespacedKey wandKey;
 private final Map<UUID, Vector> pos1Selections = new ConcurrentHashMap<>();
 private final Map<UUID, Vector> pos2Selections = new ConcurrentHashMap<>();
 private final AlexxAutoWarn plugin;


 public AutoWarnCommand(AlexxAutoWarn plugin) {
  this.plugin = plugin;
  this.settings = plugin.getSettings();
  this.zoneManager = plugin.getZoneManager();
  this.wandKey = new NamespacedKey(plugin, "autowarn_wand");
 }

 @Override
 public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
  if (!(sender instanceof Player player)) {
   sender.sendMessage(settings.getMessage("error.player-only"));
   return true;
  }

  if (args.length == 0) {
   sendHelp(player);
   return true;
  }

  String subCommand = args[0].toLowerCase();
  // Sub-command routing
  switch (subCommand) {
   case "wand" -> handleWand(player);
   case "pos1" -> handlePos(player, 1);
   case "pos2" -> handlePos(player, 2);
   case "define" -> handleDefine(player, args);
   case "remove" -> handleRemove(player, args);
   case "info" -> handleInfo(player, args);
   case "list" -> handleList(player);
   case "defaultaction" -> handleDefaultAction(player, args);
   case "setaction" -> handleSetAction(player, args);
   case "removeaction" -> handleRemoveAction(player, args);
   case "reload" -> handleReload(player);
   case "banned" -> handleBanned(player, args);
   default -> sendHelp(player);
  }

  return true;
 }

 // --- Command Handlers ---

 private void handleWand(Player player) {
  if (!player.hasPermission("autowarn.admin.wand")) {
   player.sendMessage(settings.getMessage("error.no-permission"));
   return;
  }
  ItemStack wand = new ItemStack(Material.BLAZE_ROD);
  ItemMeta meta = wand.getItemMeta();
  meta.displayName(settings.getMessage("wand.name"));
  meta.lore(Arrays.asList(
          settings.getMessage("wand.lore1"),
          settings.getMessage("wand.lore2")
  ));
  meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
  wand.setItemMeta(meta);
  player.getInventory().addItem(wand);
  player.sendMessage(settings.getMessage("command.wand-given"));
 }

 private void handlePos(Player player, int posNumber) {
  if (!player.hasPermission("autowarn.admin.define")) {
   player.sendMessage(settings.getMessage("error.no-permission"));
   return;
  }
  Location loc = player.getLocation();
  if (posNumber == 1) {
   pos1Selections.put(player.getUniqueId(), loc.toVector());
  } else {
   pos2Selections.put(player.getUniqueId(), loc.toVector());
  }
  player.sendActionBar(settings.getMessage("command.pos-set",
          Placeholder.unparsed("pos", String.valueOf(posNumber)),
          Placeholder.unparsed("coords", formatVector(loc.toVector()))));
 }

 private void handleDefine(Player player, String[] args) {
  if (!player.hasPermission("autowarn.admin.define")) {
   player.sendMessage(settings.getMessage("error.no-permission"));
   return;
  }
  if (args.length < 2) {
   player.sendMessage(settings.getMessage("error.usage.define"));
   return;
  }
  String zoneName = args[1];
  if (!ZONE_NAME_PATTERN.matcher(zoneName).matches()) {
   player.sendMessage(settings.getMessage("error.invalid-zone-name"));
   return;
  }
  Vector pos1 = pos1Selections.get(player.getUniqueId());
  Vector pos2 = pos2Selections.get(player.getUniqueId());

  if (pos1 == null || pos2 == null) {
   player.sendMessage(settings.getMessage("error.define-no-selection"));
   return;
  }

  Zone existing = zoneManager.getZone(zoneName);
  Zone.Action defaultAction = existing != null ? existing.getDefaultAction() : Zone.Action.ALERT;
  Map<Material, Zone.Action> materialActions = existing != null ? existing.getMaterialActions() : new EnumMap<>(Material.class);

  Zone newZone = new Zone(zoneName, player.getWorld(), pos1, pos2, defaultAction, materialActions);
  zoneManager.addOrUpdateZone(newZone);
  player.sendMessage(settings.getMessage("command.define-success", Placeholder.unparsed("zone", zoneName)));
 }

 private void handleRemove(Player player, String[] args) {
  if (!player.hasPermission("autowarn.admin.remove")) {
   player.sendMessage(settings.getMessage("error.no-permission"));
   return;
  }
  if (args.length < 2) {
   player.sendMessage(settings.getMessage("error.usage.remove"));
   return;
  }
  String zoneName = args[1];
  if (zoneManager.removeZone(zoneName)) {
   player.sendMessage(settings.getMessage("command.remove-success", Placeholder.unparsed("zone", zoneName)));
  } else {
   player.sendMessage(settings.getMessage("error.zone-not-found", Placeholder.unparsed("zone", zoneName)));
  }
 }

 private void handleList(Player player) {
  if (!player.hasPermission("autowarn.admin.list")) {
   player.sendMessage(settings.getMessage("error.no-permission"));
   return;
  }
  Collection<Zone> zones = zoneManager.getAllZones();
  if (zones.isEmpty()) {
   player.sendMessage(settings.getMessage("command.list-empty"));
   return;
  }

  player.sendMessage(settings.getMessage("command.list-header", Placeholder.unparsed("count", String.valueOf(zones.size()))));
  for (Zone zone : zones) {
   player.sendMessage(Component.text("- " + zone.getName()).color(NamedTextColor.YELLOW));
  }
 }

 private void handleReload(Player player) {
  if (!player.hasPermission("autowarn.admin.reload")) {
   player.sendMessage(settings.getMessage("error.no-permission"));
   return;
  }
  plugin.reload();
  player.sendMessage(settings.getMessage("command.reload-success"));
 }

 // --- Utility Methods ---
 private String formatVector(Vector vec) {
  return String.format("%d, %d, %d", vec.getBlockX(), vec.getBlockY(), vec.getBlockZ());
 }

 private void sendHelp(Player player) {
  player.sendMessage(settings.getMessage("command.help-header"));
  player.sendMessage(settings.getMessage("command.help.wand"));
  player.sendMessage(settings.getMessage("command.help.pos"));
  player.sendMessage(settings.getMessage("command.help.define"));
  player.sendMessage(settings.getMessage("command.help.remove"));
  player.sendMessage(settings.getMessage("command.help.list"));
  player.sendMessage(settings.getMessage("command.help.info"));
  player.sendMessage(settings.getMessage("command.help.setaction"));
  player.sendMessage(settings.getMessage("command.help.removeaction"));
  player.sendMessage(settings.getMessage("command.help.defaultaction"));
  player.sendMessage(settings.getMessage("command.help.banned"));
  player.sendMessage(settings.getMessage("command.help.reload"));
 }

 // --- Getters for listener ---
 public NamespacedKey getWandKey() {
  return wandKey;
 }

 public void setPos1(UUID uuid, Vector pos) {
  pos1Selections.put(uuid, pos);
 }

 public void setPos2(UUID uuid, Vector pos) {
  pos2Selections.put(uuid, pos);
 }

 // --- More command handlers needed for full functionality ---
 private void handleInfo(Player player, String[] args) { /* ... */ }
 private void handleDefaultAction(Player player, String[] args) { /* ... */ }
 private void handleSetAction(Player player, String[] args) { /* ... */ }
 private void handleRemoveAction(Player player, String[] args) { /* ... */ }
 private void handleBanned(Player player, String[] args) { /* ... */ }

 @Nullable
 @Override
 public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
  final List<String> completions = new ArrayList<>();
  if (args.length == 1) {
   StringUtil.copyPartialMatches(args[0],
           ImmutableList.of("wand", "pos1", "pos2", "define", "remove", "info", "list", "defaultaction", "setaction", "removeaction", "reload", "banned"),
           completions);
  } else if (args.length == 2) {
   switch (args[0].toLowerCase()) {
    case "define", "remove", "info", "defaultaction", "setaction", "removeaction" ->
            StringUtil.copyPartialMatches(args[1], zoneManager.getAllZones().stream().map(Zone::getName).collect(Collectors.toList()), completions);
    case "banned" -> StringUtil.copyPartialMatches(args[1], ImmutableList.of("add", "remove", "list"), completions);
   }
  } else if (args.length == 3) {
   switch (args[0].toLowerCase()) {
    case "defaultaction" ->
            StringUtil.copyPartialMatches(args[2], Stream.of(Zone.Action.values()).map(Enum::name).collect(Collectors.toList()), completions);
    case "setaction", "removeaction" ->
            StringUtil.copyPartialMatches(args[2], Arrays.stream(Material.values()).filter(m -> m.isBlock()).map(Enum::name).collect(Collectors.toList()), completions);
    case "banned" -> {
     if ("add".equalsIgnoreCase(args[1])) {
      StringUtil.copyPartialMatches(args[2], Arrays.stream(Material.values()).filter(m -> m.isItem()).map(Enum::name).collect(Collectors.toList()), completions);
     } else if ("remove".equalsIgnoreCase(args[1])) {
      StringUtil.copyPartialMatches(args[2], settings.getGloballyBannedMaterials().stream().map(Enum::name).collect(Collectors.toList()), completions);
     }
    }
   }
  } else if (args.length == 4) {
   if ("setaction".equalsIgnoreCase(args[0])) {
    StringUtil.copyPartialMatches(args[3], Stream.of(Zone.Action.values()).map(Enum::name).collect(Collectors.toList()), completions);
   }
  }

  Collections.sort(completions);
  return completions;
 }
}