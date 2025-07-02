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

public class ZoneManager {
 private final AlexxAutoWarn plugin;
 private final Map < String, Zone > zones = new ConcurrentHashMap <> ( );

 public ZoneManager ( AlexxAutoWarn plugin ) {
  this.plugin = plugin;
 }

 public CompletableFuture < Void > loadZones ( ) {
  return CompletableFuture.runAsync ( ( ) -> {
   zones.clear ( );
   FileConfiguration config = plugin.getConfig ( );
   ConfigurationSection zonesSection = config.getConfigurationSection ( "zones" );
   if ( zonesSection == null ) {
    plugin.getSettings ( ).log ( Level.INFO , "No zones section found in config.yml. Loaded 0 zones." );
    return;
   }

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
        Level.WARNING , "World '" + worldName + "' for zone '" + zoneName +
          "' not found on the server. Skipping this zone."
      );
      continue;
     }

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

     if ( corner1 == null || corner2 == null ) {
      plugin.getSettings ( ).log (
        Level.SEVERE , "Failed to load zone '" + zoneName +
          "': Missing 'corner1' or 'corner2' coordinates. Please define x, y, z for both corners."
      );
      continue;
     }

     Zone.Action defaultAction = Zone.Action.ALERT;
     String defaultActionString = zoneConfig.getString ( "default-action" );
     if ( defaultActionString != null ) {
      try {
       defaultAction = Zone.Action.valueOf ( defaultActionString.toUpperCase ( ) );
      } catch ( IllegalArgumentException e ) {
       plugin.getSettings ( ).log (
         Level.WARNING , "Invalid default-action '" + defaultActionString +
           "' for zone '" + zoneName + "'. Defaulting to ALERT."
       );
      }
     }

     Map < Material, Zone.Action > materialActions = new EnumMap <> ( Material.class );
     ConfigurationSection actionsSection = zoneConfig.getConfigurationSection ( "material-actions" );
     if ( actionsSection != null ) {
      for ( String materialKey : actionsSection.getKeys ( false ) ) {
       Material material;
       try {
        material = Material.valueOf ( materialKey.toUpperCase ( ) );
       } catch ( IllegalArgumentException e ) {
        plugin.getSettings ( ).log (
          Level.WARNING , "Invalid material name '" + materialKey +
            "' in zone '" + zoneName + "'. Skipping."
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
           Level.WARNING , "Invalid action '" + actionString +
             "' for material '" + materialKey + "' in zone '" + zoneName +
             "'. Skipping this material action."
         );
        }
       } else {
        plugin.getSettings ( ).log (
          Level.WARNING , "Invalid material name '" + materialKey +
            "' or action string for material in zone '" + zoneName + "'. Skipping."
        );
       }
      }
     }

     int priority = zoneConfig.getInt ( "priority" , 0 );

     Zone zone = new Zone ( zoneName , world , corner1 , corner2 , defaultAction , materialActions , priority );
     zones.put ( zone.getName ( ) , zone );

    } catch ( Exception e ) {
     plugin.getLogger ( ).log (
       Level.SEVERE , "An unexpected error occurred while loading zone '" +
         zoneName + "': " + e.getMessage ( ) , e
     );
    }
   }
   plugin.getSettings ( ).log ( Level.INFO , "Loaded " + zones.size ( ) + " zones." );
  } );
 }

 public void saveZones ( boolean async ) {
  Runnable saveTask = ( ) -> {
   FileConfiguration config = plugin.getConfig ( );
   config.set ( "zones" , null );

   for ( Zone zone : zones.values ( ) ) {
    String zonePath = "zones." + zone.getName ( );
    config.set ( zonePath + ".world" , zone.getWorldName ( ) );

    config.set ( zonePath + ".corner1.x" , zone.getMin ( ).getX ( ) );
    config.set ( zonePath + ".corner1.y" , zone.getMin ( ).getY ( ) );
    config.set ( zonePath + ".corner1.z" , zone.getMin ( ).getZ ( ) );
    config.set ( zonePath + ".corner2.x" , zone.getMax ( ).getX ( ) );
    config.set ( zonePath + ".corner2.y" , zone.getMax ( ).getY ( ) );
    config.set ( zonePath + ".corner2.z" , zone.getMax ( ).getZ ( ) );

    config.set ( zonePath + ".default-action" , zone.getDefaultAction ( ).name ( ) );
    config.set ( zonePath + ".priority" , zone.getPriority ( ) );

    if ( !zone.getMaterialActions ( ).isEmpty ( ) ) {
     zone.getMaterialActions ( ).forEach ( ( material , action ) -> {
      config.set ( zonePath + ".material-actions." + material.name ( ) , action.name ( ) );
     } );
    }
   }
   plugin.saveConfig ( );
   plugin.getSettings ( ).log ( Level.INFO , "Successfully saved " + zones.size ( ) + " zones." );
  };

  if ( async ) {
   plugin.getServer ( ).getScheduler ( ).runTaskAsynchronously ( plugin , saveTask );
  } else {
   saveTask.run ( );
  }
 }

 public void addOrUpdateZone ( @NotNull Zone zone ) {
  zones.put ( zone.getName ( ) , zone );
  saveZones ( true );
 }

 public boolean removeZone ( @NotNull String zoneName ) {
  Zone removed = zones.remove ( zoneName.toLowerCase ( ) );
  if ( removed != null ) {
   saveZones ( true );
   return true;
  }
  return false;
 }

 @Nullable
 public Zone getZone ( @NotNull String zoneName ) {
  return zones.get ( zoneName.toLowerCase ( ) );
 }

 @Nullable
 public Zone getZoneAt ( @NotNull Location location ) {
  for ( Zone zone : zones.values ( ) ) {
   if ( zone.contains ( location ) ) {
    return zone;
   }
  }
  return null;
 }

 @NotNull
 public Collection < Zone > getZonesAt ( @NotNull Location location ) {
  return zones.values ( ).stream ( )
    .filter ( z -> z.contains ( location ) )
    .sorted ( ( a , b ) -> Integer.compare ( b.getPriority ( ) , a.getPriority ( ) ) )
    .toList ( );
 }

 @Nullable
 public Zone getHighestPriorityZoneAt ( @NotNull Location location ) {
  return getZonesAt ( location ).stream ( ).findFirst ( ).orElse ( null );
 }

 @NotNull
 public Collection < Zone > getAllZones ( ) {
  return zones.values ( );
 }
}