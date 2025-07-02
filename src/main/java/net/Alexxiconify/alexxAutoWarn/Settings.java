package net.Alexxiconify.alexxAutoWarn;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class Settings {
 private final AlexxAutoWarn plugin;
 private final MiniMessage miniMessage;

 private boolean monitorChestAccess;
 private boolean debugLogAllowedActions;
 private Component pluginPrefix;
 private Set < Material > globallyBannedMaterials;

 public Settings ( AlexxAutoWarn plugin ) {
  this.plugin = plugin;
  this.miniMessage = MiniMessage.miniMessage ( );
 }

 public void reload ( ) {
  FileConfiguration config = plugin.getConfig ( );

  this.monitorChestAccess = config.getBoolean ( "settings.monitor-chest-access" , false );
  this.debugLogAllowedActions = config.getBoolean ( "settings.debug-log-allowed-actions" , false );
  this.pluginPrefix = miniMessage.deserialize ( config.getString (
    "messages.plugin-prefix" , "<gray>[<gold>AutoWarn</gold>]</gray> "
  ) );

  this.globallyBannedMaterials = EnumSet.noneOf ( Material.class );
  List < String > bannedMaterialsList = config.getStringList ( "settings.globally-banned-materials" );
  for ( String materialName : bannedMaterialsList ) {
   try {
    Material material = Material.valueOf ( materialName.toUpperCase ( ) );
    this.globallyBannedMaterials.add ( material );
   } catch ( IllegalArgumentException e ) {
    plugin.getLogger ( ).warning ( "Invalid globally banned material '" + materialName +
                                     "' found in config.yml. Skipping." );
   }
  }
  plugin.getLogger ( ).log (
    Level.INFO , "Reloaded {0} globally banned materials." ,
    globallyBannedMaterials.size ( )
  );
 }

 public Component getMessage ( @NotNull String key , TagResolver... resolvers ) {
  String rawMessage = plugin.getConfig ( ).getString (
    "messages." + key , "<red>Message not found: " + key + "</red>"
  );
  Component message = miniMessage.deserialize ( rawMessage , resolvers );
  return pluginPrefix.append ( message );
 }

 public void log ( Level level , String message ) {
  plugin.getLogger ( ).log ( level , MiniMessage.miniMessage ( ).stripTags ( message ) );
 }

 public boolean isMonitorChestAccess ( ) {
  return monitorChestAccess;
 }

 public boolean isDebugLogAllowedActions ( ) {
  return debugLogAllowedActions;
 }

 @NotNull
 public Set < Material > getGloballyBannedMaterials ( ) {
  return Collections.unmodifiableSet ( globallyBannedMaterials );
 }

 public void setGloballyBannedMaterials ( @NotNull Set < Material > materials ) {
  this.globallyBannedMaterials = EnumSet.copyOf ( materials );
  saveGloballyBannedMaterials ( );
 }

 public boolean addGloballyBannedMaterial ( @NotNull Material material ) {
  if ( globallyBannedMaterials.add ( material ) ) {
   saveGloballyBannedMaterials ( );
   return true;
  }
  return false;
 }

 public boolean removeGloballyBannedMaterial ( @NotNull Material material ) {
  if ( globallyBannedMaterials.remove ( material ) ) {
   saveGloballyBannedMaterials ( );
   return true;
  }
  return false;
 }

 private void saveGloballyBannedMaterials ( ) {
  List < String > bannedNames = globallyBannedMaterials.stream ( )
    .map ( Material :: name )
    .collect ( Collectors.toList ( ) );
  plugin.getConfig ( ).set ( "settings.globally-banned-materials" , bannedNames );
  plugin.saveConfig ( );
 }
}