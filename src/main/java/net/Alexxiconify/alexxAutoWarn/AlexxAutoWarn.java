package net.Alexxiconify.alexxAutoWarn;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import net.coreprotect.CoreProtectAPI.ParseResult;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
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
 // PLUGIN_PREFIX is now less critical as messages are fully configurable.
 // private static final String PLUGIN_PREFIX = ChatColor.RED + "[AutoInform] " + ChatColor.YELLOW;

 // --- Configurable Messages ---
 private final Map<String, String> messages = new HashMap<>();


 // --- Instance Variables ---
 private CoreProtectAPI coreProtectAPI;
 private final Map<String, AutoInformZone> definedZones = new HashMap<>();
 private final Set<Material> bannedMaterials = new HashSet<>(); // For placement
 private final Set<Material> bannedUsages = new HashSet<>();   // For item usage
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
  loadBannedMaterialsFromConfig(); // Loads both banned-materials and banned-usages

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
  getLogger().info(getMessage("plugin-current-banned-usages").replace("{materials}", formatMaterialList(bannedUsages)));
 }

 @Override
 public void onDisable() {
  getLogger().info(getMessage("plugin-disabled"));
  playerZoneSelections.clear();
  playerWandPos1.clear();
  playerWandPos2.clear();
  definedZones.clear();
  bannedMaterials.clear();
  bannedUsages.clear(); // Clear banned usages too
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

    ZoneAction defaultPlacementAction = ZoneAction.valueOf(zoneConfig.getString("default-material-action", "ALERT").toUpperCase());
    Map<Material, ZoneAction> materialActions = loadMaterialActions(zoneConfig.getConfigurationSection("material-actions"), zoneName, "placement");

    ZoneAction defaultUsageAction = ZoneAction.valueOf(zoneConfig.getString("default-usage-action", "ALERT").toUpperCase());
    Map<Material, ZoneAction> usageActions = loadMaterialActions(zoneConfig.getConfigurationSection("usage-actions"), zoneName, "usage");

    boolean monitorChestAccess = zoneConfig.getBoolean("monitor-chest-access", false);

    definedZones.put(zoneName, new AutoInformZone(zoneName, world, corner1, corner2, defaultPlacementAction, materialActions, defaultUsageAction, usageActions, monitorChestAccess));
    getLogger().info("Loaded zone '" + zoneName + "' in world '" + worldName + "' (Default Placement: " + defaultPlacementAction + ", Default Usage: " + defaultUsageAction + ", Monitor Chests: " + monitorChestAccess + ").");
   } catch (Exception e) {
    getLogger().severe(getMessage("plugin-error-loading-zone-coords").replace("{zone_name}", zoneName).replace("{message}", e.getMessage()));
   }
  }
 }

 /**
  * Helper method to load material/usage actions from config section.
  */
 private Map<Material, ZoneAction> loadMaterialActions(ConfigurationSection section, String zoneName, String type) {
  Map<Material, ZoneAction> actions = new HashMap<>();
  if (section != null) {
   for (String materialName : section.getKeys(false)) {
    try {
     Material material = Material.valueOf(materialName.toUpperCase());
     ZoneAction action = ZoneAction.valueOf(Objects.requireNonNull(section.getString(materialName)).toUpperCase());
     actions.put(material, action);
    } catch (IllegalArgumentException e) {
     getLogger().warning("Invalid material or action type in zone '" + zoneName + "' for " + type + " material '" + materialName + "'. Skipping.");
    }
   }
  }
  return actions;
 }

 /** Saves all currently defined AutoInform zones to config.yml. */
 private void saveZonesToConfig() {
  FileConfiguration config = getConfig();
  config.set("zones", null); // Clear existing zones section to rewrite it

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

    config.set(path + "default-material-action", zone.getDefaultPlacementAction().name());
    saveMaterialActions(config, path + "material-actions", zone.getMaterialSpecificActions());

    config.set(path + "default-usage-action", zone.getDefaultUsageAction().name());
    saveMaterialActions(config, path + "usage-actions", zone.getUsageSpecificActions());

    config.set(path + "monitor-chest-access", zone.shouldMonitorChestAccess());
   }
  }
  saveConfig();
 }

 /** Helper method to save material/usage actions to config section. */
 private void saveMaterialActions(FileConfiguration config, String path, Map<Material, ZoneAction> actions) {
  if (!actions.isEmpty()) {
   actions.forEach((material, action) ->
           config.set(path + "." + material.name(), action.name())
   );
  } else {
   config.set(path, null); // Remove section if empty
  }
 }

 /** Loads banned materials and usages from config.yml. */
 private void loadBannedMaterialsFromConfig() {
  bannedMaterials.clear();
  bannedUsages.clear();

  FileConfiguration config = getConfig();
  loadBannedList(config.getStringList("banned-materials"), bannedMaterials, "banned-materials");
  loadBannedList(config.getStringList("banned-usages"), bannedUsages, "banned-usages");
 }

 /** Helper method to load a banned list from config. */
 private void loadBannedList(List<String> materialNames, Set<Material> targetSet, String configPath) {
  for (String name : materialNames) {
   try {
    targetSet.add(Material.valueOf(name.toUpperCase()));
   } catch (IllegalArgumentException e) {
    getLogger().warning(getMessage("plugin-invalid-banned-material-config").replace("{name}", name).replace("banned-materials", configPath));
   }
  }
 }

 /** Saves currently banned materials and usages to config.yml. */
 private void saveBannedMaterialsToConfig() {
  FileConfiguration config = getConfig();
  config.set("banned-materials", bannedMaterials.stream().map(Enum::name).collect(Collectors.toList()));
  config.set("banned-usages", bannedUsages.stream().map(Enum::name).collect(Collectors.toList()));
  saveConfig();
 }

 /** Attempts to get the CoreProtect API instance. */
 private CoreProtectAPI getCoreProtectAPI() {
  Plugin plugin = Bukkit.getPluginManager().getPlugin("CoreProtect");
  if (plugin instanceof CoreProtect coreProtect) {
   CoreProtectAPI api = coreProtect.getAPI();
   if (api != null && api.isEnabled()) {
    if (api.APIVersion() < 10) { // Adjust API version check as needed
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
  // Read wand material, display name, and lore from config
  FileConfiguration config = getConfig();
  String wandMaterialName = config.getString("settings.wand.material", "WOODEN_AXE");
  String wandDisplayName = ChatColor.translateAlternateColorCodes('&', config.getString("settings.wand.display-name", "&6&lAutoInform Zone Selector Wand"));
  List<String> wandLore = config.getStringList("settings.wand.lore").stream()
          .map(line -> ChatColor.translateAlternateColorCodes('&', line))
          .collect(Collectors.toList());

  Material wandMaterial;
  try {
   wandMaterial = Material.valueOf(wandMaterialName.toUpperCase());
  } catch (IllegalArgumentException e) {
   getLogger().warning("Invalid wand material '" + wandMaterialName + "' in config.yml. Defaulting to WOODEN_AXE.");
   wandMaterial = Material.WOODEN_AXE;
  }

  ItemStack wand = new ItemStack(wandMaterial);
  ItemMeta meta = wand.getItemMeta();
  if (meta != null) {
   meta.setDisplayName(wandDisplayName);
   meta.setLore(wandLore);
   meta.getPersistentDataContainer().set(wandKey, PersistentDataType.STRING, "true");
   wand.setItemMeta(meta);
  }
  return wand;
 }

 /** Checks if an ItemStack is the AutoInform Zone Selector Wand. */
 private boolean isWand(ItemStack item) {
  return item != null && item.hasItemMeta() && Objects.requireNonNull(item.getItemMeta()).getPersistentDataContainer().has(wandKey, PersistentDataType.STRING);
 }

 /**
  * Generic method to process banned actions (placement or usage).
  * @param player The player performing the action.
  * @param location The location where the action occurs.
  * @param material The material involved in the action.
  * @param actionType The type of action (PLACEMENT or USAGE).
  * @return True if the action should be canceled (DENY), false otherwise.
  */
 private boolean processBannedAction(Player player, Location location, Material material, MonitoredActionType actionType) {
  if (player.hasPermission(PERMISSION_BYPASS)) {
   return false;
  }

  Set<Material> globalBannedList = (actionType == MonitoredActionType.PLACEMENT) ? bannedMaterials : bannedUsages;
  if (!globalBannedList.contains(material)) {
   return false;
  }

  AutoInformZone applicableZone = definedZones.values().stream()
          .filter(zone -> zone.contains(location))
          .findFirst()
          .orElse(null);

  if (applicableZone != null) {
   Map<Material, ZoneAction> specificActions = (actionType == MonitoredActionType.PLACEMENT) ?
           applicableZone.getMaterialSpecificActions() : applicableZone.getUsageSpecificActions();
   ZoneAction defaultAction = (actionType == MonitoredActionType.PLACEMENT) ?
           applicableZone.getDefaultPlacementAction() : applicableZone.getDefaultUsageAction();

   ZoneAction action = specificActions.getOrDefault(material, defaultAction);

   if (action == ZoneAction.ALLOW) {
    return false;
   }

   String actionStatus = action == ZoneAction.DENY ? "DENIED" : "ALERTED";
   String logMessage = "Player " + player.getName() + " attempted " + actionType.name().toLowerCase() + " of banned material " + material.name() + " at " + formatLocation(location) + " in protected zone '" + applicableZone.getName() + "'. Action: " + actionStatus + ".";
   getLogger().info(logMessage);

   sendStaffAlert(player, material, applicableZone, location, actionStatus, actionType, null);

   return action == ZoneAction.DENY;
  }
  return false;
 }
 /** test */
 /** Helper to get block placer using CoreProtect. */
 private String getBlockPlacer(Block block) {
  if (coreProtectAPI == null) return null;

  FileConfiguration config = getConfig();
  int lookupTime = config.getInt("settings.coreprotect.lookup-time-seconds", 600); // Default to 600 seconds (10 minutes)

  List<String[]> lookupResult = null;
  try {
   lookupResult = coreProtectAPI.blockLookup(block, (int) (System.currentTimeMillis() / 1000L) - lookupTime);
  } catch (Exception e) {
   getLogger().severe(getMessage("plugin-coreprotect-lookup-failed")
           .replace("{location}", formatLocation(block.getLocation()))
           .replace("{message}", e.getMessage()));
   return null;
  }

  if (lookupResult != null && !lookupResult.isEmpty()) {
   for (String[] result : lookupResult) {
    ParseResult parseResult = coreProtectAPI.parseResult(result);
    if (parseResult.getActionId() == 1) { // ActionId 1 is block placement
     return parseResult.getPlayer();
    }
   }
  }
  return null;
 }

 /** Helper to get block placer using CoreProtect. */
 private String getBlockPlacer(Block block) {
  if (coreProtectAPI == null) return null;

  FileConfiguration config = getConfig();
  int lookupTime = config.getInt("settings.coreprotect.lookup-time-seconds", 600); // Default to 600 seconds (10 minutes)

  List<String[]> lookupResult = null;
  try {
   lookupResult = coreProtectAPI.blockLookup(block, (int) (System.currentTimeMillis() / 1000L) - lookupTime);
  } catch (Exception e) {
   getLogger().severe(getMessage("plugin-coreprotect-lookup-failed")
           .replace("{location}", formatLocation(block.getLocation()))
           .replace("{message}", e.getMessage()));
   return null;
  }

  if (lookupResult != null && !lookupResult.isEmpty()) {
   for (String[] result : lookupResult) {
    ParseResult parseResult = coreProtectAPI.parseResult(result);
    if (parseResult.getActionId() == 1) { // ActionId 1 is block placement
     return parseResult.getPlayer();
    }
   }
  }
  return null;
 }

 /** Processes chest access by non-placers: alerts staff and determines if denied. */
 private boolean processChestAccess(Player player, Block chestBlock) {
  if (player.hasPermission(PERMISSION_BYPASS)) {
   return false;
  }

  AutoInformZone applicableZone = definedZones.values().stream()
          .filter(zone -> zone.contains(chestBlock.getLocation()) && zone.shouldMonitorChestAccess())
          .findFirst()
          .orElse(null);

  if (applicableZone == null) {
   return false;
  }

  if (!(chestBlock.getState() instanceof Container)) {
   return false;
  }

  if (coreProtectAPI == null) {
   getLogger().warning("CoreProtect API is not available for chest access monitoring.");
   return false;
  }

  String placerName = getBlockPlacer(chestBlock);

  if (placerName == null) {
   getLogger().warning(getMessage("plugin-coreprotect-no-placer").replace("{location}", formatLocation(chestBlock.getLocation())));
   return false;
  }

  if (player.getName().equalsIgnoreCase(placerName)) {
   return false;
  }

  String actionStatus = "ALERTED";
  String logMessage = "Player " + player.getName() + " attempted to access chest placed by " + placerName + " at " + formatLocation(chestBlock.getLocation()) + " in protected zone '" + applicableZone.getName() + "'. Action: " + actionStatus + ".";
  getLogger().info(logMessage);

  sendStaffAlert(player, chestBlock.getType(), applicableZone, chestBlock.getLocation(), actionStatus, MonitoredActionType.CHEST_ACCESS, placerName);

  return false; // Do not cancel chest access by default, just alert.
 }

 /** Helper to send formatted staff alerts. */
 private void sendStaffAlert(Player player, Material material, AutoInformZone zone, Location location, String actionStatus, MonitoredActionType type, String placerName) {
  String staffActionColor = actionStatus.equals("DENIED") ? ChatColor.RED.toString() : ChatColor.YELLOW.toString();
  String messageKey;

  switch (type) {
   case PLACEMENT:
    messageKey = "staff-alert-placement";
    break;
   case USAGE:
    messageKey = "staff-alert-usage";
    break;
   case CHEST_ACCESS:
    messageKey = "staff-alert-chest-access";
    break;
   default:
    messageKey = "staff-alert-message"; // Fallback
  }

  String staffMessage = getMessage(messageKey)
          .replace("{player}", player.getName())
          .replace("{material}", material.name())
          .replace("{zone_name}", zone.getName())
          .replace("{x}", String.valueOf(location.getBlockX()))
          .replace("{y}", String.valueOf(location.getBlockY()))
          .replace("{z}", String.valueOf(location.getBlockZ()))
          .replace("{action_color}", staffActionColor)
          .replace("{action_status}", actionStatus);

  if (placerName != null) {
   staffMessage = staffMessage.replace("{placer}", placerName);
  }

  String finalStaffMessage = staffMessage;
  Bukkit.getOnlinePlayers().stream()
          .filter(staff -> staff.hasPermission(PERMISSION_ALERT_RECEIVE))
          .forEach(staff -> staff.sendMessage(finalStaffMessage));
 }

 @EventHandler
 public void onPlayerInteract(PlayerInteractEvent event) {
  Player player = event.getPlayer();
  ItemStack handItem = event.getItem();
  Block clickedBlock = event.getClickedBlock();

  // Handle wand interaction first
  if (isWand(handItem)) {
   event.setCancelled(true);
   if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
    if (clickedBlock != null) {
     playerWandPos1.put(player.getUniqueId(), clickedBlock.getLocation());
     player.sendMessage(getMessage("player-wand-pos1-set").replace("{location}", formatLocation(clickedBlock.getLocation())));
    } else {
     player.sendMessage(getMessage("player-wand-click-block").replace("{pos_num}", "1"));
    }
   } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
    if (clickedBlock != null) {
     playerWandPos2.put(player.getUniqueId(), clickedBlock.getLocation());
     player.sendMessage(getMessage("player-wand-pos2-set").replace("{location}", formatLocation(clickedBlock.getLocation())));
    } else {
     player.sendMessage(getMessage("player-wand-click-block").replace("{pos_num}", "2"));
    }
   }
   return;
  }

  // Handle item usage (right-click air or block with an item that isn't a block placement)
  if (handItem != null && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
   // Special handling for TNT_MINECART placement (which is an item usage leading to placement)
   if (handItem.getType() == Material.TNT_MINECART && clickedBlock != null && clickedBlock.getType().name().contains("RAIL")) {
    Location placementLocation = clickedBlock.getLocation().add(0, 1, 0); // Minecart places above rail
    if (processBannedAction(player, placementLocation, Material.TNT_MINECART, MonitoredActionType.PLACEMENT)) {
     event.setCancelled(true);
     player.sendMessage(getMessage("player-denied-placement").replace("{material}", Material.TNT_MINECART.name()));
     return;
    }
   }
   // General item usage check
   else if (bannedUsages.contains(handItem.getType())) {
    // Corrected: Use clickedBlock.getLocation() if a block was clicked, otherwise player's location.
    Location usageLocation = (clickedBlock != null) ? clickedBlock.getLocation() : player.getLocation();
    if (processBannedAction(player, usageLocation, handItem.getType(), MonitoredActionType.USAGE)) {
     event.setCancelled(true);
     player.sendMessage(getMessage("player-denied-usage").replace("{material}", handItem.getType().name()));
     return;
    }
   }
  }

  // Handle chest access monitoring
  if (event.getAction() == Action.RIGHT_CLICK_BLOCK && clickedBlock != null) {
   if (clickedBlock.getType() == Material.CHEST || clickedBlock.getType() == Material.TRAPPED_CHEST || clickedBlock.getType() == Material.BARREL || clickedBlock.getType().name().endsWith("_SHULKER_BOX")) {
    // processChestAccess currently only alerts, does not cancel.
    processChestAccess(player, clickedBlock);
    // If we later decide to DENY chest access, this method would return true and event.setCancelled(true) would be needed here.
   }
  }
 }

 @EventHandler
 public void onPlayerEmptyBucket(PlayerBucketEmptyEvent event) {
  Location placedLocation = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
  if (processBannedAction(event.getPlayer(), placedLocation, event.getBucket(), MonitoredActionType.PLACEMENT)) {
   event.setCancelled(true);
   event.getPlayer().sendMessage(getMessage("player-denied-placement").replace("{material}", event.getBucket().name()));
  }
 }

 @EventHandler
 public void onBlockPlace(BlockPlaceEvent event) {
  if (processBannedAction(event.getPlayer(), event.getBlock().getLocation(), event.getBlock().getType(), MonitoredActionType.PLACEMENT)) {
   event.setCancelled(true);
   event.getPlayer().sendMessage(getMessage("player-denied-placement").replace("{material}", event.getBlock().getType().name()));
  }
 }

 private void handleDefineCommand(Player player, String[] args) {
  if (args.length < 2) {
   player.sendMessage(getMessage("player-command-usage").replace("{usage}", "/" + COMMAND_NAME + " define <zone_name>"));
   return;
  }
  String zoneToDefine = args[1];
  Location p1 = playerWandPos1.get(player.getUniqueId());
  Location p2 = playerWandPos2.get(player.getUniqueId());

  // Attempt to get positions from manual selections if wand selections are missing
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

  // Preserve existing settings or use defaults from config
  AutoInformZone existingZone = definedZones.get(zoneToDefine);
  FileConfiguration config = getConfig();

  ZoneAction currentDefaultPlacementAction = existingZone != null ? existingZone.getDefaultPlacementAction() :
          ZoneAction.valueOf(config.getString("settings.default-new-zone-settings.default-placement-action", "ALERT").toUpperCase());
  Map<Material, ZoneAction> currentMaterialActions = existingZone != null ? new HashMap<>(existingZone.getMaterialSpecificActions()) : new HashMap<>();

  ZoneAction currentDefaultUsageAction = existingZone != null ? existingZone.getDefaultUsageAction() :
          ZoneAction.valueOf(config.getString("settings.default-new-zone-settings.default-usage-action", "ALERT").toUpperCase());
  Map<Material, ZoneAction> currentUsageActions = existingZone != null ? new HashMap<>(existingZone.getUsageSpecificActions()) : new HashMap<>();

  boolean currentMonitorChestAccess = existingZone != null ? existingZone.shouldMonitorChestAccess() :
          config.getBoolean("settings.default-new-zone-settings.monitor-chest-access", false);


  definedZones.put(zoneToDefine, new AutoInformZone(zoneToDefine, p1.getWorld(), p1, p2,
          currentDefaultPlacementAction, currentMaterialActions,
          currentDefaultUsageAction, currentUsageActions,
          currentMonitorChestAccess));
  saveZonesToConfig();
  player.sendMessage(getMessage("player-define-zone-success")
          .replace("{zone_name}", zoneToDefine)
          .replace("{world_name}", p1.getWorld().getName()));
  player.sendMessage(getMessage("player-define-zone-corners")
          .replace("{corner1_loc}", formatLocation(p1))
          .replace("{corner2_loc}", formatLocation(p2)));
  player.sendMessage(getMessage("player-define-zone-default-placement-action").replace("{default_action}", currentDefaultPlacementAction.name()));
  player.sendMessage(getMessage("player-define-zone-default-usage-action").replace("{default_action}", currentDefaultUsageAction.name()));
  player.sendMessage(getMessage("player-zone-info-monitor-chest-access").replace("{status}", String.valueOf(currentMonitorChestAccess)));

  playerWandPos1.remove(player.getUniqueId());
  playerWandPos2.remove(player.getUniqueId());
  if (playerZoneSelections.get(player.getUniqueId()) != null) {
   playerZoneSelections.get(player.getUniqueId()).remove(zoneToDefine);
   if (playerZoneSelections.get(player.getUniqueId()).isEmpty()) {
    playerZoneSelections.remove(player.getUniqueId());
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
   case "define":
    handleDefineCommand(player, args);
    break;
   case "remove":
    handleRemoveCommand(player, args);
    break;
   case "info":
    handleInfoCommand(player, args); break; // Pass args directly
   case "list": handleListCommand(player); break;
   case "reload":
    handleReloadCommand(player);
    break;
   case "clearwand":
    handleClearWandCommand(player);
    break;
   case "banned":
    handleBannedCommand(player, args, "placement");
    break;
   case "bannedusage":
    handleBannedCommand(player, args, "usage"); break;
   case "defaultaction": handleDefaultActionCommand(player, args);
    break;
   case "setaction":
    handleSetActionCommand(player, args);
    break;
   case "monitorchest": handleMonitorChestCommand(player, args); break;
   default: sendHelpMessage(player); break;
  }
  return true;
 }

 private void handleListCommand(Player player) {
  if (definedZones.isEmpty()) {
   player.sendMessage(getMessage("player-no-zones-defined"));
  } else {
   player.sendMessage(getMessage("player-defined-zones-header"));
   definedZones.keySet().forEach(zoneName -> player.sendMessage(ChatColor.GOLD + "- " + zoneName));
  }
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

 private void handleInfoCommand(Player player, String[] args) { // Now takes args
  String zoneInfoName = null;
  if (args.length >= 2) { // If a zone name is provided as the second argument
   zoneInfoName = args[1];
  }

  if (zoneInfoName != null) {
   AutoInformZone zoneInfo = definedZones.get(zoneInfoName);
   if (zoneInfo != null) {
    player.sendMessage(getMessage("player-zone-info-header").replace("{zone_name}", zoneInfo.getName()));
    player.sendMessage(getMessage("player-zone-info-world").replace("{world_name}", zoneInfo.getWorld().getName()));
    player.sendMessage(getMessage("player-zone-info-corner1").replace("{corner1_loc}", formatLocation(zoneInfo.getCorner1())));
    player.sendMessage(getMessage("player-zone-info-corner2").replace("{corner2_loc}", formatLocation(zoneInfo.getCorner2())));
    player.sendMessage(getMessage("player-zone-info-default-placement-action").replace("{default_action}", zoneInfo.getDefaultPlacementAction().name()));
    player.sendMessage(getMessage("player-zone-info-default-usage-action").replace("{default_action}", zoneInfo.getDefaultUsageAction().name()));
    player.sendMessage(getMessage("player-zone-info-monitor-chest-access").replace("{status}", String.valueOf(zoneInfo.shouldMonitorChestAccess())));

    if (!zoneInfo.getMaterialSpecificActions().isEmpty()) {
     player.sendMessage(getMessage("player-zone-info-material-actions"));
     zoneInfo.getMaterialSpecificActions().forEach((material, action) ->
             player.sendMessage(getMessage("player-zone-info-material-action-entry")
                     .replace("{material}", material.name())
                     .replace("{action}", action.name()))
     );
    }
    if (!zoneInfo.getUsageSpecificActions().isEmpty()) {
     player.sendMessage(getMessage("player-zone-info-usage-actions"));
     zoneInfo.getUsageSpecificActions().forEach((material, action) ->
             player.sendMessage(getMessage("player-zone-info-usage-action-entry")
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
            player.sendMessage(ChatColor.GOLD + "- " + zone.getName() + ": " + ChatColor.WHITE + "World: " + zone.getWorld().getName() +
                    ", Placement: " + zone.getDefaultPlacementAction() +
                    ", Usage: " + zone.getDefaultUsageAction() +
                    ", Chests: " + zone.shouldMonitorChestAccess())
    );
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

 private void handleClearWandCommand(Player player) {
  playerWandPos1.remove(player.getUniqueId());
  playerWandPos2.remove(player.getUniqueId());
  player.sendMessage(getMessage("player-wand-selections-cleared"));
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
  sender.sendMessage(getMessage("plugin-current-banned-usages").replace("{materials}", formatMaterialList(bannedUsages)));
 }

 /** Handles banned material/usage commands (add/remove/list). */
 private void handleBannedCommand(Player player, String[] args, String type) {
  Set<Material> targetList = type.equals("placement") ? bannedMaterials : bannedUsages;
  String headerMessageKey = type.equals("placement") ? "player-banned-materials-header" : "player-banned-usages-header";
  String noMaterialsBannedKey = type.equals("placement") ? "player-no-materials-banned" : "player-no-usages-banned";
  String usageCommandBase = "/" + COMMAND_NAME + " banned" + (type.equals("usage") ? "usage" : "") + " ";


  if (args.length < 2) {
   player.sendMessage(getMessage("player-command-usage").replace("{usage}", usageCommandBase + "<add|remove|list> [material_name]"));
   return;
  }
  String bannedAction = args[1].toLowerCase();
  switch (bannedAction) {
   case "add":
    if (args.length < 3) {
     player.sendMessage(getMessage("player-command-usage").replace("{usage}", usageCommandBase + "add <material_name>"));
     return;
    }
    String materialToAdd = args[2].toUpperCase();
    try {
     Material material = Material.valueOf(materialToAdd);
     if (targetList.add(material)) {
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
     player.sendMessage(getMessage("player-command-usage").replace("{usage}", usageCommandBase + "remove <material_name>"));
     return;
    }
    String materialToRemove = args[2].toUpperCase();
    try {
     Material material = Material.valueOf(materialToRemove);
     if (targetList.remove(material)) {
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
    if (targetList.isEmpty()) {
     player.sendMessage(getMessage(noMaterialsBannedKey));
    } else {
     player.sendMessage(getMessage(headerMessageKey));
     player.sendMessage(ChatColor.WHITE + formatMaterialList(targetList));
    }
    break;
   default:
    player.sendMessage(getMessage("player-command-usage").replace("{usage}", usageCommandBase + "<add|remove|list>"));
    break;
  }
 }

 /**
  * Enum to differentiate action types for generic processing.
  */
 private enum MonitoredActionType {
  PLACEMENT, USAGE, CHEST_ACCESS
 }

 private void handleDefaultActionCommand(Player player, String[] args) {
  if (args.length < 4) {
   player.sendMessage(getMessage("player-command-usage").replace("{usage}", "/" + COMMAND_NAME + " defaultaction <zone_name> <placement|usage> <DENY|ALERT|ALLOW>"));
   return;
  }
  String zoneName = args[1];
  String actionType = args[2].toLowerCase();
  String actionString = args[3].toUpperCase();
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

  AutoInformZone updatedZone;
  if (actionType.equals("placement")) {
   updatedZone = new AutoInformZone(
           existingZone.getName(), existingZone.getWorld(), existingZone.getCorner1(), existingZone.getCorner2(),
           newDefaultAction, existingZone.getMaterialSpecificActions(),
           existingZone.getDefaultUsageAction(), existingZone.getUsageSpecificActions(),
           existingZone.shouldMonitorChestAccess()
   );
   player.sendMessage(getMessage("player-default-action-updated").replace("{type}", "placement").replace("{zone_name}", zoneName).replace("{action}", newDefaultAction.name()));
  } else if (actionType.equals("usage")) {
   updatedZone = new AutoInformZone(
           existingZone.getName(), existingZone.getWorld(), existingZone.getCorner1(), existingZone.getCorner2(),
           existingZone.getDefaultPlacementAction(), existingZone.getMaterialSpecificActions(),
           newDefaultAction, existingZone.getUsageSpecificActions(),
           existingZone.shouldMonitorChestAccess()
   );
   player.sendMessage(getMessage("player-default-action-updated").replace("{type}", "usage").replace("{zone_name}", zoneName).replace("{action}", newDefaultAction.name()));
  } else {
   player.sendMessage(getMessage("player-command-usage").replace("{usage}", "/" + COMMAND_NAME + " defaultaction <zone_name> <placement|usage> <DENY|ALERT|ALLOW>"));
   return;
  }

  definedZones.put(zoneName, updatedZone);
  saveZonesToConfig();
 }

 private void handleSetActionCommand(Player player, String[] args) {
  if (args.length < 5) {
   player.sendMessage(getMessage("player-command-usage").replace("{usage}", "/" + COMMAND_NAME + " setaction <zone_name> <placement|usage> <material> <DENY|ALERT|ALLOW>"));
   return;
  }
  String zoneName = args[1];
  String actionType = args[2].toLowerCase();
  String materialName = args[3].toUpperCase();
  String actionString = args[4].toUpperCase();

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

  AutoInformZone updatedZone;
  if (actionType.equals("placement")) {
   Map<Material, ZoneAction> updatedMaterialActions = new HashMap<>(existingZone.getMaterialSpecificActions());
   updatedMaterialActions.put(material, action);
   updatedZone = new AutoInformZone(
           existingZone.getName(), existingZone.getWorld(), existingZone.getCorner1(), existingZone.getCorner2(),
           existingZone.getDefaultPlacementAction(), updatedMaterialActions,
           existingZone.getDefaultUsageAction(), existingZone.getUsageSpecificActions(),
           existingZone.shouldMonitorChestAccess()
   );
   player.sendMessage(getMessage("player-material-action-updated").replace("{type}", "Placement").replace("{material}", material.name()).replace("{zone_name}", zoneName).replace("{action}", action.name()));
  } else if (actionType.equals("usage")) {
   Map<Material, ZoneAction> updatedUsageActions = new HashMap<>(existingZone.getUsageSpecificActions());
   updatedUsageActions.put(material, action);
   updatedZone = new AutoInformZone(
           existingZone.getName(), existingZone.getWorld(), existingZone.getCorner1(), existingZone.getCorner2(),
           existingZone.getDefaultPlacementAction(), existingZone.getMaterialSpecificActions(),
           existingZone.getDefaultUsageAction(), updatedUsageActions,
           existingZone.shouldMonitorChestAccess()
   );
   player.sendMessage(getMessage("player-material-action-updated").replace("{type}", "Usage").replace("{material}", material.name()).replace("{zone_name}", zoneName).replace("{action}", action.name()));
  } else {
   player.sendMessage(getMessage("player-command-usage").replace("{usage}", "/" + COMMAND_NAME + " setaction <zone_name> <placement|usage> <material> <DENY|ALERT|ALLOW>"));
   return;
  }

  definedZones.put(zoneName, updatedZone);
  saveZonesToConfig();
 }

 /** Handles the monitorchest command. */
 private void handleMonitorChestCommand(Player player, String[] args) {
  if (args.length < 3) {
   player.sendMessage(getMessage("player-command-usage").replace("{usage}", "/" + COMMAND_NAME + " monitorchest <zone_name> <true|false>"));
   return;
  }
  String zoneName = args[1];
  String valueString = args[2].toLowerCase();
  boolean monitorValue;

  if ("true".equals(valueString)) {
   monitorValue = true;
  } else if ("false".equals(valueString)) {
   monitorValue = false;
  } else {
   player.sendMessage(getMessage("player-command-usage").replace("{usage}", "/" + COMMAND_NAME + " monitorchest <zone_name> <true|false>"));
   return;
  }

  AutoInformZone existingZone = definedZones.get(zoneName);
  if (existingZone == null) {
   player.sendMessage(getMessage("player-zone-not-found").replace("{zone_name}", zoneName));
   return;
  }

  AutoInformZone updatedZone = new AutoInformZone(
          existingZone.getName(), existingZone.getWorld(), existingZone.getCorner1(), existingZone.getCorner2(),
          existingZone.getDefaultPlacementAction(), existingZone.getMaterialSpecificActions(),
          existingZone.getDefaultUsageAction(), existingZone.getUsageSpecificActions(),
          monitorValue
  );
  definedZones.put(zoneName, updatedZone);
  saveZonesToConfig();
  player.sendMessage(getMessage("player-zone-info-monitor-chest-access").replace("{status}", String.valueOf(monitorValue)).replace("Monitor Chest Access", "Monitoring chest access for zone '" + zoneName + "' set to"));
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
   return Arrays.asList("wand", "pos1", "pos2", "define", "remove", "info", "list", "reload", "clearwand", "banned", "bannedusage", "defaultaction", "setaction", "monitorchest").stream()
           .filter(s -> s.startsWith(args[0].toLowerCase()))
           .collect(Collectors.toList());
  } else if (args.length == 2) {
   String subCommand = args[0].toLowerCase();
   if (Arrays.asList("pos1", "pos2", "define", "remove", "info", "defaultaction", "setaction", "monitorchest").contains(subCommand)) {
    return definedZones.keySet().stream()
            .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
            .collect(Collectors.toList());
   } else if (subCommand.equals("banned") || subCommand.equals("bannedusage")) {
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
   } else if (subCommand.equals("bannedusage")) {
    String bannedAction = args[1].toLowerCase();
    if (bannedAction.equals("add")) {
     return Arrays.stream(Material.values()).map(Enum::name).filter(s -> s.startsWith(args[2].toUpperCase())).collect(Collectors.toList());
    } else if (bannedAction.equals("remove")) {
     return bannedUsages.stream().map(Enum::name).filter(s -> s.startsWith(args[2].toUpperCase())).collect(Collectors.toList());
    }
   } else if (subCommand.equals("defaultaction") || subCommand.equals("setaction")) {
    return Arrays.asList("placement", "usage").stream()
            .filter(s -> s.startsWith(args[2].toLowerCase()))
            .collect(Collectors.toList());
   } else if (subCommand.equals("monitorchest")) {
    return Arrays.asList("true", "false").stream()
            .filter(s -> s.startsWith(args[2].toLowerCase()))
            .collect(Collectors.toList());
   }
  } else if (args.length == 4) {
   String subCommand = args[0].toLowerCase();
   if (subCommand.equals("defaultaction")) {
    return Arrays.asList("DENY", "ALERT", "ALLOW").stream()
            .filter(s -> s.startsWith(args[3].toUpperCase()))
            .collect(Collectors.toList());
   } else if (subCommand.equals("setaction")) {
    String actionType = args[2].toLowerCase();
    if (actionType.equals("placement") || actionType.equals("usage")) {
     return Arrays.stream(Material.values()).map(Enum::name).filter(s -> s.startsWith(args[3].toUpperCase())).collect(Collectors.toList());
    }
   }
  } else if (args.length == 5 && args[0].equalsIgnoreCase("setaction")) {
   String actionType = args[2].toLowerCase();
   if (actionType.equals("placement") || actionType.equals("usage")) {
    return Arrays.asList("DENY", "ALERT", "ALLOW").stream()
            .filter(s -> s.startsWith(args[4].toUpperCase()))
            .collect(Collectors.toList());
   }
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
  player.sendMessage(ChatColor.GOLD + "/" + COMMAND_NAME + " <zone_name> defaultaction <placement|usage> <DENY|ALERT|ALLOW>" + ChatColor.WHITE + " - Set default action for a zone (placement or usage).");
  player.sendMessage(ChatColor.GOLD + "/" + COMMAND_NAME + " <zone_name> setaction <placement|usage> <material> <DENY|ALERT|ALLOW>" + ChatColor.WHITE + " - Set specific material action in a zone.");
  player.sendMessage(ChatColor.GOLD + "/" + COMMAND_NAME + " <zone_name> monitorchest <true|false>" + ChatColor.WHITE + " - Toggle monitoring chest access by non-placers in a zone.");
  player.sendMessage(ChatColor.GOLD + "/" + COMMAND_NAME + " remove <zone_name>" + ChatColor.WHITE + " - Remove a defined zone.");
  player.sendMessage(ChatColor.GOLD + "/" + COMMAND_NAME + " info [zone_name]" + ChatColor.WHITE + " - Show info for a specific zone or all zones.");
  player.sendMessage(ChatColor.GOLD + "/" + COMMAND_NAME + " list" + ChatColor.WHITE + " - List all defined zones.");
  player.sendMessage(ChatColor.GOLD + "/" + COMMAND_NAME + " clearwand" + ChatColor.WHITE + " - Clear your wand selections.");
  player.sendMessage(ChatColor.GOLD + "/" + COMMAND_NAME + " reload" + ChatColor.WHITE + " - Reload all zones and banned materials from config.");
  player.sendMessage(ChatColor.GOLD + "/" + COMMAND_NAME + " banned <add|remove|list>" + ChatColor.WHITE + " - Manage globally banned materials (placement).");
  player.sendMessage(ChatColor.GOLD + "/" + COMMAND_NAME + " bannedusage <add|remove|list>" + ChatColor.WHITE + " - Manage globally banned materials (usage).");
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
 * default actions for placement and usage, material-specific actions for both,
 * and a flag for monitoring chest access. This class is immutable.
 */
class AutoInformZone {
 private final String name;
 private final World world;
 private final Location corner1;
 private final Location corner2;
 private final ZoneAction defaultPlacementAction;
 private final Map<Material, ZoneAction> materialSpecificActions; // For placement
 private final ZoneAction defaultUsageAction; // New: For usage
 private final Map<Material, ZoneAction> usageSpecificActions;   // New: For usage
 private final boolean monitorChestAccess; // New: For chest access

 /**
  * Constructs a new AutoInformZone.
  * @param name The unique name of the zone.
  * @param world The world the zone is in.
  * @param corner1 The first corner of the zone.
  * @param corner2 The second corner of the zone.
  * @param defaultPlacementAction The default action for materials not explicitly defined for placement.
  * @param materialSpecificActions A map of materials to their specific actions for placement within this zone.
  * @param defaultUsageAction The default action for materials not explicitly defined for usage.
  * @param usageSpecificActions A map of materials to their specific actions for usage within this zone.
  * @param monitorChestAccess True if chest access by non-placers should be monitored in this zone.
  */
 public AutoInformZone(@NotNull String name, @NotNull World world, @NotNull Location corner1, @NotNull Location corner2,
                       @NotNull ZoneAction defaultPlacementAction, @NotNull Map<Material, ZoneAction> materialSpecificActions,
                       @NotNull ZoneAction defaultUsageAction, @NotNull Map<Material, ZoneAction> usageSpecificActions,
                       boolean monitorChestAccess) {
  this.name = Objects.requireNonNull(name, "Zone name cannot be null");
  this.world = Objects.requireNonNull(world, "Zone world cannot be null");
  this.corner1 = Objects.requireNonNull(corner1, "Zone corner1 cannot be null");
  this.corner2 = Objects.requireNonNull(corner2, "Zone corner2 cannot be null");
  this.defaultPlacementAction = Objects.requireNonNull(defaultPlacementAction, "Default placement action cannot be null");
  this.materialSpecificActions = Collections.unmodifiableMap(new HashMap<>(Objects.requireNonNull(materialSpecificActions, "Material specific actions map cannot be null")));
  this.defaultUsageAction = Objects.requireNonNull(defaultUsageAction, "Default usage action cannot be null");
  this.usageSpecificActions = Collections.unmodifiableMap(new HashMap<>(Objects.requireNonNull(usageSpecificActions, "Usage specific actions map cannot be null")));
  this.monitorChestAccess = monitorChestAccess;
 }

 public String getName() { return name;
 }

 public World getWorld() {
  return world;
 }

 public Location getCorner1() {
  return corner1;
 }

 public Location getCorner2() {
  return corner2;
 }

 public ZoneAction getDefaultPlacementAction() {
  return defaultPlacementAction;
 }

 public Map<Material, ZoneAction> getMaterialSpecificActions() {
  return materialSpecificActions;
 }

 public ZoneAction getDefaultUsageAction() {
  return defaultUsageAction;
 }

 public Map<Material, ZoneAction> getUsageSpecificActions() {
  return usageSpecificActions; }
 public boolean shouldMonitorChestAccess() { return monitorChestAccess; }

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
  return defaultPlacementAction == that.defaultPlacementAction &&
          defaultUsageAction == that.defaultUsageAction &&
          monitorChestAccess == that.monitorChestAccess &&
          name.equals(that.name) &&
          world.equals(that.world) &&
          corner1.equals(that.corner1) &&
          corner2.equals(that.corner2) &&
          materialSpecificActions.equals(that.materialSpecificActions) &&
          usageSpecificActions.equals(that.usageSpecificActions);
 }

 @Override
 public int hashCode() {
  return Objects.hash(name, world, corner1, corner2, defaultPlacementAction, materialSpecificActions, defaultUsageAction, usageSpecificActions, monitorChestAccess);
 }
}