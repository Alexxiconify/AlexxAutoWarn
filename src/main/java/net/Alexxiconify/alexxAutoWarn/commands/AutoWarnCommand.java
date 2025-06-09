package net.alexxiconify.alexxAutoWarn.commands; // Consistent casing: lowercase 'a' in alexxiconify

import com.google.common.collect.ImmutableList;
import net.alexxiconify.alexxAutoWarn.AlexxAutoWarn;
import net.alexxiconify.alexxAutoWarn.managers.ZoneManager;
import net.alexxiconify.alexxAutoWarn.objects.Zone;
import net.alexxiconify.alexxAutoWarn.utils.Settings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.*;
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
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Handles all logic for the /autowarn command using Paper's command API.
 * Implements CommandExecutor for command handling and TabCompleter for tab completion.
 */
public class AutoWarnCommand implements CommandExecutor, TabCompleter {
 private static final Pattern ZONE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,32}$");
 private final Settings settings;
 private final ZoneManager zoneManager;
 private final NamespacedKey wandKey;
 private final Map<UUID, Vector> pos1Selections = new ConcurrentHashMap<>();
 private final Map<UUID, Vector> pos2Selections = new ConcurrentHashMap<>();
 private final AlexxAutoWarn plugin;

 /**
  * Constructs a new AutoWarnCommand.
  *
  * @param plugin The main AlexxAutoWarn plugin instance.
  */
 public AutoWarnCommand(AlexxAutoWarn plugin) {
  this.plugin = plugin;
  this.settings = plugin.getSettings();
  this.zoneManager = plugin.getZoneManager();
  this.wandKey = new NamespacedKey(plugin, "autowarn_wand");
 }

 @Override
 public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
  // Log that the command was received and by whom
  plugin.getLogger().log(Level.INFO, "AutoWarnCommand received: Label='{0}', Args='{1}', Sender='{2}'", new Object[]{label, String.join(" ", args), sender.getName()});

  // Check if the sender is a player
  if (!(sender instanceof Player player)) {
   sender.sendMessage(settings.getMessage("error.player-only"));
   plugin.getLogger().log(Level.INFO, "Command '/{0}' attempted by console/non-player.", label);
   return true;
  }

  // Check base permission for using any autowarn command
  // Note: Specific sub-commands might have their own more granular permission checks below
  if (!player.hasPermission("autowarn.admin.use")) {
   player.sendMessage(settings.getMessage("error.no-permission"));
   plugin.getLogger().log(Level.INFO, "Player '{0}' does not have 'autowarn.admin.use' permission for command '/{1}'.", new Object[]{player.getName(), label});
   return true;
  }

  // If no arguments are provided, send help message
  if (args.length == 0) {
   plugin.getLogger().log(Level.INFO, "Player '{0}' issued '/{1}' with no arguments. Sending help.", new Object[]{player.getName(), label});
   sendHelp(player);
   return true;
  }

  String subCommand = args[0].toLowerCase();
  plugin.getLogger().log(Level.INFO, "Player '{0}' issued subcommand: '{1}'.", new Object[]{player.getName(), subCommand});

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
   default -> {
    plugin.getLogger().log(Level.INFO, "Player '{0}' issued unknown subcommand '{1}'. Sending help.", new Object[]{player.getName(), subCommand});
    sendHelp(player);
   }
  }

  return true;
 }

 // --- Command Handlers ---

 /**
  * Handles the /autowarn wand command, giving the player the selection wand.
  *
  * @param player The player executing the command.
  */
 private void handleWand(Player player) {
  if (!player.hasPermission("autowarn.admin.wand")) {
   player.sendMessage(settings.getMessage("error.no-permission"));
   return;
  }
  ItemStack wand = new ItemStack(Material.BLAZE_ROD);
  ItemMeta meta = wand.getItemMeta();
  // Set display name and lore for the wand
  meta.displayName(settings.getMessage("wand.name"));
  meta.lore(Arrays.asList(
          settings.getMessage("wand.lore1"),
          settings.getMessage("wand.lore2")
  ));
  // Add persistent data to identify the wand
  meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
  wand.setItemMeta(meta);
  player.getInventory().addItem(wand);
  player.sendMessage(settings.getMessage("command.wand-given"));
  plugin.getLogger().log(Level.INFO, "Player '{0}' given AutoWarn wand.", player.getName());
 }

 /**
  * Handles /autowarn pos1 and /autowarn pos2 commands, setting selection points.
  * @param player The player executing the command.
  * @param posNumber The position number (1 or 2).
  */
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
  plugin.getLogger().log(Level.INFO, "Player '{0}' set pos{1} to {2}.", new Object[]{player.getName(), posNumber, formatVector(loc.toVector())});
 }

 /**
  * Handles the /autowarn define <zone_name> command, creating or updating a zone.
  * @param player The player executing the command.
  * @param args Command arguments.
  */
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

  // If zone exists, use its current default action and material actions
  Zone existing = zoneManager.getZone(zoneName);
  Zone.Action defaultAction = existing != null ? existing.getDefaultAction() : Zone.Action.ALERT;
  Map<Material, Zone.Action> materialActions = existing != null ? new EnumMap<>(existing.getMaterialActions()) : new EnumMap<>(Material.class);

  Zone newZone = new Zone(zoneName, player.getWorld(), pos1, pos2, defaultAction, materialActions);
  zoneManager.addOrUpdateZone(newZone);
  player.sendMessage(settings.getMessage("command.define-success", Placeholder.unparsed("zone", zoneName)));
  plugin.getLogger().log(Level.INFO, "Player '{0}' defined zone '{1}'.", new Object[]{player.getName(), zoneName});
 }

 /**
  * Handles the /autowarn remove <zone_name> command, deleting a zone.
  * @param player The player executing the command.
  * @param args Command arguments.
  */
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
   plugin.getLogger().log(Level.INFO, "Player '{0}' removed zone '{1}'.", new Object[]{player.getName(), zoneName});
  } else {
   player.sendMessage(settings.getMessage("error.zone-not-found", Placeholder.unparsed("zone", zoneName)));
   plugin.getLogger().log(Level.INFO, "Player '{0}' failed to remove zone '{1}': not found.", new Object[]{player.getName(), zoneName});
  }
 }

 /**
  * Handles the /autowarn list command, displaying all defined zones.
  * @param player The player executing the command.
  */
 private void handleList(Player player) {
  if (!player.hasPermission("autowarn.admin.list")) {
   player.sendMessage(settings.getMessage("error.no-permission"));
   return;
  }
  Collection<Zone> zones = zoneManager.getAllZones();
  if (zones.isEmpty()) {
   player.sendMessage(settings.getMessage("command.list-empty"));
   plugin.getLogger().log(Level.INFO, "Player '{0}' requested zone list, but no zones defined.", player.getName());
   return;
  }

  player.sendMessage(settings.getMessage("command.list-header", Placeholder.unparsed("count", String.valueOf(zones.size()))));
  for (Zone zone : zones) {
   player.sendMessage(Component.text("- " + zone.getName()).color(NamedTextColor.YELLOW));
  }
  plugin.getLogger().log(Level.INFO, "Player '{0}' requested zone list. Sent {1} zones.", new Object[]{player.getName(), zones.size()});
 }

 /**
  * Handles the /autowarn reload command, reloading the plugin's configuration.
  * @param player The player executing the command.
  */
 private void handleReload(Player player) {
  if (!player.hasPermission("autowarn.admin.reload")) {
   player.sendMessage(settings.getMessage("error.no-permission"));
   return;
  }
  plugin.reloadConfig(); // Call the standard reloadConfig() method in the main plugin class
  player.sendMessage(settings.getMessage("command.reload-success"));
  plugin.getLogger().log(Level.INFO, "Player '{0}' reloaded plugin configuration.", player.getName());
 }

 /**
  * Handles the /autowarn info <zone_name> command, displaying detailed zone information.
  * @param player The player executing the command.
  * @param args Command arguments.
  */
 private void handleInfo(Player player, String[] args) {
  if (!player.hasPermission("autowarn.admin.info")) {
   player.sendMessage(settings.getMessage("error.no-permission"));
   return;
  }
  if (args.length < 2) {
   player.sendMessage(settings.getMessage("error.usage.info")); // Assuming you add this to config.yml
   return;
  }
  String zoneName = args[1];
  Zone zone = zoneManager.getZone(zoneName);
  if (zone == null) {
   player.sendMessage(settings.getMessage("error.zone-not-found", Placeholder.unparsed("zone", zoneName)));
   return;
  }

  // Display zone info (example, customize as needed)
  player.sendMessage(Component.text("--- Zone Info: " + zone.getName() + " ---").color(NamedTextColor.GOLD));
  player.sendMessage(Component.text("World: " + zone.getWorldName()).color(NamedTextColor.YELLOW));
  player.sendMessage(Component.text("Corner1: " + formatVector(zone.getMin())).color(NamedTextColor.YELLOW));
  player.sendMessage(Component.text("Corner2: " + formatVector(zone.getMax())).color(NamedTextColor.YELLOW));
  player.sendMessage(Component.text("Default Action: " + zone.getDefaultAction().name()).color(NamedTextColor.YELLOW));

  if (!zone.getMaterialActions().isEmpty()) {
   player.sendMessage(Component.text("Material Actions:").color(NamedTextColor.YELLOW));
   zone.getMaterialActions().forEach((material, action) ->
           player.sendMessage(Component.text("  - " + material.name().toLowerCase().replace('_', ' ') + ": " + action.name()).color(NamedTextColor.GRAY))
   );
  } else {
   player.sendMessage(Component.text("Material Actions: None").color(NamedTextColor.GRAY));
  }
  plugin.getLogger().log(Level.INFO, "Player '{0}' requested info for zone '{1}'.", new Object[]{player.getName(), zoneName});
 }

 /**
  * Handles the /autowarn defaultaction <zone_name> <action> command.
  * @param player The player executing the command.
  * @param args Command arguments.
  */
 private void handleDefaultAction(Player player, String[] args) {
  if (!player.hasPermission("autowarn.admin.defaultaction")) {
   player.sendMessage(settings.getMessage("error.no-permission"));
   return;
  }
  if (args.length < 3) {
   player.sendMessage(settings.getMessage("error.usage.defaultaction")); // Assuming you add this to config.yml
   return;
  }
  String zoneName = args[1];
  String actionString = args[2].toUpperCase();
  Zone zone = zoneManager.getZone(zoneName);
  if (zone == null) {
   player.sendMessage(settings.getMessage("error.zone-not-found", Placeholder.unparsed("zone", zoneName)));
   return;
  }
  try {
   Zone.Action action = Zone.Action.valueOf(actionString);
   // Recreate zone with new default action, keeping existing material actions
   // Need to get the World object to pass to the Zone constructor
   World world = Bukkit.getWorld(zone.getWorldName());
   if (world == null) {
    player.sendMessage(Component.text("Error: World for zone '" + zoneName + "' not found.").color(NamedTextColor.RED));
    plugin.getLogger().warning("World for zone '" + zoneName + "' not found during defaultaction command.");
    return;
   }
   Zone updatedZone = new Zone(zone.getName(), world, zone.getMin(), zone.getMax(), action, zone.getMaterialActions());
   zoneManager.addOrUpdateZone(updatedZone);
   player.sendMessage(settings.getMessage("command.defaultaction-success", // Assuming you add this to config.yml
           Placeholder.unparsed("zone", zoneName),
           Placeholder.unparsed("action", action.name().toLowerCase())));
   plugin.getLogger().log(Level.INFO, "Player '{0}' set default action for zone '{1}' to '{2}'.", new Object[]{player.getName(), zoneName, action.name()});
  } catch (IllegalArgumentException e) {
   player.sendMessage(settings.getMessage("error.invalid-action")); // Assuming you add this to config.yml
   plugin.getLogger().log(Level.WARNING, "Player '{0}' attempted to set invalid action '{1}' for zone '{2}'.", new Object[]{player.getName(), actionString, zoneName});
  }
 }

 /**
  * Handles the /autowarn setaction <zone_name> <material> <action> command.
  * @param player The player executing the command.
  * @param args Command arguments.
  */
 private void handleSetAction(Player player, String[] args) {
  if (!player.hasPermission("autowarn.admin.setaction")) {
   player.sendMessage(settings.getMessage("error.no-permission"));
   return;
  }
  if (args.length < 4) {
   player.sendMessage(settings.getMessage("error.usage.setaction")); // Assuming you add this to config.yml
   return;
  }
  String zoneName = args[1];
  String materialKey = args[2].toUpperCase();
  String actionString = args[3].toUpperCase();

  Zone zone = zoneManager.getZone(zoneName);
  if (zone == null) {
   player.sendMessage(settings.getMessage("error.zone-not-found", Placeholder.unparsed("zone", zoneName)));
   return;
  }
  Material material = Material.getMaterial(materialKey);
  if (material == null) {
   player.sendMessage(settings.getMessage("error.invalid-material")); // Assuming you add this to config.yml
   return;
  }
  try {
   Zone.Action action = Zone.Action.valueOf(actionString);
   Map<Material, Zone.Action> updatedMaterialActions = new EnumMap<>(zone.getMaterialActions());
   updatedMaterialActions.put(material, action);
   // Recreate zone with updated material actions
   World world = Bukkit.getWorld(zone.getWorldName());
   if (world == null) {
    player.sendMessage(Component.text("Error: World for zone '" + zoneName + "' not found.").color(NamedTextColor.RED));
    plugin.getLogger().warning("World for zone '" + zoneName + "' not found during setaction command.");
    return;
   }
   Zone updatedZone = new Zone(zone.getName(), world, zone.getMin(), zone.getMax(), zone.getDefaultAction(), updatedMaterialActions);
   zoneManager.addOrUpdateZone(updatedZone);
   player.sendMessage(settings.getMessage("command.setaction-success", // Assuming you add this to config.yml
           Placeholder.unparsed("zone", zoneName),
           Placeholder.unparsed("material", material.name().toLowerCase().replace('_', ' ')),
           Placeholder.unparsed("action", action.name().toLowerCase())));
   plugin.getLogger().log(Level.INFO, "Player '{0}' set action for material '{1}' in zone '{2}' to '{3}'.", new Object[]{player.getName(), materialKey, zoneName, actionString});
  } catch (IllegalArgumentException e) {
   player.sendMessage(settings.getMessage("error.invalid-action"));
   plugin.getLogger().log(Level.WARNING, "Player '{0}' attempted to set invalid action '{1}' for material '{2}' in zone '{3}'.", new Object[]{player.getName(), actionString, materialKey, zoneName});
  }
 }

 /**
  * Handles the /autowarn removeaction <zone_name> <material> command.
  * @param player The player executing the command.
  * @param args Command arguments.
  */
 private void handleRemoveAction(Player player, String[] args) {
  if (!player.hasPermission("autowarn.admin.removeaction")) {
   player.sendMessage(settings.getMessage("error.no-permission"));
   return;
  }
  if (args.length < 3) {
   player.sendMessage(settings.getMessage("error.usage.removeaction")); // Assuming you add this to config.yml
   return;
  }
  String zoneName = args[1];
  String materialKey = args[2].toUpperCase();

  Zone zone = zoneManager.getZone(zoneName);
  if (zone == null) {
   player.sendMessage(settings.getMessage("error.zone-not-found", Placeholder.unparsed("zone", zoneName)));
   return;
  }
  Material material = Material.getMaterial(materialKey);
  if (material == null) {
   player.sendMessage(settings.getMessage("error.invalid-material"));
   return;
  }
  Map<Material, Zone.Action> updatedMaterialActions = new EnumMap<>(zone.getMaterialActions());
  if (updatedMaterialActions.remove(material) != null) {
   // Recreate zone with updated material actions
   World world = Bukkit.getWorld(zone.getWorldName());
   if (world == null) {
    player.sendMessage(Component.text("Error: World for zone '" + zoneName + "' not found.").color(NamedTextColor.RED));
    plugin.getLogger().warning("World for zone '" + zoneName + "' not found during removeaction command.");
    return;
   }
   Zone updatedZone = new Zone(zone.getName(), world, zone.getMin(), zone.getMax(), zone.getDefaultAction(), updatedMaterialActions);
   zoneManager.addOrUpdateZone(updatedZone);
   player.sendMessage(settings.getMessage("command.removeaction-success", // Assuming you add this to config.yml
           Placeholder.unparsed("zone", zoneName),
           Placeholder.unparsed("material", material.name().toLowerCase().replace('_', ' '))));
   plugin.getLogger().log(Level.INFO, "Player '{0}' removed action for material '{1}' from zone '{2}'.", new Object[]{player.getName(), materialKey, zoneName});
  } else {
   player.sendMessage(settings.getMessage("error.no-material-action")); // Assuming you add this to config.yml
   plugin.getLogger().log(Level.INFO, "Player '{0}' attempted to remove non-existent action for material '{1}' from zone '{2}'.", new Object[]{player.getName(), materialKey, zoneName});
  }
 }

 /**
  * Handles the /autowarn banned [add|remove|list] command for global material bans.
  * @param player The player executing the command.
  * @param args Command arguments.
  */
 private void handleBanned(Player player, String[] args) {
  if (!player.hasPermission("autowarn.admin.banned")) {
   player.sendMessage(settings.getMessage("error.no-permission"));
   return;
  }
  if (args.length < 2) {
   player.sendMessage(settings.getMessage("error.usage.banned")); // Assuming you add this to config.yml
   return;
  }

  String sub = args[1].toLowerCase();
  if (sub.equals("add")) {
   if (args.length < 3) {
    player.sendMessage(settings.getMessage("error.usage.banned-add")); // Assuming you add this to config.yml
    return;
   }
   Material material = Material.getMaterial(args[2].toUpperCase());
   if (material == null) {
    player.sendMessage(settings.getMessage("error.invalid-material"));
    return;
   }
   if (settings.addGloballyBannedMaterial(material)) { // Assuming Settings has this method
    player.sendMessage(settings.getMessage("command.banned-add-success", // Assuming you add this to config.yml
            Placeholder.unparsed("material", material.name().toLowerCase().replace('_', ' '))));
    plugin.getLogger().log(Level.INFO, "Player '{0}' added '{1}' to globally banned materials.", new Object[]{player.getName(), material.name()});
   } else {
    player.sendMessage(settings.getMessage("error.material-already-banned")); // Assuming you add this to config.yml
    plugin.getLogger().log(Level.INFO, "Player '{0}' attempted to add already banned material '{1}'.", new Object[]{player.getName(), material.name()});
   }
  } else if (sub.equals("remove")) {
   if (args.length < 3) {
    player.sendMessage(settings.getMessage("error.usage.banned-remove")); // Assuming you add this to config.yml
    return;
   }
   Material material = Material.getMaterial(args[2].toUpperCase());
   if (material == null) {
    player.sendMessage(settings.getMessage("error.invalid-material"));
    return;
   }
   if (settings.removeGloballyBannedMaterial(material)) { // Assuming Settings has this method
    player.sendMessage(settings.getMessage("command.banned-remove-success", // Assuming you add this to config.yml
            Placeholder.unparsed("material", material.name().toLowerCase().replace('_', ' '))));
    plugin.getLogger().log(Level.INFO, "Player '{0}' removed '{1}' from globally banned materials.", new Object[]{player.getName(), material.name()});
   } else {
    player.sendMessage(settings.getMessage("error.material-not-banned")); // Assuming you add this to config.yml
    plugin.getLogger().log(Level.INFO, "Player '{0}' attempted to remove non-banned material '{1}'.", new Object[]{player.getName(), material.name()});
   }
  } else if (sub.equals("list")) {
   Collection<Material> bannedMaterials = settings.getGloballyBannedMaterials();
   if (bannedMaterials.isEmpty()) {
    player.sendMessage(settings.getMessage("command.banned-list-empty")); // Assuming you add this to config.yml
    plugin.getLogger().log(Level.INFO, "Player '{0}' requested banned materials list; list is empty.", player.getName());
   } else {
    player.sendMessage(settings.getMessage("command.banned-list-header", // Assuming you add this to config.yml
            Placeholder.unparsed("count", String.valueOf(bannedMaterials.size()))));
    for (Material material : bannedMaterials) {
     player.sendMessage(Component.text("- " + material.name().toLowerCase().replace('_', ' ')).color(NamedTextColor.YELLOW));
    }
    plugin.getLogger().log(Level.INFO, "Player '{0}' requested banned materials list. Sent {1} materials.", new Object[]{player.getName(), bannedMaterials.size()});
   }
  } else {
   player.sendMessage(settings.getMessage("error.usage.banned")); // Assuming you add this to config.yml
   plugin.getLogger().log(Level.INFO, "Player '{0}' issued invalid 'banned' subcommand '{1}'.", new Object[]{player.getName(), sub});
  }
 }

 // --- Utility Methods ---

 /**
  * Formats a Vector into a human-readable string (X, Y, Z).
  *
  * @param vec The Vector to format.
  * @return A formatted string.
  */
 private String formatVector(Vector vec) {
  return String.format("%d, %d, %d", vec.getBlockX(), vec.getBlockY(), vec.getBlockZ());
 }

 /**
  * Sends the comprehensive help message to the player.
  *
  * @param player The player to send help to.
  */
 private void sendHelp(Player player) {
  plugin.getLogger().log(Level.INFO, "Sending help messages to player '{0}'.", player.getName());
  // Retrieve and send each help message from settings.
  // Added null checks for safety, although settings.getMessage should ideally never return null.
  Component header = settings.getMessage("command.help-header");
  if (header != null) player.sendMessage(header);
  else plugin.getLogger().warning("Help header message is null!");

  Component wandHelp = settings.getMessage("command.help.wand");
  if (wandHelp != null) player.sendMessage(wandHelp);
  else plugin.getLogger().warning("Wand help message is null!");

  Component posHelp = settings.getMessage("command.help.pos");
  if (posHelp != null) player.sendMessage(posHelp);
  else plugin.getLogger().warning("Pos help message is null!");

  Component defineHelp = settings.getMessage("command.help.define");
  if (defineHelp != null) player.sendMessage(defineHelp);
  else plugin.getLogger().warning("Define help message is null!");

  Component removeHelp = settings.getMessage("command.help.remove");
  if (removeHelp != null) player.sendMessage(removeHelp);
  else plugin.getLogger().warning("Remove help message is null!");

  Component listHelp = settings.getMessage("command.help.list");
  if (listHelp != null) player.sendMessage(listHelp);
  else plugin.getLogger().warning("List help message is null!");

  Component infoHelp = settings.getMessage("command.help.info");
  if (infoHelp != null) player.sendMessage(infoHelp);
  else plugin.getLogger().warning("Info help message is null!");

  Component setActionHelp = settings.getMessage("command.help.setaction");
  if (setActionHelp != null) player.sendMessage(setActionHelp);
  else plugin.getLogger().warning("Set action help message is null!");

  Component removeActionHelp = settings.getMessage("command.help.removeaction");
  if (removeActionHelp != null) player.sendMessage(removeActionHelp);
  else plugin.getLogger().warning("Remove action help message is null!");

  Component defaultActionHelp = settings.getMessage("command.help.defaultaction");
  if (defaultActionHelp != null) player.sendMessage(defaultActionHelp);
  else plugin.getLogger().warning("Default action help message is null!");

  Component bannedHelp = settings.getMessage("command.help.banned");
  if (bannedHelp != null) player.sendMessage(bannedHelp);
  else plugin.getLogger().warning("Banned help message is null!");

  Component reloadHelp = settings.getMessage("command.help.reload");
  if (reloadHelp != null) player.sendMessage(reloadHelp);
  else plugin.getLogger().warning("Reload help message is null!");
 }

 // --- Getters for ZoneListener ---

 /**
  * Gets the NamespacedKey used to identify the AutoWarn wand.
  *
  * @return The NamespacedKey for the wand.
  */
 public NamespacedKey getWandKey() {
  return wandKey;
 }

 /**
  * Sets the first selection point for a player.
  *
  * @param uuid The UUID of the player.
  * @param pos  The Vector representing the position.
  */
 public void setPos1(UUID uuid, Vector pos) {
  pos1Selections.put(uuid, pos);
 }

 /**
  * Sets the second selection point for a player.
  *
  * @param uuid The UUID of the player.
  * @param pos  The Vector representing the position.
  */
 public void setPos2(UUID uuid, Vector pos) {
  pos2Selections.put(uuid, pos);
 }


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
     // Filter for block materials only (materials that can be placed as blocks)
            StringUtil.copyPartialMatches(args[2], Arrays.stream(Material.values()).filter(Material::isBlock).map(Enum::name).collect(Collectors.toList()), completions);
    case "banned" -> {
     if ("add".equalsIgnoreCase(args[1])) {
      // Filter for item materials only (materials that can be held as items)
      StringUtil.copyPartialMatches(args[2], Arrays.stream(Material.values()).filter(Material::isItem).map(Enum::name).collect(Collectors.toList()), completions);
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