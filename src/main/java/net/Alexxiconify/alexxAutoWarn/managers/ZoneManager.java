package net.alexxiconify.alexxAutoWarn.managers;

import net.alexxiconify.alexxAutoWarn.AlexxAutoWarn;
import net.alexxiconify.alexxAutoWarn.objects.Zone;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages the loading, storage, and retrieval of AutoWarn zones.
 * Handles asynchronous saving and loading to prevent server lag.
 */
public class ZoneManager {

 private final AlexxAutoWarn plugin;
 private final Map<String, Zone> zones = new ConcurrentHashMap<>();

 public ZoneManager(AlexxAutoWarn plugin) {
  this.plugin = plugin;
 }

 /**
  * Asynchronously loads all zones from the configuration file.
  *
  * @return A CompletableFuture that completes when loading is finished.
  */
 public CompletableFuture<Void> loadZones() {
  return CompletableFuture.runAsync(() -> {
   zones.clear();
   FileConfiguration config = plugin.getConfig();
   ConfigurationSection zonesSection = config.getConfigurationSection("zones");
   if (zonesSection == null) {
    plugin.getSettings().log(Level.INFO, "No zones found in config.yml to load.");
    return;
   }

   for (String zoneName : zonesSection.getKeys(false)) {
    ConfigurationSection zoneConfig = zonesSection.getConfigurationSection(zoneName);
    if (zoneConfig == null) continue;

    try {
     String worldName = zoneConfig.getString("world");
     World world = Bukkit.getWorld(worldName);
     if (world == null) {
      plugin.getSettings().log(Level.WARNING, "World '" + worldName + "' for zone '" + zoneName + "' not found. Skipping.");
      continue;
     }

     Vector corner1 = zoneConfig.getVector("corner1");
     Vector corner2 = zoneConfig.getVector("corner2");

     Zone.Action defaultAction = Zone.Action.valueOf(zoneConfig.getString("default-action", "ALERT").toUpperCase());

     Map<Material, Zone.Action> materialActions = new EnumMap<>(Material.class);
     ConfigurationSection actionsSection = zoneConfig.getConfigurationSection("material-actions");
     if (actionsSection != null) {
      for (String materialKey : actionsSection.getKeys(false)) {
       Material material = Material.getMaterial(materialKey.toUpperCase());
       Zone.Action action = Zone.Action.valueOf(actionsSection.getString(materialKey).toUpperCase());
       if (material != null) {
        materialActions.put(material, action);
       }
      }
     }

     Zone zone = new Zone(zoneName, world, corner1, corner2, defaultAction, materialActions);
     zones.put(zone.getName(), zone);

    } catch (Exception e) {
     plugin.getSettings().log(Level.SEVERE, "Failed to load zone '" + zoneName + "': " + e.getMessage());
    }
   }
   plugin.getSettings().log(Level.INFO, "Loaded " + zones.size() + " zones.");
  });
 }

 /**
  * Saves all zones to the configuration file.
  * Can be run asynchronously or synchronously.
  *
  * @param async If true, the save operation will be performed on an async thread.
  */
 public void saveZones(boolean async) {
  Runnable saveTask = () -> {
   FileConfiguration config = plugin.getConfig();
   // Clear the old zones section before writing
   config.set("zones", null);

   for (Zone zone : zones.values()) {
    String zonePath = "zones." + zone.getName();
    config.set(zonePath + ".world", zone.getWorldName());
    config.set(zonePath + ".corner1", zone.getMin());
    config.set(zonePath + ".corner2", zone.getMax());
    config.set(zonePath + ".default-action", zone.getDefaultAction().name());

    if (!zone.getMaterialActions().isEmpty()) {
     zone.getMaterialActions().forEach((material, action) -> {
      config.set(zonePath + ".material-actions." + material.name(), action.name());
     });
    }
   }
   plugin.saveConfig();
   plugin.getSettings().log(Level.INFO, "Successfully saved " + zones.size() + " zones.");
  };

  if (async) {
   plugin.getServer().getAsyncScheduler().runNow(plugin, (task) -> saveTask.run());
  } else {
   saveTask.run();
  }
 }

 /**
  * Adds or updates a zone and saves the configuration.
  *
  * @param zone The zone to add or update.
  */
 public void addOrUpdateZone(@NotNull Zone zone) {
  zones.put(zone.getName(), zone);
  saveZones(true); // Save asynchronously
 }

 /**
  * Removes a zone and saves the configuration.
  *
  * @param zoneName The name of the zone to remove.
  * @return true if the zone was found and removed, false otherwise.
  */
 public boolean removeZone(@NotNull String zoneName) {
  Zone removed = zones.remove(zoneName.toLowerCase());
  if (removed != null) {
   saveZones(true); // Save asynchronously
   return true;
  }
  return false;
 }

 @Nullable
 public Zone getZone(@NotNull String zoneName) {
  return zones.get(zoneName.toLowerCase());
 }

 /**
  * Finds the first zone that contains the given location.
  *
  * @param location The location to check.
  * @return An Optional containing the zone if found, otherwise empty.
  */
 @Nullable
 public Zone getZoneAt(@NotNull Location location) {
  // This linear scan is acceptable for a moderate number of zones.
  // For servers with hundreds/thousands of zones, a spatial partitioning data structure (e.g., Quadtree/Octree) would be more performant.
  for (Zone zone : zones.values()) {
   if (zone.contains(location)) {
    return zone;
   }
  }
  return null;
 }

 @NotNull
 public Collection<Zone> getAllZones() {
  return zones.values();
 }
}