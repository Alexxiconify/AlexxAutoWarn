package net.Alexxiconify.alexxAutoWarn.commands;

import net.Alexxiconify.alexxAutoWarn.AlexxAutoWarn;
import net.Alexxiconify.alexxAutoWarn.managers.ZoneManager;
import net.Alexxiconify.alexxAutoWarn.utils.MessageUtil;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
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
   messageUtil.sendMessage