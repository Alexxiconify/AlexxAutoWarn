package net.alexxiconify.alexxAutoWarn.utils;

import net.alexxiconify.alexxAutoWarn.AlexxAutoWarn;
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
 private Set<Material> globallyBannedMaterials;

 public Settings(AlexxAutoWarn plugin) {
  this.plugin = plugin;
  this.miniMessage = MiniMessage.miniMessage();
  this.plugin.saveDefaultConfig();
  reload();
 }

 /**
  * Reloads all settings from the configuration file.
  */
 public void reload() {
  plugin.reloadConfig();
  FileConfiguration config = plugin.getConfig();

  // Load settings
  this.monitorChestAccess = config.getBoolean("settings.monitor-chest-access", false);
  this.debugLogAllowedActions = config.getBoolean("settings.debug-log-allowed-actions", false);
  this.pluginPrefix = miniMessage.deserialize(config.getString("messages.plugin-prefix", "<gray>[<gold>AutoWarn</gold>]</gray> "));

  // Load globally banned materials
  this.globallyBannedMaterials = EnumSet.noneOf(Material.class);
  List<String> bannedMaterialsList = config.getStringList("settings.globally-banned-materials");
  for (String materialName : bannedMaterialsList) {
   try {
    this.globallyBannedMaterials.add(Material.valueOf(materialName.toUpperCase()));
   } catch (IllegalArgumentException e) {
    plugin.getLogger().warning("Invalid globally banned material in config: " + materialName);
   }
  }
 }

 /**
  * Retrieves a message from the configuration and formats it with placeholders.
  *
  * @param key       The message key in config.yml (e.g., "error.no-permission").
  * @param resolvers Placeholders to replace in the message (e.g., Placeholder.unparsed("player", player.getName())).
  * @return A formatted Component, ready to be sent to a player.
  */
 public Component getMessage(@NotNull String key, TagResolver... resolvers) {
  String rawMessage = plugin.getConfig().getString("messages." + key, "<red>Message not found: " + key + "</red>");
  Component message = miniMessage.deserialize(rawMessage, resolvers);
  return pluginPrefix.append(message);
 }

 /**
  * Logs a message to the console.
  *
  * @param level   The log level.
  * @param message The message to log.
  */
 public void log(Level level, String message) {
  plugin.getLogger().log(level, MiniMessage.miniMessage().stripTags(message));
 }


 // --- Getters for settings ---

 public boolean isMonitorChestAccess() {
  return monitorChestAccess;
 }

 public boolean isDebugLogAllowedActions() {
  return debugLogAllowedActions;
 }

 @NotNull
 public Set<Material> getGloballyBannedMaterials() {
  return Collections.unmodifiableSet(globallyBannedMaterials);
 }

 public boolean setGloballyBannedMaterials(Material materials) {
  this.globallyBannedMaterials = Collections.singleton(materials);
  boolean materialNames = materials.isItem();
  plugin.getConfig().set("settings.globally-banned-materials", materialNames);
  plugin.saveConfig();
  return false;
 }

 public boolean removeGloballyBannedMaterial(Material material) {
  return false;
 }

 public boolean addGloballyBannedMaterial(Material material) {
  return false;
 }
}