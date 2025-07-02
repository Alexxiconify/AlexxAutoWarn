package net.Alexxiconify.alexxAutoWarn;

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
 private final Map < String, Zone > zones = new ConcurrentHashMap <> ( );

 /**
  * Constructs a new ZoneManager.
  *
  * @param plugin The main AlexxAutoWarn plugin instance.
  */
 public ZoneManager ( AlexxAutoWarn plugin ) {
  this.plugin = plugin;
 }

 /**
  * Asynchronously loads all zones from the configuration file.
  *
  * @return A CompletableFuture that completes when loading is finished.
  */
 public CompletableFuture < Void > loadZones ( ) {
  return CompletableFuture.runAsync ( ( ) -> {
   zones.clear ( ); // Clear existing zones before loading new ones
   FileConfiguration config = plugin.getConfig ( );
   ConfigurationSection zonesSection = config.getConfigurationSection ( "zones" );
   if ( zonesSection == null ) {
    plugin.getSettings ( ).log ( Level.INFO , "No zones section found in config.yml. Loaded 0 zones." );
    return;
   }

   // Iterate through each defined zone in the config
   for ( String zoneName : zonesSection.getKeys ( false ) ) {
    ConfigurationSection zoneConfig = zonesSection.getConfigurationSection ( zoneName );
    if ( zoneConfig == null ) {
     plugin.getSettings ( ).log ( Level.WARNING , "Skipping malformed zone configuration for '" + zoneName + "'." );
     continue;
    }

    try {
     String worldName = zoneConfig.getString ( "world" );
     World world = null;
     if ( worldName != null ) {
      world = Bukkit.getWorld ( worldName );
     }
     if ( world == null ) {
      plugin.getSettings ( ).log (
        Level.WARNING , "World '" + worldName + "' for zone '" + zoneName + "' not found " +
          "on the server. Skipping this zone."
      );
      continue;
     }

     // Correctly read Vector from nested x, y, z values.
     // ConfigurationSection.getVector() expects a directly serialized Vector,
     // but the config stores x, y, z as individual keys.
     ConfigurationSection corner1Section = zoneConfig.getConfigurationSection ( "corner1" );
     ConfigurationSection corner2Section = zoneConfig.getConfigurationSection ( "corner2" );

     Vector corner1 = null;
     if ( corner1Section != null ) {
      corner1 = new Vector (
        corner1Section.getDouble ( "x" ) ,
        corner1Section.getDouble ( "y" ) ,
        corner1Section.getDouble ( "z" )
      );
     }

     Vector corner2 = null;
     if ( corner2Section != null ) {
      corner2 = new Vector (
        corner2Section.getDouble ( "x" ) ,
        corner2Section.getDouble ( "y" ) ,
        corner2Section.getDouble ( "z" )
      );
     }

     // Ensure both corners are not null before creating the Zone
     if ( corner1 == null || corner2 == null ) {
      plugin.getSettings ( ).log (
        Level.SEVERE , "Failed to load zone '" + zoneName + "': Missing 'corner1' or " +
          "'corner2' coordinates. Please define x, y, z for both corners."
      );
      continue; // Skip this zone if corners are invalid
     }

     // Parse default action, defaulting to ALERT if not specified or invalid
     Zone.Action defaultAction = Zone.Action.ALERT; // Default to ALERT
     String defaultActionString = zoneConfig.getString ( "default-action" );
     if ( defaultActionString != null ) {
      try {
       defaultAction = Zone.Action.valueOf ( defaultActionString.toUpperCase ( ) );
      } catch ( IllegalArgumentException e ) {
       plugin.getSettings ( ).log (
         Level.WARNING ,
         "Invalid default-action '" + defaultActionString + "' for zone '" + zoneName + "'" +
           ". Defaulting to ALERT."
       );
      }
     }

     // Parse material-specific actions
     Map < Material, Zone.Action > materialActions = new EnumMap <> ( Material.class );
     ConfigurationSection actionsSection = zoneConfig.getConfigurationSection ( "material-actions" );
     if ( actionsSection != null ) {
      for ( String materialKey : actionsSection.getKeys ( false ) ) {
       Material material;
       try {
        material = Material.valueOf ( materialKey.toUpperCase ( ) );
       } catch ( IllegalArgumentException e ) {
        plugin.getSettings ( ).log (
          Level.WARNING ,
          "Invalid material name '" + materialKey + "' in zone '" + zoneName + "'. " +
            "Skipping."
        );
        continue;
       }
       String actionString = actionsSection.getString ( materialKey );
       if ( actionString != null ) {
        try {
         Zone.Action action = Zone.Action.valueOf ( actionString.toUpperCase ( ) );
         materialActions.put ( material , action );
        } catch ( IllegalArgumentException e ) {
         plugin.getSettings ( ).log (
           Level.WARNING ,
           "Invalid action '" + actionString + "' for material '" + materialKey + "' in " +
             "zone '" + zoneName + "'. Skipping this material action."
         );
        }
       } else {
        plugin.getSettings ( ).log (
          Level.WARNING , "Invalid material name '" + materialKey + "' or action string " +
            "for material in zone '" + zoneName + "'. Skipping."
        );
       }
      }
     }

     // Create and store the new Zone object
     Zone zone = new Zone ( zoneName , world , corner1 , corner2 , defaultAction , materialActions );
     zones.put ( zone.getName ( ) , zone );

    } catch ( Exception e ) {
     // Log any other unexpected errors during zone loading
     plugin.getLogger ( ).log (
       Level.SEVERE ,
       "An unexpected error occurred while loading zone '" + zoneName + "': " + e.getMessage ( ) , e
     );
    }
   }
   // Log the total number of zones loaded
   plugin.getSettings ( ).log ( Level.INFO , "Loaded " + zones.size ( ) + " zones." );
  } );
 }

 /**
  * Saves all zones to the configuration file.
  * Can be run asynchronously or synchronously.
  *
  * @param async If true, the save operation will be performed on an async thread.
  */
 public void saveZones ( boolean async ) {
  Runnable saveTask = ( ) -> {
   FileConfiguration config = plugin.getConfig ( );
   // Clear the old "zones" section before writing to prevent stale data
   config.set ( "zones" , null );

   for ( Zone zone : zones.values ( ) ) {
    String zonePath = "zones." + zone.getName ( );
    config.set ( zonePath + ".world" , zone.getWorldName ( ) );

    // Save Vectors as nested x, y, z for readability in config
    config.set ( zonePath + ".corner1.x" , zone.getMin ( ).getX ( ) );
    config.set ( zonePath + ".corner1.y" , zone.getMin ( ).getY ( ) );
    config.set ( zonePath + ".corner1.z" , zone.getMin ( ).getZ ( ) );
    config.set ( zonePath + ".corner2.x" , zone.getMax ( ).getX ( ) );
    config.set ( zonePath + ".corner2.y" , zone.getMax ( ).getY ( ) );
    config.set ( zonePath + ".corner2.z" , zone.getMax ( ).getZ ( ) );

    config.set ( zonePath + ".default-action" , zone.getDefaultAction ( ).name ( ) );

    if ( !zone.getMaterialActions ( ).isEmpty ( ) ) {
     zone.getMaterialActions ( ).forEach ( ( material , action ) -> {
      config.set ( zonePath + ".material-actions." + material.name ( ) , action.name ( ) );
     } );
    }
   }
   // Save the configuration file to disk
   plugin.saveConfig ( );
   plugin.getSettings ( ).log ( Level.INFO , "Successfully saved " + zones.size ( ) + " zones." );
  };

  // Execute the save task either asynchronously or synchronously
  if ( async ) {
   // Using Bukkit's scheduler for async tasks to ensure thread safety with Bukkit API calls
   plugin.getServer ( ).getScheduler ( ).runTaskAsynchronously ( plugin , saveTask );
  } else {
   saveTask.run ( ); // Execute on the current thread
  }
 }

 /**
  * Adds or updates a zone in memory and triggers an asynchronous save to config.
  *
  * @param zone The zone to add or update.
  */
 public void addOrUpdateZone ( @NotNull Zone zone ) {
  zones.put ( zone.getName ( ) , zone );
  saveZones ( true ); // Save asynchronously to prevent server lag
 }

 /**
  * Removes a zone from memory and triggers an asynchronous save to config.
  *
  * @param zoneName The name of the zone to remove.
  * @return true if the zone was found and removed, false otherwise.
  */
 public boolean removeZone ( @NotNull String zoneName ) {
  Zone removed = zones.remove ( zoneName.toLowerCase ( ) ); // Ensure case-insensitive removal
  if ( removed != null ) {
   saveZones ( true ); // Save asynchronously
   return true;
  }
  return false;
 }

 /**
  * Retrieves a zone by its name.
  *
  * @param zoneName The name of the zone (case-insensitive).
  * @return The Zone object if found, otherwise null.
  */
 @Nullable
 public Zone getZone ( @NotNull String zoneName ) {
  return zones.get ( zoneName.toLowerCase ( ) ); // Ensure case-insensitive retrieval
 }

 /**
  * Finds the first zone that contains the given location.
  * This method performs a linear scan through all loaded zones.
  *
  * @param location The location to check.
  * @return The Zone object if found, otherwise null.
  */
 @Nullable
 public Zone getZoneAt ( @NotNull Location location ) {
  // This linear scan is acceptable for a moderate number of zones.
  // For servers with hundreds/thousands of zones, a spatial partitioning data structure (e.g., Quadtree/Octree)
  // would be more performant.
  for ( Zone zone : zones.values ( ) ) {
   if ( zone.contains ( location ) ) {
    return zone;
   }
  }
  return null;
 }

 /**
  * Retrieves all currently loaded zones.
  *
  * @return A collection of all Zone objects.
  */
 @NotNull
 public Collection < Zone > getAllZones ( ) {
  return zones.values ( );
 }
}