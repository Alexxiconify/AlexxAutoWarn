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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
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

 // Set of materials that are globally banned (denied outside any specific zone)
 private final Set<Material> globallyBannedMaterials;

 /**
  * Constructor for ZoneManager.
  *
  * @param plugin The main plugin instance.
  */
 public ZoneManager(AlexxAutoWarn plugin, MessageUtil messageUtil) {
  this.plugin = plugin;
  this.messageUtil = messageUtil;
  this.zones = new HashMap<>();
  this.globallyBannedMaterials = new HashSet<>();
 }

 /**
  * Loads all zones from the plugin's config.yml.
  * This method should be called on plugin enable and reload.
  */
 public void loadZones() {
  zones.clear(); // Clear existing zones before loading
  FileConfiguration config = plugin.getConfig();
  ConfigurationSection zonesSection = config.getConfigurationSection("zones");

  if (zonesSection == null) {
   messageUtil.log(Level.INFO, "plugin-no-zones-in-config");
   return;
  }

  int loadedCount = 0;
  for (String zoneName : zonesSection.getKeys(false)) {
   ConfigurationSection zoneConfig = zonesSection.getConfigurationSection(zoneName);
   if (zoneConfig == null) {
    messageUtil.log(Level.WARNING, "plugin-invalid-zone-config", "{zone_name}", zoneName);
    continue;
   }

   try {
    String worldName = zoneConfig.getString("world");
    if (worldName == null) {
     messageUtil.log(Level.WARNING, "plugin-invalid-zone-config", "{zone_name}", zoneName);
     continue;
    }
    World world = Bukkit.getWorld(worldName);
    if (world == null) {
     messageUtil.log(Level.WARNING, "plugin-world-not-found", "{world_name}", worldName, "{zone_name}", zoneName);
     continue;
    }

    Location corner1 = deserializeLocation(zoneConfig.getString("corner1"), world);
    Location corner2 = deserializeLocation(zoneConfig.getString("corner2"), world);
    ZoneAction defaultAction = ZoneAction.valueOf(zoneConfig.getString("default-material-action", "ALLOW").toUpperCase());

    Map<Material, ZoneAction> materialActions = new HashMap<>();
    ConfigurationSection materialActionsSection = zoneConfig.getConfigurationSection("material-actions");
    if (materialActionsSection != null) {
     for (String materialKey : materialActionsSection.getKeys(false)) {
      try {
       Material material = Material.valueOf(materialKey.toUpperCase());
       ZoneAction action = ZoneAction.valueOf(materialActionsSection.getString(materialKey, "ALLOW").toUpperCase());
       materialActions.put(material, action);
      } catch (IllegalArgumentException e) {
       messageUtil.log(Level.WARNING, "plugin-invalid-material-action", "{material_key}", materialKey, "{zone_name}", zoneName, "{error}", e.getMessage());
      }
     }
    }
    zones.put(zoneName.toLowerCase(), new AutoInformZone(zoneName, corner1, corner2, defaultAction, materialActions));
    loadedCount++;

   } catch (Exception e) {
    messageUtil.log(Level.SEVERE, "plugin-error-loading-zone", "{zone_name}", zoneName, "{error}", e.getMessage(), e);
   }
  }
  messageUtil.log(Level.INFO, "plugin-zones-loaded", "{count}", loadedCount);
 }

 /**
  * Saves all currently defined zones to the plugin's config.yml.
  * This method should be called on plugin disable and after zone modifications.
  */
 public void saveZones() {
  FileConfiguration config = plugin.getConfig();
  config.set("zones", null); // Clear existing zones section to rewrite it

  for (AutoInformZone zone : zones.values()) {
   String zonePath = "zones." + zone.getName();
   config.set(zonePath + ".world", zone.getWorld().getName());
   config.set(zonePath + ".corner1", serializeLocation(zone.getCorner1()));
   config.set(zonePath + ".corner2", serializeLocation(zone.getCorner2()));
   config.set(zonePath + ".default-material-action", zone.getDefaultAction().name());

   ConfigurationSection materialActionsSection = config.createSection(zonePath + ".material-actions");
   for (Map.Entry<Material, ZoneAction> entry : zone.getMaterialSpecificActions().entrySet()) {
    materialActionsSection.set(entry.getKey().name(), entry.getValue().name());
   }
  }
  plugin.saveConfig();
  messageUtil.log(Level.INFO, "plugin-zones-saved", "{count}", zones.size());
 }

 /**
  * Loads the list of globally banned materials from config.yml.
  * This method should be called on plugin enable and reload.
  */
 public void loadGloballyBannedMaterials() {
  globallyBannedMaterials.clear();
  FileConfiguration config = plugin.getConfig();
  List<String> bannedMaterialsList = config.getStringList("banned-materials");

  if (bannedMaterialsList.isEmpty()) {
   messageUtil.log(Level.INFO, "plugin-no-banned-materials");
   return;
  }

  for (String materialName : bannedMaterialsList) {
   try {
    Material material = Material.valueOf(materialName.toUpperCase());
    globallyBannedMaterials.add(material);
   } catch (IllegalArgumentException e) {
    messageUtil.log(Level.WARNING, "plugin-invalid-banned-material", "{material_name}", materialName, "{error}", e.getMessage());
   }
  }
  messageUtil.log(Level.INFO, "plugin-banned-materials-loaded", "{count}", globallyBannedMaterials.size());
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
  messageUtil.log(Level.INFO, "plugin-banned-materials-saved", "{count}", globallyBannedMaterials.size());
 }

 /**
  * Adds a material to the globally banned list and saves to config.
  *
  * @param material The material to add.
  * @return true if added, false if already present.
  */
 public boolean addGloballyBannedMaterial(Material material) {
  if (globallyBannedMaterials.add(material)) {
   saveGloballyBannedMaterials(); // Corrected: Call the new public method
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
   saveGloballyBannedMaterials(); // Corrected: Call the new public method
   return true;
  }
  return false;
 }

 /**
  * Checks if a material is in the globally banned list.
  *
  * @param material The material to check.
  * @return true if the material is globally banned, false otherwise.
  */
 public boolean isGloballyBanned(Material material) {
  return globallyBannedMaterials.contains(material);
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
  * Gets a zone by its name.
  *
  * @param name The name of the zone (case-insensitive).
  * @return The AutoInformZone if found, null otherwise.
  */
 @Nullable
 public AutoInformZone getZone(String name) {
  return zones.get(name.toLowerCase());
 }

 /**
  * Gets the zone at a specific location.
  *
  * @param location The location to check.
  * @return The AutoInformZone containing the location, or null if no zone contains it.
  */
 @Nullable
 public AutoInformZone getZoneAtLocation(@NotNull Location location) {
  for (AutoInformZone zone : zones.values()) {
   if (zone.contains(location)) {
    return zone;
   }
  }
  return null;
 }

 /**
  * Adds or updates a zone.
  *
  * @param zone The AutoInformZone to add or update.
  */
 public void addZone(@NotNull AutoInformZone zone) {
  zones.put(zone.getName().toLowerCase(), zone);
  saveZones(); // Save zones after adding/updating
 }

 /**
  * Removes a zone by its name.
  *
  * @param name The name of the zone to remove.
  * @return true if the zone was removed, false if not found.
  */
 public boolean removeZone(String name) {
  if (zones.remove(name.toLowerCase()) != null) {
   saveZones(); // Save zones after removal
   return true;
  }
  return false;
 }

 /**
  * Gets an unmodifiable collection of all defined zones.
  *
  * @return A Collection of all AutoInformZones.
  */
 public Collection<AutoInformZone> getAllZones() {
  return Collections.unmodifiableCollection(zones.values());
 }

 /**
  * Serializes a Location object to a string format for config storage.
  * Format: "x,y,z"
  */
 private String serializeLocation(@NotNull Location location) {
  return String.format("%.0f,%.0f,%.0f", location.getX(), location.getY(), location.getZ());
 }

 /**
  * Deserializes a string back into a Location object.
  *
  * @param locationString The string representation of the location.
  * @param world          The world the location belongs to.
  * @return The Location object.
  * @throws IllegalArgumentException if the string format is invalid.
  */
 private Location deserializeLocation(@Nullable String locationString, @NotNull World world) {
  if (locationString == null || locationString.isEmpty()) {
   throw new IllegalArgumentException("Location string cannot be null or empty.");
  }
  String[] parts = locationString.split(",");
  if (parts.length != 3) {
   throw new IllegalArgumentException("Invalid location string format: " + locationString);
  }
  try {
   double x = Double.parseDouble(parts[0]);
   double y = Double.parseDouble(parts[1]);
   double z = Double.parseDouble(parts[2]);
   return new Location(world, x, y, z);
  } catch (NumberFormatException e) {
   throw new IllegalArgumentException("Invalid number format in location string: " + locationString, e);
  }
 }
}