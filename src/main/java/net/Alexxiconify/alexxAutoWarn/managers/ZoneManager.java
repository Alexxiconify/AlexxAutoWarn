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
 // Corrected to store AutoInformZone objects
 private final Map<String, AutoInformZone> zones;

 // Set of materials that are globally banned (denied outside any specific zone)
 private final Set<Material> globallyBannedMaterials;

 /**
  * Constructor for ZoneManager.
  *
  * @param plugin The main plugin instance.
  * @param messageUtil The MessageUtil instance for sending messages and logging.
  */
 public ZoneManager(AlexxAutoWarn plugin, MessageUtil messageUtil) {
  this.plugin = plugin;
  this.messageUtil = messageUtil;
  this.zones = new HashMap<>(); // Initialize the map
  this.globallyBannedMaterials = new HashSet<>(); // Initialize the set
 }

 /**
  * Loads all AutoInform zones from the plugin's config.yml.
  * Clears existing zones before loading.
  */
 public void loadZones() {
  this.zones.clear(); // Clear existing zones
  plugin.reloadConfig(); // Reload config to get fresh data
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
    if (worldName == null || worldName.isEmpty()) {
     messageUtil.log(Level.WARNING, "zone-config-missing-world", "{zone_name}", zoneName);
     continue;
    }

    World world = Bukkit.getWorld(worldName);
    if (world == null) {
     messageUtil.log(Level.WARNING, "plugin-world-not-found", "{world_name}", worldName, "{zone_name}", zoneName);
     continue;
    }

    String corner1String = zoneConfig.getString("corner1");
    String corner2String = zoneConfig.getString("corner2");

    if (corner1String == null || corner2String == null || corner1String.isEmpty() || corner2String.isEmpty()) {
     messageUtil.log(Level.WARNING, "zone-config-missing-corners", "{zone_name}", zoneName);
     continue;
    }

    // Call deserializeLocation with the x,y,z string and the World object
    Location corner1 = deserializeLocation(corner1String, world);
    Location corner2 = deserializeLocation(corner2String, world);

    // Default material action
    String defaultActionString = zoneConfig.getString("default-material-action", "ALERT"); // Default to ALERT if not specified
    ZoneAction defaultMaterialAction = ZoneAction.valueOf(defaultActionString.toUpperCase());

    // Material-specific actions
    Map<Material, ZoneAction> materialActions = new EnumMap<>(Material.class);
    ConfigurationSection materialActionsSection = zoneConfig.getConfigurationSection("material-actions");
    if (materialActionsSection != null) {
     for (String materialKey : materialActionsSection.getKeys(false)) {
      try {
       Material material = Material.valueOf(materialKey.toUpperCase());
       String actionString = materialActionsSection.getString(materialKey);
       ZoneAction action = ZoneAction.valueOf(actionString.toUpperCase());
       materialActions.put(material, action);
      } catch (IllegalArgumentException e) {
       messageUtil.log(Level.WARNING, "zone-config-invalid-material-action", "{zone_name}", zoneName, "{material_key}", materialKey, "{error}", e.getMessage());
      }
     }
    }

    // --- CORRECTED: Use AutoInformZone instead of ZoneConfig ---
    AutoInformZone zone = new AutoInformZone(zoneName, corner1, corner2, defaultMaterialAction, materialActions);
    this.zones.put(zoneName.toLowerCase(), zone);
    messageUtil.log(Level.INFO, "zone-loaded", "{zone_name}", zoneName);

   } catch (IllegalArgumentException e) { // Catching exceptions from deserializeLocation or valueOf
    messageUtil.log(Level.SEVERE, "plugin-invalid-zone-config-error", "{zone_name}", zoneName, "{error}", e.getMessage());
   } catch (Exception e) { // Catch any other unexpected errors during zone loading
    messageUtil.log(Level.SEVERE, "plugin-error-loading-zone", "{zone_name}", zoneName, "{error}", e.getMessage());
   }
  }
  messageUtil.log(Level.INFO, "plugin-zones-loaded", "{count}", this.zones.size());
 }

 /**
  * Saves all currently defined AutoInform zones to the plugin's config.yml.
  * Existing zones in the config will be overwritten.
  */
 public void saveZones() {
  FileConfiguration config = plugin.getConfig();
  config.set("zones", null); // Clear existing zones section before saving new ones

  if (this.zones.isEmpty()) {
   messageUtil.log(Level.INFO, "plugin-no-zones-defined"); // Changed message key
   plugin.saveConfig();
   return;
  }

  for (AutoInformZone zone : zones.values()) {
   String path = "zones." + zone.getName().toLowerCase();
   config.set(path + ".world", zone.getWorld().getName());
   config.set(path + ".corner1", serializeLocation(zone.getCorner1()));
   config.set(path + ".corner2", serializeLocation(zone.getCorner2()));
   config.set(path + ".default-material-action", zone.getDefaultAction().name());

   if (!zone.getMaterialSpecificActions().isEmpty()) {
    ConfigurationSection materialActionsSection = config.createSection(path + ".material-actions");
    zone.getMaterialSpecificActions().forEach((material, action) ->
            materialActionsSection.set(material.name(), action.name()));
   }
  }
  plugin.saveConfig();
  messageUtil.log(Level.INFO, "plugin-zones-saved", "{count}", this.zones.size());
 }

 /**
  * Loads globally banned materials from config.yml.
  */
 public void loadGloballyBannedMaterials() {
  this.globallyBannedMaterials.clear();
  FileConfiguration config = plugin.getConfig();
  List<String> bannedList = config.getStringList("globally-banned-materials");

  if (bannedList != null && !bannedList.isEmpty()) {
   for (String materialName : bannedList) {
    try {
     Material material = Material.valueOf(materialName.toUpperCase());
     this.globallyBannedMaterials.add(material);
    } catch (IllegalArgumentException e) {
     messageUtil.log(Level.WARNING, "plugin-invalid-banned-material", "{material}", materialName);
    }
   }
   messageUtil.log(Level.INFO, "plugin-banned-materials-loaded", "{count}", this.globallyBannedMaterials.size());
  } else {
   messageUtil.log(Level.INFO, "plugin-no-banned-materials"); // Changed message key
  }
 }

 /**
  * Saves globally banned materials to config.yml.
  */
 public void saveGloballyBannedMaterials() {
  FileConfiguration config = plugin.getConfig();
  List<String> bannedList = globallyBannedMaterials.stream()
          .map(Enum::name)
          .collect(Collectors.toList());
  config.set("globally-banned-materials", bannedList);
  plugin.saveConfig();
  messageUtil.log(Level.INFO, "plugin-banned-materials-saved", "{count}", this.globallyBannedMaterials.size());
 }

 /**
  * Adds a zone to the manager. If a zone with the same name already exists, it is updated.
  *
  * @param zone The AutoInformZone to add or update.
  */
 public void addZone(@NotNull AutoInformZone zone) {
  this.zones.put(zone.getName().toLowerCase(), zone);
  saveZones(); // Persist changes
 }

 /**
  * Retrieves a zone by its name (case-insensitive).
  *
  * @param name The name of the zone.
  * @return The AutoInformZone object, or null if not found.
  */
 @Nullable
 public AutoInformZone getZone(@NotNull String name) {
  return zones.get(name.toLowerCase());
 }

 /**
  * Removes a zone by its name.
  *
  * @param name The name of the zone to remove.
  * @return true if the zone was removed, false otherwise.
  */
 public boolean removeZone(@NotNull String name) {
  AutoInformZone removed = zones.remove(name.toLowerCase());
  if (removed != null) {
   saveZones(); // Persist changes
   return true;
  }
  return false;
 }

 /**
  * Gets the AutoInformZone at a specific location, if any.
  * If multiple zones overlap, the first one found is returned.
  * (You might want to implement priority logic if overlaps are common).
  *
  * @param location The location to check.
  * @return The AutoInformZone at the location, or null if no zone contains it.
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
  * @return true if the material was added, false if it was already present.
  */
 public boolean addGloballyBannedMaterial(@NotNull Material material) {
  boolean added = globallyBannedMaterials.add(material);
  if (added) {
   saveGloballyBannedMaterials(); // Persist changes
  }
  return added;
 }

 /**
  * Removes a material from the globally banned list.
  *
  * @param material The material to remove.
  * @return true if the material was removed, false if it was not present.
  */
 public boolean removeGloballyBannedMaterial(@NotNull Material material) {
  boolean removed = globallyBannedMaterials.remove(material);
  if (removed) {
   saveGloballyBannedMaterials(); // Persist changes
  }
  return removed;
 }

 /**
  * Returns an unmodifiable set of all globally banned materials.
  *
  * @return A Set of globally banned Materials.
  */
 public Set<Material> getGloballyBannedMaterials() {
  return Collections.unmodifiableSet(globallyBannedMaterials);
 }

 /**
  * Returns an unmodifiable collection of all AutoInformZones.
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
  * @param locationString The string representation of the location (x,y,z).
  * @param world          The world the location belongs to.
  * @return The Location object.
  * @throws IllegalArgumentException if the string format is invalid or parsing fails.
  */
 private Location deserializeLocation(@Nullable String locationString, @NotNull World world) {
  if (locationString == null || locationString.isEmpty()) {
   throw new IllegalArgumentException("Location string cannot be null or empty.");
  }
  String[] parts = locationString.split(",");
  if (parts.length != 3) { // Expecting 3 parts: x, y, z
   throw new IllegalArgumentException("Invalid location string format: " + locationString);
  }
  try {
   double x = Double.parseDouble(parts[0]);
   double y = Double.parseDouble(parts[1]);
   double z = Double.parseDouble(parts[2]);
   return new Location(world, x, y, z);
  } catch (NumberFormatException e) {
   throw new IllegalArgumentException("Invalid number format in location string: " + locationString + " -> " + e.getMessage());
  }
 }
}