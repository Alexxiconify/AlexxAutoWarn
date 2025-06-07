package net.Alexxiconify.alexxAutoWarn.commands;

import net.Alexxiconify.alexxAutoWarn.AlexxAutoWarn;
import net.Alexxiconify.alexxAutoWarn.managers.ZoneManager;
import net.Alexxiconify.alexxAutoWarn.objects.AutoInformZone;
import net.Alexxiconify.alexxAutoWarn.objects.ZoneAction;
import net.Alexxiconify.alexxAutoWarn.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
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
 // Regex pattern for validating material names
 private static final Pattern MATERIAL_PATTERN = Pattern.compile("^[A-Z_]+$");

 // NamespacedKey for storing wand selection data on player's persistent data container
 private final NamespacedKey wandSelectionKey;
 private final ConcurrentHashMap<java.util.UUID, PlayerSelections> playerSelectionsMap;

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
  this.playerSelectionsMap = new ConcurrentHashMap<>();
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

  if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
   sendHelpMessage(sender);
   return true;
  }

  String subCommand = args[0].toLowerCase();

  // Commands that require a player sender
  if (!(sender instanceof Player) && !subCommand.equalsIgnoreCase("reload") && !subCommand.equalsIgnoreCase("debug")) {
   messageUtil.sendMessage(sender, "error-player-only-command");
   return true;
  }

  Player player = (sender instanceof Player) ? (Player) sender : null;

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
   case "togglechestmonitor":
    handleToggleChestMonitorCommand(sender);
    break;
   case "debug":
    handleDebugCommand(sender);
    break;
   default:
    messageUtil.sendMessage(sender, "error-unknown-subcommand", "{command}", label);
    sendHelpMessage(sender);
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
 public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
  if (!sender.hasPermission("autoinform.admin.set") && !args[0].equalsIgnoreCase("help")) {
   return Collections.emptyList(); // No completion if no permission, unless asking for help
  }
  // If console and not reload/debug/help, return empty list (console can't use pos1/pos2 etc.)
  if (!(sender instanceof Player) && args.length > 0 && !(args[0].equalsIgnoreCase("reload") || args[0].equalsIgnoreCase("debug") || args[0].equalsIgnoreCase("help"))) {
   return Collections.emptyList();
  }

  List<String> completions = new ArrayList<>();
  if (args.length == 1) {
   // Main subcommands
   if (sender.hasPermission("autoinform.command.wand")) completions.add("wand");
   if (sender.hasPermission("autoinform.command.pos1")) completions.add("pos1");
   if (sender.hasPermission("autoinform.command.pos2")) completions.add("pos2");
   if (sender.hasPermission("autoinform.command.define")) completions.add("define");
   if (sender.hasPermission("autoinform.command.defaultaction")) completions.add("defaultaction");
   if (sender.hasPermission("autoinform.command.setaction")) completions.add("setaction");
   if (sender.hasPermission("autoinform.command.remove")) completions.add("remove");
   if (sender.hasPermission("autoinform.command.info")) completions.add("info");
   if (sender.hasPermission("autoinform.command.list")) completions.add("list");
   if (sender.hasPermission("autoinform.command.clearwand")) completions.add("clearwand");
   if (sender.hasPermission("autoinform.command.reload")) completions.add("reload");
   if (sender.hasPermission("autoinform.command.banned")) completions.add("banned");
   if (sender.hasPermission("autoinform.command.togglechestmonitor")) completions.add("togglechestmonitor");
   if (sender.hasPermission("autoinform.command.debug")) completions.add("debug");
   completions.add("help"); // Always suggest help

   return completions.stream()
           .filter(s -> s.startsWith(args[0].toLowerCase()))
           .collect(Collectors.toList());

  } else if (args.length >= 2) {
   String subCommand = args[0].toLowerCase();
   String zoneName = args.length > 2 ? args[2] : "";
   String materialName = args.length > 3 ? args[3] : "";

   switch (subCommand) {
    case "define":
     if (args.length == 2) { // Expected zone name
      // No completions here as define takes a new zone name
     } else if (args.length == 3) { // Expected default action
      return Arrays.stream(ZoneAction.values())
              .map(Enum::name)
              .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
              .collect(Collectors.toList());
     }
     break;
    case "defaultaction":
    case "setaction":
    case "remove":
    case "info":
     if (args.length == 2) { // Second argument is zone name
      return zoneManager.getZoneNames().stream()
              .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
              .collect(Collectors.toList());
     }
     if (subCommand.equals("defaultaction")) {
      if (args.length == 3) { // Third argument is ZoneAction
       return Arrays.stream(ZoneAction.values())
               .map(Enum::name)
               .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
               .collect(Collectors.toList());
      }
     }
     if (subCommand.equals("setaction")) {
      if (args.length == 3) { // Third argument is Material
       return Arrays.stream(Material.values())
               .map(Enum::name)
               .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
               .collect(Collectors.toList());
      } else if (args.length == 4) { // Fourth argument is ZoneAction
       return Arrays.stream(ZoneAction.values())
               .map(Enum::name)
               .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
               .collect(Collectors.toList());
      }
     }
     if (subCommand.equals("remove") && args.length == 3) {
      // Third arg for remove material action from zone
      // Only suggest materials that actually have specific actions in that zone
      AutoInformZone zone = zoneManager.getZone(args[1]);
      if (zone != null) {
       return zone.getMaterialSpecificActions().keySet().stream()
               .map(Enum::name)
               .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
               .collect(Collectors.toList());
      }
     }
     break;
    case "banned":
     if (args.length == 2) { // Second argument for 'banned' is sub-action (add, remove, list)
      StringUtil.copyPartialMatches(args[1], Arrays.asList("add", "remove", "list"), completions);
     } else if (args.length == 3 && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
      // Third argument for 'banned add/remove' is material name
      StringUtil.copyPartialMatches(args[2], Arrays.stream(Material.values())
              .map(Enum::name)
              .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
              .collect(Collectors.toList()), completions);
     }
     break;
   }
  }
  Collections.sort(completions);
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
  plugin.giveAutoInformWand(player);
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

  // Retrieve selections from player's persistent data container
  String selectionsJson = player.getPersistentDataContainer().get(wandSelectionKey, PersistentDataType.STRING);
  PlayerSelections selections = PlayerSelections.fromJson(selectionsJson, plugin);

  selections.addSelection(zoneName, subCommand.equals("pos1") ? "pos1" : "pos2",
          String.format("%s,%.2f,%.2f,%.2f", playerLocation.getWorld().getName(), playerLocation.getX(), playerLocation.getY(), playerLocation.getZ()));

  player.getPersistentDataContainer().set(wandSelectionKey, PersistentDataType.STRING, selections.toJson());

  messageUtil.sendMessage(player, "pos-set", "{zone_name}", zoneName, "{position}", subCommand.toUpperCase(),
          "{location}", String.format("%.0f, %.0f, %.0f", playerLocation.getX(), playerLocation.getY(), playerLocation.getZ()));
 }

 /**
  * Handles the '/autoinform define <zone_name> <default_action>' command. Defines or updates a zone.
  * This command now also takes a default action when defining.
  *
  * @param sender The command sender.
  */
 private void handleDefineCommand(CommandSender sender, String[] args) {
  if (!(sender instanceof Player player)) {
   messageUtil.sendMessage(sender, "error-player-only-command");
   return;
  }
  if (args.length < 3) { // Expecting /define <zone_name> <default_action>
   messageUtil.sendMessage(player, "error-usage-define", "{command}", args[0]);
   return;
  }

  String zoneName = args[1];
  String defaultActionString = args[2].toUpperCase();

  ZoneAction defaultAction;
  try {
   defaultAction = ZoneAction.valueOf(defaultActionString);
  } catch (IllegalArgumentException e) {
   messageUtil.sendMessage(player, "error-invalid-action", "{action}", defaultActionString);
   return;
  }


  // Retrieve selections from player's persistent data container
  String selectionsJson = player.getPersistentDataContainer().get(wandSelectionKey, PersistentDataType.STRING);
  PlayerSelections selections = PlayerSelections.fromJson(selectionsJson, plugin);

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
  // Corrected: Pass the defaultAction argument
  zoneManager.defineZone(zoneName, pos1, pos2, defaultAction);
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
  * Handles the '/autoinform remove <zone_name> [material]' command.
  * Can remove an entire zone or a material-specific action from a zone.
  *
  * @param sender The command sender.
  * @param args The command arguments.
  */
 private void handleRemoveCommand(CommandSender sender, String[] args) {
  if (args.length < 2) {
   messageUtil.sendMessage(sender, "error-usage-remove", "{command}", args[0]);
   return;
  }

  String zoneName = args[1];

  if (args.length == 2) {
   // Remove the entire zone
   if (zoneManager.removeZone(zoneName)) {
    messageUtil.sendMessage(sender, "zone-removed", "{zone_name}", zoneName);
   } else {
    messageUtil.sendMessage(sender, "error-zone-not-found", "{zone_name}", zoneName);
   }
  } else if (args.length == 3) {
   // Remove a material-specific action from a zone
   String materialString = args[2].toUpperCase();
   try {
    Material material = Material.valueOf(materialString);
    if (zoneManager.removeZoneMaterialAction(zoneName, material)) {
     messageUtil.sendMessage(sender, "material-action-removed", "{zone_name}", zoneName, "{material}", material.name());
    } else {
     messageUtil.sendMessage(sender, "error-material-action-not-found", "{zone_name}", zoneName, "{material}", material.name());
    }
   } catch (IllegalArgumentException e) {
    messageUtil.sendMessage(sender, "error-invalid-material", "{material}", materialString);
   }
  } else {
   messageUtil.sendMessage(sender, "error-usage-remove", "{command}", args[0]);
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

  Set<Material> globallyBanned = zoneManager.getGloballyBannedMaterials();
  if (!globallyBanned.isEmpty()) {
   messageUtil.sendMessage(sender, "command-list-global-banned-header");
   messageUtil.sendMessage(sender, "command-list-global-banned-entry",
           "{materials}", globallyBanned.stream().map(Enum::name).collect(Collectors.joining(", ")));
  } else {
   messageUtil.sendMessage(sender, "command-list-no-global-banned");
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
  plugin.reloadConfig();
  zoneManager.loadZonesFromConfig();
  messageUtil.loadMessages();
  messageUtil.sendMessage(sender, "plugin-config-reloaded");
 }

 /**
  * Handles the '/autoinform banned <add|remove|list> [material]' command.
  * Manages the global list of banned materials.
  *
  * @param sender The command sender.
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
  * Handles the 'togglechestmonitor' command. Toggles the global chest monitoring setting.
  * Syntax: /autoinform togglechestmonitor
  *
  * @param sender The sender of the command.
  */
 private void handleToggleChestMonitorCommand(CommandSender sender) {
  boolean currentState = plugin.isMonitorChestAccess();
  plugin.setMonitorChestAccess(!currentState);
  messageUtil.sendMessage(sender, "command-togglechestmonitor-success",
          "{state}", plugin.isMonitorChestAccess() ? "enabled" : "disabled");
 }

 /**
  * Handles the 'debug' command. Toggles debug logging level.
  * Syntax: /autoinform debug
  *
  * @param sender The sender of the command.
  */
 private void handleDebugCommand(CommandSender sender) {
  Level currentLevel = plugin.getLogger().getLevel();
  Level newLevel;
  if (currentLevel == Level.FINE || currentLevel == Level.FINER || currentLevel == Level.FINEST) {
   newLevel = Level.INFO;
   messageUtil.sendMessage(sender, "command-debug-off");
  } else {
   newLevel = Level.FINE;
   messageUtil.sendMessage(sender, "command-debug-on");
  }
  plugin.getLogger().setLevel(newLevel);
 }

 /**
  * Sends the main help message to the sender.
  *
  * @param sender The recipient of the help message.
  */
 private void sendHelpMessage(CommandSender sender) {
  messageUtil.sendMessage(sender, "main-help-header");
  if (sender.hasPermission("autoinform.admin.set")) {
   messageUtil.sendMessage(sender, "main-help-wand");
   messageUtil.sendMessage(sender, "main-help-pos1");
   messageUtil.sendMessage(sender, "main-help-pos2");
   messageUtil.sendMessage(sender, "main-help-define");
   messageUtil.sendMessage(sender, "main-help-defaultaction");
   messageUtil.sendMessage(sender, "main-help-setaction");
   messageUtil.sendMessage(sender, "main-help-remove");
   messageUtil.sendMessage(sender, "main-help-info");
   messageUtil.sendMessage(sender, "main-help-list");
   messageUtil.sendMessage(sender, "main-help-clearwand");
   messageUtil.sendMessage(sender, "main-help-reload");
   messageUtil.sendMessage(sender, "main-help-banned");
   messageUtil.sendMessage(sender, "main-help-togglechestmonitor");
   messageUtil.sendMessage(sender, "main-help-debug");
  }
 }


 /**
  * Inner class to manage player's wand selections using JSON for persistent data.
  * This class needs a reference to the main plugin to access its logger for warnings.
  */
 private static class PlayerSelections {
  private final java.util.Map<String, java.util.Map<String, String>> selections;
  private final AlexxAutoWarn plugin;

  public PlayerSelections(AlexxAutoWarn plugin) {
   this.selections = new java.util.HashMap<>();
   this.plugin = plugin;
  }

  /**
   * Parses a JSON string into a PlayerSelections object.
   *
   * @param json The JSON string representation of selections.
   * @param plugin The main plugin instance (used for logging errors).
   * @return A PlayerSelections object, or an empty one if JSON is null or invalid.
   */
  public static PlayerSelections fromJson(@Nullable String json, AlexxAutoWarn plugin) {
   PlayerSelections playerSelections = new PlayerSelections(plugin);
   if (json == null || json.isEmpty() || json.equals("{}")) {
    return playerSelections;
   }
   try {
    json = json.trim();
    if (json.startsWith("{") && json.endsWith("}")) {
     String content = json.substring(1, json.length() - 1);
     if (content.isEmpty()) {
      return playerSelections;
     }
     String[] zoneEntries = content.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

     for (String zoneEntry : zoneEntries) {
      zoneEntry = zoneEntry.trim();
      if (zoneEntry.isEmpty()) continue;

      int firstColon = zoneEntry.indexOf(':');
      if (firstColon == -1) continue;

      String zoneNameKey = zoneEntry.substring(0, firstColon).trim();
      if (!zoneNameKey.startsWith("\"") || !zoneNameKey.endsWith("\"")) continue;
      String zoneName = zoneNameKey.substring(1, zoneNameKey.length() - 1);

      String zoneValuePart = zoneEntry.substring(firstColon + 1).trim();
      if (!zoneValuePart.startsWith("{") || !zoneValuePart.endsWith("}")) continue;

      String selectionContent = zoneValuePart.substring(1, zoneValuePart.length() - 1);
      if (selectionContent.isEmpty()) {
       playerSelections.selections.put(zoneName, new java.util.HashMap<>());
       continue;
      }

      String[] posEntries = selectionContent.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

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
    plugin.getLogger().log(java.util.logging.Level.WARNING, "Failed to parse player selections JSON: " + json, e);
    return new PlayerSelections(plugin);
   }
   return playerSelections;
  }

  /**
   * Converts the PlayerSelections object to a JSON string.
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
    if (parts.length != 4) {
     plugin.getLogger().warning("Invalid location string format for zone '" + zoneName + "', type '" + type + "': " + locationString);
     return null;
    }

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