package net.Alexxiconify.alexxAutoWarn.commands;

import net.Alexxiconify.alexxAutoWarn.AlexxAutoWarn;
import net.Alexxiconify.alexxAutoWarn.managers.ZoneManager;
import net.Alexxiconify.alexxAutoWarn.objects.AutoInformZone;
import net.Alexxiconify.alexxAutoWarn.objects.ZoneAction;
import net.Alexxiconify.alexxAutoWarn.utils.LocationUtil;
import net.Alexxiconify.alexxAutoWarn.utils.MessageUtil;
import net.Alexxiconify.alexxAutoWarn.utils.StringUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.tools.jconsole.inspector.Utils;

import java.util.*;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Handles all commands for the AlexxAutoWarn plugin.
 * Implements CommandExecutor for command execution and TabCompleter for tab completion.
 */
public class AutoInformCommandExecutor implements CommandExecutor, TabCompleter {

 private final AlexxAutoWarn plugin;
 private final ZoneManager zoneManager;
 private final MessageUtil messageUtil;
 // Pattern to validate zone names: Alphanumeric, hyphens, and underscores allowed
 private static final Pattern ZONE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
 private final NamespacedKey wandKey; // Added for wand check
 private Object playerSelections; // Changed to Object to resolve the identifier issue
 private Location pos1;
 private Location pos2;
 private Player player;
 private String[] args;

 public AutoInformCommandExecutor(AlexxAutoWarn plugin) {
  this.plugin = plugin;
  this.zoneManager = plugin.getZoneManager();
  this.messageUtil = plugin.getMessageUtil();
  this.wandKey = new NamespacedKey(plugin, "autoinform_wand");
 }

 @Override
 public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
  Utils playerSelections;
  if (!(sender instanceof Player player)) {
   messageUtil.sendMessage(playerSelections.getClass(), "command-player-only");
   return true;
  }

  if (!player.hasPermission("alexxautowarn.admin.set")) {
   messageUtil.sendMessage(playerSelections.getClass(), "error-no-permission");
   return true;
  }

  if (args.length < 1) {
   sendHelpMessage(player);
   return true;
  }

  String subCommand = args[0].toLowerCase();

  switch (subCommand) {
   case "wand":
    handleWandCommand(player);
    break;
   case "pos1":
   case "pos2":
    handleSetPositionCommand(player, args);
    break;
   case "define":
    handleDefineCommand(player, args);
    break;
   case "defaultaction":
    handleDefaultActionCommand(player, args);
    break;
   case "setaction":
    handleSetActionCommand(player, args);
    break;
   case "remove":
    handleRemoveCommand(player, args);
    break;
   case "info":
    handleInfoCommand(player, args);
    break;
   case "list":
    handleListCommand(player);
    break;
   case "clearwand":
    handleClearWandCommand(player);
    break;
   case "reload":
    handleReloadCommand(player);
    break;
   case "banned":
    handleBannedMaterialsCommand(player, args);
    break;
   case "debug": // Removed from main command, handled by MessageUtil internally
    messageUtil.sendMessage(playerSelections.getClass(), "command-debug-removed");
    break;
   default:
    sendHelpMessage(player);
    break;
  }
  return true;
 }

 private void sendHelpMessage(Player player) {
  messageUtil.sendMessage(playerSelections.getClass(), "main-help-header");
  messageUtil.sendMessage(playerSelections.getClass(), "main-help-wand", "{command}", "autoinform");
  messageUtil.sendMessage(playerSelections.getClass(), "main-help-pos1", "{command}", "autoinform");
  messageUtil.sendMessage(playerSelections.getClass(), "main-help-pos2", "{command}", "autoinform");
  messageUtil.sendMessage(player, "main-help-define", "{command}", "autoinform");
  messageUtil.sendMessage(player, "main-help-defaultaction", "{command}", "autoinform");
  messageUtil.sendMessage(playerSelections.getClass(), "main-help-setaction", "{command}", "autoinform");
  messageUtil.sendMessage(player, "main-help-remove", "{command}", "autoinform");
  messageUtil.sendMessage(player, "main-help-info", "{command}", "autoinform");
  messageUtil.sendMessage(playerSelections.getClass(), "main-help-list", "{command}", "autoinform");
  messageUtil.sendMessage(playerSelections.getClass(), "main-help-clearwand", "{command}", "autoinform");
  messageUtil.sendMessage(playerSelections.getClass(), "main-help-reload", "{command}", "autoinform");
  messageUtil.sendMessage(playerSelections.getClass(), "main-help-banned", "{command}", "autoinform");
 }

 private void handleWandCommand(Player player) {
  plugin.giveWand(player);
  messageUtil.sendMessage(player, "wand-given");
  messageUtil.log(Level.INFO, "command-wand-given-console", "{player}", player.getName());
 }

 private void handleSetPositionCommand(Player player, String[] args) {
  this.player = player;
  this.args = args;
  if (args.length < 2) {
   messageUtil.sendMessage(player, "command-pos-usage");
   return;
  }
  String zoneName = args[1];
  if (!ZONE_NAME_PATTERN.matcher(zoneName).matches()) {
   messageUtil.sendMessage(player, "error-invalid-zone-name");
   return;
  }
  if (args.length < 5) {
   messageUtil.sendMessage(playerSelections.getClass(), "command-pos-coords-missing");
   return;
  }

  try {
   double x = Double.parseDouble(args[2]);
   double y = Double.parseDouble(args[3]);
   double z = Double.parseDouble(args[4]);
   Location location = new Location(player.getWorld(), x, y, z);
   String posType = args[0].toLowerCase(); // "pos1" or "pos2"

   zoneManager.getPlayerSelections().setSelection(player, posType, location);
   messageUtil.sendMessage(playerSelections.getClass(), "command-pos-set",
           "{position}", posType,
           "{location}", LocationUtil.toBlockString(location));

   messageUtil.log(Level.FINE, "debug-pos-saved",
           "{player}", player.getName(),
           "{position}", posType,
           "{zone_name}", zoneName, // Pass zoneName for debug context
           "{location_string}", LocationUtil.toBlockString(location));

  } catch (NumberFormatException e) {
   messageUtil.sendMessage(player, "error-invalid-coordinates");
  }
 }


 private void handleDefineCommand(Player player, String[] args) {
  this.player = player;
  this.args = args;
  if (args.length < 2) {
   messageUtil.sendMessage(playerSelections.getClass(), "command-define-usage");
   return;
  }

  String zoneName = args[1];
  if (!ZONE_NAME_PATTERN.matcher(zoneName).matches()) {
   messageUtil.sendMessage(player, "error-invalid-zone-name");
   return;
  }

  // Get positions from player's selections
  Location pos1 = zoneManager.getPlayerSelections().getSelection(player, "pos1");
  Location pos2 = zoneManager.getPlayerSelections().getSelection(player, "pos2");

  messageUtil.log(Level.FINE, "debug-define-start", "{player}", player.getName(), "{zone_name}", zoneName,
          "{raw_json}", "{ \"pos1\": \"" + (pos1 != null ? LocationUtil.toBlockString(pos1) : "null") + "\", \"pos2\": \"" + (pos2 != null ? LocationUtil.toBlockString(pos2) : "null") + "\" }");


  if (pos1 == null || pos2 == null) {
   messageUtil.sendMessage(playerSelections.getClass(), "command-define-selections-missing");
   messageUtil.log(Level.FINE, "debug-define-selections", "{zone_name}", zoneName,
           "{pos1_status}", (pos1 == null ? "NOT_SET" : "SET"),
           "{pos2_status}", (pos2 == null ? "NOT_SET" : "SET"));
   return;
  }

  if (!pos1.getWorld().equals(pos2.getWorld())) {
   messageUtil.sendMessage(playerSelections.getClass(), "error-worlds-not-match");
   return;
  }

  ZoneAction defaultAction = ZoneAction.ALLOW; // Default to ALLOW if not specified
  if (args.length >= 3) {
   try {
    defaultAction = ZoneAction.valueOf(args[2].toUpperCase());
   } catch (IllegalArgumentException e) {
    messageUtil.sendMessage(player, "error-invalid-default-action", "{action}", args[2]);
    return;
   }
  }

  AutoInformZone existingZone = zoneManager.getZone(zoneName);
  if (existingZone != null) {
   // Update existing zone
   existingZone.getCorner1(pos1);
   existingZone.getCorner2(pos2);
   existingZone.setDefaultAction(defaultAction);
   zoneManager.saveZones(); // Save changes to config
   messageUtil.sendMessage(playerSelections.getClass(), "zone-updated", "{zone_name}", zoneName);
   messageUtil.log(Level.INFO, "command-zone-updated-console", "{player}", player.getName(), "{zone_name}", zoneName);
  } else {
   // Create new zone
   AutoInformZone newZone = new AutoInformZone(zoneName, pos1, pos2, defaultAction, new HashMap<>());
   zoneManager.addZone(newZone);
   messageUtil.sendMessage(player, "zone-defined", "{zone_name}", zoneName);
   messageUtil.log(Level.INFO, "command-zone-defined-console", "{player}", player.getName(), "{zone_name}", zoneName);
  }

  // Clear player's selections after defining a zone
  zoneManager.getPlayerSelections().clearSelections(player);
  messageUtil.sendMessage(player, "command-selections-cleared");
 }

 private void handleDefaultActionCommand(Player player, String[] args) {
  if (args.length < 3) {
   messageUtil.sendMessage(playerSelections.getClass(), "command-defaultaction-usage");
   return;
  }
  String zoneName = args[1];
  String actionString = args[2].toUpperCase();

  if (!ZONE_NAME_PATTERN.matcher(zoneName).matches()) {
   messageUtil.sendMessage(playerSelections.getClass(), "error-invalid-zone-name");
   return;
  }

  AutoInformZone zone = zoneManager.getZone(zoneName);
  if (zone == null) {
   messageUtil.sendMessage(player, "error-zone-not-found", "{zone_name}", zoneName);
   return;
  }

  try {
   ZoneAction newAction = ZoneAction.valueOf(actionString);
   zone.setDefaultAction(newAction);
   zoneManager.saveZones(); // Save changes to config
   messageUtil.sendMessage(playerSelections.getClass(), "zone-defaultaction-set", "{zone_name}", zoneName, "{action}", newAction.name());
   messageUtil.log(Level.INFO, "command-zone-defaultaction-console", "{player}", player.getName(), "{zone_name}", zoneName, "{action}", newAction.name());
  } catch (IllegalArgumentException e) {
   messageUtil.sendMessage(player, "error-invalid-action-type", "{action}", actionString);
  }
 }

 private void handleSetActionCommand(Player player, String[] args) {
  if (args.length < 4) {
   messageUtil.sendMessage(player, "command-setaction-usage");
   return;
  }
  String zoneName = args[1];
  String materialName = args[2].toUpperCase();
  String actionString = args[3].toUpperCase();

  if (!ZONE_NAME_PATTERN.matcher(zoneName).matches()) {
   messageUtil.sendMessage(playerSelections.getClass(), "error-invalid-zone-name");
   return;
  }

  AutoInformZone zone = zoneManager.getZone(zoneName);
  if (zone == null) {
   messageUtil.sendMessage(player, "error-zone-not-found", "{zone_name}", zoneName);
   return;
  }

  try {
   Material material = Material.valueOf(materialName);
   ZoneAction action = ZoneAction.valueOf(actionString);

   zone.setDefaultAction(material, action);
   zoneManager.saveZones(); // Save changes to config
   messageUtil.sendMessage(player, "zone-setaction-set", "{zone_name}", zoneName, "{material}", material.name(), "{action}", action.name());
   messageUtil.log(Level.INFO, "command-zone-setaction-console", "{player}", player.getName(), "{zone_name}", zoneName, "{material}", material.name(), "{action}", action.name());
  } catch (IllegalArgumentException e) {
   messageUtil.sendMessage(playerSelections.getClass(), "error-invalid-material-or-action", "{material}", materialName, "{action}", actionString);
  }
 }

 private void handleRemoveCommand(Player player, String[] args) {
  if (args.length < 2) {
   messageUtil.sendMessage(playerSelections.getClass(), "command-remove-usage");
   return;
  }
  String zoneName = args[1];
  if (!ZONE_NAME_PATTERN.matcher(zoneName).matches()) {
   messageUtil.sendMessage(playerSelections.getClass(), "error-invalid-zone-name");
   return;
  }

  if (zoneManager.removeZone(zoneName)) {
   messageUtil.sendMessage(player, "zone-removed-success", "{zone_name}", zoneName);
   messageUtil.log(Level.INFO, "command-zone-removed-console", "{player}", player.getName(), "{zone_name}", zoneName);
  } else {
   messageUtil.sendMessage(playerSelections.getClass(), "error-zone-not-found", "{zone_name}", zoneName);
  }
 }

 private void handleInfoCommand(Player player, String[] args) {
  if (args.length < 2) { // Display info for all zones
   if (zoneManager.getAllZones().isEmpty()) {
    messageUtil.sendMessage(player, "plugin-no-zones-defined", "{command}", "autoinform");
    return;
   }
   messageUtil.sendMessage(player, "info-all-zones-header");
   for (AutoInformZone zone : zoneManager.getAllZones()) {
    sendZoneInfo(player, zone);
   }
  } else { // Display info for specific zone
   String zoneName = args[1];
   if (!ZONE_NAME_PATTERN.matcher(zoneName).matches()) {
    messageUtil.sendMessage(playerSelections.getClass(), "error-invalid-zone-name");
    return;
   }

   AutoInformZone zone = zoneManager.getZone(zoneName);
   if (zone == null) {
    messageUtil.sendMessage(playerSelections.getClass(), "error-zone-not-found", "{zone_name}", zoneName);
   } else {
    sendZoneInfo(player, zone);
   }
  }
 }

 private void sendZoneInfo(Player player, AutoInformZone zone) {
  messageUtil.sendMessage(playerSelections.getClass(), "info-zone-header", "{zone_name}", zone.getName());
  messageUtil.sendMessage(playerSelections.getClass(), "info-name", "{zone_name}", zone.getName());
  messageUtil.sendMessage(playerSelections.getClass(), "info-world", "{world}", zone.getWorld().getName());
  messageUtil.sendMessage(playerSelections.getClass(), "info-corner1", "{corner1}", LocationUtil.toBlockString(zone.getCorner1(pos1)));
  messageUtil.sendMessage(playerSelections.getClass(), "info-corner2", "{corner2}", LocationUtil.toBlockString(zone.getCorner2(pos2)));
  messageUtil.sendMessage(playerSelections.getClass(), "info-default-action", "{action}", zone.getDefaultAction().name());

  if (zone.getMaterialSpecificActions().isEmpty()) {
   messageUtil.sendMessage(player, "info-no-material-actions");
  } else {
   messageUtil.sendMessage(playerSelections.getClass(), "info-material-actions-header");
   for (Map.Entry<Material, ZoneAction> entry : zone.getMaterialSpecificActions().entrySet()) {
    Material material = entry.getKey();
    ZoneAction action = entry.getValue();
    messageUtil.sendMessage(player, "info-material-action-entry", "{material}", material.name(), "{action}", action.name());
   }
  }
 }

 private void handleListCommand(Player player) {
  Collection<AutoInformZone> zones = zoneManager.getAllZones();
  if (zones.isEmpty()) {
   messageUtil.sendMessage(player, "plugin-no-zones-defined", "{command}", "autoinform");
  } else {
   messageUtil.sendMessage(playerSelections.getClass(), "list-header", "{count}", String.valueOf(zones.size()));
   for (AutoInformZone zone : zones) {
    messageUtil.sendMessage(player, "list-entry", "{zone_name}", zone.getName());
   }
  }
 }

 private void handleClearWandCommand(Player player) {
  zoneManager.getPlayerSelections().clearSelections(player);
  messageUtil.sendMessage(playerSelections.getClass(), "command-selections-cleared");
  messageUtil.log(Level.INFO, "command-selections-cleared-console", "{player}", player.getName());
 }

 private void handleReloadCommand(Player player) {
  plugin.reloadConfigInternal(); // Call the public reload method on the main plugin class
  messageUtil.sendMessage(player, "command-reload-success");
  messageUtil.log(Level.INFO, "command-reload-success-console", "{player}", player.getName());
 }

 private void handleBannedMaterialsCommand(Player player, String[] args) {
  if (args.length < 2) {
   messageUtil.sendMessage(playerSelections.getClass(), "command-banned-usage");
   return;
  }

  String subCommand = args[1].toLowerCase();
  switch (subCommand) {
   case "add":
    if (args.length < 3) {
     messageUtil.sendMessage(playerSelections.getClass(), "command-banned-add-usage");
     return;
    }
    try {
     Material material = Material.valueOf(args[2].toUpperCase());
     if (zoneManager.addGloballyBannedMaterial(material)) {
      messageUtil.sendMessage(playerSelections.getClass(), "command-banned-add-success", "{material}", material.name());
      messageUtil.log(Level.INFO, "command-banned-add-console", "{player}", player.getName(), "{material}", material.name());
     } else {
      messageUtil.sendMessage(playerSelections.getClass(), "command-banned-already-banned", "{material}", material.name());
     }
    } catch (IllegalArgumentException e) {
     messageUtil.sendMessage(playerSelections.getClass(), "error-invalid-material", "{material}", args[2]);
    }
    break;
   case "remove":
    if (args.length < 3) {
     messageUtil.sendMessage(player, "command-banned-remove-usage");
     return;
    }
    try {
     Material material = Material.valueOf(args[2].toUpperCase());
     if (zoneManager.removeGloballyBannedMaterial(material)) {
      messageUtil.sendMessage(playerSelections.getClass(), "command-banned-remove-success", "{material}", material.name());
      messageUtil.log(Level.INFO, "command-banned-remove-console", "{player}", player.getName(), "{material}", material.name());
     } else {
      messageUtil.sendMessage(playerSelections.getClass(), "command-banned-not-banned", "{material}", material.name());
     }
    } catch (IllegalArgumentException e) {
     messageUtil.sendMessage(player, "error-invalid-material", "{material}", args[2]);
    }
    break;
   case "list":
    Set<Material> bannedMaterials = zoneManager.getGloballyBannedMaterials();
    if (bannedMaterials.isEmpty()) {
     messageUtil.sendMessage(playerSelections.getClass(), "plugin-no-banned-materials");
    } else {
     messageUtil.sendMessage(player, "plugin-current-banned-materials", "{materials}",
             bannedMaterials.stream().map(Enum::name).collect(Collectors.joining(", ")));
    }
    break;
   default:
    messageUtil.sendMessage(playerSelections.getClass(), "command-banned-usage");
    break;
  }
 }


 @Override
 public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
  if (!(sender instanceof Player)) {
   return Collections.emptyList();
  }

  List<String> completions = new ArrayList<>();

  if (args.length == 1) {
   // Top-level commands
   List<String> subCommands = Arrays.asList("wand", "pos1", "pos2", "define", "defaultaction", "setaction", "remove", "info", "list", "clearwand", "reload", "banned");
   StringUtil.copyPartialMatches(args[0], subCommands, completions);
  } else if (args.length == 2) {
   String subCommand = args[0].toLowerCase();
   switch (subCommand) {
    case "pos1":
    case "pos2":
    case "define":
    case "defaultaction":
    case "setaction":
    case "remove":
    case "info":
     // Suggest existing zone names
     StringUtil.copyPartialMatches(args[1], zoneManager.getAllZones().stream().map(AutoInformZone::getName).collect(Collectors.toList()), completions);
     break;
    case "banned":
     List<String> bannedSubCommands = Arrays.asList("add", "remove", "list");
     StringUtil.copyPartialMatches(args[1], bannedSubCommands, completions);
     break;
   }
  } else if (args.length == 3) {
   String subCommand = args[0].toLowerCase();
   if ("banned".equals(subCommand)) {
    String bannedAction = args[1].toLowerCase();
    if ("add".equals(bannedAction)) {
     // Suggest all materials for 'banned add'
     StringUtil.copyPartialMatches(args[2], Arrays.stream(Material.values()).map(Enum::name).collect(Collectors.toList()), completions);
    } else if ("remove".equals(bannedAction)) {
     // Suggest only currently banned materials for 'banned remove'
     StringUtil.copyPartialMatches(args[2], zoneManager.getGloballyBannedMaterials().stream().map(Enum::name).collect(Collectors.toList()), completions);
    }
   } else if ("defaultaction".equals(subCommand)) {
    // Suggest ZoneAction values
    StringUtil.copyPartialMatches(args[2], Arrays.stream(ZoneAction.values()).map(Enum::name).collect(Collectors.toList()), completions);
   } else if ("setaction".equals(subCommand)) {
    // Suggest all materials for 'setaction'
    StringUtil.copyPartialMatches(args[2], Arrays.stream(Material.values()).map(Enum::name).collect(Collectors.toList()), completions);
   }
  } else if (args.length == 4) {
   String subCommand = args[0].toLowerCase();
   if ("setaction".equals(subCommand)) {
    // Suggest ZoneAction values for 'setaction'
    StringUtil.copyPartialMatches(args[3], Arrays.stream(ZoneAction.values()).map(Enum::name).collect(Collectors.toList()), completions);
   }
  }

  Collections.sort(completions);
  return completions;
 }
}