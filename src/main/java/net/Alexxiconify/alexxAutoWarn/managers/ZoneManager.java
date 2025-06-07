package net.Alexxiconify.alexxAutoWarn.managers;

import net.Alexxiconify.alexxAutoWarn.AlexxAutoWarn;
import net.Alexxiconify.alexxAutoWarn.objects.AutoInformZone;
import net.Alexxiconify.alexxAutoWarn.objects.ZoneAction;
import net.Alexxiconify.alexxAutoWarn.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Manages the loading, storage, and retrieval of AutoInform zones.
 * This class is responsible for interacting with the plugin's configuration file
 * to persist zone data and provide efficient lookup. It operates within the Bukkit/Paper API context.
 */
public class ZoneManager {

 private final AlexxAutoWarn plugin;
 private final MessageUtil messageUtil;

 // Map to store active zones, keyed by their name for quick lookup
 private final Map<String, AutoInformZone> zones;

 // Stores temporary player selections for zone creation (pos1, pos2)
 private final PlayerSelectionManager playerSelections;

 // Set of materials that are globally banned (denied outside any specific zone)
 private final Set<Material> globallyBannedMaterials;
 private Location pos1;
 private Location pos2;

 /**
  * Constructor for ZoneManager.
  *
  * @param plugin The main plugin instance.
  * @param messageUtil The MessageUtil instance for messaging and logging.
  */
 public ZoneManager(AlexxAutoWarn plugin, MessageUtil messageUtil) {
  this.plugin = plugin;
  this.messageUtil = messageUtil;
  this.zones = new ConcurrentHashMap<>(); // Use ConcurrentHashMap for thread safety
  this.playerSelections = new PlayerSelectionManager();
  this.globallyBannedMaterials = Collections.synchronizedSet(new HashSet<>()); // Thread-safe set
 }

 /**
  * Loads zones from the plugin's config.yml file.
  * This method is called on plugin enable and on reload.
  */
 public void loadZones() {
  zones.clear(); // Clear existing zones before reloading
  FileConfiguration config = plugin.getConfig();
  ConfigurationSection zonesSection = config.getConfigurationSection("zones");

  if (zonesSection == null) {
   messageUtil.log(Level.INFO, "plugin-no-zones-in-config");
   return;
  }

  for (String zoneName : zonesSection.getKeys(false)) {
   ConfigurationSection zoneConfig = zonesSection.getConfigurationSection(zoneName);
   if (zoneConfig == null) {
    messageUtil.log(Level.WARNING, "plugin-invalid-zone-config", "{zone_name}", zoneName);
    continue;
   }

   try {
    String worldName = zoneConfig.getString("world");
    World world = Bukkit.getWorld(worldName);
    if (world == null) {
     messageUtil.log(Level.WARNING, "plugin-world-not-found", "{world_name}", worldName, "{zone_name}", zoneName);
     continue;
    }

    Location corner1 = parseLocation(zoneConfig.getString("corner1"), world);
    Location corner2 = parseLocation(zoneConfig.getString("corner2"), world);

    if (corner1 == null || corner2 == null) {
     messageUtil.log(Level.WARNING, "plugin-invalid-zone-config", "{zone_name}", zoneName);
     continue;
    }

    ZoneAction defaultAction = ZoneAction.valueOf(zoneConfig.getString("default-material-action", "ALLOW").toUpperCase());

    Map<Material, ZoneAction> materialActions = new HashMap<>();
    ConfigurationSection materialActionsSection = zoneConfig.getConfigurationSection("material-actions");
    if (materialActionsSection != null) {
     for (String matKey : materialActionsSection.getKeys(false)) {
      try {
       Material material = Material.valueOf(matKey.toUpperCase());
       ZoneAction action = ZoneAction.valueOf(materialActionsSection.getString(matKey).toUpperCase());
       materialActions.put(material, action);
      } catch (IllegalArgumentException e) {
       messageUtil.log(Level.WARNING, "plugin-invalid-material-action", "{material_key}", matKey, "{zone_name}", zoneName, e);
      }
     }
    }

    AutoInformZone zone = new AutoInformZone(zoneName, corner1, corner2, defaultAction, materialActions);
    zones.put(zoneName.toLowerCase(), zone); // Store in lowercase for case-insensitive lookup

   } catch (Exception e) {
    messageUtil.log(Level.SEVERE, "plugin-error-loading-zone", "{zone_name}", zoneName, e);
   }
  }
  messageUtil.log(Level.INFO, "plugin-zones-loaded", "{count}", String.valueOf(zones.size()));
 }

 /**
  * Parses a location string (e.g., "x,y,z") into a Bukkit Location object.
  * World must be provided separately as it's not part of the string.
  *
  * @param locString The location string.
  * @param world The world this location belongs to.
  * @return The Location object, or null if parsing fails.
  */
 private Location parseLocation(String locString, World world) {
  if (locString == null) return null;
  try {
   String[] parts = locString.split(",");
   if (parts.length == 3) {
    double x = Double.parseDouble(parts[0]);
    double y = Double.parseDouble(parts[1]);
    double z = Double.parseDouble(parts[2]);
    return new Location(world, x, y, z);
   }
  } catch (NumberFormatException e) {
   messageUtil.log(Level.WARNING, "plugin-invalid-location-format", "{location_string}", locString, e);
  }
  return null;
 }

 /**
  * Saves all currently loaded zones back to the plugin's config.yml file.
  */
 public void saveZones() {
  FileConfiguration config = plugin.getConfig();
  config.set("zones", null); // Clear existing zones section to write fresh

  if (zones.isEmpty()) {
   plugin.saveConfig();
   return;
  }

  ConfigurationSection zonesSection = config.createSection("zones");
  for (AutoInformZone zone : zones.values()) {
   ConfigurationSection zoneConfig = zonesSection.createSection(zone.getName());
   zoneConfig.set("world", zone.getWorld().getName());
   // This line needs LocationUtil which might be missing. If so, manual conversion is needed.
   zoneConfig.set("corner1", net.Alexxiconify.alexxAutoWarn.utils.LocationUtil.toCommaString(zone.getCorner1(pos1)));
   zoneConfig.set("corner2", net.Alexxiconify.alexxAutoWarn.utils.LocationUtil.toCommaString(zone.getCorner2(pos2)));
   zoneConfig.set("default-material-action", zone.getDefaultAction().name());

   if (!zone.getMaterialSpecificActions().isEmpty()) {
    ConfigurationSection materialActionsSection = zoneConfig.createSection("material-actions");
    zone.getMaterialSpecificActions().forEach((material, action) ->
            materialActionsSection.set(material.name(), action.name()));
   }
  }
  plugin.saveConfig();
  messageUtil.log(Level.FINE, "debug-zones-saved", "{count}", String.valueOf(zones.size()));
 }


 /**
  * Adds a new zone to the manager and saves it to config.
  *
  * @param zone The AutoInformZone object to add.
  * @return True if the zone was added, false if a zone with the same name already exists.
  */
 public boolean addZone(AutoInformZone zone) {
  if (zones.containsKey(zone.getName().toLowerCase())) {
   return false;
  }
  zones.put(zone.getName().toLowerCase(), zone);
  saveZones(); // Save immediately after adding
  try {
   messageUtil.sendMessage(playerSelections.getClass());
  } catch (ClassNotFoundException e) {
   throw new RuntimeException(e);
  }
  return true;
 }

 /**
  * Removes a zone from the manager and saves the change to config.
  *
  * @param zoneName The name of the zone to remove.
  * @return True if the zone was removed, false if it did not exist.
  */
 public boolean removeZone(String zoneName) {
  if (zones.remove(zoneName.toLowerCase()) != null) {
   saveZones(); // Save immediately after removing
   try {
    messageUtil.sendMessage(playerSelections.getClass());
   } catch (ClassNotFoundException e) {
    throw new RuntimeException(e);
   }
   return true;
  }
  return false;
 }

 /**
  * Retrieves a zone by its name.
  *
  * @param zoneName The name of the zone.
  * @return The AutoInformZone object, or null if not found.
  */
 public AutoInformZone getZone(String zoneName) {
  return zones.get(zoneName.toLowerCase());
 }

 /**
  * Retrieves the zone that contains the given location.
  * If multiple zones overlap, the first one found is returned.
  *
  * @param location The location to check.
  * @return The AutoInformZone containing the location, or null if no zone contains it.
  */
 public AutoInformZone getZoneAtLocation(Location location) {
  for (AutoInformZone zone : zones.values()) {
   if (zone.contains(location)) {
    return zone;
   }
  }
  return null;
 }

 /**
  * Gets an unmodifiable collection of all defined zones.
  *
  * @return A Collection of AutoInformZone objects.
  */
 public Collection<AutoInformZone> getAllZones() {
  return Collections.unmodifiableCollection(zones.values());
 }

 /**
  * Retrieves the PlayerSelectionManager instance.
  *
  * @return The PlayerSelectionManager instance.
  */
 public PlayerSelectionManager getPlayerSelections() {
  return playerSelections;
 }

 /**
  * Loads globally banned materials from the config.yml.
  */
 public void loadGloballyBannedMaterials() {
  globallyBannedMaterials.clear();
  FileConfiguration config = plugin.getConfig();
  List<String> bannedMaterialsList = config.getStringList("banned-materials");

  for (String materialName : bannedMaterialsList) {
   try {
    Material material = Material.valueOf(materialName.toUpperCase());
    globallyBannedMaterials.add(material);
   } catch (IllegalArgumentException e) {
    messageUtil.log(Level.WARNING, "plugin-invalid-banned-material", "{material_name}", materialName, e);
   }
  }
  if (globallyBannedMaterials.isEmpty()) {
   messageUtil.log(Level.INFO, "plugin-no-banned-materials");
  } else {
   messageUtil.log(Level.INFO, "plugin-banned-materials-loaded", "{count}", String.valueOf(globallyBannedMaterials.size()));
  }
 }

 /**
  * Checks if a material is globally banned.
  *
  * @param material The material to check.
  * @return true if the material is globally banned, false otherwise.
  */
 public boolean isGloballyBanned(Material material) {
  return globallyBannedMaterials.contains(material);
 }

 /**
  * Adds a material to the globally banned list and saves to config.
  *
  * @param material The material to add.
  * @return true if added, false if already present.
  */
 public boolean addGloballyBannedMaterial(Material material) {
  if (globallyBannedMaterials.add(material)) {
   saveGloballyBannedMaterials();
   return true;
  }
  return false;
 }

 /**
  * Removes a material from the globally banned list and saves to config.
  *
  * @param material The material to remove.
  * @return true if removed, false if not present.
  */
 public boolean removeGloballyBannedMaterial(Material material) {
  if (globallyBannedMaterials.remove(material)) {
   saveGloballyBannedMaterials();
   return true;
  }
  return false;
 }

 /**
  * Gets an unmodifiable set of globally banned materials.
  *
  * @return A Set of globally banned materials.
  */
 public Set<Material> getGloballyBannedMaterials() {
  return Collections.unmodifiableSet(globallyBannedMaterials);
 }

 /**
  * Saves the current list of globally banned materials to the config.yml.
  */
 public void saveGloballyBannedMaterials() {
  FileConfiguration config = plugin.getConfig();
  // Convert the set of Material enums to a list of String names
  config.set("banned-materials", globallyBannedMaterials.stream()
          .map(Enum::name)
          .collect(Collectors.toList()));
  plugin.saveConfig();
  messageUtil.log(Level.FINE, "debug-banned-materials-saved", "{count}", String.valueOf(globallyBannedMaterials.size()));
 }


 /**
  * Inner class to manage player-specific selections (pos1, pos2) in memory.
  * This avoids saving incomplete selections to config.
  */
 public static class PlayerSelectionManager {
  // Map: Player UUID -> Map: "pos1" / "pos2" -> Location
  private final Map<UUID, Map<String, Location>> playerSelections = new ConcurrentHashMap<>();

  /**
   * Sets a selection point for a player.
   *
   * @param player   The player.
   * @param type     "pos1" or "pos2".
   * @param location The selected location.
   */
  public void setSelection(Player player, String type, Location location) {
   playerSelections
           .computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
           .put(type, location);
  }

  /**
   * Gets a selection point for a player.
   *
   * @param player The player.
   * @param type   "pos1" or "pos2".
   * @return The Location, or null if not set.
   */
  @Nullable
  public Location getSelection(Player player, String type) {
   return playerSelections.getOrDefault(player.getUniqueId(), Collections.emptyMap()).get(type);
  }

  /**
   * Clears all selections for a specific player.
   *
   * @param player The player whose selections to clear.
   */
  public void clearSelections(Player player) {
   playerSelections.remove(player.getUniqueId());
  }

  /**
   * Clears a specific selection point for a player.
   *
   * @param player The player.
   * @param type   "pos1" or "pos2".
   */
  public void clearSelection(Player player, String type) {
   Map<String, Location> selections = playerSelections.get(player.getUniqueId());
   if (selections != null) {
    selections.remove(type);
    if (selections.isEmpty()) {
     playerSelections.remove(player.getUniqueId());
    }
   }
  }
 }
}