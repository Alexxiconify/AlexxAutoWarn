package net.Alexxiconify.alexxAutoWarn.managers;

import MessageUtil;
import net.Alexxiconify.alexxAutoWarn.AlexxAutoWarn;
import net.Alexxiconify.alexxAutoWarn.objects.AutoInformZone;
import net.Alexxiconify.alexxAutoWarn.objects.ZoneAction;
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
 * Manages the creation, storage, loading, and persistence of AutoInform zones.
 * It also handles the global list of banned materials.
 */
public class ZoneManager {

 private final AlexxAutoWarn plugin;
 private final MessageUtil messageUtil;
 private final Map<String, AutoInformZone> definedZones; // ZoneName -> AutoInformZone
 private final Set<Material> globallyBannedMaterials;

 /**
  * Constructor for ZoneManager.
  *
  * @param plugin The main plugin instance.
  */
 public ZoneManager(AlexxAutoWarn plugin) {
  this.plugin = plugin;
  this.messageUtil = plugin.getMessageUtil(); // Access the MessageUtil from the plugin instance
  this.definedZones = new HashMap<>();
  this.globallyBannedMaterials = new HashSet<>();
 }

 /**
  * Defines or updates an AutoInform zone in memory and saves it to the config.
  * This version now takes a default action when defining a new zone.
  *
  * @param name          The name of the zone.
  * @param corner1       The first corner location.
  * @param corner2       The second corner location.
  * @param defaultAction The default action for the zone.
  */
 public void defineZone(@NotNull String name, @NotNull Location corner1, @NotNull Location corner2, @NotNull ZoneAction defaultAction) {
  // If the zone already exists, merge with its current material-specific actions
  AutoInformZone existingZone = definedZones.get(name.toLowerCase());
  Map<Material, ZoneAction> materialActions = (existingZone != null) ? new HashMap<>(existingZone.getMaterialSpecificActions()) : new HashMap<>();

  // Create a new AutoInformZone object (AutoInformZone constructor now handles world extraction)
  AutoInformZone newZone = new AutoInformZone(name, corner1, corner2, defaultAction, materialActions);
  definedZones.put(name.toLowerCase(), newZone); // Add or update in memory, use lowercase for key
  saveZoneToConfig(newZone); // Persist to config.yml
  messageUtil.log(Level.INFO, "zone-defined", "{zone_name}", name); // Log success
 }


 /**
  * Removes a zone from memory and the config.
  *
  * @param name The name of the zone to remove.
  * @return true if the zone was removed, false if not found.
  */
 public boolean removeZone(@NotNull String name) {
  if (definedZones.remove(name.toLowerCase()) != null) { // Remove from memory, use lowercase key
   FileConfiguration config = plugin.getConfig();
   ConfigurationSection zonesSection = config.getConfigurationSection("zones");
   if (zonesSection != null) {
    zonesSection.set(name, null); // Remove from config (case-sensitive here for YAML key)
    plugin.saveConfig(); // Save changes to config.yml
    messageUtil.log(Level.INFO, "zone-removed", "{zone_name}", name); // Log success
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
 public boolean setZoneDefaultAction(@NotNull String zoneName, @NotNull ZoneAction action) {
  AutoInformZone zone = definedZones.get(zoneName.toLowerCase()); // Use lowercase key
  if (zone != null) {
   // Create a new zone object with the updated default action (AutoInformZone is immutable for world/corners)
   AutoInformZone updatedZone = new AutoInformZone(
           zone.getName(),
           zone.getCorner1(),
           zone.getCorner2(),
           action, // Updated default action
           zone.getMaterialSpecificActions() // Keep existing material actions
   );
   definedZones.put(zoneName.toLowerCase(), updatedZone); // Replace old zone with updated one in map
   saveZoneToConfig(updatedZone); // Persist change to config.yml
   messageUtil.log(Level.INFO, "default-action-set", "{zone_name}", zoneName, "{action}", action.name());
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
 public boolean setZoneMaterialAction(@NotNull String zoneName, @NotNull Material material, @NotNull ZoneAction action) {
  AutoInformZone zone = definedZones.get(zoneName.toLowerCase()); // Use lowercase key
  if (zone != null) {
   // Create a mutable copy of material actions, update it, then create a new immutable zone
   Map<Material, ZoneAction> updatedMaterialActions = new HashMap<>(zone.getMaterialSpecificActions());
   updatedMaterialActions.put(material, action);

   AutoInformZone updatedZone = new AutoInformZone(
           zone.getName(),
           zone.getCorner1(),
           zone.getCorner2(),
           zone.getDefaultAction(), // Keep existing default action
           updatedMaterialActions // Updated material actions
   );
   definedZones.put(zoneName.toLowerCase(), updatedZone); // Replace old zone with updated one in map
   saveZoneToConfig(updatedZone); // Persist change to config.yml
   messageUtil.log(Level.INFO, "material-action-set", "{zone_name}", zoneName, "{material}", material.name(), "{action}", action.name());
   return true;
  }
  return false;
 }

 /**
  * Removes a material-specific action from a zone.
  *
  * @param zoneName The name of the zone.
  * @param material The material whose action to remove.
  * @return true if the action was removed, false if the zone or material action was not found.
  */
 public boolean removeZoneMaterialAction(@NotNull String zoneName, @NotNull Material material) {
  AutoInformZone zone = definedZones.get(zoneName.toLowerCase()); // Use lowercase key
  if (zone != null) {
   Map<Material, ZoneAction> updatedMaterialActions = new HashMap<>(zone.getMaterialSpecificActions());
   if (updatedMaterialActions.remove(material) != null) {
    // Create a new zone object with the updated material actions
    AutoInformZone updatedZone = new AutoInformZone(
            zone.getName(),
            zone.getCorner1(),
            zone.getCorner2(),
            zone.getDefaultAction(),
            updatedMaterialActions
    );
    definedZones.put(zoneName.toLowerCase(), updatedZone);
    saveZoneToConfig(updatedZone);
    messageUtil.log(Level.INFO, "material-action-removed-console", "{zone_name}", zoneName, "{material}", material.name());
    return true;
   }
  }
  return false;
 }


 /**
  * Saves a single zone's configuration to the config.yml file.
  * This method is called after any modification to a zone.
  *
  * @param zone The AutoInformZone object to save.
  */
 private void saveZoneToConfig(@NotNull AutoInformZone zone) {
  FileConfiguration config = plugin.getConfig();
  ConfigurationSection zonesSection = config.getConfigurationSection("zones");
  if (zonesSection == null) {
   zonesSection = config.createSection("zones"); // Create if it doesn't exist
  }

  ConfigurationSection zoneConfig = zonesSection.createSection(zone.getName()); // Use original case for YAML key
  zoneConfig.set("world", zone.getWorld().getName());
  zoneConfig.set("corner1.x", zone.getCorner1().getX());
  zoneConfig.set("corner1.y", zone.getCorner1().getY());
  zoneConfig.set("corner1.z", zone.getCorner1().getZ());
  zoneConfig.set("corner2.x", zone.getCorner2().getX());
  zoneConfig.set("corner2.y", zone.getCorner2().getY());
  zoneConfig.set("corner2.z", zone.getCorner2().getZ());
  zoneConfig.set("default-material-action", zone.getDefaultAction().name());

  Map<String, String> materialActionsMap = zone.getMaterialSpecificActions().entrySet().stream()
          .collect(Collectors.toMap(e -> e.getKey().name(), e -> e.getValue().name()));
  zoneConfig.set("material-actions", materialActionsMap);

  plugin.saveConfig(); // Save changes to config.yml
  messageUtil.log(Level.FINE, "debug-zone-saved-to-config", "{zone_name}", zone.getName()); // Debug log
 }

 /**
  * Finds the AutoInformZone that contains the given location.
  *
  * @param location The location to check.
  * @return The AutoInformZone containing the location, or null if no zone contains it.
  */
 @Nullable
 public AutoInformZone getZoneAtLocation(@NotNull Location location) {
  // Iterate through all zones and check if the location is within their bounds
  for (AutoInformZone zone : definedZones.values()) {
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
  definedZones.clear();
  globallyBannedMaterials.clear();
  messageUtil.log(Level.FINE, "debug-cleared-zones-and-banned-materials"); // Debug log
 }

 /**
  * Gets an unmodifiable set of all currently loaded zone names.
  *
  * @return A Set of zone names.
  */
 public @NotNull Set<String> getZoneNames() {
  return Collections.unmodifiableSet(definedZones.keySet());
 }

 /**
  * Gets a specific zone by name.
  *
  * @param name The name of the zone.
  * @return The AutoInformZone object, or null if not found.
  */
 @Nullable
 public AutoInformZone getZone(@NotNull String name) {
  return definedZones.get(name.toLowerCase()); // Retrieve using lowercase key
 }

 /**
  * Gets an unmodifiable map of all defined zones.
  *
  * @return A Map of defined zones.
  */
 public @NotNull Map<String, AutoInformZone> getDefinedZones() {
  return Collections.unmodifiableMap(definedZones);
 }

 /**
  * Checks if a material is in the globally banned list.
  *
  * @param material The material to check.
  * @return true if the material is globally banned, false otherwise.
  */
 public boolean isGloballyBanned(@NotNull Material material) {
  return globallyBannedMaterials.contains(material);
 }

 /**
  * Adds a material to the globally banned list and saves to config.
  *
  * @param material The material to add.
  * @return true if added, false if already present.
  */
 public boolean addGloballyBannedMaterial(@NotNull Material material) {
  if (globallyBannedMaterials.add(material)) {
   saveGloballyBannedMaterials();
   messageUtil.log(Level.INFO, "banned-material-added", "{material}", material.name());
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
 public boolean removeGloballyBannedMaterial(@NotNull Material material) {
  if (globallyBannedMaterials.remove(material)) {
   saveGloballyBannedMaterials();
   messageUtil.log(Level.INFO, "banned-material-removed", "{material}", material.name());
   return true;
  }
  return false;
 }

 /**
  * Gets an unmodifiable set of globally banned materials.
  *
  * @return A Set of globally banned materials.
  */
 public @NotNull Set<Material> getGloballyBannedMaterials() {
  return Collections.unmodifiableSet(globallyBannedMaterials);
 }

 /**
  * Loads all zone configurations from the plugin's config.yml file.
  * Clears existing zones and reloads them.
  */
 public void loadZonesFromConfig() {
  definedZones.clear(); // Clear existing zones before reloading
  globallyBannedMaterials.clear(); // Clear banned materials too

  FileConfiguration config = plugin.getConfig();

  // Load globally banned materials (if they are still part of config.yml)
  loadGloballyBannedMaterialsFromConfig(config); // Renamed for clarity

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
    AutoInformZone zone = new AutoInformZone(zoneName, corner1, corner2, defaultAction, materialActions);
    definedZones.put(zoneName.toLowerCase(), zone); // Store with lowercase key
    messageUtil.log(Level.INFO, "zone-loaded", "{zone_name}", zoneName);

   } catch (IllegalArgumentException e) {
    // Log with the exception
    messageUtil.log(Level.SEVERE, "plugin-invalid-zone-config-error",
            "{zone_name}", zoneName, "{error}", e.getMessage(), e);
   } catch (Exception e) {
    // Catch-all for any other unexpected exceptions during zone loading
    messageUtil.log(Level.SEVERE, "plugin-error-loading-zone",
            "{zone_name}", zoneName, "{error}", e.getMessage(), e);
   }
  }
  messageUtil.log(Level.INFO, "plugin-zones-loaded", "{count}", String.valueOf(definedZones.size()));
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
 private void loadGloballyBannedMaterialsFromConfig(FileConfiguration config) {
  // Clear existing banned materials to ensure a fresh load
  globallyBannedMaterials.clear();

  // Get the list of banned materials from the config
  List<String> materialNames = config.getStringList("globally-banned-materials"); // Explicitly using List<String>
  if (materialNames != null) {
   for (String materialName : materialNames) {
    try {
     Material material = Material.valueOf(materialName.toUpperCase());
     globallyBannedMaterials.add(material);
    } catch (IllegalArgumentException e) {
     // Log a warning if an invalid material name is found in the config
     messageUtil.log(Level.WARNING, "config-invalid-banned-material", "{material}", materialName);
    }
   }
  }
  messageUtil.log(Level.INFO, "plugin-banned-materials-loaded", "{count}", String.valueOf(globallyBannedMaterials.size()));
 }


 /**
  * Saves the current list of globally banned materials to the config.yml.
  */
 public void saveGloballyBannedMaterials() {
  FileConfiguration config = plugin.getConfig();
  // Convert the set of Material enums to a list of String names
  config.set("globally-banned-materials", globallyBannedMaterials.stream()
          .map(Enum::name)
          .collect(Collectors.toList())); // Changed to Collectors.toList() for simplicity
  plugin.saveConfig();
  messageUtil.log(Level.INFO, "plugin-banned-materials-saved", "{count}", String.valueOf(globallyBannedMaterials.size()));
 }
}