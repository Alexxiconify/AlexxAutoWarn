package net.Alexxiconify.alexxAutoWarn;

import net.Alexxiconify.alexxAutoWarn.commands.AutoInformCommandExecutor;
import net.Alexxiconify.alexxAutoWarn.listeners.AutoInformEventListener;
import net.Alexxiconify.alexxAutoWarn.managers.ZoneManager;
import net.Alexxiconify.alexxAutoWarn.utils.MessageUtil;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.logging.Level;

/**
 * The main class for the AlexxAutoWarn plugin.
 * This plugin allows server administrators to define protected zones where certain material interactions
 * can be denied, alerted to staff, or explicitly allowed. It integrates with CoreProtect for logging.
 * <p>
 * This class handles plugin startup/shutdown, loads configurations, initializes managers and listeners,
 * and provides access to the CoreProtect API.
 */
public final class AlexxAutoWarn extends JavaPlugin {

 // Instance of the plugin, used for static access (e.g., getting configuration or CoreProtect API)
 private static AlexxAutoWarn plugin;

 // Manager for handling all AutoInform zones
 private ZoneManager zoneManager;

 // Utility for sending messages, initialized with the plugin's message configuration
 private MessageUtil messageUtil;

 // CoreProtect API instance for logging actions. Can be null if CoreProtect is not found.
 private CoreProtectAPI coreProtectAPI;

 /**
  * Provides static access to the plugin instance.
  *
  * @return The AlexxAutoWarn plugin instance.
  */
 public static AlexxAutoWarn getPlugin() {
  return plugin;
 }

 /**
  * Called when the plugin is enabled.
  * Initializes the plugin instance, loads configurations, sets up managers,
  * registers commands and event listeners, and attempts to hook into CoreProtect.
  */
 @Override
 public void onEnable() {
  plugin = this; // Set the static plugin instance

  // Load or create the default configuration file
  saveDefaultConfig();

  // Initialize MessageUtil to handle all plugin messages
  this.messageUtil = new MessageUtil(this);
  // Send a startup message to the console
  messageUtil.log(Level.INFO, "plugin-startup");

  // Attempt to load zones from the config.yml
  this.zoneManager = new ZoneManager(this);
  zoneManager.loadZonesFromConfig();

  // Register commands and their executors
  // The main command "autoinform" and its alias "ainform" are handled by AutoInformCommandExecutor
  Objects.requireNonNull(getCommand("autoinform")).setExecutor(new AutoInformCommandExecutor(this));
  Objects.requireNonNull(getCommand("autoinform")).setTabCompleter(new AutoInformCommandExecutor(this));

  // Register event listeners to monitor player actions
  // Ensure AutoInformEventListener constructor expects 'this' (AlexxAutoWarn plugin instance)
  getServer().getPluginManager().registerEvents(new AutoInformEventListener(this), this);

  // Attempt to hook into CoreProtect
  if (!setupCoreProtect()) {
   messageUtil.log(Level.WARNING, "plugin-coreprotect-not-found");
  } else {
   messageUtil.log(Level.INFO, "plugin-coreprotect-found");
  }

  // Inform the console that the plugin has been enabled
  messageUtil.log(Level.INFO, "plugin-enabled");
 }

 /**
  * Called when the plugin is disabled.
  * Handles cleanup operations.
  */
 @Override
 public void onDisable() {
  // Send a shutdown message to the console
  messageUtil.log(Level.INFO, "plugin-shutting-down");
  // Clear cached zones to free up memory
  if (zoneManager != null) {
   zoneManager.clearZones();
  }
  // Inform the console that the plugin has been disabled
  messageUtil.log(Level.INFO, "plugin-disabled");
 }

 /**
  * Attempts to get the CoreProtect API instance.
  *
  * @return true if CoreProtect was found and the API was successfully retrieved, false otherwise.
  */
 private boolean setupCoreProtect() {
  Plugin coreProtectPlugin = getServer().getPluginManager().getPlugin("CoreProtect");
  if (coreProtectPlugin instanceof CoreProtect) {
   coreProtectAPI = ((CoreProtect) coreProtectPlugin).getAPI();
   return coreProtectAPI != null; // Return true only if API is not null
  }
  return false;
 }

 /**
  * Gets the ZoneManager instance.
  *
  * @return The ZoneManager.
  */
 public ZoneManager getZoneManager() {
  return zoneManager;
 }

 /**
  * Gets the MessageUtil instance.
  *
  * @return The MessageUtil.
  */
 public MessageUtil getMessageUtil() {
  return messageUtil;
 }

 /**
  * Gets the CoreProtectAPI instance.
  * Changed access modifier to public for other classes to access.
  *
  * @return The CoreProtectAPI instance, or null if CoreProtect is not hooked.
  */
 @Nullable
 public CoreProtectAPI getCoreProtectAPI() {
  return coreProtectAPI;
 }
}