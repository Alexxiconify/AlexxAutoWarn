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
  * @param plugin      The main plugin instance.
  * @param messageUtil The MessageUtil instance for sending messages.
  */
 public ZoneManager(AlexxAutoWarn plugin, MessageUtil messageUtil) {
  this.plugin = plugin;
  this.messageUtil = messageUtil;
  this.zones = new HashMap<>();
  this.globallyBannedMaterials = new HashSet<>();
 }

 /**
  * Loads zones and globally banned materials from the plugin's configuration.
  * This method is typically called on plugin enable and reload.
  */
 public void loadZones() {
  zones.clear(); // Clear existing zones before loading
  globallyBannedMaterials.clear(); // Clear existing banned materials

  FileConfiguration config = plugin.getConfig();

  // Load globally banned materials
  List<String> bannedMaterialsList = config.getStringList("globally-banned-materials");
  for (String materialName : bannedMaterialsList) {
   try {
    Material material = Material.valueOf(materialName.toUpperCase());
    globallyBannedMaterials.add(material);
   } catch (IllegalArgumentException e) {
    messageUtil.log(Level.WARNING, "plugin-invalid-banned-material", "{material_name}", materialName);
   }
  }
  messageUtil.log(Level.INFO, "plugin-banned-materials-loaded", "{count}", globallyBannedMaterials.size());


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

    Location corner1 = deserializeLocation(zoneConfig.getString("corner1.x") + "," + zoneConfig.getString("corner1.y") + "," + zoneConfig.getString("corner1.z"), world);
    Location corner2 = deserializeLocation(zoneConfig.getString("corner2.x") + "," + zoneConfig.getString("corner2.y") + "," + zoneConfig.getString("corner2.z"), world);

    ZoneAction defaultAction = ZoneAction.valueOf(zoneConfig.getString("default-material-action", "ALERT").toUpperCase());

    Map<Material, ZoneAction> materialActions = new HashMap<>();
    ConfigurationSection materialActionsSection = zoneConfig.getConfigurationSection("material-actions");
    if (materialActionsSection != null) {
     for (String materialKey : materialActionsSection.getKeys(false)) {
      try {
       Material material = Material.valueOf(materialKey.toUpperCase());
       ZoneAction action = ZoneAction.valueOf(materialActionsSection.getString(materialKey).toUpperCase());
       materialActions.put(material, action);
      } catch (IllegalArgumentException e) {
       messageUtil.log(Level.WARNING, "plugin-invalid-material-action-entry",
               "{material_key}", materialKey, "{zone_name}", zoneName, "{error}", e.getMessage());
      }
     }
    }

    AutoInformZone zone = new AutoInformZone(zoneName, corner1, corner2, defaultAction, materialActions);
    zones.put(zoneName.toLowerCase(), zone); // Store with lowercase name for consistent lookup

   } catch (IllegalArgumentException e) {
    messageUtil.log(Level.WARNING, "plugin-invalid-zone-config", "{zone_name}", zoneName, "{error}", e.getMessage());
   }
  }
  messageUtil.log(Level.INFO, "plugin-zones-loaded", "{count}", zones.size());
 }

 /**
  * Saves all current zones and globally banned materials to the plugin's configuration.
  * This method is typically called on plugin disable and after zone modifications.
  */
 public void saveZones() {
  FileConfiguration config = plugin.getConfig();

  // Save globally banned materials
  List<String> bannedMaterialsList = globallyBannedMaterials.stream()
          .map(Enum::name)
          .collect(Collectors.toList());
  config.set("globally-banned-materials", bannedMaterialsList);


  config.set("zones", null); // Clear existing zones section to write fresh data

  for (AutoInformZone zone : zones.values()) {
   String zonePath = "zones." + zone.getName().toLowerCase();
   config.set(zonePath + ".world", zone.getWorld().getName());
   Location c1 = zone.getCorner1();
   config.set(zonePath + ".corner1.x", c1.getX());
   config.set(zonePath + ".corner1.y", c1.getY());
   config.set(zonePath + ".corner1.z", c1.getZ());

   Location c2 = zone.getCorner2();
   config.set(zonePath + ".corner2.x", c2.getX());
   config.set(zonePath + ".corner2.y", c2.getY());
   config.set(zonePath + ".corner2.z", c2.getZ());

   config.set(zonePath + ".default-material-action", zone.getDefaultAction().name());

   // Save material-specific actions
   if (!zone.getMaterialSpecificActions().isEmpty()) {
    ConfigurationSection materialActionsSection = config.createSection(zonePath + ".material-actions");
    zone.getMaterialSpecificActions().forEach((material, action) ->
            materialActionsSection.set(material.name(), action.name()));
   } else {
    config.set(zonePath + ".material-actions", null); // Ensure section is removed if empty
   }
  }
  plugin.saveConfig(); // Save the configuration file to disk
  messageUtil.log(Level.INFO, "plugin-zones-saved", "{count}", zones.size());
 }

 /**
  * Adds or updates an AutoInformZone.
  *
  * @param zone The AutoInformZone to add or update.
  */
 public void addZone(@NotNull AutoInformZone zone) {
  zones.put(zone.getName().toLowerCase(), zone);
  saveZones(); // Save changes to config immediately
 }

 /**
  * Removes an AutoInformZone by its name.
  *
  * @param zoneName The name of the zone to remove.
  * @return true if the zone was removed, false otherwise.
  */
 public boolean removeZone(@NotNull String zoneName) {
  if (zones.remove(zoneName.toLowerCase()) != null) {
   saveZones(); // Save changes to config immediately
   return true;
  }
  return false;
 }

 /**
  * Retrieves an AutoInformZone by its name.
  *
  * @param zoneName The name of the zone.
  * @return The AutoInformZone, or null if not found.
  */
 @Nullable
 public AutoInformZone getZone(@NotNull String zoneName) {
  return zones.get(zoneName.toLowerCase());
 }

 /**
  * Finds the first AutoInformZone that contains the given location.
  *
  * @param location The location to check.
  * @return The AutoInformZone containing the location, or null if no zone applies.
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
  * Checks if a material is globally banned.
  *
  * @param material The material to check.
  * @return true if the material is globally banned, false otherwise.
  */
 public boolean isGloballyBanned(@NotNull Material material) {
  return globallyBannedMaterials.contains(material);
 }

 /**
  * Adds a material to the globally banned list.
  *
  * @param material The material to add.
  * @return true if the material was added, false if it was already in the list.
  */
 public boolean addGloballyBannedMaterial(@NotNull Material material) {
  if (globallyBannedMaterials.add(material)) {
   saveZones();
   return true;
  }
  return false;
 }

 /**
  * Removes a material from the globally banned list.
  *
  * @param material The material to remove.
  * @return true if the material was removed, false if it was not in the list.
  */
 public boolean removeGloballyBannedMaterial(@NotNull Material material) {
  if (globallyBannedMaterials.remove(material)) {
   saveZones();
   return true;
  }
  return false;
 }

 /**
  * Returns an unmodifiable set of globally banned materials.
  */
 public Set<Material> getGloballyBannedMaterials() {
  return Collections.unmodifiableSet(globallyBannedMaterials);
 }


 /**
  * Returns a collection of all AutoInformZones.
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