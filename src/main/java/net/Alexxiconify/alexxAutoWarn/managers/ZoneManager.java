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
  * @param messageUtil The MessageUtil instance for logging.
  */
 public ZoneManager(AlexxAutoWarn plugin, MessageUtil messageUtil) {
  this.plugin = plugin;
  this.messageUtil = messageUtil;
  this.zones = new HashMap<>();
  this.globallyBannedMaterials = new HashSet<>();
  loadZones();
 }

 /**
  * Loads zones and globally banned materials from the plugin's config.yml.
  * This method is called on plugin enable and reload.
  */
 public void loadZones() {
  zones.clear();
  globallyBannedMaterials.clear();

  FileConfiguration config = plugin.getConfig();

  // Load globally banned materials
  List<String> bannedMaterialNames = config.getStringList("banned-materials");
  for (String materialName : bannedMaterialNames) {
   try {
    Material material = Material.valueOf(materialName.toUpperCase());
    globallyBannedMaterials.add(material);
   } catch (IllegalArgumentException e) {
    messageUtil.log(Level.WARNING, "plugin-invalid-banned-material", "{material}", materialName);
   }
  }
  messageUtil.log(Level.INFO, "plugin-banned-materials-loaded", "{count}", String.valueOf(globallyBannedMaterials.size()));


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

    Location corner1 = parseLocation(zoneConfig.getConfigurationSection("corner1"), world);
    Location corner2 = parseLocation(zoneConfig.getConfigurationSection("corner2"), world);
    if (corner1 == null || corner2 == null) {
     messageUtil.log(Level.WARNING, "plugin-invalid-zone-config", "{zone_name}", zoneName);
     continue;
    }

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
       messageUtil.log(Level.WARNING, "plugin-invalid-material-action", "{material}", materialKey, "{zone_name}", zoneName);
      }
     }
    }

    AutoInformZone zone = new AutoInformZone(zoneName, corner1, corner2, defaultAction, materialActions);
    zones.put(zoneName.toLowerCase(), zone);
   } catch (Exception e) {
    messageUtil.log(Level.SEVERE, "plugin-error-loading-zone", "{zone_name}", zoneName, e);
   }
  }
  messageUtil.log(Level.INFO, "plugin-zones-loaded", "{count}", String.valueOf(zones.size()));
 }

 /**
  * Helper method to parse a Location from a ConfigurationSection.
  */
 private Location parseLocation(ConfigurationSection section, World world) {
  if (section == null) return null;
  try {
   double x = section.getDouble("x");
   double y = section.getDouble("y");
   double z = section.getDouble("z");
   return new Location(world, x, y, z);
  } catch (Exception e) {
   plugin.getLogger().log(Level.WARNING, "Failed to parse location from config section: " + section.getCurrentPath(), e);
   return null;
  }
 }

 /**
  * Gets a zone by its name.
  *
  * @param name The name of the zone (case-insensitive).
  * @return The AutoInformZone, or null if not found.
  */
 @Nullable
 public AutoInformZone getZone(@NotNull String name) {
  return zones.get(name.toLowerCase());
 }

 /**
  * Gets the zone that contains the given location.
  * If multiple zones contain the location, the first one found is returned.
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
  * Adds a new zone to the manager and saves it to the config.yml.
  * If a zone with the same name already exists, it will be updated.
  *
  * @param zone The AutoInformZone to add/update.
  */
 public void addZone(@NotNull AutoInformZone zone) {
  zones.put(zone.getName().toLowerCase(), zone);
  saveZoneToConfig(zone);
 }

 /**
  * Updates an existing zone in the manager and saves it to the config.yml.
  * This is typically used after modifying a zone's properties (e.g., default action, material actions).
  *
  * @param zone The AutoInformZone to update.
  */
 public void updateZone(@NotNull AutoInformZone zone) {
  saveZoneToConfig(zone);
 }

 /**
  * Removes a zone from the manager and from the config.yml.
  *
  * @param name The name of the zone to remove.
  * @return true if the zone was removed, false if not found.
  */
 public boolean removeZone(@NotNull String name) {
  AutoInformZone removedZone = zones.remove(name.toLowerCase());
  if (removedZone != null) {
   FileConfiguration config = plugin.getConfig();
   config.set("zones." + name, null);
   plugin.saveConfig();
   return true;
  }
  return false;
 }

 /**
  * Saves a single zone's data to the config.yml.
  * This method is used internally by addZone and updateZone.
  */
 private void saveZoneToConfig(@NotNull AutoInformZone zone) {
  FileConfiguration config = plugin.getConfig();
  String zonePath = "zones." + zone.getName();

  config.set(zonePath + ".world", zone.getWorld().getName());
  config.set(zonePath + ".corner1.x", zone.getCorner1().getX());
  config.set(zonePath + ".corner1.y", zone.getCorner1().getY());
  config.set(zonePath + ".corner1.z", zone.getCorner1().getZ());
  config.set(zonePath + ".corner2.x", zone.getCorner2().getX());
  config.set(zonePath + ".corner2.y", zone.getCorner2().getY());
  config.set(zonePath + ".corner2.z", zone.getCorner2().getZ());
  config.set(zonePath + ".default-material-action", zone.getDefaultAction().name());

  if (!zone.getMaterialSpecificActions().isEmpty()) {
   ConfigurationSection materialActionsSection = config.createSection(zonePath + ".material-actions");
   for (Map.Entry<Material, ZoneAction> entry : zone.getMaterialSpecificActions().entrySet()) {
    materialActionsSection.set(entry.getKey().name(), entry.getValue().name());
   }
  } else {
   config.set(zonePath + ".material-actions", null);
  }

  plugin.saveConfig();
 }

 /**
  * Returns an unmodifiable collection of all currently loaded zones.
  *
  * @return A Collection of AutoInformZone objects.
  */
 public Collection<AutoInformZone> getAllZones() {
  return Collections.unmodifiableCollection(zones.values());
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
  config.set("banned-materials", globallyBannedMaterials.stream().map(Enum::name).collect(Collectors.toList()));
  plugin.saveConfig();
 }
}