package net.Alexxiconify.alexxAutoWarn.commands;

import net.Alexxiconify.alexxAutoWarn.AlexxAutoWarn;
import net.Alexxiconify.alexxAutoWarn.managers.ZoneManager;
import net.Alexxiconify.alexxAutoWarn.objects.AutoInformZone;
import net.Alexxiconify.alexxAutoWarn.objects.ZoneAction;
import net.Alexxiconify.alexxAutoWarn.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Handles all commands for the AlexxAutoWarn plugin.
 * Implements CommandExecutor for command execution and TabCompleter for tab completion.
 */
public class AutoInformCommandExecutor implements CommandExecutor, TabCompleter {

 private final AlexxAutoWarn plugin;
 private final ZoneManager zoneManager;
 private final MessageUtil messageUtil;

 // Pattern for validating zone names
 private static final Pattern ZONE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
 // Stores player's selection for zone creation: PlayerUUID -> ZoneName -> ("pos1" or "pos2") -> "world,x,y,z"
 // Using ConcurrentHashMap for thread-safety as players might interact concurrently
 private final Map<UUID, Map<String, Map<String, String>>> selections;

 public AutoInformCommandExecutor(AlexxAutoWarn plugin, ZoneManager zoneManager, MessageUtil messageUtil) {
  this.plugin = plugin;
  this.zoneManager = zoneManager;
  this.messageUtil = messageUtil;
  this.selections = new ConcurrentHashMap<>();
 }

 @Override
 public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
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
  plugin.giveSelectionWand(player); // Corrected method name
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

 private boolean handleDefineCommand(Player player, String[] args) {
  if (!player.hasPermission("autoinform.command.define")) {
   messageUtil.sendMessage(player, "error-no-permission");
   return false;
  }

  if (args.length < 2) {
   messageUtil.sendMessage(player, "command-define-usage");
   return false;
  }

  String zoneName = args[1].toLowerCase();
  if (!ZONE_NAME_PATTERN.matcher(zoneName).matches()) {
   messageUtil.sendMessage(player, "error-invalid-zone-name");
   return true;
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
   messageUtil.sendMessage(player, "command-define-no-selection",
           "{pos1_status}", pos1Set ? "Set" : "NOT Set",
           "{pos2_status}", pos2Set ? "Set" : "NOT Set");
   return pos1Set;
  }

  if (!pos1.getWorld().equals(pos2.getWorld())) {
   messageUtil.sendMessage(player, "command-define-different-worlds");
   return pos1Set;
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
  return pos1Set;
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
   materialActions.forEach((material, action) ->
           messageUtil.sendMessage(player, "info-material-action-entry",
                   "{material}", material.name(),
                   "{action}", action.name()));
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
   messageUtil.sendMessage(player, "command-defaultaction-success",
           "{zone_name}", zoneName, "{action}", action.name());
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
   messageUtil.sendMessage(player, "command-setaction-success",
           "{zone_name}", zoneName, "{material}", material.name(), "{action}", action.name());
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

  if (zone.removeMaterialAction(material) != null) {
   zoneManager.addZone(zone);
   messageUtil.sendMessage(player, "command-removeaction-success",
           "{zone_name}", zoneName, "{material}", material.name());
  } else {
   messageUtil.sendMessage(player, "command-removeaction-not-found",
           "{zone_name}", zoneName, "{material}", material.name());
  }
 }

 private void handleClearWandCommand(Player player) {
  if (!player.hasPermission("autoinform.command.clearwand")) {
   messageUtil.sendMessage(player, "error-no-permission");
   return;
  }

  clearPlayerSelections(player);
  messageUtil.sendMessage(player, "command-clearwand-success");
  messageUtil.log(Level.FINE, "debug-player-selection-cleared-all", "{player}", player.getName());
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
   messageUtil.sendMessage(player, "command-banned-usage");
   return;
  }

  String action = args[1].toLowerCase();
  switch (action) {
   case "list":
    Set<Material> bannedMaterials = zoneManager.getGloballyBannedMaterials();
    if (bannedMaterials.isEmpty()) {
     messageUtil.sendMessage(player, "plugin-no-banned-materials");
    } else {
     messageUtil.sendMessage(player, "plugin-current-banned-materials",
             "{materials}", plugin.formatMaterialList(bannedMaterials));
    }
    break;
   case "add":
    if (args.length < 3) {
     messageUtil.sendMessage(player, "command-banned-add-usage");
     return;
    }
    String materialToAddString = args[2].toUpperCase();
    try {
     Material material = Material.valueOf(materialToAddString);
     if (zoneManager.addGloballyBannedMaterial(material)) {
      messageUtil.sendMessage(player, "command-banned-add-success", "{material}", material.name());
     } else {
      messageUtil.sendMessage(player, "command-banned-add-already-banned", "{material}", material.name());
     }
    } catch (IllegalArgumentException e) {
     messageUtil.sendMessage(player, "error-invalid-material", "{material}", materialToAddString);
    }
    break;
   case "remove":
    if (args.length < 3) {
     messageUtil.sendMessage(player, "command-banned-remove-usage");
     return;
    }
    String materialToRemoveString = args[2].toUpperCase();
    try {
     Material material = Material.valueOf(materialToRemoveString);
     if (zoneManager.removeGloballyBannedMaterial(material)) {
      messageUtil.sendMessage(player, "command-banned-remove-success", "{material}", material.name());
     } else {
      messageUtil.sendMessage(player, "command-banned-remove-not-banned", "{material}", material.name());
     }
    } catch (IllegalArgumentException e) {
     messageUtil.sendMessage(player, "error-invalid-material", "{material}", materialToRemoveString);
    }
    break;
   default:
    messageUtil.sendMessage(player, "command-banned-usage");
    break;
  }
 }

 // --- Tab Completer ---
 @Override
 public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
  if (!(sender instanceof Player player)) {
   return Collections.emptyList();
  }

  if (!player.hasPermission("autoinform.admin.set")) {
   return Collections.emptyList();
  }

  List<String> completions = new ArrayList<>();
  List<String> subCommands = Arrays.asList("wand", "pos1", "pos2", "define", "remove", "info", "list",
          "defaultaction", "setaction", "removeaction", "clearwand", "reload", "banned");

  if (args.length == 1) {
   // Suggest main sub-commands
   subCommands.stream()
           .filter(s -> s.startsWith(args[0].toLowerCase()))
           .forEach(completions::add);
  } else if (args.length > 1) {
   String subCommand = args[0].toLowerCase();
   switch (subCommand) {
    case "pos1":
    case "pos2":
    case "define":
    case "remove":
    case "defaultaction":
    case "setaction":
    case "removeaction":
    case "info":
     // Suggest zone names
     if (args.length == 2) {
      String input = args[1].toLowerCase();
      zoneManager.getAllZones().stream()
              .map(AutoInformZone::getName)
              .filter(name -> name.startsWith(input))
              .forEach(completions::add);
     }
     if (subCommand.equals("defaultaction") && args.length == 3) {
      // Suggest ZoneAction types
      String input = args[2].toLowerCase();
      Arrays.stream(ZoneAction.values())
              .map(Enum::name)
              .filter(name -> name.toLowerCase().startsWith(input))
              .forEach(completions::add);
     }
     if ((subCommand.equals("setaction") || subCommand.equals("removeaction")) && args.length == 3) {
      // Suggest Materials for setaction/removeaction
      String input = args[2].toLowerCase();
      Arrays.stream(Material.values())
              .map(Enum::name)
              .filter(name -> name.toLowerCase().startsWith(input))
              .forEach(completions::add);
     }
     if (subCommand.equals("setaction") && args.length == 4) {
      // Suggest ZoneAction types for setaction
      String input = args[3].toLowerCase();
      Arrays.stream(ZoneAction.values())
              .map(Enum::name)
              .filter(name -> name.toLowerCase().startsWith(input))
              .forEach(completions::add);
     }
     break;
    case "banned":
     if (args.length == 2) {
      String input = args[1].toLowerCase();
      Arrays.asList("add", "remove", "list").stream()
              .filter(s -> s.startsWith(input))
              .forEach(completions::add);
     } else if (args.length == 3 && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
      // Suggest Materials for add/remove banned materials
      String input = args[2].toLowerCase();
      Arrays.stream(Material.values())
              .map(Enum::name)
              .filter(name -> name.toLowerCase().startsWith(input))
              .forEach(completions::add);
     }
     break;
   }
  }

  return completions;
 }

 // --- Player Selection Management (Moved from a separate manager) ---

 /**
  * Sets a player's selection for a specific zone and position type.
  * Uses a thread-safe map to store selections.
  *
  * @param player   The player making the selection.
  * @param zoneName The name of the zone the selection belongs to.
  * @param posType  The type of position ("pos1" or "pos2").
  * @param location The Location being set.
  */
 public void setPlayerSelection(@NotNull Player player, @NotNull String zoneName, @NotNull String posType, @NotNull Location location) {
  selections.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
          .computeIfAbsent(zoneName.toLowerCase(), k -> new ConcurrentHashMap<>())
          .put(posType.toLowerCase(), serializeLocation(location));

  messageUtil.log(Level.FINEST, "debug-player-selection-set",
          "{player}", player.getName(),
          "{uuid}", player.getUniqueId().toString(),
          "{zone_name}", zoneName,
          "{pos_type}", posType,
          "{location}", serializeLocation(location));
 }

 /**
  * Retrieves a player's selection for a specific zone and position type.
  *
  * @param player   The player whose selection is being retrieved.
  * @param zoneName The name of the zone.
  * @param posType  The type of position ("pos1" or "pos2").
  * @return The Location of the selection, or null if not found.
  */
 @Nullable
 public Location getPlayerSelection(@NotNull Player player, @NotNull String zoneName, @NotNull String posType) {
  String locationString = selections
          .getOrDefault(player.getUniqueId(), Collections.emptyMap())
          .getOrDefault(zoneName.toLowerCase(), Collections.emptyMap())
          .get(posType.toLowerCase());

  messageUtil.log(Level.FINEST, "debug-player-selection-get",
          "{player}", player.getName(),
          "{uuid}", player.getUniqueId().toString(),
          "{zone_name}", zoneName,
          "{pos_type}", posType,
          "{location_string}", locationString != null ? locationString : "null");

  return deserializeLocation(locationString);
 }

 /**
  * Clears all selections for a specific zone for a given player.
  *
  * @param player   The player whose selections to clear.
  * @param zoneName The name of the zone for which to clear selections.
  */
 public void clearPlayerSelections(@NotNull Player player, @NotNull String zoneName) {
  Map<String, Map<String, String>> playerZoneSelections = selections.get(player.getUniqueId());
  if (playerZoneSelections != null) {
   playerZoneSelections.remove(zoneName.toLowerCase());
   if (playerZoneSelections.isEmpty()) {
    selections.remove(player.getUniqueId()); // Remove player entry if no more zones
   }
  }
  messageUtil.log(Level.FINEST, "debug-player-selection-cleared-zone",
          "{player}", player.getName(),
          "{uuid}", player.getUniqueId().toString(),
          "{zone_name}", zoneName);
 }

 /**
  * Clears all selections for a given player across all zones.
  *
  * @param player The player whose all selections to clear.
  */
 public void clearPlayerSelections(@NotNull Player player) {
  selections.remove(player.getUniqueId());
  messageUtil.log(Level.FINEST, "debug-player-selection-cleared-all",
          "{player}", player.getName(),
          "{uuid}", player.getUniqueId().toString());
 }

 /**
  * Converts the internal selections map for a player to a JSON-like string for debugging.
  *
  * @param playerUUID The UUID of the player.
  * @return A string representation of the player's selections.
  */
 private String getSelectionsAsJson(@NotNull UUID playerUUID) {
  Map<String, Map<String, String>> playerZoneSelections = selections.get(playerUUID);
  if (playerZoneSelections == null || playerZoneSelections.isEmpty()) {
   return "{}";
  }
  StringBuilder json = new StringBuilder("{");
  boolean firstZone = true;
  for (Map.Entry<String, Map<String, String>> zoneEntry : playerZoneSelections.entrySet()) {
   if (!firstZone) json.append(", ");
   json.append("\"").append(zoneEntry.getKey()).append("\": {");
   boolean firstPos = true;
   for (Map.Entry<String, String> posEntry : zoneEntry.getValue().entrySet()) {
    if (!firstPos) json.append(", ");
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
 public String serializeLocation(@NotNull Location location) { // Changed to public
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
 public Location deserializeLocation(@Nullable String locationString) { // Changed to public
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

   return new Location(Bukkit.getWorld(worldName), x, y, z);
  } catch (NumberFormatException | NullPointerException e) {
   messageUtil.log(Level.WARNING, "debug-error-parsing-location-string", "{location_string}", locationString, "{error}", e.getMessage());
   return null;
  }
 }
}