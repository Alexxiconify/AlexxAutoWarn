package net.alexxiconify.alexxAutoWarn; // Reverted to user's specified casing: Uppercase 'A' in Alexxiconify

import com.google.common.base.Stopwatch;
import net.alexxiconify.alexxAutoWarn.commands.AutoWarnCommand;
import net.alexxiconify.alexxAutoWarn.listeners.ZoneListener;
import net.alexxiconify.alexxAutoWarn.managers.ZoneManager;
import net.alexxiconify.alexxAutoWarn.utils.Settings;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Main class for the AlexxAutoWarn plugin.
 * Handles plugin lifecycle, configuration loading, CoreProtect integration,
 * and registration of commands and event listeners.
 */
public final class AlexxAutoWarn extends JavaPlugin {

 private Settings settings;
 private ZoneManager zoneManager;
 private CoreProtectAPI coreProtectAPI;
 private AutoWarnCommand autoWarnCommand; // Added field to hold the command instance

 @Override
 public void onEnable() {
  final Stopwatch stopwatch = Stopwatch.createStarted();
  this.getLogger().info("Starting AlexxAutoWarn...");

  // FIX: Ensure default config is saved and loaded
  // This will create the plugin's data folder (plugins/AlexxAutoWarn)
  // and copy the bundled config.yml if it doesn't exist.
  saveDefaultConfig();
  // Reload config to ensure the Settings class reads the correct, potentially new, config.
  reloadConfig();


  // Initialize settings and managers
  // Pass the plugin instance to Settings, which will then use getConfig() internally.
  this.settings = new Settings(this);
  this.zoneManager = new ZoneManager(this);

  // Asynchronously load zones from config
  this.zoneManager.loadZones().thenRun(() -> {
   this.getLogger().info("All zones loaded successfully.");
  });

  // Setup CoreProtect API
  setupCoreProtect();

  // Initialize and register commands
  this.autoWarnCommand = new AutoWarnCommand(this); // Initialize the command instance

  // FIX: Add explicit null check and logging for command registration
  PluginCommand command = this.getCommand("autowarn"); // Use PluginCommand type
  if (command == null) {
   getLogger().severe("Command 'autowarn' not found in plugin.yml! Commands will not work.");
  } else {
   command.setExecutor(this.autoWarnCommand); // Set the executor to our specific instance
   command.setTabCompleter(this.autoWarnCommand); // Set the tab completer to our specific instance
   getLogger().info("Command 'autowarn' registered successfully.");
  }

  // Register listeners
  this.getServer().getPluginManager().registerEvents(new ZoneListener(this, this.autoWarnCommand), this);


  long time = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS);
  this.getLogger().log(Level.INFO, "AlexxAutoWarn enabled successfully in {0}ms.", time);
 }

 @Override
 public void onDisable() {
  this.getLogger().info("Disabling AlexxAutoWarn...");
  // Synchronously save zones on disable to ensure data is written before shutdown
  if (this.zoneManager != null) {
   this.zoneManager.saveZones(false); // Perform a blocking save on disable
  }
  this.getLogger().info("AlexxAutoWarn has been disabled.");
 }

 /**
  * Reloads the plugin's configuration and all associated components.
  * This method overrides JavaPlugin's reloadConfig() and should be used
  * as the primary entry point for plugin reloads.
  */
 @Override
 public void reloadConfig() {
  super.reloadConfig(); // Call the parent method to reload the underlying configuration file
  if (this.settings != null) {
   this.settings.reload(); // Tell your custom Settings class to reload its cached data
  }
  if (this.zoneManager != null) {
   this.zoneManager.loadZones(); // Reload zones after config is reloaded
  }
 }

 /**
  * Sets up the CoreProtect API hook.
  */
 private void setupCoreProtect() {
  final Plugin coreProtectPlugin = getServer().getPluginManager().getPlugin("CoreProtect");
  if (!(coreProtectPlugin instanceof CoreProtect)) {
   getLogger().warning("CoreProtect not found! Logging features will be disabled.");
   this.coreProtectAPI = null;
   return;
  }

  final CoreProtectAPI api = ((CoreProtect) coreProtectPlugin).getAPI();
  if (!api.isEnabled()) {
   getLogger().warning("CoreProtect found, but the API is not enabled. Logging disabled.");
   this.coreProtectAPI = null;
   return;
  }

  // Check for a compatible CoreProtect version
  if (api.APIVersion() < 9) {
   getLogger().warning("Unsupported CoreProtect version found. Please update CoreProtect. Logging disabled.");
   this.coreProtectAPI = null;
   return;
  }

  this.coreProtectAPI = api;
  getLogger().info("Successfully hooked into CoreProtect API.");
 }

 // --- Getters ---

 @NotNull
 public Settings getSettings() {
  return settings;
 }

 @NotNull
 public ZoneManager getZoneManager() {
  return zoneManager;
 }

 @Nullable
 public CoreProtectAPI getCoreProtectAPI() {
  return coreProtectAPI;
 }
}