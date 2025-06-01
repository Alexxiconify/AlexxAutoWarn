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

import java.util.*;
import java.util.logging.Level;

/**
 * Manages the loading, storage, and retrieval of AutoInform zones.
 * This class is responsible for interacting with the plugin's configuration file
 * to persist zone data and provide efficient lookup.
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
 public ZoneManager(AlexxAutoWarn plugin) {
  this.plugin = plugin;
  this.messageUtil = plugin.getMessageUtil(); // Access the MessageUtil from the plugin instance
  this.zones = new HashMap<>();
  this.globallyBannedMaterials = new HashSet<>();
 }

 /**
  * Loads all zone configurations from the plugin's config.yml file.
  * Clears existing zones and reloads them.
  */
 public void loadZonesFromConfig() {
  zones.clear(); // Clear existing zones before reloading
  globallyBannedMaterials.clear(); // Fixed typo: was globallyBannedLightMaterials

  FileConfiguration config = plugin.getConfig();

  // Load globally banned materials
  loadGloballyBannedMaterials(config);

  // Load zones section
  ConfigurationSection zonesSection = config.getConfigurationSection("zones");
  if (zonesSection == null) {
   messageUtil.log(Level.INFO, "plugin-no-zones-in-config");
   return;
  }

  // Iterate through each zone defined in the config
  for (String zoneName : zonesSection.getKeys(false)) {
   ConfigurationSection zoneConfig = zonesSection.getConfigurationSection(zoneName);
   if (zoneConfig == null) {
    messageUtil.log(Level.WARNING, "plugin-invalid-zone-config", "{zone_name}", zoneName);
    continue;
   }

   try {
    // Get world
    String worldName = zoneConfig.getString("world");
    if (worldName == null || worldName.isEmpty()) {
     messageUtil.log(Level.WARNING, "zone-config-missing-world", "{zone_name}", zoneName);
     continue;
    }
    World world = Bukkit.getWorld(worldName);
    if (world == null) {
     messageUtil.log(Level.WARNING, "plugin-world-not-found", "{world_name}", worldName, "{zone_name}", zoneName);
     continue;
    }

    // Get corner locations
    Location corner1 = parseLocation(zoneConfig.getConfigurationSection("corner1"), world, "corner1", zoneName);
    Location corner2 = parseLocation(zoneConfig.getConfigurationSection("corner2"), world, "corner2", zoneName);

    if (corner1 == null || corner2 == null) {
     messageUtil.log(Level.WARNING, "zone-config-missing-corners", "{zone_name}", zoneName);
     continue;
    }

    // Get default material action
    String defaultActionString = zoneConfig.getString("default-material-action", "ALERT"); // Default to ALERT if not specified
    ZoneAction defaultAction = ZoneAction.valueOf(defaultActionString.toUpperCase());

    // Get material-specific actions
    Map<Material, ZoneAction> materialActions = new HashMap<>();
    ConfigurationSection materialActionsSection = zoneConfig.getConfigurationSection("material-actions");
    if (materialActionsSection != null) {
     for (String materialKey : materialActionsSection.getKeys(false)) {
      try {
       Material material = Material.valueOf(materialKey.toUpperCase());
       String actionString = materialActionsSection.getString(materialKey);
       if (actionString != null) {
        ZoneAction action = ZoneAction.valueOf(actionString.toUpperCase());
        materialActions.put(material, action);
       }
      } catch (IllegalArgumentException e) {
       messageUtil.log(Level.WARNING, "zone-config-invalid-material-action",
               "{zone_name}", zoneName, "{material_key}", materialKey, "{error}", e.getMessage());
      }
     }
    }

    // Create and store the AutoInformZone object
    AutoInformZone zone = new AutoInformZone(zoneName, world, corner1, corner2, defaultAction, materialActions);
    zones.put(zoneName, zone);
    messageUtil.log(Level.INFO, "zone-loaded", "{zone_name}", zoneName);

   } catch (IllegalArgumentException e) {
    // Log with the exception
    messageUtil.log(Level.SEVERE, "plugin-invalid-zone-config-error",
            "{zone_name}", zoneName, "{error}", e.getMessage(), e);
   } catch (Exception e) {
    // Log with the exception
    messageUtil.log(Level.SEVERE, "plugin-error-loading-zone",
            "{zone_name}", zoneName, "{error}", e.getMessage(), e);
   }
  }
  messageUtil.log(Level.INFO, "plugin-zones-loaded", "{count}", String.valueOf(zones.size()));
 }

 /**
  * Parses a location from a ConfigurationSection.
  *
  * @param section     The ConfigurationSection containing x, y, z coordinates.
  * @param world       The world the location belongs to.
  * @param sectionName The name of the section (for logging purposes).
  * @param zoneName    The name of the zone (for logging purposes).
  * @return A Location object, or null if parsing fails.
  */
 private Location parseLocation(ConfigurationSection section, World world, String sectionName, String zoneName) {
  if (section == null) {
   messageUtil.log(Level.WARNING, "zone-config-missing-section", "{section_name}", sectionName, "{zone_name}", zoneName);
   return null;
  }
  if (!section.contains("x") || !section.contains("y") || !section.contains("z")) {
   messageUtil.log(Level.WARNING, "zone-config-incomplete-coordinates", "{section_name}", sectionName, "{zone_name}", zoneName);
   return null;
  }
  try {
   double x = section.getDouble("x");
   double y = section.getDouble("y");
   double z = section.getDouble("z");
   return new Location(world, x, y, z);
  } catch (Exception e) {
   // Log with the exception
   messageUtil.log(Level.SEVERE, "zone-config-invalid-coordinates",
           "{section_name}", sectionName, "{zone_name}", zoneName, "{error}", e.getMessage(), e);
   return null;
  }
 }

 /**
  * Loads globally banned materials from the config.yml.
  *
  * @param config The plugin's FileConfiguration.
  */
 private void loadGloballyBannedMaterials(FileConfiguration config) {
  // Clear existing banned materials to ensure a fresh load
  globallyBannedMaterials.clear();

  // Get the list of banned materials from the config
  // Use getStringList to get a List<String>, then stream to convert to Material objects
  for (String materialName : config.getStringList("banned-materials")) {
   try {
    Material material = Material.valueOf(materialName.toUpperCase());
    globallyBannedMaterials.add(material);
   } catch (IllegalArgumentException e) {
    // Log a warning if an invalid material name is found in the config
    messageUtil.log(Level.WARNING, "config-invalid-banned-material", "{material}", materialName);
   }
  }
  messageUtil.log(Level.INFO, "plugin-banned-materials-loaded", "{count}", String.valueOf(globallyBannedMaterials.size()));
 }


 /**
  * Defines or updates a zone in memory and saves it to the config.
  *
  * @param name    The name of the zone.
  * @param corner1 The first corner location.
  * @param corner2 The second corner location.
  */
 public void defineZone(String name, Location corner1, Location corner2) {
  // If the zone already exists, use its current default action and material actions
  AutoInformZone existingZone = zones.get(name);
  ZoneAction defaultAction = (existingZone != null) ? existingZone.getDefaultAction() : ZoneAction.ALERT; // Default to ALERT if new
  Map<Material, ZoneAction> materialActions = (existingZone != null) ? new HashMap<>(existingZone.getMaterialSpecificActions()) : new HashMap<>();

  AutoInformZone newZone = new AutoInformZone(name, corner1.getWorld(), corner1, corner2, defaultAction, materialActions);
  zones.put(name, newZone); // Add or update in memory
  saveZoneToConfig(newZone); // Persist to config.yml
 }

 /**
  * Removes a zone from memory and the config.
  *
  * @param name The name of the zone to remove.
  * @return true if the zone was removed, false if not found.
  */
 public boolean removeZone(String name) {
  if (zones.remove(name) != null) { // Remove from memory
   FileConfiguration config = plugin.getConfig();
   ConfigurationSection zonesSection = config.getConfigurationSection("zones");
   if (zonesSection != null) {
    zonesSection.set(name, null); // Remove from config
    plugin.saveConfig(); // Save changes to config.yml
    return true;
   }
  }
  return false;
 }

 /**
  * Sets the default action for a specific zone.
  *
  * @param zoneName The name of the zone.
  * @param action   The new default action.
  * @return true if the zone exists and action was set, false otherwise.
  */
 public boolean setZoneDefaultAction(String zoneName, ZoneAction action) {
  AutoInformZone zone = zones.get(zoneName);
  if (zone != null) {
   zone.setDefaultAction(action); // Update in memory
   saveZoneToConfig(zone); // Persist change to config.yml
   return true;
  }
  return false;
 }

 /**
  * Sets a material-specific action for a zone.
  *
  * @param zoneName The name of the zone.
  * @param material The material.
  * @param action   The action for the material.
  * @return true if the zone exists and action was set, false otherwise.
  */
 public boolean setZoneMaterialAction(String zoneName, Material material, ZoneAction action) {
  AutoInformZone zone = zones.get(zoneName);
  if (zone != null) {
   zone.getMaterialSpecificActions().put(material, action); // Update in memory
   saveZoneToConfig(zone); // Persist change to config.yml
   return true;
  }
  return false;
 }

 /**
  * Saves a single zone's configuration to the config.yml file.
  * This method is called after any modification to a zone.
  *
  * @param zone The AutoInformZone object to save.
  */
 private void saveZoneToConfig(AutoInformZone zone) {
  FileConfiguration config = plugin.getConfig();
  ConfigurationSection zonesSection = config.getConfigurationSection("zones");
  if (zonesSection == null) {
   zonesSection = config.createSection("zones"); // Create if it doesn't exist
  }

  ConfigurationSection zoneConfig = zonesSection.createSection(zone.getName());
  zoneConfig.set("world", zone.getWorld().getName());
  zoneConfig.set("corner1.x", zone.getCorner1().getX());
  zoneConfig.set("corner1.y", zone.getCorner1().getY());
  zoneConfig.set("corner1.z", zone.getCorner1().getZ());
  zoneConfig.set("corner2.x", zone.getCorner2().getX());
  zoneConfig.set("corner2.y", zone.getCorner2().getY());
  zoneConfig.set("corner2.z", zone.getCorner2().getZ());
  zoneConfig.set("default-material-action", zone.getDefaultAction().name());

  ConfigurationSection materialActionsSection = zoneConfig.createSection("material-actions");
  zone.getMaterialSpecificActions().forEach((material, action) ->
          materialActionsSection.set(material.name(), action.name()));

  plugin.saveConfig(); // Save changes to config.yml
 }

 /**
  * Finds the AutoInformZone that contains the given location.
  *
  * @param location The location to check.
  * @return The AutoInformZone containing the location, or null if no zone contains it.
  */
 public AutoInformZone getZoneAtLocation(Location location) {
  // Iterate through all zones and check if the location is within their bounds
  for (AutoInformZone zone : zones.values()) {
   if (zone.contains(location)) {
    return zone;
   }
  }
  return null;
 }

 /**
  * Clears all loaded zones from memory. Used during plugin disable or reload.
  */
 public void clearZones() {
  zones.clear();
  globallyBannedMaterials.clear();
 }

 /**
  * Gets an unmodifiable set of all currently loaded zone names.
  *
  * @return A Set of zone names.
  */
 public Set<String> getZoneNames() {
  return Collections.unmodifiableSet(zones.keySet());
 }

 /**
  * Gets a specific zone by name.
  *
  * @param name The name of the zone.
  * @return The AutoInformZone object, or null if not found.
  */
 public AutoInformZone getZone(String name) {
  return zones.get(name);
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
  * Adds a material to the globally banned list and saves to config.
  *
  * @param material The material to add.
  * @return true if added, false if already present.
  */
 public boolean addGloballyBannedMaterial(Material material) {
  if (globallyBannedMaterials.add(material)) {
   saveGloballyBannedMaterialsToConfig();
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
   saveGloballyBannedMaterialsToConfig();
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
 private void saveGloballyBannedMaterialsToConfig() {
  FileConfiguration config = plugin.getConfig();
  // Convert the set of Material enums to a list of String names
  config.set("banned-materials", globallyBannedMaterials.stream()
          .map(Enum::name)
          .collect(java.util.ArrayList::new, java.util.ArrayList::add, java.util.ArrayList::addAll));
  plugin.saveConfig();
 }
}