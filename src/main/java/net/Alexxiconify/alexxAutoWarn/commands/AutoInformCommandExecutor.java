package net.Alexxiconify.alexxAutoWarn.commands;

import net.Alexxiconify.alexxAutoWarn.AlexxAutoWarn;
import net.Alexxiconify.alexxAutoWarn.managers.ZoneManager;
import net.Alexxiconify.alexxAutoWarn.objects.AutoInformZone;
import net.Alexxiconify.alexxAutoWarn.objects.ZoneAction;
import net.Alexxiconify.alexxAutoWarn.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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

 // Stores player's selection for zone creation: PlayerUUID -> ZoneName -> ("pos1" or "pos2") -> "world,x,y,z"
 // Using ConcurrentHashMap for thread-safety as players might interact concurrently
 private final Map<UUID, Map<String, Map<String, String>>> selections;

 // Pattern for validating zone names
 private static final Pattern ZONE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

 public AutoInformCommandExecutor(AlexxAutoWarn plugin, ZoneManager zoneManager, MessageUtil messageUtil) {
  this.plugin = plugin;
  this.zoneManager = zoneManager;
  this.messageUtil = messageUtil;
  this.selections = new ConcurrentHashMap<>();
 }

 @Override
 public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
  if (!(sender instanceof Player player)) {
   messageUtil.sendMessage(sender, "error-player-only-command");
   return true;
  }

  // Permission check for main command usage
  if (!player.hasPermission("autoinform.admin.set")) {
   messageUtil.sendMessage(player, "error-no-permission");
   return true;
  }

  if (args.length == 0) {
   messageUtil.sendHelpMessage(player, label);
   return true;
  }

  String subCommand = args[0].toLowerCase();

  switch (subCommand) {
   case "wand":
    handleWandCommand(player);
    break;
   case "pos1":
   case "pos2":
    handlePosCommand(player, args);
    break;
   case "define":
    handleDefineCommand(player, args);
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
   case "defaultaction":
    handleDefaultActionCommand(player, args);
    break;
   case "setaction":
    handleSetActionCommand(player, args);
    break;
   case "removeaction":
    handleRemoveActionCommand(player, args);
    break;
   case "clearwand":
    handleClearWandCommand(player);
    break;
   case "reload":
    handleReloadCommand(player);
    break;
   case "banned":
    handleBannedCommand(player, args);
    break;
   default:
    messageUtil.sendHelpMessage(player, label);
    break;
  }
  return true;
 }

 // --- Command Handlers ---

 private void handleWandCommand(Player player) {
  if (!player.hasPermission("autoinform.command.wand")) {
   messageUtil.sendMessage(player, "error-no-permission");
   return;
  }
  plugin.giveSelectionWand(player);
  messageUtil.sendMessage(player, "command-wand-given");
 }

 private void handlePosCommand(Player player, String[] args) {
  if (!player.hasPermission("autoinform.command.pos")) {
   messageUtil.sendMessage(player, "error-no-permission");
   return;
  }

  if (args.length < 2) {
   messageUtil.sendMessage(player, "command-pos-usage");
   return;
  }

  String zoneName = args[1].toLowerCase();
  if (!ZONE_NAME_PATTERN.matcher(zoneName).matches()) {
   messageUtil.sendMessage(player, "error-invalid-zone-name");
   return;
  }

  String posType = args[0].toLowerCase(); // "pos1" or "pos2"
  Location location = player.getLocation().getBlock().getLocation(); // Get block location to ignore decimals

  setPlayerSelection(player, zoneName, posType, location); // Direct call within this class
  messageUtil.sendMessage(player, "command-pos-set",
          "{position}", posType.toUpperCase(),
          "{zone_name}", zoneName,
          "{location}", String.format("%s,%.0f,%.0f,%.0f", location.getWorld().getName(), location.getX(), location.getY(), location.getZ()));

  messageUtil.log(Level.FINE, "debug-pos-saved",
          "{player}", player.getName(),
          "{position}", posType,
          "{zone_name}", zoneName,
          "{location_string}", serializeLocation(location),
          "{current_json}", getSelectionsAsJson(player.getUniqueId()));
 }

 private void handleDefineCommand(Player player, String[] args) {
  if (!player.hasPermission("autoinform.command.define")) {
   messageUtil.sendMessage(player, "error-no-permission");
   return;
  }

  if (args.length < 2) {
   messageUtil.sendMessage(player, "command-define-usage");
   return;
  }

  String zoneName = args[1].toLowerCase();
  if (!ZONE_NAME_PATTERN.matcher(zoneName).matches()) {
   messageUtil.sendMessage(player, "error-invalid-zone-name");
   return;
  }

  messageUtil.log(Level.FINE, "debug-define-start",
          "{player}", player.getName(),
          "{zone_name}", zoneName,
          "{raw_json}", getSelectionsAsJson(player.getUniqueId()));


  Location pos1 = getPlayerSelection(player, zoneName, "pos1");
  Location pos2 = getPlayerSelection(player, zoneName, "pos2");

  boolean pos1Set = (pos1 != null);
  boolean pos2Set = (pos2 != null);

  messageUtil.log(Level.FINE, "debug-define-selections",
          "{zone_name}", zoneName,
          "{pos1_status}", String.valueOf(pos1Set),
          "{pos2_status}", String.valueOf(pos2Set));

  if (!pos1Set || !pos2Set) {
   messageUtil.sendMessage(player, "error-define-no-selection",
           "{pos1_status}", pos1Set ? "Set" : "NOT Set",
           "{pos2_status}", pos2Set ? "Set" : "NOT Set");
   return;
  }

  if (!pos1.getWorld().equals(pos2.getWorld())) {
   messageUtil.sendMessage(player, "command-define-different-worlds");
   return;
  }

  AutoInformZone existingZone = zoneManager.getZone(zoneName);
  AutoInformZone newZone;

  if (existingZone == null) {
   // New zone
   newZone = new AutoInformZone(zoneName, pos1, pos2, ZoneAction.ALERT, new HashMap<>());
   messageUtil.sendMessage(player, "command-define-new-zone", "{zone_name}", zoneName);
  } else {
   // Update existing zone
   newZone = existingZone;
   newZone.setCorner1(pos1);
   newZone.setCorner2(pos2);
   messageUtil.sendMessage(player, "command-define-updated-zone", "{zone_name}", zoneName);
  }

  zoneManager.addZone(newZone);
  clearPlayerSelections(player, zoneName); // Clear selections after defining
  messageUtil.sendMessage(player, "command-define-success", "{zone_name}", zoneName);
 }

 private void handleRemoveCommand(Player player, String[] args) {
  if (!player.hasPermission("autoinform.command.remove")) {
   messageUtil.sendMessage(player, "error-no-permission");
   return;
  }

  if (args.length < 2) {
   messageUtil.sendMessage(player, "command-remove-usage");
   return;
  }

  String zoneName = args[1].toLowerCase();
  if (!ZONE_NAME_PATTERN.matcher(zoneName).matches()) {
   messageUtil.sendMessage(player, "error-invalid-zone-name");
   return;
  }

  if (zoneManager.removeZone(zoneName)) {
   messageUtil.sendMessage(player, "command-remove-success", "{zone_name}", zoneName);
  } else {
   messageUtil.sendMessage(player, "command-remove-not-found", "{zone_name}", zoneName);
  }
 }

 private void handleInfoCommand(Player player, String[] args) {
  if (!player.hasPermission("autoinform.command.info")) {
   messageUtil.sendMessage(player, "error-no-permission");
   return;
  }

  if (args.length == 1) { // /autoinform info - list all zones
   handleListCommand(player); // Delegate to list command
   return;
  }

  String zoneName = args[1].toLowerCase();
  if (!ZONE_NAME_PATTERN.matcher(zoneName).matches()) {
   messageUtil.sendMessage(player, "error-invalid-zone-name");
   return;
  }

  AutoInformZone zone = zoneManager.getZone(zoneName);
  if (zone == null) {
   messageUtil.sendMessage(player, "command-info-not-found", "{zone_name}", zoneName);
   return;
  }

  messageUtil.sendMessage(player, "command-info-header", "{zone_name}", zone.getName());
  messageUtil.sendMessage(player, "info-name", "{zone_name}", zone.getName());
  messageUtil.sendMessage(player, "info-world", "{world}", zone.getWorld().getName());
  messageUtil.sendMessage(player, "info-corner1", "{corner1}", serializeLocation(zone.getCorner1()));
  messageUtil.sendMessage(player, "info-corner2", "{corner2}", serializeLocation(zone.getCorner2()));
  messageUtil.sendMessage(player, "info-default-action", "{action}", zone.getDefaultAction().name());

  Map<Material, ZoneAction> materialActions = zone.getMaterialSpecificActions();
  if (materialActions.isEmpty()) {
   messageUtil.sendMessage(player, "info-no-material-actions");
  } else {
   messageUtil.sendMessage(player, "info-material-actions-header");
   materialActions.forEach((material, action) -> messageUtil.sendMessage(player, "info-material-action-entry", "{material}", material.name(), "{action}", action.name()));
  }
 }

 private void handleListCommand(Player player) {
  if (!player.hasPermission("autoinform.command.list")) {
   messageUtil.sendMessage(player, "error-no-permission");
   return;
  }
  Collection<AutoInformZone> zones = zoneManager.getAllZones();
  if (zones.isEmpty()) {
   messageUtil.sendMessage(player, "plugin-no-zones-defined", "{command}", "autoinform");
   return;
  }
  messageUtil.sendMessage(player, "list-header", "{count}", zones.size());
  zones.forEach(zone -> messageUtil.sendMessage(player, "list-entry", "{zone_name}", zone.getName()));
 }

 private void handleDefaultActionCommand(Player player, String[] args) {
  if (!player.hasPermission("autoinform.command.defaultaction")) {
   messageUtil.sendMessage(player, "error-no-permission");
   return;
  }
  if (args.length < 3) {
   messageUtil.sendMessage(player, "command-defaultaction-usage");
   return;
  }
  String zoneName = args[1].toLowerCase();
  if (!ZONE_NAME_PATTERN.matcher(zoneName).matches()) {
   messageUtil.sendMessage(player, "error-invalid-zone-name");
   return;
  }
  AutoInformZone zone = zoneManager.getZone(zoneName);
  if (zone == null) {
   messageUtil.sendMessage(player, "command-defaultaction-zone-not-found", "{zone_name}", zoneName);
   return;
  }
  String actionString = args[2].toUpperCase();
  try {
   ZoneAction action = ZoneAction.valueOf(actionString);
   zone.setDefaultAction(action);
   zoneManager.addZone(zone);
   messageUtil.sendMessage(player, "command-defaultaction-success", "{zone_name}", zoneName, "{action}", action.name());
  } catch (IllegalArgumentException e) {
   messageUtil.sendMessage(player, "error-invalid-action-type", "{action_type}", actionString);
  }
 }

 private void handleSetActionCommand(Player player, String[] args) {
  if (!player.hasPermission("autoinform.command.setaction")) {
   messageUtil.sendMessage(player, "error-no-permission");
   return;
  }
  if (args.length < 4) {
   messageUtil.sendMessage(player, "command-setaction-usage");
   return;
  }
  String zoneName = args[1].toLowerCase();
  if (!ZONE_NAME_PATTERN.matcher(zoneName).matches()) {
   messageUtil.sendMessage(player, "error-invalid-zone-name");
   return;
  }
  AutoInformZone zone = zoneManager.getZone(zoneName);
  if (zone == null) {
   messageUtil.sendMessage(player, "command-setaction-zone-not-found", "{zone_name}", zoneName);
   return;
  }
  String materialString = args[2].toUpperCase();
  Material material;
  try {
   material = Material.valueOf(materialString);
  } catch (IllegalArgumentException e) {
   messageUtil.sendMessage(player, "error-invalid-material", "{material}", materialString);
   return;
  }
  String actionString = args[3].toUpperCase();
  try {
   ZoneAction action = ZoneAction.valueOf(actionString);
   zone.setMaterialAction(material, action);
   zoneManager.addZone(zone);
   messageUtil.sendMessage(player, "command-setaction-success", "{zone_name}", zoneName, "{material}", material.name(), "{action}", action.name());
  } catch (IllegalArgumentException e) {
   messageUtil.sendMessage(player, "error-invalid-action-type", "{action_type}", actionString);
  }
 }

 private void handleRemoveActionCommand(Player player, String[] args) {
  if (!player.hasPermission("autoinform.command.removeaction")) {
   messageUtil.sendMessage(player, "error-no-permission");
   return;
  }
  if (args.length < 3) {
   messageUtil.sendMessage(player, "command-removeaction-usage");
   return;
  }
  String zoneName = args[1].toLowerCase();
  if (!ZONE_NAME_PATTERN.matcher(zoneName).matches()) {
   messageUtil.sendMessage(player, "error-invalid-zone-name");
   return;
  }
  AutoInformZone zone = zoneManager.getZone(zoneName);
  if (zone == null) {
   messageUtil.sendMessage(player, "command-removeaction-zone-not-found", "{zone_name}", zoneName);
   return;
  }
  String materialString = args[2].toUpperCase();
  Material material;
  try {
   material = Material.valueOf(materialString);
  } catch (IllegalArgumentException e) {
   messageUtil.sendMessage(player, "error-invalid-material", "{material}", materialString);
   return;
  }

  if (zone.removeMaterialAction(material)) {
   zoneManager.addZone(zone);
   messageUtil.sendMessage(player, "command-removeaction-success", "{material}", material.name(), "{zone_name}", zoneName);
  } else {
   messageUtil.sendMessage(player, "command-removeaction-not-found", "{material}", material.name(), "{zone_name}", zoneName);
  }
 }

 private void handleClearWandCommand(Player player) {
  if (!player.hasPermission("autoinform.command.clearwand")) {
   messageUtil.sendMessage(player, "error-no-permission");
   return;
  }
  clearPlayerSelections(player);
  messageUtil.sendMessage(player, "wand-selections-cleared");
 }

 private void handleReloadCommand(Player player) {
  if (!player.hasPermission("autoinform.command.reload")) {
   messageUtil.sendMessage(player, "error-no-permission");
   return;
  }
  plugin.reloadPluginConfig();
  messageUtil.sendMessage(player, "plugin-config-reloaded");
 }

 private void handleBannedCommand(Player player, String[] args) {
  if (!player.hasPermission("autoinform.command.banned")) {
   messageUtil.sendMessage(player, "error-no-permission");
   return;
  }

  if (args.length < 2) {
   messageUtil.sendMessage(player, "error-usage-banned");
   return;
  }

  String action = args[1].toLowerCase();
  switch (action) {
   case "add":
    if (args.length < 3) {
     messageUtil.sendMessage(player, "error-usage-banned-add");
     return;
    }
    handleAddBannedMaterial(player, args[2]);
    break;
   case "remove":
    if (args.length < 3) {
     messageUtil.sendMessage(player, "error-usage-banned-remove");
     return;
    }
    handleRemoveBannedMaterial(player, args[2]);
    break;
   case "list":
    handleListBannedMaterials(player);
    break;
   default:
    messageUtil.sendMessage(player, "error-usage-banned");
    break;
  }
 }

 private void handleAddBannedMaterial(Player player, String materialName) {
  try {
   Material material = Material.valueOf(materialName.toUpperCase());
   if (zoneManager.addGloballyBannedMaterial(material)) {
    messageUtil.sendMessage(player, "banned-material-added", "{material}", material.name());
   } else {
    messageUtil.sendMessage(player, "banned-material-already-added", "{material}", material.name());
   }
  } catch (IllegalArgumentException e) {
   messageUtil.sendMessage(player, "error-invalid-material", "{material}", materialName);
  }
 }

 private void handleRemoveBannedMaterial(Player player, String materialName) {
  try {
   Material material = Material.valueOf(materialName.toUpperCase());
   if (zoneManager.removeGloballyBannedMaterial(material)) {
    messageUtil.sendMessage(player, "banned-material-removed", "{material}", material.name());
   } else {
    messageUtil.sendMessage(player, "banned-material-not-found", "{material}", material.name());
   }
  } catch (IllegalArgumentException e) {
   messageUtil.sendMessage(player, "error-invalid-material", "{material}", materialName);
  }
 }

 private void handleListBannedMaterials(Player player) {
  Set<Material> bannedMaterials = zoneManager.getGloballyBannedMaterials();
  if (bannedMaterials.isEmpty()) {
   messageUtil.sendMessage(player, "plugin-no-banned-materials");
  } else {
   messageUtil.sendMessage(player, "plugin-current-banned-materials", "{materials}", plugin.formatMaterialList(bannedMaterials));
  }
 }


 // --- Player Selection Management for Wand ---

 /**
  * Sets a player's selection (pos1 or pos2) for a given zone name.
  * Uses a specific structure: playerUUID -> zoneName -> posType (e.g., "pos1") -> locationString
  * This allows multiple players to set selections for different zones concurrently.
  *
  * @param player   The player making the selection.
  * @param zoneName The name of the zone being defined.
  * @param posType  "pos1" or "pos2".
  * @param location The location to save.
  */
 public void setPlayerSelection(@NotNull Player player, @NotNull String zoneName, @NotNull String posType, @NotNull Location location) {
  selections.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
          .computeIfAbsent(zoneName, k -> new ConcurrentHashMap<>())
          .put(posType, serializeLocation(location));

  messageUtil.log(Level.FINE, "debug-player-selection-set",
          "{player_uuid}", player.getUniqueId().toString(),
          "{json_string}", getSelectionsAsJson(player.getUniqueId()));
 }

 /**
  * Overloaded method for wand interactions where zoneName is not explicitly given in the command.
  * Assumes a default/temporary zone selection state for the player.
  *
  * @param player         The player making the selection.
  * @param posType        "pos1" or "pos2".
  * @param locationString The location string to save.
  */
 public void setPlayerSelection(@NotNull Player player, @NotNull String posType, @NotNull String locationString) {
  // Use a generic placeholder if zoneName is not provided directly by command (e.g., from wand)
  String defaultZoneName = "wand_selection";
  selections.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
          .computeIfAbsent(defaultZoneName, k -> new ConcurrentHashMap<>())
          .put(posType, locationString);

  messageUtil.log(Level.FINE, "debug-player-selection-set",
          "{player_uuid}", player.getUniqueId().toString(),
          "{json_string}", getSelectionsAsJson(player.getUniqueId()));
 }


 /**
  * Retrieves a player's saved selection for a specific zone and position type.
  *
  * @param player   The player.
  * @param zoneName The name of the zone.
  * @param posType  "pos1" or "pos2".
  * @return The Location object, or null if not found.
  */
 @Nullable
 public Location getPlayerSelection(@NotNull Player player, @NotNull String zoneName, @NotNull String posType) {
  Map<String, String> zoneSelections = selections.getOrDefault(player.getUniqueId(), Collections.emptyMap()).get(zoneName);
  if (zoneSelections == null || !zoneSelections.containsKey(posType)) {
   messageUtil.log(Level.FINE, "debug-player-selection-get",
           "{player_uuid}", player.getUniqueId().toString(),
           "{json_string}", getSelectionsAsJson(player.getUniqueId()) + " - No " + posType + " for " + zoneName);
   return null;
  }
  String locationString = zoneSelections.get(posType);
  return deserializeLocation(locationString);
 }

 /**
  * Clears all wand selections for a specific player and zone.
  *
  * @param player   The player whose selections to clear.
  * @param zoneName The zone for which to clear selections.
  */
 public void clearPlayerSelections(@NotNull Player player, @NotNull String zoneName) {
  Map<String, Map<String, String>> playerSelections = selections.get(player.getUniqueId());
  if (playerSelections != null) {
   playerSelections.remove(zoneName);
   if (playerSelections.isEmpty()) {
    selections.remove(player.getUniqueId());
   }
  }
  messageUtil.log(Level.FINE, "debug-player-selection-set",
          "{player_uuid}", player.getUniqueId().toString(),
          "{json_string}", getSelectionsAsJson(player.getUniqueId()));
 }

 /**
  * Clears all wand selections for a specific player across all zones.
  *
  * @param player The player whose selections to clear.
  */
 public void clearPlayerSelections(@NotNull Player player) {
  selections.remove(player.getUniqueId());
  messageUtil.log(Level.FINE, "debug-player-selection-set",
          "{player_uuid}", player.getUniqueId().toString(),
          "{json_string}", "{}"); // After clearing, JSON should be empty
 }

 // --- Utility Methods ---

 /**
  * Converts the current player selections for debugging purposes into a JSON string.
  * This helps visualize the internal state of selections.
  * Note: This is a simplified JSON representation for logging, not a robust JSON serializer.
  */
 private String getSelectionsAsJson(@NotNull UUID playerUUID) {
  Map<String, Map<String, String>> playerSelections = selections.get(playerUUID);
  if (playerSelections == null || playerSelections.isEmpty()) {
   return "{}";
  }
  StringBuilder json = new StringBuilder("{");
  boolean firstZone = true;
  for (Map.Entry<String, Map<String, String>> zoneEntry : playerSelections.entrySet()) {
   if (!firstZone) {
    json.append(", ");
   }
   json.append("\"").append(zoneEntry.getKey()).append("\": {");
   boolean firstPos = true;
   for (Map.Entry<String, String> posEntry : zoneEntry.getValue().entrySet()) {
    if (!firstPos) {
     json.append(", ");
    }
    json.append("\"").append(posEntry.getKey()).append("\": \"").append(posEntry.getValue()).append("\"");
    firstPos = false;
   }
   json.append("}");
   firstZone = false;
  }
  json.append("}");
  return json.toString();
 }


 /**
  * Serializes a Location object to a string format for persistent data storage.
  * Format: "worldName,x,y,z"
  */
 private String serializeLocation(@NotNull Location location) {
  return String.format("%s,%.0f,%.0f,%.0f",
          location.getWorld().getName(), location.getX(), location.getY(), location.getZ());
 }

 /**
  * Deserializes a string back into a Location object.
  * Expects format: "worldName,x,y,z"
  *
  * @param locationString The string representation of the location.
  * @return The Location object, or null if the string format is invalid or world not found.
  */
 @Nullable
 private Location deserializeLocation(@Nullable String locationString) {
  if (locationString == null || locationString.isEmpty()) {
   return null;
  }
  String[] parts = locationString.split(",");
  if (parts.length != 4) {
   messageUtil.log(Level.WARNING, "debug-invalid-location-string-format", "{location_string}", locationString);
   return null;
  }
  try {
   String worldName = parts[0];
   double x = Double.parseDouble(parts[1]);
   double y = Double.parseDouble(parts[2]);
   double z = Double.parseDouble(parts[3]);

   World world = Bukkit.getWorld(worldName);
   if (world == null) {
    messageUtil.log(Level.WARNING, "plugin-world-not-found", "{world_name}", worldName, "{location_string}", locationString);
    return null;
   }
   return new Location(world, x, y, z);
  } catch (NumberFormatException e) {
   messageUtil.log(Level.WARNING, "debug-location-parse-error", "{location_string}", locationString, "{error}", e.getMessage());
   return null;
  }
 }

 @Override
 public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {
  if (!(sender instanceof Player)) {
   return Collections.emptyList();
  }

  List<String> completions = new ArrayList<>();
  if (args.length == 1) {
   StringUtil.copyPartialMatches(args[0], Arrays.asList(
           "wand", "pos1", "pos2", "define", "remove", "info", "list",
           "defaultaction", "setaction", "removeaction", "clearwand", "reload", "banned"
   ), completions);
  } else if (args.length == 2) {
   String subCommand = args[0].toLowerCase();
   switch (subCommand) {
    case "pos1":
    case "pos2":
    case "define":
    case "remove":
    case "info":
    case "defaultaction":
    case "setaction":
    case "removeaction":
     StringUtil.copyPartialMatches(args[1], zoneManager.getAllZones().stream()
             .map(AutoInformZone::getName)
             .collect(Collectors.toList()), completions);
     break;
    case "banned":
     StringUtil.copyPartialMatches(args[1], Arrays.asList("add", "remove", "list"), completions);
     break;
   }
  } else if (args.length == 3) {
   String subCommand = args[0].toLowerCase();
   if (subCommand.equals("defaultaction")) {
    StringUtil.copyPartialMatches(args[2], Arrays.asList("DENY", "ALERT", "ALLOW"), completions);
   } else if (subCommand.equals("setaction") || subCommand.equals("removeaction")) {
    StringUtil.copyPartialMatches(args[2], Arrays.stream(Material.values())
            .map(Enum::name)
            .collect(Collectors.toList()), completions);
   } else if (subCommand.equals("banned")) {
    String bannedAction = args[1].toLowerCase();
    if (bannedAction.equals("add")) {
     StringUtil.copyPartialMatches(args[2], Arrays.stream(Material.values())
             .map(Enum::name)
             .collect(Collectors.toList()), completions);
    } else if (bannedAction.equals("remove")) {
     StringUtil.copyPartialMatches(args[2], zoneManager.getGloballyBannedMaterials().stream()
             .map(Enum::name)
             .collect(Collectors.toList()), completions);
    }
   }
  } else if (args.length == 4) {
   String subCommand = args[0].toLowerCase();
   if (subCommand.equals("setaction")) {
    StringUtil.copyPartialMatches(args[3], Arrays.asList("DENY", "ALERT", "ALLOW"), completions);
   }
  }
  Collections.sort(completions);
  return completions;
 }
}