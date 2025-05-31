package net.Alexxiconify.alexxAutoWarn.AlexxsAutoWarn;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Action;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

// Note: The STR."..." syntax is a Java 21+ String Template feature.
// If your server uses an older Java version, you'll need to change these to traditional string concatenation.
// e.g., getLogger().severe("World '" + worldName + "' not found for zone definition!");

/**
 * Represents a defined AutoInform zone with two corners, a world, and a denial setting.
 * This class is immutable.
 */
class AutoInformZone { // This class remains as it was, handling material-based actions
 private final String name;
 private final World world;
 private final Location corner1;
 private final Location corner2;
 private final boolean denyPlacement; // This field is for the old deny-placement logic

 /**
  * Constructs a new AutoInformZone.
  * @param name The unique name of the zone.
  * @param world The world the zone is in.
  * @param corner1 The first corner of the zone.
  * @param corner2 The second corner of the zone.
  * @param denyPlacement True if placement should be denied in this zone, false for alerts only.
  * @throws NullPointerException if world, corner1, or corner2 are null.
  */
 public AutoInformZone(@NotNull String name, @NotNull World world, @NotNull Location corner1, @NotNull Location corner2, boolean denyPlacement) {
  this.name = Objects.requireNonNull(name, "Zone name cannot be null");
  this.world = Objects.requireNonNull(world, "Zone world cannot be null");
  this.corner1 = Objects.requireNonNull(corner1, "Zone corner1 cannot be null");
  this.corner2 = Objects.requireNonNull(corner2, "Zone corner2 cannot be null");
  this.denyPlacement = denyPlacement;
 }

 public String getName() { return name; }
 public World getWorld() { return world; }
 public Location getCorner1() { return corner1; }
 public Location getCorner2() { return corner2; }
 public boolean shouldDenyPlacement() { return denyPlacement; }

 /**
  * Checks if a given location is within this zone's bounding box.
  * The location must be in the same world as the zone.
  * @param loc The location to check.
  * @return true if the location is within the zone, false otherwise.
  */
 public boolean contains(@NotNull Location loc) {
  // Must be in the same world
  if (!loc.getWorld().equals(this.world)) {
   return false;
  }

  double x = loc.getX();
  double y = loc.getY();
  double z = loc.getZ();

  double minX = Math.min(corner1.getX(), corner2.getX());
  double minY = Math.min(corner1.getY(), corner2.getY());
  double minZ = Math.min(corner1.getZ(), corner2.getZ());

  double maxX = Math.max(corner1.getX(), corner2.getX());
  double maxY = Math.max(corner1.getY(), corner2.getY());
  double maxZ = Math.max(corner1.getZ(), corner2.getZ());

  // Check if the location is within the bounding box defined by the two corners
  return (x >= minX && x <= maxX) &&
          (y >= minY && y <= maxY) &&
          (z >= minZ && z <= maxZ);
 }

 @Override
 public boolean equals(Object o) {
  if (this == o) return true;
  if (o == null || getClass() != o.getClass()) return false;
  AutoInformZone that = (AutoInformZone) o;
  return denyPlacement == that.denyPlacement &&
          name.equals(that.name) &&
          world.equals(that.world) &&
          corner1.equals(that.corner1) &&
          corner2.equals(that.corner2);
 }

 @Override
 public int hashCode() {
  return Objects.hash(name, world, corner1, corner2, denyPlacement);
 }
}

@SuppressWarnings("ALL") // Suppress warnings for Java 21+ preview features like String Templates
public class AlexxAutoWarn extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

 // --- Plugin Constants ---
 private static final String COMMAND_NAME = "autoinform"; // Changed command name
 private static final String PERMISSION_ADMIN_SET = "autoinform.admin.set";
 private static final String PERMISSION_ALERT_RECEIVE = "autoinform.alert.receive";
 private static final String WAND_KEY_STRING = "ainform_wand";
 private static final String WAND_DISPLAY_NAME = ChatColor.GOLD + "" + ChatColor.BOLD + "AutoInform Zone Selector Wand";
 private static final List<String> WAND_LORE = Arrays.asList(
         ChatColor.GRAY + "Left-click: Set Position 1",
         ChatColor.GRAY + "Right-click: Set Position 2"
 );
 private static final String PLUGIN_PREFIX = ChatColor.RED + "[AutoInform] " + ChatColor.YELLOW;


 // --- Instance Variables ---
 private CoreProtectAPI coreProtectAPI;
 private final Map<String, AutoInformZone> definedZones = new HashMap<>(); // For material-based zones
 private final Set<Material> bannedMaterials = new HashSet<>(); // Global banned materials list

 // --- New fields for Region management (from Region.java, RegionSelection.java) ---
 private Map<String, Region> regions; // Stores all defined regions by name
 private Map<UUID, RegionSelection> playerSelections; // Stores ongoing region selections for players

 private final Map<UUID, Map<String, Map<String, Location>>> playerZoneSelections = new HashMap<>(); // Manual selections (for old zones)
 private final Map<UUID, Location> playerWandPos1 = new HashMap<>(); // Wand selections (for both zone types)
 private final Map<UUID, Location> playerWandPos2 = new HashMap<>(); // Wand selections (for both zone types)
 private NamespacedKey wandKey;

 // --- Custom config for messages.yml ---
 private File customConfigFile;
 private FileConfiguration customConfig;


 @Override
 public void onEnable() {
  getLogger().info("AlexxAutoWarn plugin starting...");

  // Register custom serializers for configuration.
  // IMPORTANT: Must be called before loading config.
  ConfigurationSerialization.registerClass(Region.class, "AlexxAutoWarnRegion");

  this.wandKey = new NamespacedKey(this, WAND_KEY_STRING);
  this.coreProtectAPI = getCoreProtectAPI();

  if (this.coreProtectAPI == null) {
   getLogger().warning("CoreProtect API not found! Related functionality might be affected.");
  } else {
   getLogger().info("CoreProtect API hooked successfully.");
  }

  saveDefaultConfig(); // Creates config.yml if it doesn't exist
  loadCustomConfig(); // Load messages.yml

  loadZonesFromConfig(); // Load AutoInformZones
  loadBannedMaterialsFromConfig(); // Load global banned materials

  // --- Initialize and load new Region system ---
  regions = new HashMap<>();
  playerSelections = new HashMap<>();
  loadRegions(); // Load Regions from config

  // Register this class as the main listener for all events (including region events)
  Bukkit.getPluginManager().registerEvents(this, this);

  Objects.requireNonNull(getCommand(COMMAND_NAME)).setExecutor(this);
  Objects.requireNonNull(getCommand(COMMAND_NAME)).setTabCompleter(this);

  getLogger().info("AlexxAutoWarn plugin enabled!");
  if (definedZones.isEmpty()) {
   getLogger().warning(getMessage("plugin-warning-no-zones-old").replace("{command}", COMMAND_NAME));
  } else {
   getLogger().info(getMessage("plugin-success-zones-loaded-old").replace("{count}", String.valueOf(definedZones.size())));
  }
  if (regions.isEmpty()) {
   getLogger().warning(getMessage("plugin-warning-no-regions"));
  } else {
   getLogger().info(getMessage("plugin-success-regions-loaded").replace("{count}", String.valueOf(regions.size())));
  }
  getLogger().info(getMessage("plugin-current-banned-materials").replace("{materials}", formatMaterialList(bannedMaterials)));
 }

 @Override
 public void onDisable() {
  getLogger().info("AlexxAutoWarn plugin shutting down...");
  playerZoneSelections.clear();
  playerWandPos1.clear();
  playerWandPos2.clear();
  definedZones.clear();
  bannedMaterials.clear();
  playerSelections.clear();
  saveRegions(); // Save regions on disable
  regions.clear();
  getLogger().info("AlexxAutoWarn plugin disabled!");
 }

 /**
  * Loads all AutoInform zones from the plugin's config.yml.
  */
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
    Location corner1 = new Location(world,
            zoneConfig.getDouble("corner1.x"),
            zoneConfig.getDouble("corner1.y"),
            zoneConfig.getDouble("corner1.z"));
    Location corner2 = new Location(world,
            zoneConfig.getDouble("corner2.x"),
            zoneConfig.getDouble("corner2.y"),
            zoneConfig.getDouble("corner2.z"));
    boolean denyPlacement = zoneConfig.getBoolean("deny-placement", false); // Read new field, default to false

    definedZones.put(zoneName, new AutoInformZone(zoneName, world, corner1, corner2, denyPlacement));
    getLogger().info(STR."Loaded zone '\{zoneName}' in world '\{worldName}' (Deny: \{denyPlacement}).");
   } catch (Exception e) {
    getLogger().severe(getMessage("plugin-error-loading-zone-coords").replace("{zone_name}", zoneName).replace("{message}", e.getMessage()));
   }
  }
 }

 /**
  * Saves all currently defined AutoInform zones to the plugin's config.yml.
  */
 private void saveZonesToConfig() {
  FileConfiguration config = getConfig();
  config.set("zones", null); // Clear existing zones section to rewrite it

  if (!definedZones.isEmpty()) {
   for (AutoInformZone zone : definedZones.values()) {
    String path = STR."zones.\{zone.getName()}.";
    config.set(path + "world", zone.getWorld().getName());
    config.set(path + "corner1.x", zone.getCorner1().getX());
    config.set(path + "corner1.y", zone.getCorner1().getY());
    config.set(path + "corner1.z", zone.getCorner1().getZ());
    config.set(path + "corner2.x", zone.getCorner2().getX());
    config.set(path + "corner2.y", zone.getCorner2().getY());
    config.set(path + "corner2.z", zone.getCorner2().getZ());
    config.set(path + "deny-placement", zone.shouldDenyPlacement()); // Save new field
   }
  }
  saveConfig();
 }

 /**
  * Loads banned materials from the plugin's config.yml.
  */
 private void loadBannedMaterialsFromConfig() {
  bannedMaterials.clear();
  FileConfiguration config = getConfig();
  List<String> materialNames = config.getStringList("banned-materials");

  for (String name : materialNames) {
   try {
    Material material = Material.valueOf(name.toUpperCase());
    bannedMaterials.add(material);
   } catch (IllegalArgumentException e) {
    getLogger().warning(getMessage("plugin-invalid-banned-material-config").replace("{name}", name));
   }
  }
 }

 /**
  * Saves currently banned materials to the plugin's config.yml.
  */
 private void saveBannedMaterialsToConfig() {
  FileConfiguration config = getConfig();
  List<String> materialNames = bannedMaterials.stream()
          .map(Enum::name)
          .collect(Collectors.toList());
  config.set("banned-materials", materialNames);
  saveConfig();
 }

 // --- New methods for region management ---

 // Load regions from config.yml
 private void loadRegions() {
  // Ensure the "regions" section is separate from "zones"
  if (getConfig().isConfigurationSection("regions")) {
   for (String key : getConfig().getConfigurationSection("regions").getKeys(false)) {
    try {
     Object obj = getConfig().get("regions." + key);
     if (obj instanceof Map) {
      Map<String, Object> serializedRegion = (Map<String, Object>) obj;
      Region region = (Region) ConfigurationSerialization.deserializeObject(serializedRegion, Region.class);
      regions.put(region.getName(), region);
      getLogger().info("Loaded region: " + region.getName());
     } else {
      getLogger().warning("Invalid region format for key: " + key);
     }
    } catch (Exception e) {
     getLogger().log(Level.SEVERE, "Failed to load region: " + key, e);
    }
   }
  }
 }

 // Save regions to config.yml
 private void saveRegions() {
  getConfig().set("regions", null); // Clear existing regions section to prevent old data
  for (Region region : regions.values()) {
   getConfig().set("regions." + region.getName(), region); // Serialize Region object to config
  }
  saveConfig(); // Save the main config.yml
 }


 /**
  * Attempts to get the CoreProtect API instance.
  * @return The CoreProtectAPI instance, or null if not found or not enabled.
  */
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

 /**
  * Creates and returns the AutoInform Zone Selector Wand item.
  * @return The ItemStack representing the wand.
  */
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

 /**
  * Checks if an ItemStack is the AutoInform Zone Selector Wand.
  * @param item The ItemStack to check.
  * @return true if it's the wand, false otherwise.
  */
 private boolean isWand(ItemStack item) {
  return item != null && item.hasItemMeta() &&
          Objects.requireNonNull(item.getItemMeta()).getPersistentDataContainer().has(wandKey, PersistentDataType.STRING);
 }

 /**
  * Helper method to check if a location is within any defined AutoInformZone,
  * inform staff/log, and determine if the event should be cancelled.
  * @param player The player performing the action.
  * @param location The location of the action.
  * @param material The material being placed/used.
  * @return true if the event should be cancelled (i.e., placement denied), false otherwise.
  */
 private boolean processBannedMaterialPlacement(Player player, Location location, Material material) {
  if (!bannedMaterials.contains(material)) {
   return false; // Not a banned material, so no action needed.
  }

  boolean shouldDeny = false;
  AutoInformZone applicableZone = null; // Store the zone that caused the alert/denial

  for (AutoInformZone zone : definedZones.values()) {
   if (zone.contains(location)) {
    applicableZone = zone;
    if (zone.shouldDenyPlacement()) {
     shouldDeny = true; // If any zone says deny, we deny
    }
    // Don't break here, we still want to log/alert for the first matching zone
    // and if multiple zones overlap, we deny if ANY of them deny.
    break; // Found the first matching zone, no need to check others for logging/alerting
   }
  }

  if (applicableZone != null) {
   String actionStatus = (shouldDeny ? "DENIED" : "ALERTED");
   String logMessage = STR."Player \{player.getName()} attempted to place banned material \{material.name()} at \{formatLocation(location)} in protected zone '\{applicableZone.getName()}'. Action: \{actionStatus}.";
   getLogger().info(logMessage); // Log to server console

   String staffActionColor = (shouldDeny ? ChatColor.RED : ChatColor.YELLOW).toString();
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

   // Optional: Log with CoreProtect if you have custom flags or reasons
   // if (this.coreProtectAPI != null) {
   //    this.coreProtectAPI.logPlacement(player.getName(), location, material, null);
   // }
  }
  return shouldDeny; // Return whether the event should be cancelled
 }

 // --- Event Handlers (consolidated from RegionListener.java and existing handlers) ---

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
  Player player = event.getPlayer();
  Block placedBlock = event.getBlockPlaced();

  // First, check for AutoInformZone (global banned materials + deny-placement option)
  if (processBannedMaterialPlacement(player, placedBlock.getLocation(), placedBlock.getType())) {
   event.setCancelled(true);
   player.sendMessage(getMessage("player-denied-placement").replace("{material}", placedBlock.getType().name()));
   return; // If denied by AutoInformZone, no need to check Region system
  }

  // Then, check for custom regions (alexxautowarn.region.bypass)
  if (player.hasPermission("alexxautowarn.region.bypass")) {
   return;
  }

  for (Region region : regions.values()) {
   if (region.contains(placedBlock.getLocation())) {
    if (region.getBannedBlockPlacement().contains(placedBlock.getType())) {
     player.sendMessage(getMessage("region-banned-block-placement")
             .replace("{block}", placedBlock.getType().name())
             .replace("{region}", region.getName()));
     event.setCancelled(true);
     return;
    }
   }
  }
 }

 @EventHandler
 public void onPlayerInteract(PlayerInteractEvent event) {
  Player player = event.getPlayer();
  ItemStack handItem = event.getItem();
  Block clickedBlock = event.getClickedBlock();

  // Handle wand interactions first
  if (isWand(handItem)) {
   event.setCancelled(true); // Always cancel wand interactions to prevent accidental block changes

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
   return; // Don't process other interactions if it's the wand
  }

  // Handle placement of items that spawn entities (like TNT Minecart) from global banned materials
  if (handItem != null && bannedMaterials.contains(handItem.getType()) && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
   if (handItem.getType() == Material.TNT_MINECART && clickedBlock != null && clickedBlock.getType().name().contains("RAIL")) {
    Location placementLocation = clickedBlock.getLocation().add(0, 1, 0);
    if (processBannedMaterialPlacement(player, placementLocation, Material.TNT_MINECART)) {
     event.setCancelled(true);
     player.sendMessage(getMessage("player-denied-placement").replace("{material}", Material.TNT_MINECART.name()));
    }
   }
  }

  // --- Handle Custom Region Interactions ---
  if (player.hasPermission("alexxautowarn.region.bypass")) {
   return;
  }

  // Handle Chest Interactions
  if (event.getAction().name().contains("RIGHT_CLICK") && clickedBlock != null) {
   InventoryHolder holder = null;
   if (clickedBlock.getState() instanceof Chest) {
    holder = ((Chest) clickedBlock.getState());
   } else if (clickedBlock.getState() instanceof Container) {
    holder = (Container) clickedBlock.getState();
   }

   if (holder != null) {
    if (holder instanceof DoubleChest) {
     holder = ((DoubleChest) holder).getRightSide();
    }

    for (Region region : regions.values()) {
     if (region.contains(clickedBlock.getLocation())) {
      if (region.isBanChestInteraction()) {
       player.sendMessage(getMessage("region-banned-chest-interaction")
               .replace("{region}", region.getName()));
       event.setCancelled(true);
       return;
      }
     }
    }
   }
  }

  // Handle Item Usage (e.g., eating, ender pearl throws, potion drinking)
  if (event.getAction().name().contains("RIGHT_CLICK") && handItem != null && handItem.getType() != Material.AIR) {
   for (Region region : regions.values()) {
    if (region.contains(player.getLocation())) { // Check if player is *in* the region
     if (region.getBannedItemUsage().contains(handItem.getType())) {
      player.sendMessage(getMessage("region-banned-item-usage")
              .replace("{item}", handItem.getType().name())
              .replace("{region}", region.getName()));
      event.setCancelled(true);
      return;
     }
    }
   }
  }
 }

 @EventHandler
 public void onPlayerQuit(PlayerQuitEvent event) {
  playerSelections.remove(event.getPlayer().getUniqueId());
  playerWandPos1.remove(event.getPlayer().getUniqueId());
  playerWandPos2.remove(event.getPlayer().getUniqueId());
  playerZoneSelections.remove(event.getPlayer().getUniqueId()); // Clear old zone selections too
 }

 @Override
 public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
  if (!command.getName().equalsIgnoreCase(COMMAND_NAME) && !command.getName().equalsIgnoreCase("autoinform") && !command.getName().equalsIgnoreCase("aw")) {
   return false;
  }

  // Console reload command
  if (!(sender instanceof Player player)) {
   if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
    handleReloadCommand(sender);
    return true;
   }
   sender.sendMessage(getMessage("player-console-only-reload").replace("{command_name}", COMMAND_NAME));
   return true;
  }

  // Player commands require permission
  if (!player.hasPermission(PERMISSION_ADMIN_SET) && !player.hasPermission("alexxautowarn.region.admin")) {
   player.sendMessage(getMessage("player-no-permission"));
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
    handlePosCommand(player, subCommand, args);
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
   case "reload":
    handleReloadCommand(player);
    break;
   case "clearwand":
    handleClearWandCommand(player);
    break;
   case "banned":
    handleBannedCommand(player, args);
    break;
   case "deny": // New command to toggle deny-placement for old zones
    handleDenyCommand(player, args);
    break;
   case "region": // New command handler for the consolidated region management
    handleRegionCommand(player, Arrays.copyOfRange(args, 1, args.length));
    break;
   default:
    sendHelpMessage(player);
    break;
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
   player.sendMessage(getMessage("player-command-usage").replace("{usage}", STR."/\{COMMAND_NAME} \{posType} <zone_name>"));
   return;
  }
  String zoneNameForPos = args[1];
  playerZoneSelections
          .computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
          .computeIfAbsent(zoneNameForPos, k -> new HashMap<>())
          .put(posType, player.getLocation());
  player.sendMessage(getMessage("player-wand-pos1-set").replace("{location}", formatLocation(player.getLocation()))); // Reusing pos1/pos2 message
  playerWandPos1.remove(player.getUniqueId());
  playerWandPos2.remove(player.getUniqueId());
 }

 private void handleDefineCommand(Player player, String[] args) {
  if (args.length < 2) {
   player.sendMessage(getMessage("player-command-usage").replace("{usage}", STR."/\{COMMAND_NAME} define <zone_name>"));
   return;
  }
  String zoneToDefine = args[1];
  Location p1 = playerWandPos1.get(player.getUniqueId());
  Location p2 = playerWandPos2.get(player.getUniqueId());

  if (p1 == null || p2 == null) { // Check if wand selections exist first
   Map<String, Location> playerSelectionsMap = playerZoneSelections.getOrDefault(player.getUniqueId(), Collections.emptyMap()).get(zoneToDefine);
   if (playerSelectionsMap == null || !playerSelectionsMap.containsKey("pos1") || !playerSelectionsMap.containsKey("pos2")) {
    player.sendMessage(getMessage("player-command-usage").replace("{usage}", STR."You must set both positions for zone '\{zoneToDefine}' first using the wand or /\{COMMAND_NAME} \{zoneToDefine} pos1 and /\{COMMAND_NAME} \{zoneToDefine} pos2."));
    return;
   }
   p1 = playerSelectionsMap.get("pos1");
   p2 = playerSelectionsMap.get("pos2");
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

  // Preserve existing deny-placement setting if zone already exists, otherwise default to false
  boolean currentDenySetting = false;
  if (definedZones.containsKey(zoneToDefine)) {
   currentDenySetting = definedZones.get(zoneToDefine).shouldDenyPlacement();
  }

  definedZones.put(zoneToDefine, new AutoInformZone(zoneToDefine, p1.getWorld(), p1, p2, currentDenySetting));
  saveZonesToConfig();
  player.sendMessage(getMessage("player-define-zone-success")
          .replace("{zone_name}", zoneToDefine)
          .replace("{world_name}", p1.getWorld().getName()));
  player.sendMessage(getMessage("player-define-zone-corners")
          .replace("{corner1_loc}", formatLocation(p1))
          .replace("{corner2_loc}", formatLocation(p2)));
  player.sendMessage(getMessage("player-define-zone-deny-setting").replace("{default_action}", String.valueOf(currentDenySetting)));

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
   player.sendMessage(getMessage("player-command-usage").replace("{usage}", STR."/\{COMMAND_NAME} remove <zone_name>"));
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
    player.sendMessage(getMessage("player-zone-info-deny-setting").replace("{default_action}", String.valueOf(zoneInfo.shouldDenyPlacement())));
   } else {
    player.sendMessage(getMessage("player-zone-not-found").replace("{zone_name}", zoneInfoName));
   }
  } else { // No zone name specified, list all
   if (definedZones.isEmpty()) {
    player.sendMessage(getMessage("player-no-zones-defined"));
   } else {
    player.sendMessage(getMessage("player-all-zones-header"));
    definedZones.values().forEach(zone ->
            player.sendMessage(ChatColor.GOLD + STR."- \{zone.getName()}: " + ChatColor.WHITE + STR."World: \{zone.getWorld().getName()}, Deny: \{zone.shouldDenyPlacement()}")
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
  reloadConfig(); // Reload main config.yml
  loadCustomConfig(); // Reload messages.yml
  // setupCoreProtect(); // This is now handled by getCoreProtectAPI()
  coreProtectAPI = getCoreProtectAPI(); // Re-fetch CoreProtect API
  loadZonesFromConfig();
  loadBannedMaterialsFromConfig();
  regions.clear(); // Clear existing regions
  loadRegions(); // Load fresh regions from config

  sender.sendMessage(getMessage("plugin-config-reloaded"));
  if (definedZones.isEmpty() && regions.isEmpty()) { // Check both zone types
   sender.sendMessage(getMessage("plugin-warning-no-zones"));
  } else {
   sender.sendMessage(getMessage("plugin-success-zones-loaded-old").replace("{count}", String.valueOf(definedZones.size())) + " and " + getMessage("plugin-success-regions-loaded").replace("{count}", String.valueOf(regions.size())));
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
   player.sendMessage(getMessage("player-command-usage").replace("{usage}", STR."/\{COMMAND_NAME} banned <add|remove|list> [material_name]"));
   return;
  }
  String bannedAction = args[1].toLowerCase();
  switch (bannedAction) {
   case "add":
    if (args.length < 3) {
     player.sendMessage(getMessage("player-command-usage").replace("{usage}", STR."/\{COMMAND_NAME} banned add <material_name>"));
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
     player.sendMessage(getMessage("player-command-usage").replace("{usage}", STR."/\{COMMAND_NAME} banned remove <material_name>"));
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
    player.sendMessage(getMessage("player-command-usage").replace("{usage}", STR."/\{COMMAND_NAME} banned <add|remove|list>"));
    break;
  }
 }

 private void handleDenyCommand(Player player, String[] args) {
  if (args.length < 3) {
   player.sendMessage(getMessage("player-command-usage").replace("{usage}", STR."/\{COMMAND_NAME} deny <zone_name> <true|false>"));
   return;
  }
  String zoneName = args[1];
  String valueString = args[2].toLowerCase();
  boolean denyValue;

  if ("true".equals(valueString)) {
   denyValue = true;
  } else if ("false".equals(valueString)) {
   denyValue = false;
  } else {
   player.sendMessage(getMessage("player-command-usage").replace("{usage}", "Invalid value. Please use 'true' or 'false'."));
   return;
  }

  AutoInformZone existingZone = definedZones.get(zoneName);
  if (existingZone == null) {
   player.sendMessage(getMessage("player-zone-not-found").replace("{zone_name}", zoneName));
   return;
  }

  // Create a new AutoInformZone object with the updated denyPlacement setting
  // AutoInformZone is immutable, so we create a new instance
  AutoInformZone updatedZone = new AutoInformZone(
          existingZone.getName(),
          existingZone.getWorld(),
          existingZone.getCorner1(),
          existingZone.getCorner2(),
          denyValue
  );
  definedZones.put(zoneName, updatedZone);
  saveZonesToConfig();
  player.sendMessage(getMessage("player-deny-setting-updated").replace("{zone_name}", zoneName).replace("{action}", String.valueOf(denyValue)));
 }

 // --- New Command Handler for RegionSubCommand ---
 private void handleRegionCommand(Player player, String[] args) {
  if (args.length < 1) {
   sendRegionHelpMessage(player); // Send specific help for region commands
   return;
  }

  String subCommand = args[0].toLowerCase();
  // Shift args for region subcommands
  String[] regionArgs = Arrays.copyOfRange(args, 1, args.length);

  switch (subCommand) {
   case "select":
    if (regionArgs.length < 1) {
     player.sendMessage(getMessage("player-command-usage").replace("{usage}", "/autowarn region select <name>"));
     return;
    }
    String regionName = regionArgs[0];
    if (regions.containsKey(regionName)) {
     player.sendMessage(getMessage("region-name-exists").replace("{name}", regionName));
     return;
    }
    RegionSelection selection = playerSelections.getOrDefault(player.getUniqueId(), new RegionSelection(regionName, player.getWorld()));
    selection.setRegionName(regionName); // Update name in case of ongoing selection
    selection.setWorld(player.getWorld()); // Ensure correct world
    playerSelections.put(player.getUniqueId(), selection);
    player.sendMessage(getMessage("region-selection-started").replace("{name}", regionName));
    player.sendMessage(getMessage("region-selection-prompt-pos1"));
    break;
   case "pos1":
    RegionSelection selection1 = playerSelections.get(player.getUniqueId());
    if (selection1 == null || !selection1.getWorld().equals(player.getWorld())) {
     player.sendMessage(getMessage("region-no-active-selection"));
     return;
    }
    selection1.setPos1(player.getLocation().getBlock().getLocation());
    player.sendMessage(getMessage("region-pos1-set").replace("{location}", formatLocation(selection1.getPos1())));
    if (selection1.isComplete()) {
     createRegionFromSelection(player, selection1);
    } else {
     player.sendMessage(getMessage("region-selection-prompt-pos2"));
    }
    break;
   case "pos2":
    RegionSelection selection2 = playerSelections.get(player.getUniqueId());
    if (selection2 == null || !selection2.getWorld().equals(player.getWorld())) {
     player.sendMessage(getMessage("region-no-active-selection"));
     return;
    }
    selection2.setPos2(player.getLocation().getBlock().getLocation());
    player.sendMessage(getMessage("region-pos2-set").replace("{location}", formatLocation(selection2.getPos2())));
    if (selection2.isComplete()) {
     createRegionFromSelection(player, selection2);
    } else {
     player.sendMessage(getMessage("region-selection-prompt-pos1"));
    }
    break;
   case "create":
    String createRegionName = (regionArgs.length > 0) ? regionArgs[0] : null;
    RegionSelection createSelection = playerSelections.get(player.getUniqueId());
    if (createSelection == null || !createSelection.isComplete()) {
     player.sendMessage(getMessage("region-selection-incomplete"));
     return;
    }
    if (createRegionName == null || createRegionName.isEmpty()) {
     createRegionName = createSelection.getRegionName();
    } else {
     createSelection.setRegionName(createRegionName);
    }

    if (regions.containsKey(createRegionName)) {
     player.sendMessage(getMessage("region-name-exists").replace("{name}", createRegionName));
     return;
    }
    createRegionFromSelection(player, createSelection);
    break;
   case "delete":
    if (regionArgs.length < 1) {
     player.sendMessage(getMessage("player-command-usage").replace("{usage}", "/autowarn region delete <name>"));
     return;
    }
    String deleteRegionName = regionArgs[0];
    if (!regions.containsKey(deleteRegionName)) {
     player.sendMessage(getMessage("region-not-found").replace("{name}", deleteRegionName));
     return;
    }
    regions.remove(deleteRegionName);
    saveRegions();
    player.sendMessage(getMessage("region-deleted").replace("{name}", deleteRegionName));
    break;
   case "list":
    if (regions.isEmpty()) {
     player.sendMessage(getMessage("region-list-empty"));
     return;
    }
    player.sendMessage(getMessage("region-list-header").replace("{count}", String.valueOf(regions.size())));
    for (Region reg : regions.values()) {
     player.sendMessage(getMessage("region-list-entry")
             .replace("{name}", reg.getName())
             .replace("{world}", reg.getWorldName())
             .replace("{min}", String.format("%.0f,%.0f,%.0f", reg.getMin().getX(), reg.getMin().getY(), reg.getMin().getZ()))
             .replace("{max}", String.format("%.0f,%.0f,%.0f", reg.getMax().getX(), reg.getMax().getY(), reg.getMax().getZ())));
    }
    break;
   case "info":
    if (regionArgs.length < 1) {
     player.sendMessage(getMessage("player-command-usage").replace("{usage}", "/autowarn region info <name>"));
     return;
    }
    String infoRegionName = regionArgs[0];
    Region infoRegion = regions.get(infoRegionName);
    if (infoRegion == null) {
     player.sendMessage(getMessage("region-not-found").replace("{name}", infoRegionName));
     return;
    }

    player.sendMessage(getMessage("region-info-header").replace("{name}", infoRegion.getName()));
    player.sendMessage(getMessage("region-info-world").replace("{world}", infoRegion.getWorldName()));
    player.sendMessage(getMessage("region-info-coords")
            .replace("{min}", String.format("%.0f,%.0f,%.0f", infoRegion.getMin().getX(), infoRegion.getMin().getY(), infoRegion.getMin().getZ()))
            .replace("{max}", String.format("%.0f,%.0f,%.0f", infoRegion.getMax().getX(), infoRegion.getMax().getY(), infoRegion.getMax().getZ())));

    player.sendMessage(getMessage("region-info-banned-blocks")
            .replace("{list}", infoRegion.getBannedBlockPlacement().isEmpty() ? "None" :
                    infoRegion.getBannedBlockPlacement().stream().map(Enum::name).collect(Collectors.joining(", "))));
    player.sendMessage(getMessage("region-info-ban-chest-interaction")
            .replace("{status}", infoRegion.isBanChestInteraction() ? "Yes" : "No"));
    player.sendMessage(getMessage("region-info-banned-items")
            .replace("{list}", infoRegion.getBannedItemUsage().isEmpty() ? "None" :
                    infoRegion.getBannedItemUsage().stream().map(Enum::name).collect(Collectors.joining(", "))));
    break;
   // GUI command will be added here later
   // case "gui":
   //     if (regionArgs.length < 1) {
   //         player.sendMessage(getMessage("player-command-usage").replace("{usage}", "/autowarn region gui <name>"));
   //         return;
   //     }
   //     String guiRegionName = regionArgs[0];
   //     Region guiRegion = regions.get(guiRegionName);
   //     if (guiRegion == null) {
   //         player.sendMessage(getMessage("region-not-found").replace("{name}", guiRegionName));
   //         return;
   //     }
   //     // Open GUI for guiRegion
   //     break;
   default:
    sendRegionHelpMessage(player);
    break;
  }
 }

 private void createRegionFromSelection(Player player, RegionSelection selection) {
  Region newRegion = new Region(selection.getRegionName(), selection.getWorld(), selection.getPos1().toVector(), selection.getPos2().toVector());
  regions.put(newRegion.getName(), newRegion);
  saveRegions();
  playerSelections.remove(player.getUniqueId()); // Clear selection
  player.sendMessage(getMessage("region-created")
          .replace("{name}", newRegion.getName())
          .replace("{world}", newRegion.getWorldName())
          .replace("{min}", formatLocation(newRegion.getMin().toLocation(newRegion.getWorld())))
          .replace("{max}", formatLocation(newRegion.getMax().toLocation(newRegion.getWorld()))));
 }


 @Override
 public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
  if (!cmd.getName().equalsIgnoreCase(COMMAND_NAME) && !cmd.getName().equalsIgnoreCase("autoinform") && !cmd.getName().equalsIgnoreCase("aw")) {
   return Collections.emptyList();
  }

  if (!sender.hasPermission(PERMISSION_ADMIN_SET) && !sender.hasPermission("alexxautowarn.region.admin")) {
   return Collections.emptyList();
  }

  if (args.length == 1) {
   List<String> subCommands = new ArrayList<>(List.of("wand", "pos1", "pos2", "define", "remove", "info", "list", "reload", "clearwand", "banned", "deny", "region"));
   return subCommands.stream()
           .filter(s -> s.startsWith(args[0].toLowerCase()))
           .collect(Collectors.toList());
  } else if (args.length == 2) {
   String subCommand = args[0].toLowerCase();
   if (subCommand.equals("pos1") || subCommand.equals("pos2") || subCommand.equals("define") || subCommand.equals("remove") || subCommand.equals("info") || subCommand.equals("deny")) {
    return definedZones.keySet().stream()
            .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
            .collect(Collectors.toList());
   } else if (subCommand.equals("banned")) {
    return Arrays.asList("add", "remove", "list").stream()
            .filter(s -> s.startsWith(args[1].toLowerCase()))
            .collect(Collectors.toList());
   } else if (subCommand.equals("region")) {
    return Arrays.asList("select", "pos1", "pos2", "create", "delete", "list", "info", "gui").stream()
            .filter(s -> s.startsWith(args[1].toLowerCase()))
            .collect(Collectors.toList());
   }
  } else if (args.length == 3) {
   String subCommand = args[0].toLowerCase();
   if (subCommand.equals("banned")) {
    String bannedAction = args[1].toLowerCase();
    if (bannedAction.equals("add")) {
     return Arrays.stream(Material.values())
             .map(Enum::name)
             .filter(s -> s.startsWith(args[2].toUpperCase()))
             .collect(Collectors.toList());
    } else if (bannedAction.equals("remove")) {
     return bannedMaterials.stream()
             .map(Enum::name)
             .filter(s -> s.startsWith(args[2].toUpperCase()))
             .collect(Collectors.toList());
    }
   } else if (subCommand.equals("deny")) {
    return Arrays.asList("true", "false").stream()
            .filter(s -> s.startsWith(args[2].toLowerCase()))
            .collect(Collectors.toList());
   } else if (subCommand.equals("region")) {
    String regionSubCommand = args[1].toLowerCase();
    if (regionSubCommand.equals("select") || regionSubCommand.equals("delete") || regionSubCommand.equals("info") || regionSubCommand.equals("gui")) {
     return regions.keySet().stream()
             .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
             .collect(Collectors.toList());
    }
   }
  } else if (args.length == 4 && args[0].equalsIgnoreCase("region")) {
   String regionSubCommand = args[1].toLowerCase();
   if (regionSubCommand.equals("create")) {
    // Suggest existing region names for 'create' if user wants to use existing name
    return regions.keySet().stream()
            .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
            .collect(Collectors.toList());
   }
  }
  return Collections.emptyList();
 }

 /**
  * Sends the main help message to the player.
  * @param player The player to send the message to.
  */
 private void sendHelpMessage(Player player) {
  player.sendMessage(ChatColor.AQUA + "--- AutoInform Setter Help ---");
  player.sendMessage(ChatColor.GOLD + STR."/\{COMMAND_NAME} wand" + ChatColor.WHITE + " - Get the selection wand.");
  player.sendMessage(ChatColor.GOLD + STR."/\{COMMAND_NAME} <zone_name> pos1" + ChatColor.WHITE + " - Set first zone corner (manual).");
  player.sendMessage(ChatColor.GOLD + STR."/\{COMMAND_NAME} <zone_name> pos2" + ChatColor.WHITE + " - Set second zone corner (manual).");
  player.sendMessage(ChatColor.GOLD + STR."/\{COMMAND_NAME} <zone_name> define" + ChatColor.WHITE + " - Define/update <zone_name> using wand or manual selections.");
  player.sendMessage(ChatColor.GOLD + STR."/\{COMMAND_NAME} <zone_name> deny <true|false>" + ChatColor.WHITE + " - Toggle placement denial for a zone.");
  player.sendMessage(ChatColor.GOLD + STR."/\{COMMAND_NAME} remove <zone_name>" + ChatColor.WHITE + " - Remove a defined zone.");
  player.sendMessage(ChatColor.GOLD + STR."/\{COMMAND_NAME} info [zone_name]" + ChatColor.WHITE + " - Show info for a specific zone or all zones.");
  player.sendMessage(ChatColor.GOLD + STR."/\{COMMAND_NAME} list" + ChatColor.WHITE + " - List all defined zones.");
  player.sendMessage(ChatColor.GOLD + STR."/\{COMMAND_NAME} clearwand" + ChatColor.WHITE + " - Clear your wand selections.");
  player.sendMessage(ChatColor.GOLD + STR."/\{COMMAND_NAME} reload" + ChatColor.WHITE + " - Reload all zones and banned materials from config.");
  player.sendMessage(ChatColor.GOLD + STR."/\{COMMAND_NAME} banned <add|remove|list>" + ChatColor.WHITE + " - Manage banned materials.");
  player.sendMessage(ChatColor.GOLD + STR."/\{COMMAND_NAME} region <subcommand>" + ChatColor.WHITE + " - Manage custom regions (block, chest, item bans).");
  player.sendMessage(ChatColor.YELLOW + "Note: Placement will be denied in zones set to 'deny-placement: true'. Otherwise, staff will be alerted.");
 }

 /**
  * Sends the help message for region specific commands to the player.
  * @param player The player to send the message to.
  */
 private void sendRegionHelpMessage(Player player) {
  player.sendMessage(ChatColor.AQUA + "--- AutoInform Region Management Help ---");
  player.sendMessage(ChatColor.GOLD + "/autowarn region select <name>" + ChatColor.WHITE + " - Start selection for a new region.");
  player.sendMessage(ChatColor.GOLD + "/autowarn region pos1" + ChatColor.WHITE + " - Set first position of the region to your current location.");
  player.sendMessage(ChatColor.GOLD + "/autowarn region pos2" + ChatColor.WHITE + " - Set second position of the region to your current location.");
  player.sendMessage(ChatColor.GOLD + "/autowarn region create [name]" + ChatColor.WHITE + " - Create a region from your selection.");
  player.sendMessage(ChatColor.GOLD + "/autowarn region delete <name>" + ChatColor.WHITE + " - Delete an existing region.");
  player.sendMessage(ChatColor.GOLD + "/autowarn region list" + ChatColor.WHITE + " - List all defined regions.");
  player.sendMessage(ChatColor.GOLD + "/autowarn region info <name>" + ChatColor.WHITE + " - Show detailed info for a region.");
  player.sendMessage(ChatColor.GOLD + "/autowarn region gui <name>" + ChatColor.WHITE + " - Open GUI for region configuration (coming soon).");
 }

 /**
  * Formats a Location object into a readable string.
  * @param loc The location to format.
  * @return A formatted string representation of the location.
  */
 private String formatLocation(Location loc) {
  if (loc == null) return "N/A";
  return String.format("X: %.1f, Y: %.1f, Z: %.1f (World: %s)", loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName());
 }

 /**
  * Formats a set of Materials into a comma-separated string.
  * @param materials The set of materials to format.
  * @return A string representation of the materials.
  */
 private String formatMaterialList(Set<Material> materials) {
  if (materials.isEmpty()) {
   return "None";
  }
  return materials.stream()
          .map(Enum::name)
          .collect(Collectors.joining(", "));
 }

 // --- Inner Class: Region (from Region.java) ---
 @SerializableAs("AlexxAutoWarnRegion")
 public static class Region implements ConfigurationSerializable {

  private String name;
  private String worldName;
  private World world;
  private Vector min;
  private Vector max;

  private List<Material> bannedBlockPlacement;
  private boolean banChestInteraction;
  private List<Material> bannedItemUsage;

  public Region(String name, World world, Vector pos1, Vector pos2) {
   this.name = name;
   this.world = world;
   this.worldName = world.getName();
   this.min = Vector.getMinimum(pos1, pos2);
   this.max = Vector.getMaximum(pos1, pos2);

   this.bannedBlockPlacement = new ArrayList<>();
   this.banChestInteraction = false;
   this.bannedItemUsage = new ArrayList<>();
  }

  // Constructor for deserialization from config
  public Region(Map<String, Object> map) {
   this.name = (String) map.get("name");
   this.worldName = (String) map.get("world");
   this.world = Bukkit.getWorld(worldName); // Attempt to get world
   this.min = (Vector) map.get("min");
   this.max = (Vector) map.get("max");

   this.bannedBlockPlacement = ((List<?>) map.getOrDefault("banned_block_placement", new ArrayList<>()))
           .stream()
           .map(obj -> Material.valueOf((String) obj))
           .collect(Collectors.toList());
   this.banChestInteraction = (boolean) map.getOrDefault("ban_chest_interaction", false);
   this.bannedItemUsage = ((List<?>) map.getOrDefault("banned_item_usage", new ArrayList<>()))
           .stream()
           .map(obj -> Material.valueOf((String) obj))
           .collect(Collectors.toList());

   if (this.world == null) {
    Bukkit.getLogger().warning("AlexxAutoWarn: Region '" + name + "' refers to non-existent world: " + worldName);
   }
  }

  @Override
  public Map<String, Object> serialize() {
   Map<String, Object> map = new HashMap<>();
   map.put("name", name);
   map.put("world", worldName);
   map.put("min", min);
   map.put("max", max);
   map.put("banned_block_placement", bannedBlockPlacement.stream().map(Enum::name).collect(Collectors.toList()));
   map.put("ban_chest_interaction", banChestInteraction);
   map.put("banned_item_usage", bannedItemUsage.stream().map(Enum::name).collect(Collectors.toList()));
   return map;
  }

  public String getName() { return name; }
  public World getWorld() {
   if (world == null) {
    world = Bukkit.getWorld(worldName);
   }
   return world;
  }
  public String getWorldName() { return worldName; }
  public Vector getMin() { return min; }
  public Vector getMax() { return max; }
  public List<Material> getBannedBlockPlacement() { return bannedBlockPlacement; }
  public boolean isBanChestInteraction() { return banChestInteraction; }
  public List<Material> getBannedItemUsage() { return bannedItemUsage; }

  public void setBannedBlockPlacement(List<Material> bannedBlockPlacement) { this.bannedBlockPlacement = bannedBlockPlacement; }
  public void addBannedBlockPlacement(Material material) { if (!this.bannedBlockPlacement.contains(material)) { this.bannedBlockPlacement.add(material); } }
  public void removeBannedBlockPlacement(Material material) { this.bannedBlockPlacement.remove(material); }
  public void setBanChestInteraction(boolean banChestInteraction) { this.banChestInteraction = banChestInteraction; }
  public void setBannedItemUsage(List<Material> bannedItemUsage) { this.bannedItemUsage = bannedItemUsage; }
  public void addBannedItemUsage(Material material) { if (!this.bannedItemUsage.contains(material)) { this.bannedItemUsage.add(material); } }
  public void removeBannedItemUsage(Material material) { this.bannedItemUsage.remove(material); }

  public boolean contains(Location loc) {
   if (!loc.getWorld().getName().equals(worldName)) {
    return false;
   }
   return loc.getX() >= min.getX() && loc.getX() <= max.getX() + 1 &&
           loc.getY() >= min.getY() && loc.getY() <= max.getY() + 1 &&
           loc.getZ() >= min.getZ() && loc.getZ() <= max.getZ() + 1;
  }

  @Override
  public boolean equals(Object o) {
   if (this == o) return true;
   if (o == null || getClass() != o.getClass()) return false;
   Region region = (Region) o;
   return Objects.equals(name, region.name);
  }

  @Override
  public int hashCode() { return Objects.hash(name); }
 }

 // --- Inner Class: RegionSelection (from RegionSelection.java) ---
 public static class RegionSelection {
  private String regionName;
  private World world;
  private Location pos1;
  private Location pos2;

  public RegionSelection(String regionName, World world) {
   this.regionName = regionName;
   this.world = world;
  }

  public String getRegionName() { return regionName; }
  public World getWorld() { return world; }
  public Location getPos1() { return pos1; }
  public void setPos1(Location pos1) { this.pos1 = pos1; }
  public Location getPos2() { return pos2; }
  public void setPos2(Location pos2) { this.pos2 = pos2; }

  public void setRegionName(String regionName) { this.regionName = regionName; }
  public void setWorld(World world) { this.world = world; }

  public boolean isComplete() {
   return pos1 != null && pos2 != null && pos1.getWorld().equals(pos2.getWorld());
  }
 }

 // Helper to get messages from messages.yml
 public String getMessage(String path) {
  String message = getCustomConfig().getString(path);
  if (message == null) {
   return ChatColor.RED + "Error: Message '" + path + "' not found in messages.yml";
  }
  return ChatColor.translateAlternateColorCodes('&', message);
 }

 // Load messages.yml
 private void loadCustomConfig() {
  customConfigFile = new File(getDataFolder(), "messages.yml");
  if (!customConfigFile.exists()) {
   saveResource("messages.yml", false);
  }
  customConfig = YamlConfiguration.loadConfiguration(customConfigFile);

  // Look for updates from the included messages.yml
  InputStream defaultStream = getResource("messages.yml");
  if (defaultStream != null) {
   YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
   customConfig.setDefaults(defaultConfig);
   customConfig.options().copyDefaults(true); // Copy missing defaults
   try {
    customConfig.save(customConfigFile); // Save updated config
   } catch (IOException e) {
    getLogger().log(Level.SEVERE, "Could not save messages.yml!", e);
   }
  }
 }

 public FileConfiguration getCustomConfig() {
  return customConfig;
 }

 // Helper to setup CoreProtect (moved here for better organization)
 private void setupCoreProtect() {
  // This method is already called in onEnable and handleReloadCommand.
  // Its logic is already part of getCoreProtectAPI() so this method is redundant.
  // Keeping it here for now but it can be removed if getCoreProtectAPI is always used directly.
 }
}