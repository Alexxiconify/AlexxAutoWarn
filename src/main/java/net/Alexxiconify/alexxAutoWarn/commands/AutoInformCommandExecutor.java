package net.Alexxiconify.alexxAutoWarn.commands;

import net.Alexxiconify.alexxAutoWarn.AlexxAutoWarn;
import net.Alexxiconify.alexxAutoWarn.managers.ZoneManager;
import net.Alexxiconify.alexxAutoWarn.objects.AutoInformZone;
import net.Alexxiconify.alexxAutoWarn.objects.ZoneAction;
import net.Alexxiconify.alexxAutoWarn.utils.MessageUtil;
import org.bukkit.Bukkit;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles all commands for the AlexxAutoWarn plugin.
 * Implements CommandExecutor for command execution and TabCompleter for tab completion.
 */
public class AutoInformCommandExecutor implements CommandExecutor, TabCompleter {

 private final AlexxAutoWarn plugin;
 private final ZoneManager zoneManager;
 private final MessageUtil messageUtil;

 // NamespacedKey for storing wand selection data on player's persistent data container
 private final NamespacedKey wandSelectionKey;

 /**
  * Constructor for AutoInformCommandExecutor.
  *
  * @param plugin The main plugin instance.
  */
 public AutoInformCommandExecutor(AlexxAutoWarn plugin) {
  this.plugin = plugin;
  this.zoneManager = plugin.getZoneManager();
  this.messageUtil = plugin.getMessageUtil();
  // Initialize NamespacedKey for wand selections
  this.wandSelectionKey = new NamespacedKey(plugin, "autoinform_wand_selections");
 }

 /**
  * Executes the given command.
  *
  * @param sender The sender of the command (Player, ConsoleSender, etc.)
  * @param command The command that was executed.
  * @param label The alias of the command used.
  * @param args The arguments passed to the command.
  * @return true if the command was handled successfully, false otherwise.
  */
 @Override
 public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
  // Ensure the sender has the base permission to use any 'autoinform' command
  if (!sender.hasPermission("autoinform.admin.set")) {
   messageUtil.sendMessage(sender, "error-no-permission");
   return true;
  }

  if (args.length == 0) {
   // If no arguments, show general help
   messageUtil.sendMessage(sender, "main-help-header");
   messageUtil.sendMessage(sender, "main-help-wand", "{command}", label);
   messageUtil.sendMessage(sender, "main-help-pos1", "{command}", label);
   messageUtil.sendMessage(sender, "main-help-pos2", "{command}", label);
   messageUtil.sendMessage(sender, "main-help-define", "{command}", label);
   messageUtil.sendMessage(sender, "main-help-defaultaction", "{command}", label);
   messageUtil.sendMessage(sender, "main-help-setaction", "{command}", label);
   messageUtil.sendMessage(sender, "main-help-remove", "{command}", label);
   messageUtil.sendMessage(sender, "main-help-info", "{command}", label);
   messageUtil.sendMessage(sender, "main-help-list", "{command}", label);
   messageUtil.sendMessage(sender, "main-help-clearwand", "{command}", label);
   messageUtil.sendMessage(sender, "main-help-reload", "{command}", label);
   messageUtil.sendMessage(sender, "main-help-banned", "{command}", label);
   return true;
  }

  String subCommand = args[0].toLowerCase();

  switch (subCommand) {
   case "wand":
    handleWandCommand(sender);
    break;
   case "pos1":
   case "pos2":
    handlePosCommand(sender, args, subCommand);
    break;
   case "define":
    handleDefineCommand(sender, args);
    break;
   case "defaultaction":
    handleDefaultActionCommand(sender, args);
    break;
   case "setaction":
    handleSetActionCommand(sender, args);
    break;
   case "remove":
    handleRemoveCommand(sender, args);
    break;
   case "info":
    handleInfoCommand(sender, args);
    break;
   case "list":
    handleListCommand(sender);
    break;
   case "clearwand":
    handleClearWandCommand(sender);
    break;
   case "reload":
    handleReloadCommand(sender);
    break;
   case "banned":
    handleBannedCommand(sender, args);
    break;
   default:
    messageUtil.sendMessage(sender, "error-unknown-subcommand", "{command}", label);
    break;
  }
  return true;
 }

 /**
  * Provides tab completions for the command.
  *
  * @param sender The sender of the command.
  * @param command The command that was executed.
  * @param label The alias of the command used.
  * @param args The arguments passed to the command.
  * @return A list of possible tab completions.
  */
 @Override
 public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
  if (!sender.hasPermission("autoinform.admin.set")) {
   return Collections.emptyList(); // No completion if no permission
  }

  List<String> completions = new ArrayList<>();
  if (args.length == 1) {
   // First argument: subcommands
   StringUtil.copyPartialMatches(args[0], Arrays.asList(
           "wand", "pos1", "pos2", "define", "defaultaction", "setaction",
           "remove", "info", "list", "clearwand", "reload", "banned"
   ), completions);
  } else if (args.length >= 2) {
   String subCommand = args[0].toLowerCase();
   switch (subCommand) {
    case "pos1":
    case "pos2":
    case "define":
    case "defaultaction":
    case "setaction":
    case "remove":
    case "info":
     // Second argument for these commands is zone name
     StringUtil.copyPartialMatches(args[1], zoneManager.getZoneNames(), completions);
     break;
    case "banned":
     if (args.length == 2) {
      StringUtil.copyPartialMatches(args[1], Arrays.asList("add", "remove", "list"), completions);
     } else if (args.length == 3 && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
      // Third argument for 'banned add/remove' is material name
      StringUtil.copyPartialMatches(args[2], Arrays.stream(Material.values())
              .map(Enum::name)
              .collect(Collectors.toList()), completions);
     }
     break;
   }

   if (args.length >= 3) {
    switch (subCommand) {
     case "defaultaction":
      if (args.length == 3) {
       // Third argument for 'defaultaction' is ZoneAction (DENY, ALERT, ALLOW)
       StringUtil.copyPartialMatches(args[2], Arrays.asList("DENY", "ALERT", "ALLOW"), completions);
      }
      break;
     case "setaction":
      if (args.length == 3) {
       // Third argument for 'setaction' is Material
       StringUtil.copyPartialMatches(args[2], Arrays.stream(Material.values())
               .map(Enum::name)
               .collect(Collectors.toList()), completions);
      } else if (args.length == 4) {
       // Fourth argument for 'setaction' is ZoneAction
       StringUtil.copyPartialMatches(args[3], Arrays.asList("DENY", "ALERT", "ALLOW"), completions);
      }
      break;
    }
   }
  }
  Collections.sort(completions); // Sort completions alphabetically
  return completions;
 }

 /**
  * Handles the '/autoinform wand' command. Gives the player the selection wand.
  *
  * @param sender The command sender.
  */
 private void handleWandCommand(CommandSender sender) {
  if (!(sender instanceof Player player)) {
   messageUtil.sendMessage(sender, "error-player-only-command");
   return;
  }

  ItemStack wand = new ItemStack(Material.BLAZE_ROD);
  ItemMeta meta = wand.getItemMeta();
  if (meta != null) {
   // Deprecated setDisplayName/setLore are used here, but still functional.
   meta.setDisplayName(MessageUtil.colorize("&6&lAutoInform Wand"));
   meta.setLore(Collections.singletonList(MessageUtil.colorize("&eRight-Click: Set Pos1 | Left-Click: Set Pos2")));
   // Add a persistent tag to identify this as the AutoInform wand
   meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "autoinform_wand"), PersistentDataType.BYTE, (byte) 1);
   wand.setItemMeta(meta);
  }
  player.getInventory().addItem(wand);
  messageUtil.sendMessage(player, "wand-given");
 }

 /**
  * Handles the '/autoinform pos1/pos2 <zone_name>' commands. Manually sets zone corners.
  *
  * @param sender The command sender.
  * @param args The command arguments.
  * @param subCommand The specific subcommand ("pos1" or "pos2").
  */
 private void handlePosCommand(CommandSender sender, String[] args, String subCommand) {
  if (!(sender instanceof Player player)) {
   messageUtil.sendMessage(sender, "error-player-only-command");
   return;
  }
  if (args.length < 2) {
   messageUtil.sendMessage(player, "error-usage-pos", "{command}", args[0]);
   return;
  }

  String zoneName = args[1];
  Location playerLocation = player.getLocation();

  // Store location in player's persistent data container
  String selectionTag = (subCommand.equals("pos1") ? "pos1" : "pos2");
  String locationString = String.format("%s,%.2f,%.2f,%.2f",
          playerLocation.getWorld().getName(),
          playerLocation.getX(),
          playerLocation.getY(),
          playerLocation.getZ());

  // Get existing selections map or create a new one, passing the plugin instance
  String selectionsJson = player.getPersistentDataContainer().get(wandSelectionKey, PersistentDataType.STRING);
  PlayerSelections selections = PlayerSelections.fromJson(selectionsJson, plugin); // Pass plugin here

  selections.addSelection(zoneName, selectionTag, locationString);
  player.getPersistentDataContainer().set(wandSelectionKey, PersistentDataType.STRING, selections.toJson());

  messageUtil.sendMessage(player, "pos-set", "{zone_name}", zoneName, "{position}", selectionTag.toUpperCase(), "{location}", String.format("%.0f, %.0f, %.0f", playerLocation.getX(), playerLocation.getY(), playerLocation.getZ()));
 }

 /**
  * Handles the '/autoinform define <zone_name>' command. Defines or updates a zone.
  *
  * @param sender The command sender.
  */
 private void handleDefineCommand(CommandSender sender, String[] args) {
  if (!(sender instanceof Player player)) {
   messageUtil.sendMessage(sender, "error-player-only-command");
   return;
  }
  if (args.length < 2) {
   messageUtil.sendMessage(player, "error-usage-define", "{command}", args[0]);
   return;
  }

  String zoneName = args[1];

  // Retrieve selections from player's persistent data container, passing the plugin instance
  String selectionsJson = player.getPersistentDataContainer().get(wandSelectionKey, PersistentDataType.STRING);
  PlayerSelections selections = PlayerSelections.fromJson(selectionsJson, plugin); // Pass plugin here

  Location pos1 = selections.getSelection(zoneName, "pos1", plugin);
  Location pos2 = selections.getSelection(zoneName, "pos2", plugin);

  if (pos1 == null || pos2 == null) {
   messageUtil.sendMessage(player, "error-define-no-selection", "{zone_name}", zoneName);
   return;
  }

  if (!pos1.getWorld().equals(pos2.getWorld())) {
   messageUtil.sendMessage(player, "error-define-different-worlds");
   return;
  }

  // Define/update the zone in ZoneManager and save to config
  zoneManager.defineZone(zoneName, pos1, pos2);
  messageUtil.sendMessage(player, "zone-defined", "{zone_name}", zoneName);

  // Clear selections for this zone after definition
  selections.clearZoneSelections(zoneName);
  player.getPersistentDataContainer().set(wandSelectionKey, PersistentDataType.STRING, selections.toJson());
  messageUtil.sendMessage(player, "wand-selection-cleared-for-zone", "{zone_name}", zoneName);
 }

 /**
  * Handles the '/autoinform defaultaction <zone_name> <action>' command.
  * Sets the default action for a zone.
  *
  * @param sender The command sender.
  * @param args The command arguments.
  */
 private void handleDefaultActionCommand(CommandSender sender, String[] args) {
  if (args.length < 3) {
   messageUtil.sendMessage(sender, "error-usage-defaultaction", "{command}", args[0]);
   return;
  }

  String zoneName = args[1];
  String actionString = args[2].toUpperCase();

  try {
   ZoneAction action = ZoneAction.valueOf(actionString);
   if (!zoneManager.setZoneDefaultAction(zoneName, action)) {
    messageUtil.sendMessage(sender, "error-zone-not-found", "{zone_name}", zoneName);
    return;
   }
   messageUtil.sendMessage(sender, "default-action-set", "{zone_name}", zoneName, "{action}", action.name());
  } catch (IllegalArgumentException e) {
   messageUtil.sendMessage(sender, "error-invalid-action", "{action}", actionString);
  }
 }

 /**
  * Handles the '/autoinform setaction <zone_name> <material> <action>' command.
  * Sets a material-specific action for a zone.
  *
  * @param sender The command sender.
  * @param args The command arguments.
  */
 private void handleSetActionCommand(CommandSender sender, String[] args) {
  if (args.length < 4) {
   messageUtil.sendMessage(sender, "error-usage-setaction", "{command}", args[0]);
   return;
  }

  String zoneName = args[1];
  String materialString = args[2].toUpperCase();
  String actionString = args[3].toUpperCase();

  try {
   Material material = Material.valueOf(materialString);
   ZoneAction action = ZoneAction.valueOf(actionString);

   if (!zoneManager.setZoneMaterialAction(zoneName, material, action)) {
    messageUtil.sendMessage(sender, "error-zone-not-found", "{zone_name}", zoneName);
    return;
   }
   messageUtil.sendMessage(sender, "material-action-set", "{zone_name}", zoneName, "{material}", material.name(), "{action}", action.name());
  } catch (IllegalArgumentException e) {
   messageUtil.sendMessage(sender, "error-invalid-material-or-action", "{material}", materialString, "{action}", actionString);
  }
 }

 /**
  * Handles the '/autoinform remove <zone_name>' command. Removes a defined zone.
  *
  * @param sender The command sender.
  */
 private void handleRemoveCommand(CommandSender sender, String[] args) {
  if (args.length < 2) {
   messageUtil.sendMessage(sender, "error-usage-remove", "{command}", args[0]);
   return;
  }

  String zoneName = args[1];
  if (zoneManager.removeZone(zoneName)) {
   messageUtil.sendMessage(sender, "zone-removed", "{zone_name}", zoneName);
  } else {
   messageUtil.sendMessage(sender, "error-zone-not-found", "{zone_name}", zoneName);
  }
 }

 /**
  * Handles the '/autoinform info [zone_name]' command. Displays information about a zone.
  *
  * @param sender The command sender.
  */
 private void handleInfoCommand(CommandSender sender, String[] args) {
  if (args.length == 1) {
   // No zone name specified, show info about all zones
   Set<String> zoneNames = zoneManager.getZoneNames();
   if (zoneNames.isEmpty()) {
    messageUtil.sendMessage(sender, "plugin-no-zones-defined");
    return;
   }
   messageUtil.sendMessage(sender, "info-header-all");
   for (String name : zoneNames) {
    AutoInformZone zone = zoneManager.getZone(name);
    if (zone != null) {
     messageUtil.sendMessage(sender, "info-list-entry",
             "{zone_name}", zone.getName(),
             "{world}", zone.getWorld().getName(),
             "{corner1}", String.format("%.0f,%.0f,%.0f", zone.getCorner1().getX(), zone.getCorner1().getY(), zone.getCorner1().getZ()),
             "{corner2}", String.format("%.0f,%.0f,%.0f", zone.getCorner2().getX(), zone.getCorner2().getY(), zone.getCorner2().getZ()),
             "{default_action}", zone.getDefaultAction().name());
    }
   }
  } else {
   // Specific zone info requested
   String zoneName = args[1];
   AutoInformZone zone = zoneManager.getZone(zoneName);
   if (zone == null) {
    messageUtil.sendMessage(sender, "error-zone-not-found", "{zone_name}", zoneName);
    return;
   }
   messageUtil.sendMessage(sender, "info-header-single", "{zone_name}", zone.getName());
   messageUtil.sendMessage(sender, "info-name", "{zone_name}", zone.getName());
   messageUtil.sendMessage(sender, "info-world", "{world}", zone.getWorld().getName());
   messageUtil.sendMessage(sender, "info-corner1", "{corner1}", String.format("%.2f,%.2f,%.2f", zone.getCorner1().getX(), zone.getCorner1().getY(), zone.getCorner1().getZ()));
   messageUtil.sendMessage(sender, "info-corner2", "{corner2}", String.format("%.2f,%.2f,%.2f", zone.getCorner2().getX(), zone.getCorner2().getY(), zone.getCorner2().getZ()));
   messageUtil.sendMessage(sender, "info-default-action", "{action}", zone.getDefaultAction().name());
   if (!zone.getMaterialSpecificActions().isEmpty()) {
    messageUtil.sendMessage(sender, "info-material-actions-header");
    zone.getMaterialSpecificActions().forEach((material, action) ->
            messageUtil.sendMessage(sender, "info-material-action-entry", "{material}", material.name(), "{action}", action.name()));
   } else {
    messageUtil.sendMessage(sender, "info-no-material-actions");
   }
  }
 }

 /**
  * Handles the '/autoinform list' command. Lists all defined zones.
  *
  * @param sender The command sender.
  */
 private void handleListCommand(CommandSender sender) {
  Set<String> zoneNames = zoneManager.getZoneNames();
  if (zoneNames.isEmpty()) {
   messageUtil.sendMessage(sender, "plugin-no-zones-defined");
  } else {
   messageUtil.sendMessage(sender, "list-header");
   zoneNames.forEach(name -> messageUtil.sendMessage(sender, "list-entry", "{zone_name}", name));
  }
 }

 /**
  * Handles the '/autoinform clearwand' command. Clears player's wand selections.
  *
  * @param sender The command sender.
  */
 private void handleClearWandCommand(CommandSender sender) {
  if (!(sender instanceof Player player)) {
   messageUtil.sendMessage(sender, "error-player-only-command");
   return;
  }
  player.getPersistentDataContainer().remove(wandSelectionKey);
  messageUtil.sendMessage(player, "wand-selections-cleared");
 }

 /**
  * Handles the '/autoinform reload' command. Reloads configuration.
  *
  * @param sender The command sender.
  */
 private void handleReloadCommand(CommandSender sender) {
  plugin.reloadConfig(); // Reload the config.yml file
  zoneManager.loadZonesFromConfig(); // Reload zones from the reloaded config
  messageUtil.loadMessages(); // Reload messages from messages.yml
  messageUtil.sendMessage(sender, "plugin-config-reloaded");
 }

 /**
  * Handles the '/autoinform banned <add|remove|list> [material]' command.
  * Manages the global list of banned materials.
  *
  * @param sender The command sender.
  * @param args The command arguments.
  */
 private void handleBannedCommand(CommandSender sender, String[] args) {
  if (args.length < 2) {
   messageUtil.sendMessage(sender, "error-usage-banned", "{command}", args[0]);
   return;
  }

  String action = args[1].toLowerCase();
  Set<Material> bannedMaterials = zoneManager.getGloballyBannedMaterials();

  switch (action) {
   case "add":
    if (args.length < 3) {
     messageUtil.sendMessage(sender, "error-usage-banned-add");
     return;
    }
    try {
     Material material = Material.valueOf(args[2].toUpperCase());
     if (zoneManager.addGloballyBannedMaterial(material)) {
      messageUtil.sendMessage(sender, "banned-material-added", "{material}", material.name());
     } else {
      messageUtil.sendMessage(sender, "banned-material-already-added", "{material}", material.name());
     }
    } catch (IllegalArgumentException e) {
     messageUtil.sendMessage(sender, "error-invalid-material", "{material}", args[2]);
    }
    break;
   case "remove":
    if (args.length < 3) {
     messageUtil.sendMessage(sender, "error-usage-banned-remove");
     return;
    }
    try {
     Material material = Material.valueOf(args[2].toUpperCase());
     if (zoneManager.removeGloballyBannedMaterial(material)) {
      messageUtil.sendMessage(sender, "banned-material-removed", "{material}", material.name());
     } else {
      messageUtil.sendMessage(sender, "banned-material-not-found", "{material}", material.name());
     }
    } catch (IllegalArgumentException e) {
     messageUtil.sendMessage(sender, "error-invalid-material", "{material}", args[2]);
    }
    break;
   case "list":
    if (bannedMaterials.isEmpty()) {
     messageUtil.sendMessage(sender, "plugin-no-banned-materials");
    } else {
     messageUtil.sendMessage(sender, "plugin-current-banned-materials",
             "{materials}", bannedMaterials.stream().map(Enum::name).collect(Collectors.joining(", ")));
    }
    break;
   default:
    messageUtil.sendMessage(sender, "error-usage-banned", "{command}", args[0]);
    break;
  }
 }

 /**
  * Helper class to manage player's wand selections using JSON for persistent data.
  */
 private static class PlayerSelections {
  // Map: ZoneName -> Map: "pos1" or "pos2" -> LocationString (e.g., "world,X.Y,Z")
  private final java.util.Map<String, java.util.Map<String, String>> selections;

  private PlayerSelections() {
   this.selections = new java.util.HashMap<>();
  }

  /**
   * Parses a JSON string into a PlayerSelections object.
   *
   * @param json The JSON string representation of selections.
   * @param plugin The main plugin instance (used for logging errors).
   * @return A PlayerSelections object, or an empty one if JSON is null or invalid.
   */
  public static PlayerSelections fromJson(@Nullable String json, AlexxAutoWarn plugin) {
   PlayerSelections playerSelections = new PlayerSelections();
   if (json == null || json.isEmpty() || json.equals("{}")) {
    return playerSelections;
   }
   try {
    // Manually parse JSON to a Map<String, Map<String, String>>
    // This is a simplified parser for expected JSON structure
    // For more complex JSON, a library like Gson or Jackson would be better
    json = json.trim();
    if (json.startsWith("{") && json.endsWith("}")) {
     String content = json.substring(1, json.length() - 1);
     String[] zoneEntries = content.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"); // Split by comma outside quotes

     for (String zoneEntry : zoneEntries) {
      zoneEntry = zoneEntry.trim();
      if (zoneEntry.isEmpty()) continue;

      int firstColon = zoneEntry.indexOf(':');
      if (firstColon == -1) continue;

      String zoneNameKey = zoneEntry.substring(0, firstColon).trim();
      if (!zoneNameKey.startsWith("\"") || !zoneNameKey.endsWith("\"")) continue; // Must be quoted
      String zoneName = zoneNameKey.substring(1, zoneNameKey.length() - 1);

      String zoneValuePart = zoneEntry.substring(firstColon + 1).trim();
      if (!zoneValuePart.startsWith("{") || !zoneValuePart.endsWith("}")) continue;

      String selectionContent = zoneValuePart.substring(1, zoneValuePart.length() - 1);
      String[] posEntries = selectionContent.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"); // Split by comma outside quotes

      java.util.Map<String, String> zoneSelections = new java.util.HashMap<>();
      for (String posEntry : posEntries) {
       posEntry = posEntry.trim();
       if (posEntry.isEmpty()) continue;

       int posColon = posEntry.indexOf(':');
       if (posColon == -1) continue;

       String posKey = posEntry.substring(0, posColon).trim();
       if (!posKey.startsWith("\"") || !posKey.endsWith("\"")) continue;
       String posType = posKey.substring(1, posKey.length() - 1);

       String locValue = posEntry.substring(posColon + 1).trim();
       if (!locValue.startsWith("\"") || !locValue.endsWith("\"")) continue;
       String locationString = locValue.substring(1, locValue.length() - 1);

       zoneSelections.put(posType, locationString);
      }
      playerSelections.selections.put(zoneName, zoneSelections);
     }
    }
   } catch (Exception e) {
    // Now accessing getLogger() through the passed plugin instance
    plugin.getLogger().log(java.util.logging.Level.WARNING, "Failed to parse player selections JSON: " + json, e);
    return new PlayerSelections(); // Return empty on error
   }
   return playerSelections;
  }

  /**
   * Converts the PlayerSelections object to a JSON string.
   * This is a simple manual JSON serializer. For complex objects, use a library.
   *
   * @return JSON string representation.
   */
  public String toJson() {
   StringBuilder sb = new StringBuilder();
   sb.append("{");
   boolean firstZone = true;
   for (java.util.Map.Entry<String, java.util.Map<String, String>> zoneEntry : selections.entrySet()) {
    if (!firstZone) {
     sb.append(",");
    }
    sb.append("\"").append(zoneEntry.getKey()).append("\":{");
    boolean firstPos = true;
    for (java.util.Map.Entry<String, String> posEntry : zoneEntry.getValue().entrySet()) {
     if (!firstPos) {
      sb.append(",");
     }
     sb.append("\"").append(posEntry.getKey()).append("\":\"").append(posEntry.getValue()).append("\"");
     firstPos = false;
    }
    sb.append("}");
    firstZone = false;
   }
   sb.append("}");
   return sb.toString();
  }

  /**
   * Adds a selection (pos1 or pos2) for a given zone.
   *
   * @param zoneName The name of the zone.
   * @param type The type of selection ("pos1" or "pos2").
   * @param locationString The location string (e.g., "world,X.Y,Z").
   */
  public void addSelection(String zoneName, String type, String locationString) {
   selections.computeIfAbsent(zoneName, k -> new java.util.HashMap<>()).put(type, locationString);
  }

  /**
   * Retrieves a selection for a given zone and type.
   *
   * @param zoneName The name of the zone.
   * @param type The type of selection ("pos1" or "pos2").
   * @param plugin The main plugin instance for world lookup.
   * @return The Location object, or null if not found or world is invalid.
   */
  @Nullable
  public Location getSelection(String zoneName, String type, AlexxAutoWarn plugin) {
   java.util.Map<String, String> zoneSelections = selections.get(zoneName);
   if (zoneSelections == null) return null;

   String locationString = zoneSelections.get(type);
   if (locationString == null) return null;

   try {
    String[] parts = locationString.split(",");
    if (parts.length != 4) return null;

    String worldName = parts[0];
    double x = Double.parseDouble(parts[1]);
    double y = Double.parseDouble(parts[2]);
    double z = Double.parseDouble(parts[3]);

    org.bukkit.World world = Bukkit.getWorld(worldName);
    if (world == null) {
     plugin.getLogger().warning("World '" + worldName + "' not found for wand selection of zone '" + zoneName + "'.");
     return null;
    }
    return new Location(world, x, y, z);
   } catch (NumberFormatException e) {
    plugin.getLogger().log(java.util.logging.Level.WARNING, "Invalid location string format in wand selection: " + locationString, e);
    return null;
   }
  }

  /**
   * Clears all selections for a specific zone.
   *
   * @param zoneName The name of the zone to clear selections for.
   */
  public void clearZoneSelections(String zoneName) {
   selections.remove(zoneName);
  }
 }

 /**
  * Utility class for string operations, specifically for tab completion.
  * Moved from org.bukkit.command.defaults.StringUtil.
  */
 private static class StringUtil {
  public static <T extends Collection<? super String>> T copyPartialMatches(@NotNull final String token, @NotNull final Iterable<String> completions, @NotNull final T collection) {
   for (String completion : completions) {
    if (startsWithIgnoreCase(completion, token)) {
     collection.add(completion);
    }
   }
   return collection;
  }

  public static boolean startsWithIgnoreCase(@NotNull final String string, @NotNull final String prefix) {
   return string.length() >= prefix.length() && string.regionMatches(true, 0, prefix, 0, prefix.length());
  }
 }
}