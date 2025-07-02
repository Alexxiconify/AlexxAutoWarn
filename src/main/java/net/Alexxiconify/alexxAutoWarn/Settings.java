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

/**
 * Manages loading and providing access to all plugin settings and messages from config.yml.
 * Uses the Adventure API for modern, component-based messages.
 */
public class Settings {

 private final AlexxAutoWarn plugin;
 private final MiniMessage miniMessage;

 // --- Settings ---
 private boolean monitorChestAccess;
 private boolean debugLogAllowedActions;
 private Component pluginPrefix;
 private Set < Material > globallyBannedMaterials;

 public Settings ( AlexxAutoWarn plugin ) {
  this.plugin = plugin;
  this.miniMessage = MiniMessage.miniMessage ( );
  // saveDefaultConfig() is handled in AlexxAutoWarn's onEnable,
  // and reload() is called there too.
  // No need to call saveDefaultConfig here again.
  // reload(); // This is now called from AlexxAutoWarn.onEnable
 }

 /**
  * Reloads all settings from the configuration file.
  * This method is typically called after plugin.reloadConfig().
  */
 public void reload ( ) {
  // No need to call plugin.reloadConfig() here, it's called by AlexxAutoWarn.reloadConfig()
  FileConfiguration config = plugin.getConfig ( );

  // Load general settings
  this.monitorChestAccess = config.getBoolean ( "settings.monitor-chest-access" , false );
  this.debugLogAllowedActions = config.getBoolean ( "settings.debug-log-allowed-actions" , false );
  this.pluginPrefix = miniMessage.deserialize ( config.getString (
    "messages.plugin-prefix" , "<gray>[<gold>AutoWarn" +
      "</gold>]</gray> "
  ) );

  // Load globally banned materials
  // Ensure a fresh set is created to avoid old materials persisting after reload
  this.globallyBannedMaterials = EnumSet.noneOf ( Material.class );
  List < String > bannedMaterialsList = config.getStringList ( "settings.globally-banned-materials" );
  for ( String materialName : bannedMaterialsList ) {
   try {
    // Ensure correct case conversion before attempting to get Material enum
    Material material = Material.valueOf ( materialName.toUpperCase ( ) );
    this.globallyBannedMaterials.add ( material );
   } catch ( IllegalArgumentException e ) {
    // Log a warning for each invalid material encountered
    plugin.getLogger ( ).warning ( "Invalid globally banned material '" + materialName + "' found in config.yml. " +
                                     "Skipping." );
   }
  }
  plugin.getLogger ( ).log (
    Level.INFO , "Reloaded {0} globally banned materials." ,
    globallyBannedMaterials.size ( )
  );
 }

 /**
  * Retrieves a message from the configuration and formats it with placeholders.
  *
  * @param key       The message key in config.yml (e.g., "error.no-permission").
  * @param resolvers Placeholders to replace in the message (e.g., Placeholder.unparsed("player", player.getName())).
  * @return A formatted Component, ready to be sent to a player.
  */
 public Component getMessage ( @NotNull String key , TagResolver... resolvers ) {
  // Fallback message ensures something is always returned, even if key is missing
  String rawMessage = plugin.getConfig ( ).getString (
    "messages." + key , "<red>Message not found: " + key + "</red" +
      ">"
  );
  Component message = miniMessage.deserialize ( rawMessage , resolvers );
  return pluginPrefix.append ( message );
 }


 /**
  * Logs a message to the console using the plugin's logger.
  * Strips MiniMessage tags for plain text console output.
   *
   * @param level   The log level.
   * @param message The message to log.
   */
 public void log ( Level level , String message ) {
  plugin.getLogger ( ).log ( level , MiniMessage.miniMessage ( ).stripTags ( message ) );
 }


 // --- Getters and Setters for settings ---

 public boolean isMonitorChestAccess ( ) {
  return monitorChestAccess;
 }

 public boolean isDebugLogAllowedActions ( ) {
  return debugLogAllowedActions;
 }

 @NotNull
 public Set < Material > getGloballyBannedMaterials ( ) {
  // Return an unmodifiable set to prevent external modification
  return Collections.unmodifiableSet ( globallyBannedMaterials );
 }

 /**
  * Updates the entire set of globally banned materials and saves the config.
  * This method is generally used for internal setting during reload, or
  * when the set is explicitly built elsewhere.
  *
  * @param materials The new set of materials.
  */
 public void setGloballyBannedMaterials ( @NotNull Set < Material > materials ) {
  this.globallyBannedMaterials = EnumSet.copyOf ( materials ); // Create a mutable copy
  saveGloballyBannedMaterials ( );
 }

 /**
  * Adds a material to the globally banned list and saves the config.
  *
  * @param material The material to add.
  * @return true if the material was added, false if it was already present.
  */
 public boolean addGloballyBannedMaterial ( @NotNull Material material ) {
  if ( globallyBannedMaterials.add ( material ) ) { // Add returns true if the set changed (material was new)
   saveGloballyBannedMaterials ( );
   return true;
  }
  return false; // Material was already in the set
 }

 /**
  * Removes a material from the globally banned list and saves the config.
  *
  * @param material The material to remove.
  * @return true if the material was removed, false if it was not present.
  */
 public boolean removeGloballyBannedMaterial ( @NotNull Material material ) {
  if ( globallyBannedMaterials.remove ( material ) ) { // Remove returns true if the set changed (material was removed)
   saveGloballyBannedMaterials ( );
   return true;
  }
  return false; // Material was not in the set
 }

 /**
  * Helper method to save the current state of globallyBannedMaterials to config.
  * Converts the EnumSet of Materials to a List of String names for YAML storage.
  */
 private void saveGloballyBannedMaterials ( ) {
  // Convert the EnumSet of Materials to a List of String names
  List < String > bannedNames = globallyBannedMaterials.stream ( )
    .map ( Material :: name )
    .collect ( Collectors.toList ( ) );
  plugin.getConfig ( ).set ( "settings.globally-banned-materials" , bannedNames );
  plugin.saveConfig ( ); // Persist changes to disk
 }
}