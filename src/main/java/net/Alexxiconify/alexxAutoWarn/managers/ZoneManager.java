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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
  globallyBannedMaterials.clear();

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
    AutoInformZ