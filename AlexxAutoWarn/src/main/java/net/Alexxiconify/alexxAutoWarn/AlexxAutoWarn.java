package net.Alexxiconify.alexxAutoWarn;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class AlexxAutoWarn extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

 // --- Plugin Constants ---
 private static final String COMMAND_NAME = "autoinform";
 private static final String PERMISSION_ADMIN_SET = "autoinform.admin.set";
 private static final String PERMISSION_ALERT_RECEIVE = "autoinform.alert.receive";
 private static final String PERMISSION_BYPASS = "autoinform.bypass";
 private static final String WAND_KEY_STRING = "ainform_wand";
 private static final String WAND_DISPLAY_NAME = ChatColor.GOLD + "" + ChatColor.BOLD + "AutoInform Zone Selector Wand";
 private static final List<String> WAND_LORE = Arrays.asList(
         ChatColor.GRAY + "Left-click: Set Position 1",
         ChatColor.GRAY + "Right-click: Set Position 2"
 );
 private static final String PLUGIN_PREFIX = ChatColor.RED + "[AutoInform] " + ChatColor.YELLOW;

 // --- Configurable Messages ---
 private final Map<String, String> messages = new HashMap<>();


 // --- Instance Variables ---
 private CoreProtectAPI coreProtectAPI;
 private final Map<String, AutoInformZone> definedZones = new HashMap<>();
 private final Set<Material> bannedMaterials = new HashSet<>();
 private final Map<UUID, Map<String, Map<String, Location>>> playerZoneSelections = new HashMap<>();
 private final Map<UUID, Location> playerWandPos1 = new HashMap<>();
 private final Map<UUID, Location> playerWandPos2 = new HashMap<>();
 private NamespacedKey wandKey;

 @Override
 public void onEnable() {
  this.wandKey = new NamespacedKey(this, WAND_KEY_STRING);
  this.coreProtectAPI = getCoreProtectAPI();

  if (this.coreProtectAPI == null) {
   getLogger().warning(getMessage("plugin-coreprotect-not-found"));
  } else {
   getLogger().info(getMessage("plugin-coreprotect-hooked"));
  }

  saveDefaultConfig();
  loadMessagesFromConfig();
  loadZonesFromConfig();
  loadBannedMaterialsFromConfig();

  Bukkit.getPluginManager().registerEvents(this, this);
  Objects.requireNonNull(getCommand(COMMAND_NAME)).setExecutor(this);
  Objects.requireNonNull(getCommand(COMMAND_NAME)).setTabCompleter(this);

  getLogger().info(getMessage("plugin-enabled"));
  if (definedZones.isEmpty()) {
   getLogger().warning(getMessage("plugin-warning-no-zones").replace("{COMMAND_NAME}", COMMAND_NAME));
  } else {
   getLogger().info(getMessage("plugin-success-zones-loaded").replace("{count}", String.valueOf(definedZones.size())));
  }
  getLogger().info(getMessage("plugin-current-banned-materials").replace("{materials}", formatMaterialList(bannedMaterials)));
 }

 @Override
 public void onDisable() {
  getLogger().info(getMessage("plugin-disabled"));
  playerZoneSelections.clear();
  playerWandPos1.clear();
  playerWandPos2.clear();
  definedZones.clear();
  bannedMaterials.clear();
  messages.clear();
 }

 /** Loads all custom messages from config.yml. */
 private void loadMessagesFromConfig() {
  messages.clear();
  FileConfiguration config = getConfig();
  ConfigurationSection messagesSection = config.getConfigurationSection("messages");

  if (messagesSection != null) {
   for (String key : messagesSection.getKeys(false)) {
    messages.put(key, ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(messagesSection.getString(key))));
   }
  }
 }

 /** Gets a message from the loaded messages, with placeholders replaced. */
 private String getMessage(String key) {
  return messages.getOrDefault(key, "§cError: Message '" + key + "' not found in config.yml!");
 }

 /** Loads all AutoInform zones from config.yml. */
 private void loadZonesFromConfig() {
  definedZones.clear();
  FileConfiguration config = getConfig();
  ConfigurationSection zonesSection = config.getConfigurationSection("zones");

  if (zonesSection == null) {
   getLogger().info(getMessage("plugin-no-zones-in-config"));
   return;
  }

  for (String zoneName : zonesSection.getKeys(false)) {
   ConfigurationSection zoneConfig = zonesSection.getConfigurationSection(zoneName);
   if (zoneConfig == null) {
    getLogger().warning(getMessage("plugin-invalid-zone-config").replace("{zone_name}", zoneName));
    continue;
   }

   String worldName = zoneConfig.getString("world");
   World world = Bukkit.getWorld(worldName);
   if (world == null) {
    getLogger().severe(getMessage("plugin-world-not-found").replace("{world_name}", worldName).replace("{zone_name}", zoneName));
    continue;
   }

   try {
    Location corner1 = new Location(world, zoneConfig.getDouble("corner1.x"), zoneConfig.getDouble("corner1.y"), zoneConfig.getDouble("corner1.z"));
    Location corner2 = new Location(world, zoneConfig.getDouble("corner2.x"), zoneConfig.getDouble("corner2.y"), zoneConfig.getDouble("corner2.z"));

    // Load default action
    ZoneAction defaultAction = ZoneAction.valueOf(zoneConfig.getString("default-material-action", "ALERT").toUpperCase());

    // Load material-specific actions
    Map<Material, ZoneAction> materialActions = new HashMap<>();
    ConfigurationSection materialActionsSection = zoneConfig.getConfigurationSection("material-actions");
    if (materialActionsSection != null) {
     for (String materialName : materialActionsSection.getKeys(false)) {
      try {
       Material material = Material.valueOf(materialName.toUpperCase());
       ZoneAction action = ZoneAction.valueOf(Objects.requireNonNull(materialActionsSection.getString(materialName)).toUpperCase());
       materialActions.put(material, action);
      } catch (IllegalArgumentException e) {
       getLogger().warning("Invalid material or action type in zone '" + zoneName + "' for material '" + materialName + "'. Skipping.");
      }
     }
    }

    definedZones.put(zoneName, new AutoInformZone(zoneName, world, corner1, corner2, defaultAction, materialActions));
    getLogger().info("Loaded zone '" + zoneName + "' in world '" + worldName + "' (Default Action: " + defaultAction + ").");
   } catch (Exception e) {
    getLogger().severe(getMessage("plugin-error-loading-zone-coords").replace("{zone_name}", zoneName).replace("{message}", e.getMessage()));
   }
  }
 }

 /** Saves all currently defined AutoInform zones to config.yml. */
 private void saveZonesToConfig() {
  FileConfiguration config = getConfig();
  config.set("zones", null);

  if (!definedZones.isEmpty()) {
   for (AutoInformZone zone : definedZones.values()) {
    String path = "zones." + zone.getName() + ".";
    config.set(path + "world", zone.getWorld().getName());
    config.set(path + "corner1.x", zone.getCorner1().getX());
    config.set(path + "corner1.y", zone.getCorner1().getY());
    config.set(path + "corner1.z", zone.getCorner1().getZ());
    config.set(path + "corner2.x", zone.getCorner2().getX());
    config.set(path + "corner2.y", zone.getCorner2().getY());
    config.set(path + "corner2.z", zone.getCorner2().getZ());
    config.set(path + "default-material-action", zone.getDefaultAction().name());

    // Save material-specific actions
    if (!zone.getMaterialSpecificActions().isEmpty()) {
     zone.getMaterialSpecificActions().forEach((material, action) ->
             config.set(path + "material-actions." + material.name(), action.name())
     );
    } else {
     config.set(path + "material-actions", null); // Remove section if empty
    }
   }
  }
  saveConfig();
 }

 /** Loads banned materials from config.yml. */
 private void loadBannedMaterialsFromConfig() {
  bannedMaterials.clear();
  FileConfiguration config = getConfig();
  List<String> materialNames = config.getStringList("banned-materials");

  for (String name : materialNames) {
   try {
    bannedMaterials.add(Material.valueOf(name.toUpperCase()));
   } catch (IllegalArgumentException e) {
    getLogger().warning(getMessage("plugin-invalid-banned-material-config").replace("{name}", name));
   }
  }
 }

 /** Saves currently banned materials to config.yml. */
 private void saveBannedMaterialsToConfig() {
  FileConfiguration config = getConfig();
  config.set("banned-materials", bannedMaterials.stream().map(Enum::name).collect(Collectors.toList()));
  saveConfig();
 }

 /** Attempts to get the CoreProtect API instance. */
 private CoreProtectAPI getCoreProtectAPI() {
  Plugin plugin = Bukkit.getPluginManager().getPlugin("CoreProtect");
  if (plugin instanceof CoreProtect coreProtect) {
   CoreProtectAPI api = coreProtect.getAPI();
   if (api != null && api.isEnabled()) {
    if (api.APIVersion() < 10) {
     getLogger().warning(getMessage("plugin-coreprotect-api-outdated").replace("{version}", String.valueOf(api.APIVersion())));
    }
    return api;
   } else {
    getLogger().warning(getMessage("plugin-coreprotect-api-disabled"));
   }
  } else {
   getLogger().warning(getMessage("plugin-coreprotect-not-found"));
  }
  return null;
 }

 /** Creates the AutoInform Zone Selector Wand item. */
 private ItemStack createWand() {
  ItemStack wand = new ItemStack(Material.WOODEN_AXE);
  ItemMeta meta = wand.getItemMeta();
  if (meta != null) {
   meta.setDisplayName(WAND_DISPLAY_NAME);
   meta.setLore(WAND_LORE);
   meta.getPersistentDataContainer().set(wandKey, PersistentDataType.STRING, "true");
   wand.setItemMeta(meta);
  }
  return wand;
 }

 /** Checks if an ItemStack is the AutoInform Zone Selector Wand. */
 private boolean isWand(ItemStack item) {
  return item != null && item.hasItemMeta() && Objects.requireNonNull(item.getItemMeta()).getPersistentDataContainer().has(wandKey, PersistentDataType.STRING);
 }

 /** Processes banned material placement: logs, alerts staff, and determines if denied. */
 private boolean processBannedMaterialPlacement(Player player, Location location, Material material) {
  // If player has bypass permission, do nothing
  if (player.hasPermission(PERMISSION_BYPASS)) {
   return false;
  }

  // Only proceed if the material is in the global banned list
  if (!bannedMaterials.contains(material)) {
   return false;
  }

  AutoInformZone applicableZone = null;
  for (AutoInformZone zone : definedZones.values()) {
   if (zone.contains(location)) {
    applicableZone = zone;
    break; // Found the first matching zone
   }
  }

  if (applicableZone != null) {
   ZoneAction action = applicableZone.getMaterialSpecificActions().getOrDefault(material, applicableZone.getDefaultAction());

   if (action == ZoneAction.ALLOW) {
    return false; // Explicitly allowed by zone, do nothing
   }

   String actionStatus = action == ZoneAction.DENY ? "DENIED" : "ALERTED";
   String logMessage = "Player " + player.getName() + " attempted to place banned material " + material.name() + " at " + formatLocation(location) + " in protected zone '" + applicableZone.getName() + "'. Action: " + actionStatus + ".";
   getLogger().info(logMessage);

   String staffActionColor = action == ZoneAction.DENY ? ChatColor.RED.toString() : ChatColor.YELLOW.toString();
   String staffMessage = getMessage("staff-alert-message")
           .replace("{player}", player.getName())
           .replace("{material}", material.name())
           .replace("{zone_name}", applicableZone.getName())
           .replace("{x}", String.valueOf(location.getBlockX()))
           .replace("{y}", String.valueOf(location.getBlockY()))
           .replace("{z}", String.valueOf(location.getBlockZ()))
           .replace("{action_color}", staffActionColor)
           .replace("{action_status}", actionStatus);

   Bukkit.getOnlinePlayers().stream()
           .filter(staff -> staff.hasPermission(PERMISSION_ALERT_RECEIVE))
           .forEach(staff -> staff.sendMessage(staffMessage));

   return action == ZoneAction.DENY; // Cancel if DENY, otherwise don't
  }
  return false; // No applicable zone
 }

 @EventHandler
 public void onPlayerEmptyBucket(PlayerBucketEmptyEvent event) {
  Location placedLocation = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
  if (processBannedMaterialPlacement(event.getPlayer(), placedLocation, event.getBucket())) {
   event.setCancelled(true);
   event.getPlayer().sendMessage(getMessage("player-denied-placement").replace("{material}", event.getBucket().name()));
  }
 }

 @EventHandler
 public void onBlockPlace(BlockPlaceEvent event) {
  if (processBannedMaterialPlacement(event.getPlayer(), event.getBlock().getLocation(), event.getBlock().getType())) {
   event.setCancelled(true);
   event.getPlayer().sendMessage(getMessage("player-denied-placement").replace("{material}", event.getBlock().getType().name()));
  }
 }

 @EventHandler
 public void onPlayerInteract(PlayerInteractEvent event) {
  Player player = event.getPlayer();
  ItemStack handItem = event.getItem();

  if (isWand(handItem)) {
   event.setCancelled(true);

   if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
    if (event.getClickedBlock() != null) {
     playerWandPos1.put(player.getUniqueId(), event.getClickedBlock().getLocation());
     player.sendMessage(getMessage("player-wand-pos1-set").replace("{location}", formatLocation(event.getClickedBlock().getLocation())));
    } else {
     player.sendMessage(getMessage("player-wand-click-block").replace("{pos_num}", "1"));
    }
   } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
    if (event.getClickedBlock() != null) {
     playerWandPos2.put(player.getUniqueId(), event.getClickedBlock().getLocation());
     player.sendMessage(getMessage("player-wand-pos2-set").replace("{location}", formatLocation(event.getClickedBlock().getLocation())));
    } else {
     player.sendMessage(getMessage("player-wand-click-block").replace("{pos_num}", "2"));
    }
   }
   return;
  }

  if (handItem != null && bannedMaterials.contains(handItem.getType()) && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
   if (handItem.getType() == Material.TNT_MINECART && event.getClickedBlock() != null && event.getClickedBlock().getType().name().contains("RAIL")) {
    Location placementLocation = event.getClickedBlock().getLocation().add(0, 1, 0);
    if (processBannedMaterialPlacement(player, placementLocation, Material.TNT_MINECART)) {
     event.setCancelled(true);
     player.sendMessage(getMessage("player-denied-placement").replace("{material}", Material.TNT_MINECART.name()));
    }
   }
  }
 }

 @Override
 public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
  if (!command.getName().equalsIgnoreCase(COMMAND_NAME) && !command.getName().equalsIgnoreCase("ainform")) return false;

  if (!(sender instanceof Player player)) {
   if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
    handleReloadCommand(sender);
    return true;
   }
   sender.sendMessage(getMessage("player-console-only-reload").replace("{command_name}", COMMAND_NAME));
   return true;
  }

  if (!player.hasPermission(PERMISSION_ADMIN_SET)) {
   player.sendMessage(getMessage("player-no-permission"));
   return true;
  }

  if (args.length < 1) {
   sendHelpMessage(player);
   return true;
  }

  String subCommand = args[0].toLowerCase();
  switch (subCommand) {
   case "wand": handleWandCommand(player); break;
   case "pos1":
   case "pos2": handlePosCommand(player, subCommand, args); break;
   case "define": handleDefineCommand(player, args); break;
   case "remove": handleRemoveCommand(player, args); break;
   case "info": handleInfoCommand(player, args); break;
   case "list": handleListCommand(player); break;
   case "reload": handleReloadCommand(player); break;
   case "clearwand": handleClearWandCommand(player); break;
   case "banned": handleBannedCommand(player, args); break;
   case "defaultaction": handleDefaultActionCommand(player, args); break;
   case "setaction": handleSetActionCommand(player, args); break;
   default: sendHelpMessage(player); break;
  }
  return true;
 }

 // --- Command Handlers ---

 private void handleWandCommand(Player player) {
  player.getInventory().addItem(createWand());
  player.sendMessage(getMessage("player-wand-received"));
 }

 private void handlePosCommand(Player player, String posType, String[] args) {
  if (args.length < 2) {
   player.sendMessage(getMessage("player-command-usage").replace("{usage}", "/" + COMMAND_NAME + " " + posType + " <zone_name>"));
   return;
  }
  String zoneNameForPos = args[1];
  playerZoneSelections.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).computeIfAbsent(zoneNameForPos, k -> new HashMap<>()).put(posType, player.getLocation());
  player.sendMessage(getMessage("player-wand-pos" + posType.substring(3) + "-set").replace("{location}", formatLocation(player.getLocation())));
  playerWandPos1.remove(player.getUniqueId());
  playerWandPos2.remove(player.getUniqueId());
 }

 private void handleDefineCommand(Player player, String[] args) {
  if (args.length < 2) {
   player.sendMessage(getMessage("player-command-usage").replace("{usage}", "/" + COMMAND_NAME + " define <zone_name>"));
   return;
  }
  String zoneToDefine = args[1];
  Location p1 = playerWandPos1.get(player.getUniqueId());
  Location p2 = playerWandPos2.get(player.getUniqueId());

  if (p1 == null || p2 == null) {
   Map<String, Location> playerSelections = playerZoneSelections.getOrDefault(player.getUniqueId(), Collections.emptyMap()).get(zoneToDefine);
   if (playerSelections == null || !playerSelections.containsKey("pos1") || !playerSelections.containsKey("pos2")) {
    player.sendMessage(getMessage("player-command-usage").replace("{usage}", "You must set both positions for zone '" + zoneToDefine + "' first using the wand or /" + COMMAND_NAME + " " + zoneToDefine + " pos1 and /" + COMMAND_NAME + " " + zoneToDefine + " pos2."));
    return;
   }
   p1 = playerSelections.get("pos1");
   p2 = playerSelections.get("pos2");
   player.sendMessage(ChatColor.AQUA + "Using manual selections for zone '" + zoneToDefine + "'.");
  } else {
   player.sendMessage(ChatColor.AQUA + "Using wand selections for zone '" + zoneToDefine + "'.");
  }

  if (!p1.getWorld().equals(p2.getWorld())) {
   player.sendMessage(getMessage("player-positions-same-world"));
   playerWandPos1.remove(player.getUniqueId());
   playerWandPos2.remove(player.getUniqueId());
   return;
  }

  // Preserve existing settings or use defaults
  ZoneAction currentDefaultAction = ZoneAction.ALERT;
  Map<Material, ZoneAction> currentMaterialActions = new HashMap<>();

  if (definedZones.containsKey(zoneToDefine)) {
   AutoInformZone existingZone = definedZones.get(zoneToDefine);
   currentDefaultAction = existingZone.getDefaultAction();
   currentMaterialActions.putAll(existingZone.getMaterialSpecificActions());
  }

  definedZones.put(zoneToDefine, new AutoInformZone(zoneToDefine, p1.getWorld(), p1, p2, currentDefaultAction, currentMaterialActions));
  saveZonesToConfig();
  player.sendMessage(getMessage("player-define-zone-success")
          .replace("{zone_name}", zoneToDefine)
          .replace("{world_name}", p1.getWorld().getName()));
  player.sendMessage(getMessage("player-define-zone-corners")
          .replace("{corner1_loc}", formatLocation(p1))
          .replace("{corner2_loc}", formatLocation(p2)));
  player.sendMessage(getMessage("player-define-zone-deny-setting").replace("{default_action}", currentDefaultAction.name()));

  playerWandPos1.remove(player.getUniqueId());
  playerWandPos2.remove(player.getUniqueId());
  if (playerZoneSelections.get(player.getUniqueId()) != null) {
   playerZoneSelections.get(player.getUniqueId()).remove(zoneToDefine);
   if (playerZoneSelections.get(player.getUniqueId()).isEmpty()) {
    playerZoneSelections.remove(player.getUniqueId());
   }
  }
 }

 private void handleRemoveCommand(Player player, String[] args) {
  if (args.length < 2) {
   player.sendMessage(getMessage("player-command-usage").replace("{usage}", "/" + COMMAND_NAME + " remove <zone_name>"));
   return;
  }
  String zoneToRemove = args[1];
  if (definedZones.remove(zoneToRemove) != null) {
   saveZonesToConfig();
   player.sendMessage(getMessage("player-zone-removed").replace("{zone_name}", zoneToRemove));
  } else {
   player.sendMessage(getMessage("player-zone-not-found").replace("{zone_name}", zoneToRemove));
  }
 }

 private void handleInfoCommand(Player player, String[] args) {
  if (args.length == 2) {
   String zoneInfoName = args[1];
   AutoInformZone zoneInfo = definedZones.get(zoneInfoName);
   if (zoneInfo != null) {
    player.sendMessage(getMessage("player-zone-info-header").replace("{zone_name}", zoneInfo.getName()));
    player.sendMessage(getMessage("player-zone-info-world").replace("{world_name}", zoneInfo.getWorld().getName()));
    player.sendMessage(getMessage("player-zone-info-corner1").replace("{corner1_loc}", formatLocation(zoneInfo.getCorner1())));
    player.sendMessage(getMessage("player-zone-info-corner2").replace("{corner2_loc}", formatLocation(zoneInfo.getCorner2())));
    player.sendMessage(getMessage("player-zone-info-default-action").replace("{default_action}", zoneInfo.getDefaultAction().name()));
    if (!zoneInfo.getMaterialSpecificActions().isEmpty()) {
     player.sendMessage(getMessage("player-zone-info-material-actions"));
     zoneInfo.getMaterialSpecificActions().forEach((material, action) ->
             player.sendMessage(getMessage("player-zone-info-material-action-entry")
                     .replace("{material}", material.name())
                     .replace("{action}", action.name()))
     );
    }
   } else {
    player.sendMessage(getMessage("player-zone-not-found").replace("{zone_name}", zoneInfoName));
   }
  } else {
   if (definedZones.isEmpty()) {
    player.sendMessage(getMessage("player-no-zones-defined"));
   } else {
    player.sendMessage(getMessage("player-all-zones-header"));
    definedZones.values().forEach(zone ->
            player.sendMessage(ChatColor.GOLD + "- " + zone.getName() + ": " + ChatColor.WHITE + "World: " + zone.getWorld().getName() + ", Default: " + zone.getDefaultAction())
    );
   }
  }
 }

 private void handleListCommand(Player player) {
  if (definedZones.isEmpty()) {
   player.sendMessage(getMessage("player-no-zones-defined"));
  } else {
   player.sendMessage(getMessage("player-defined-zones-header"));
   definedZones.keySet().forEach(zoneName -> player.sendMessage(ChatColor.GOLD + "- " + zoneName));
  }
 }

 private void handleReloadCommand(CommandSender sender) {
  loadMessagesFromConfig();
  loadZonesFromConfig();
  loadBannedMaterialsFromConfig();
  sender.sendMessage(getMessage("plugin-config-reloaded"));
  if (definedZones.isEmpty()) {
   sender.sendMessage(getMessage("plugin-warning-no-zones").replace("{COMMAND_NAME}", COMMAND_NAME));
  } else {
   sender.sendMessage(getMessage("plugin-success-zones-loaded").replace("{count}", String.valueOf(definedZones.size())));
  }
  sender.sendMessage(getMessage("plugin-current-banned-materials").replace("{materials}", formatMaterialList(bannedMaterials)));
 }

 private void handleClearWandCommand(Player player) {
  playerWandPos1.remove(player.getUniqueId());
  playerWandPos2.remove(player.getUniqueId());
  player.sendMessage(getMessage("player-wand-selections-cleared"));
 }

 private void handleBannedCommand(Player player, String[] args) {
  if (args.length < 2) {
   player.sendMessage(getMessage("player-command-usage").replace("{usage}", "/" + COMMAND_NAME + " banned <add|remove|list> [material_name]"));
   return;
  }
  String bannedAction = args[1].toLowerCase();
  switch (bannedAction) {
   case "add":
    if (args.length < 3) {
     player.sendMessage(getMessage("player-command-usage").replace("{usage}", "/" + COMMAND_NAME + " banned add <material_name>"));
     return;
    }
    String materialToAdd = args[2].toUpperCase();
    try {
     Material material = Material.valueOf(materialToAdd);
     if (bannedMaterials.add(material)) {
      saveBannedMaterialsToConfig();
      player.sendMessage(getMessage("player-material-added-banned").replace("{material}", material.name()));
     } else {
      player.sendMessage(getMessage("player-material-already-banned").replace("{material}", material.name()));
     }
    } catch (IllegalArgumentException e) {
     player.sendMessage(getMessage("player-invalid-material-name").replace("{material}", materialToAdd));
    }
    break;
   case "remove":
    if (args.length < 3) {
     player.sendMessage(getMessage("player-command-usage").replace("{usage}", "/" + COMMAND_NAME + " banned remove <material_name>"));
     return;
    }
    String materialToRemove = args[2].toUpperCase();
    try {
     Material material = Material.valueOf(materialToRemove);
     if (bannedMaterials.remove(material)) {
      saveBannedMaterialsToConfig();
      player.sendMessage(getMessage("player-material-removed-banned").replace("{material}", material.name()));
     } else {
      player.sendMessage(getMessage("player-material-not-banned").replace("{material}", material.name()));
     }
    } catch (IllegalArgumentException e) {
     player.sendMessage(getMessage("player-invalid-material-name").replace("{material}", materialToRemove));
    }
    break;
   case "list":
    if (bannedMaterials.isEmpty()) {
     player.sendMessage(getMessage("player-no-materials-banned"));
    } else {
     player.sendMessage(getMessage("player-banned-materials-header"));
     player.sendMessage(ChatColor.WHITE + formatMaterialList(bannedMaterials));
    }
    break;
   default:
    player.sendMessage(getMessage("player-command-usage").replace("{usage}", "/" + COMMAND_NAME + " banned <add|remove|list>"));
    break;
  }
 }

 private void handleDefaultActionCommand(Player player, String[] args) {
  if (args.length < 3) {
   player.sendMessage(getMessage("player-command-usage").replace("{usage}", "/" + COMMAND_NAME + " defaultaction <zone_name> <DENY|ALERT|ALLOW>"));
   return;
  }
  String zoneName = args[1];
  String actionString = args[2].toUpperCase();
  ZoneAction newDefaultAction;

  try {
   newDefaultAction = ZoneAction.valueOf(actionString);
  } catch (IllegalArgumentException e) {
   player.sendMessage(getMessage("player-invalid-action-type"));
   return;
  }

  AutoInformZone existingZone = definedZones.get(zoneName);
  if (existingZone == null) {
   player.sendMessage(getMessage("player-zone-not-found").replace("{zone_name}", zoneName));
   return;
  }

  AutoInformZone updatedZone = new AutoInformZone(
          existingZone.getName(),
          existingZone.getWorld(),
          existingZone.getCorner1(),
          existingZone.getCorner2(),
          newDefaultAction,
          existingZone.getMaterialSpecificActions()
  );
  definedZones.put(zoneName, updatedZone);
  saveZonesToConfig();
  player.sendMessage(getMessage("player-deny-setting-updated").replace("{zone_name}", zoneName).replace("{action}", newDefaultAction.name()));
 }

 private void handleSetActionCommand(Player player, String[] args) {
  if (args.length < 4) {
   player.sendMessage(getMessage("player-command-usage").replace("{usage}", "/" + COMMAND_NAME + " setaction <zone_name> <material> <DENY|ALERT|ALLOW>"));
   return;
  }
  String zoneName = args[1];
  String materialName = args[2].toUpperCase();
  String actionString = args[3].toUpperCase();

  Material material;
  ZoneAction action;

  try {
   material = Material.valueOf(materialName);
  } catch (IllegalArgumentException e) {
   player.sendMessage(getMessage("player-invalid-material-name").replace("{material}", materialName));
   return;
  }

  try {
   action = ZoneAction.valueOf(actionString);
  } catch (IllegalArgumentException e) {
   player.sendMessage(getMessage("player-invalid-action-type"));
   return;
  }

  AutoInformZone existingZone = definedZones.get(zoneName);
  if (existingZone == null) {
   player.sendMessage(getMessage("player-zone-not-found").replace("{zone_name}", zoneName));
   return;
  }

  Map<Material, ZoneAction> updatedMaterialActions = new HashMap<>(existingZone.getMaterialSpecificActions());
  updatedMaterialActions.put(material, action);

  AutoInformZone updatedZone = new AutoInformZone(
          existingZone.getName(),
          existingZone.getWorld(),
          existingZone.getCorner1(),
          existingZone.getCorner2(),
          existingZone.getDefaultAction(),
          updatedMaterialActions
  );
  definedZones.put(zoneName, updatedZone);
  saveZonesToConfig();
  player.sendMessage(getMessage("player-material-action-updated")
          .replace("{material}", material.name())
          .replace("{zone_name}", zoneName)
          .replace("{action}", action.name()));
 }


 @Override
 public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
  if (!cmd.getName().equalsIgnoreCase(COMMAND_NAME) && !cmd.getName().equalsIgnoreCase("ainform")) {
   return Collections.emptyList();
  }

  if (!sender.hasPermission(PERMISSION_ADMIN_SET)) {
   return Collections.emptyList();
  }

  if (args.length == 1) {
   return Arrays.asList("wand", "pos1", "pos2", "define", "remove", "info", "list", "reload", "clearwand", "banned", "defaultaction", "setaction").stream()
           .filter(s -> s.startsWith(args[0].toLowerCase()))
           .collect(Collectors.toList());
  } else if (args.length == 2) {
   String subCommand = args[0].toLowerCase();
   if (Arrays.asList("pos1", "pos2", "define", "remove", "info", "defaultaction", "setaction").contains(subCommand)) {
    return definedZones.keySet().stream()
            .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
            .collect(Collectors.toList());
   } else if (subCommand.equals("banned")) {
    return Arrays.asList("add", "remove", "list").stream()
            .filter(s -> s.startsWith(args[1].toLowerCase()))
            .collect(Collectors.toList());
   }
  } else if (args.length == 3) {
   String subCommand = args[0].toLowerCase();
   if (subCommand.equals("banned")) {
    String bannedAction = args[1].toLowerCase();
    if (bannedAction.equals("add")) {
     return Arrays.stream(Material.values()).map(Enum::name).filter(s -> s.startsWith(args[2].toUpperCase())).collect(Collectors.toList());
    } else if (bannedAction.equals("remove")) {
     return bannedMaterials.stream().map(Enum::name).filter(s -> s.startsWith(args[2].toUpperCase())).collect(Collectors.toList());
    }
   } else if (subCommand.equals("defaultaction")) {
    return Arrays.asList("DENY", "ALERT", "ALLOW").stream()
            .filter(s -> s.startsWith(args[2].toUpperCase()))
            .collect(Collectors.toList());
   } else if (subCommand.equals("setaction")) {
    return Arrays.stream(Material.values()).map(Enum::name).filter(s -> s.startsWith(args[2].toUpperCase())).collect(Collectors.toList());
   }
  } else if (args.length == 4 && args[0].equalsIgnoreCase("setaction")) {
   return Arrays.asList("DENY", "ALERT", "ALLOW").stream()
           .filter(s -> s.startsWith(args[3].toUpperCase()))
           .collect(Collectors.toList());
  }
  return Collections.emptyList();
 }

 /** Sends the help message to the player. */
 private void sendHelpMessage(Player player) {
  player.sendMessage(ChatColor.AQUA + "--- AutoInform Setter Help ---");
  player.sendMessage(ChatColor.GOLD + "/" + COMMAND_NAME + " wand" + ChatColor.WHITE + " - Get the selection wand.");
  player.sendMessage(ChatColor.GOLD + "/" + COMMAND_NAME + " <zone_name> pos1" + ChatColor.WHITE + " - Set first zone corner (manual).");
  player.sendMessage(ChatColor.GOLD + "/" + COMMAND_NAME + " <zone_name> pos2" + ChatColor.WHITE + " - Set second zone corner (manual).");
  player.sendMessage(ChatColor.GOLD + "/" + COMMAND_NAME + " <zone_name> define" + ChatColor.WHITE + " - Define/update <zone_name> using wand or manual selections.");
  player.sendMessage(ChatColor.GOLD + "/" + COMMAND_NAME + " <zone_name> defaultaction <DENY|ALERT|ALLOW>" + ChatColor.WHITE + " - Set default action for a zone.");
  player.sendMessage(ChatColor.GOLD + "/" + COMMAND_NAME + " <zone_name> setaction <material> <DENY|ALERT|ALLOW>" + ChatColor.WHITE + " - Set specific material action in a zone.");
  player.sendMessage(ChatColor.GOLD + "/" + COMMAND_NAME + " remove <zone_name>" + ChatColor.WHITE + " - Remove a defined zone.");
  player.sendMessage(ChatColor.GOLD + "/" + COMMAND_NAME + " info [zone_name]" + ChatColor.WHITE + " - Show info for a specific zone or all zones.");
  player.sendMessage(ChatColor.GOLD + "/" + COMMAND_NAME + " list" + ChatColor.WHITE + " - List all defined zones.");
  player.sendMessage(ChatColor.GOLD + "/" + COMMAND_NAME + " clearwand" + ChatColor.WHITE + " - Clear your wand selections.");
  player.sendMessage(ChatColor.GOLD + "/" + COMMAND_NAME + " reload" + ChatColor.WHITE + " - Reload all zones and banned materials from config.");
  player.sendMessage(ChatColor.GOLD + "/" + COMMAND_NAME + " banned <add|remove|list>" + ChatColor.WHITE + " - Manage globally banned materials.");
  player.sendMessage(ChatColor.YELLOW + "Note: Player with 'autoinform.bypass' permission can bypass all restrictions.");
 }

 /** Formats a Location object into a readable string. */
 private String formatLocation(Location loc) {
  if (loc == null) return "N/A";
  return String.format("X: %.1f, Y: %.1f, Z: %.1f (World: %s)", loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName());
 }

 /** Formats a set of Materials into a comma-separated string. */
 private String formatMaterialList(Set<Material> materials) {
  if (materials.isEmpty()) return "None";
  return materials.stream().map(Enum::name).collect(Collectors.joining(", "));
 }
}

/**
 * Defines the possible actions for a material within a zone.
 */
enum ZoneAction {
 DENY,
 ALERT,
 ALLOW
}

/**
 * Represents a defined AutoInform zone with two corners, a world,
 * a default action, and material-specific actions. This class is immutable.
 */
class AutoInformZone {
 private final String name;
 private final World world;
 private final Location corner1;
 private final Location corner2;
 private final ZoneAction defaultAction;
 private final Map<Material, ZoneAction> materialSpecificActions;

 /**
  * Constructs a new AutoInformZone.
  * @param name The unique name of the zone.
  * @param world The world the zone is in.
  * @param corner1 The first corner of the zone.
  * @param corner2 The second corner of the zone.
  * @param defaultAction The default action for materials not explicitly defined.
  * @param materialSpecificActions A map of materials to their specific actions within this zone.
  */
 public AutoInformZone(@NotNull String name, @NotNull World world, @NotNull Location corner1, @NotNull Location corner2,
                       @NotNull ZoneAction defaultAction, @NotNull Map<Material, ZoneAction> materialSpecificActions) {
  this.name = Objects.requireNonNull(name, "Zone name cannot be null");
  this.world = Objects.requireNonNull(world, "Zone world cannot be null");
  this.corner1 = Objects.requireNonNull(corner1, "Zone corner1 cannot be null");
  this.corner2 = Objects.requireNonNull(corner2, "Zone corner2 cannot be null");
  this.defaultAction = Objects.requireNonNull(defaultAction, "Default action cannot be null");
  this.materialSpecificActions = Collections.unmodifiableMap(new HashMap<>(Objects.requireNonNull(materialSpecificActions, "Material specific actions map cannot be null")));
 }

 public String getName() { return name; }
 public World getWorld() { return world; }
 public Location getCorner1() { return corner1; }
 public Location getCorner2() { return corner2; }
 public ZoneAction getDefaultAction() { return defaultAction; }
 public Map<Material, ZoneAction> getMaterialSpecificActions() { return materialSpecificActions; }

 /** Checks if a given location is within this zone's bounding box. */
 public boolean contains(@NotNull Location loc) {
  if (!loc.getWorld().equals(this.world)) return false;

  double x = loc.getX();
  double y = loc.getY();
  double z = loc.getZ();

  double minX = Math.min(corner1.getX(), corner2.getX());
  double minY = Math.min(corner1.getY(), corner2.getY());
  double minZ = Math.min(corner1.getZ(), corner2.getZ());

  double maxX = Math.max(corner1.getX(), corner2.getX());
  double maxY = Math.max(corner1.getY(), corner2.getY());
  double maxZ = Math.max(corner1.getZ(), corner2.getZ());

  return (x >= minX && x <= maxX) && (y >= minY && y <= maxY) && (z >= minZ && z <= maxZ);
 }

 @Override
 public boolean equals(Object o) {
  if (this == o) return true;
  if (o == null || getClass() != o.getClass()) return false;
  AutoInformZone that = (AutoInformZone) o;
  return defaultAction == that.defaultAction &&
          name.equals(that.name) &&
          world.equals(that.world) &&
          corner1.equals(that.corner1) &&
          corner2.equals(that.corner2) &&
          materialSpecificActions.equals(that.materialSpecificActions);
 }

 @Override
 public int hashCode() {
  return Objects.hash(name, world, corner1, corner2, defaultAction, materialSpecificActions);
 }
}
