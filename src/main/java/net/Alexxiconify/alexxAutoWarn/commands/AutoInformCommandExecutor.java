package net.Alexxiconify.alexxAutoWarn.commands;

import net.Alexxiconify.alexxAutoWarn.AlexxAutoWarn;
import net.Alexxiconify.alexxAutoWarn.managers.PlayerSelectionManager;
import net.Alexxiconify.alexxAutoWarn.managers.ZoneManager;
import net.Alexxiconify.alexxAutoWarn.objects.AutoInformZone;
import net.Alexxiconify.alexxAutoWarn.objects.ZoneAction;
import net.Alexxiconify.alexxAutoWarn.util.MessageUtil;
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

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Handles all commands for the AlexxAutoWarn plugin.
 * Implements CommandExecutor for command execution and TabCompleter for tab completion.
 */
public class AutoInformCommandExecutor implements CommandExecutor, TabCompleter {

 private final AlexxAutoWarn plugin;
 private final ZoneManager zoneManager;
 private final MessageUtil messageUtil;
 private final PlayerSelectionManager playerSelectionManager;
 private final NamespacedKey wandKey;

 public AutoInformCommandExecutor(AlexxAutoWarn plugin) {
  this.plugin = plugin;
  this.zoneManager = plugin.getZoneManager();
  this.messageUtil = plugin.getMessageUtil();
  this.playerSelectionManager = plugin.getPlayerSelectionManager();
  this.wandKey = plugin.getWandKey();
 }

 @Override
 public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
  if (args.length == 0) {
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

  // Check for player-only commands
  if (!(sender instanceof Player)) {
   if (subCommand.equals("wand") || subCommand.equals("pos1") || subCommand.equals("pos2") || subCommand.equals("define") || subCommand.equals("defaultaction") || subCommand.equals("setaction") || subCommand.equals("remove") || subCommand.equals("clearwand")) {
    messageUtil.sendMessage(sender, "error-player-only-command");
    return true;
   }
  }
  Player player = (sender instanceof Player) ? (Player) sender : null;

  switch (subCommand) {
   case "wand":
    handleWandCommand(player);
    break;
   case "pos1":
   case "pos2":
    handlePosCommand(player, subCommand, args);
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
    handleInfoCommand(sender, args);
    break;
   case "list":
    handleListCommand(sender);
    break;
   case "clearwand":
    handleClearWandCommand(player);
    break;
   case "reload":
    handleReloadCommand(sender);
    break;
   case "banned":
    handleBannedCommand(sender, args);
    break;
   default:
    messageUtil.sendMessage(sender, "error-invalid-command", "{command}", label);
    break;
  }
  return true;
 }

 private void handleWandCommand(@NotNull Player player) {
  plugin.giveWand(player);
  messageUtil.sendMessage(player, "wand-given");
 }

 private void handlePosCommand(@NotNull Player player, String posType, String[] args) {
  if (args.length < 2) {
   messageUtil.sendMessage(player, "error-missing-zone-name");
   return;
  }
  String zoneName = args[1];

  AutoInformZone zone = zoneManager.getZone(zoneName);
  if (zone == null) {
   messageUtil.sendMessage(player, "error-zone-not-found", "{zone_name}", zoneName);
   return;
  }

  Location newCornerLocation = player.getLocation().getBlock().getLocation();

  if (!newCornerLocation.getWorld().equals(zone.getWorld())) {
   messageUtil.sendMessage(player, "error-different-worlds-for-zone-corner", new Object[]{
           "{world}", newCornerLocation.getWorld().getName(),
           "{zone_world}", zone.getWorld().getName()
   });
   return;
  }

  if (posType.equals("pos1")) {
   zone.setCorner1(newCornerLocation);
  } else { // posType.equals("pos2")
   zone.setCorner2(newCornerLocation);
  }
  zoneManager.updateZone(zone);

  messageUtil.sendMessage(player, "zone-corner-set", new Object[]{
          "{zone_name}", zoneName,
          "{pos_type}", posType,
          "{location}", formatLocation(newCornerLocation),
          "{world}", newCornerLocation.getWorld().getName()
  });

  messageUtil.log(Level.FINE, "debug-zone-corner-set",
          "{player}", player.getName(),
          "{zone_name}", zoneName,
          "{pos_type}", posType,
          "{location_string}", formatLocation(newCornerLocation));
 }


 private void handleDefineCommand(@NotNull Player player, String[] args) {
  if (args.length < 2) {
   messageUtil.sendMessage(player, "error-missing-zone-name");
   return;
  }
  String zoneName = args[1];

  Location pos1 = playerSelectionManager.getSelection(player, "pos1");
  Location pos2 = playerSelectionManager.getSelection(player, "pos2");

  if (pos1 == null || pos2 == null) {
   messageUtil.sendMessage(player, "error-selection-not-set");
   return;
  }

  if (!pos1.getWorld().equals(pos2.getWorld())) {
   messageUtil.sendMessage(player, "error-different-worlds");
   return;
  }

  ZoneAction defaultAction = ZoneAction.ALERT;
  if (args.length >= 3) {
   try {
    defaultAction = ZoneAction.valueOf(args[2].toUpperCase());
   } catch (IllegalArgumentException e) {
    messageUtil.sendMessage(player, "error-invalid-action", "{action}", args[2]);
    return;
   }
  }

  Map<Material, ZoneAction> materialActions = Collections.emptyMap();

  AutoInformZone newZone = new AutoInformZone(zoneName, pos1, pos2, defaultAction, materialActions);

  zoneManager.addZone(newZone);
  messageUtil.sendMessage(player, "zone-defined", "{zone_name}", zoneName);
  messageUtil.log(Level.INFO, "debug-define-success",
          "{player}", player.getName(), "{zone_name}", zoneName,
          "{pos1}", String.format("%s [%d, %d, %d]", pos1.getWorld().getName(), pos1.getBlockX(), pos1.getBlockY(), pos1.getBlockZ()),
          "{pos2}", String.format("%s [%d, %d, %d]", pos2.getWorld().getName(), pos2.getBlockX(), pos2.getBlockY(), pos2.getBlockZ()),
          "{default_action}", defaultAction.name());

  playerSelectionManager.clearSelections(player);
 }

 private void handleDefaultActionCommand(@NotNull Player player, String[] args) {
  if (args.length < 3) {
   messageUtil.sendMessage(player, "error-missing-zone-action");
   return;
  }
  String zoneName = args[1];
  String actionString = args[2].toUpperCase();

  AutoInformZone zone = zoneManager.getZone(zoneName);
  if (zone == null) {
   messageUtil.sendMessage(player, "error-zone-not-found", "{zone_name}", zoneName);
   return;
  }

  try {
   ZoneAction newAction = ZoneAction.valueOf(actionString);
   zone.setDefaultAction(newAction);
   zoneManager.updateZone(zone);
   messageUtil.sendMessage(player, "zone-default-action-set",
           "{zone_name}", zoneName, "{action}", newAction.name());
  } catch (IllegalArgumentException e) {
   messageUtil.sendMessage(player, "error-invalid-action", "{action}", actionString);
  }
 }

 private void handleSetActionCommand(@NotNull Player player, String[] args) {
  if (args.length < 4) {
   messageUtil.sendMessage(player, "error-missing-zone-material-action");
   return;
  }
  String zoneName = args[1];
  String materialString = args[2].toUpperCase();
  String actionString = args[3].toUpperCase();

  AutoInformZone zone = zoneManager.getZone(zoneName);
  if (zone == null) {
   messageUtil.sendMessage(player, "error-zone-not-found", "{zone_name}", zoneName);
   return;
  }

  Material material;
  try {
   material = Material.valueOf(materialString);
  } catch (IllegalArgumentException e) {
   messageUtil.sendMessage(player, "error-invalid-material", "{material}", materialString);
   return;
  }

  try {
   ZoneAction action = ZoneAction.valueOf(actionString);
   zone.addMaterialSpecificAction(material, action);
   zoneManager.updateZone(zone);
   messageUtil.sendMessage(player, "zone-material-action-set",
           "{material}", material.name(), "{action}", action.name(), "{zone_name}", zoneName);
  } catch (IllegalArgumentException e) {
   messageUtil.sendMessage(player, "error-invalid-action", "{action}", actionString);
  }
 }

 private void handleRemoveCommand(@NotNull Player player, String[] args) {
  if (args.length < 2) {
   messageUtil.sendMessage(player, "error-missing-zone-name");
   return;
  }
  String zoneName = args[1];

  if (zoneManager.removeZone(zoneName)) {
   messageUtil.sendMessage(player, "zone-removed", "{zone_name}", zoneName);
  } else {
   messageUtil.sendMessage(player, "error-zone-not-found", "{zone_name}", zoneName);
  }
 }

 private void handleInfoCommand(@NotNull CommandSender sender, String[] args) {
  if (args.length < 2) {
   Collection<AutoInformZone> zones = zoneManager.getAllZones();
   if (zones.isEmpty()) {
    messageUtil.sendMessage(sender, "plugin-no-zones-defined");
    return;
   }
   messageUtil.sendMessage(sender, "list-header", "{count}", String.valueOf(zones.size()));
   for (AutoInformZone zone : zones) {
    messageUtil.sendMessage(sender, "list-entry", "{zone_name}", zone.getName());
   }
  } else {
   String zoneName = args[1];
   AutoInformZone zone = zoneManager.getZone(zoneName);
   if (zone == null) {
    messageUtil.sendMessage(sender, "error-zone-not-found", "{zone_name}", zoneName);
    return;
   }

   messageUtil.sendMessage(sender, "info-header", "{zone_name}", zone.getName());
   messageUtil.sendMessage(sender, "info-name", "{zone_name}", zone.getName());
   messageUtil.sendMessage(sender, "info-world", "{world}", zone.getWorld().getName());
   messageUtil.sendMessage(sender, "info-corner1", "{corner1}", formatLocation(zone.getCorner1()));
   messageUtil.sendMessage(sender, "info-corner2", "{corner2}", formatLocation(zone.getCorner2()));
   messageUtil.sendMessage(sender, "info-default-action", "{action}", zone.getDefaultAction().name());

   Map<Material, ZoneAction> materialActions = zone.getMaterialSpecificActions();
   if (materialActions.isEmpty()) {
    messageUtil.sendMessage(sender, "info-no-material-actions");
   } else {
    messageUtil.sendMessage(sender, "info-material-actions-header");
    for (Map.Entry<Material, ZoneAction> entry : materialActions.entrySet()) {
     messageUtil.sendMessage(sender, "info-material-action-entry",
             "{material}", entry.getKey().name(), "{action}", entry.getValue().name());
    }
   }
  }
 }

 private String formatLocation(Location loc) {
  return String.format("[%d, %d, %d]", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
 }

 private void handleListCommand(@NotNull CommandSender sender) {
  Collection<AutoInformZone> zones = zoneManager.getAllZones();
  if (zones.isEmpty()) {
   messageUtil.sendMessage(sender, "plugin-no-zones-defined");
   return;
  }
  messageUtil.sendMessage(sender, "list-header", "{count}", String.valueOf(zones.size()));
  for (AutoInformZone zone : zones) {
   messageUtil.sendMessage(sender, "list-entry", "{zone_name}", zone.getName());
  }
 }

 private void handleClearWandCommand(@NotNull Player player) {
  playerSelectionManager.clearSelections(player);
  messageUtil.sendMessage(player, "wand-selections-cleared");
 }

 private void handleReloadCommand(@NotNull CommandSender sender) {
  plugin.reloadPluginConfig();
  messageUtil.sendMessage(sender, "plugin-config-reloaded");
 }

 private void handleBannedCommand(@NotNull CommandSender sender, String[] args) {
  if (args.length < 2) {
   messageUtil.sendMessage(sender, "error-missing-banned-arg");
   return;
  }
  String action = args[1].toLowerCase();

  switch (action) {
   case "add":
    if (args.length < 3) {
     messageUtil.sendMessage(sender, "error-missing-material");
     return;
    }
    String materialToAdd = args[2].toUpperCase();
    try {
     Material material = Material.valueOf(materialToAdd);
     if (zoneManager.addGloballyBannedMaterial(material)) {
      messageUtil.sendMessage(sender, "banned-material-added", "{material}", material.name());
     } else {
      messageUtil.sendMessage(sender, "error-material-already-banned", "{material}", material.name());
     }
    } catch (IllegalArgumentException e) {
     messageUtil.sendMessage(sender, "error-invalid-material", "{material}", materialToAdd);
    }
    break;
   case "remove":
    if (args.length < 3) {
     messageUtil.sendMessage(sender, "error-missing-material");
     return;
    }
    String materialToRemove = args[2].toUpperCase();
    try {
     Material material = Material.valueOf(materialToRemove);
     if (zoneManager.removeGloballyBannedMaterial(material)) {
      messageUtil.sendMessage(sender, "banned-material-removed", "{material}", material.name());
     } else {
      messageUtil.sendMessage(sender, "error-material-not-banned", "{material}", material.name());
     }
    } catch (IllegalArgumentException e) {
     messageUtil.sendMessage(sender, "error-invalid-material", "{material}", materialToRemove);
    }
    break;
   case "list":
    Set<Material> bannedMaterials = zoneManager.getGloballyBannedMaterials();
    if (bannedMaterials.isEmpty()) {
     messageUtil.sendMessage(sender, "plugin-no-banned-materials");
    } else {
     messageUtil.sendMessage(sender, "plugin-current-banned-materials", "{materials}", plugin.formatMaterialList(bannedMaterials));
    }
    break;
   default:
    messageUtil.sendMessage(sender, "error-invalid-banned-action", "{action}", action);
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
   return Arrays.asList("wand", "pos1", "pos2", "define", "defaultaction", "setaction", "remove", "info", "list", "clearwand", "reload", "banned")
           .stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
  } else if (args.length == 2) {
   String subCommand = args[0].toLowerCase();
   if (Arrays.asList("pos1", "pos2", "define", "defaultaction", "setaction", "remove", "info").contains(subCommand)) {
    return zoneManager.getAllZones().stream()
            .map(AutoInformZone::getName)
            .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
            .collect(Collectors.toList());
   } else if (subCommand.equals("banned")) {
    return Arrays.asList("add", "remove", "list")
            .stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
   }
  } else if (args.length == 3) {
   String subCommand = args[0].toLowerCase();
   if (subCommand.equals("defaultaction")) {
    return Arrays.asList("DENY", "ALERT", "ALLOW")
            .stream().filter(s -> s.startsWith(args[2].toUpperCase())).collect(Collectors.toList());
   } else if (subCommand.equals("setaction")) {
    return Arrays.stream(Material.values())
            .map(Enum::name)
            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
            .collect(Collectors.toList());
   } else if (subCommand.equals("banned")) {
    String bannedAction = args[1].toLowerCase();
    if (bannedAction.equals("add")) {
     return Arrays.stream(Material.values())
             .map(Enum::name)
             .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
             .collect(Collectors.toList());
    } else if (bannedAction.equals("remove")) {
     return zoneManager.getGloballyBannedMaterials().stream()
             .map(Enum::name)
             .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
             .collect(Collectors.toList());
    }
   }
  } else if (args.length == 4) {
   String subCommand = args[0].toLowerCase();
   if (subCommand.equals("setaction")) {
    return Arrays.asList("DENY", "ALERT", "ALLOW")
            .stream().filter(s -> s.startsWith(args[3].toUpperCase())).collect(Collectors.toList());
   }
  }
  return completions;
 }
}