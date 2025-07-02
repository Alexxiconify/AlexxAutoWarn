package net.Alexxiconify.alexxAutoWarn;

import com.google.common.base.Stopwatch;
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
 private static AutoWarnAPI api;

 public static AutoWarnAPI getAPI ( ) { return api; }

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
  // Reload custom settings and zones after the base config has been reloaded.
  // These checks are now guaranteed to be non-null due to initialization order in onEnable.
  if (this.settings != null) {
   this.settings.reload(); // Tell your custom Settings class to reload its cached data
  }
  if (this.zoneManager != null) {
   this.zoneManager.loadZones(); // Reload zones after config is reloaded
  }
 }

 /**
  * Sets up the CoreProtect API hook.
  * Checks if CoreProtect plugin is present, enabled, and compatible.
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

  // Check for a compatible CoreProtect version (API Version 9 is minimum for modern features)
  if (api.APIVersion() < 9) {
   getLogger().warning("Unsupported CoreProtect version found (API v" + api.APIVersion() + "). Please update CoreProtect to at least API v9. Logging disabled.");
   this.coreProtectAPI = null;
   return;
  }

  this.coreProtectAPI = api;
  getLogger().info("Successfully hooked into CoreProtect API.");
 }

 // --- Getters ---

 /**
  * Provides access to the plugin's settings manager.
  *
  * @return The Settings instance.
  */
 @NotNull
 public Settings getSettings() {
  return settings;
 }

 /**
  * Provides access to the plugin's zone manager.
  * @return The ZoneManager instance.
  */
 @NotNull
 public ZoneManager getZoneManager() {
  return zoneManager;
 }

 /**
  * Provides access to the CoreProtect API instance.
  * @return The CoreProtectAPI instance, or null if not hooked.
  */
 @Nullable
 public CoreProtectAPI getCoreProtectAPI() {
  return coreProtectAPI;
 }

 /**
  * Provides access to the plugin's command instance.
  *
  * @return The AutoWarnCommand instance.
  */
 @NotNull
 public AutoWarnCommand getAutoWarnCommand ( ) {
  return autoWarnCommand;
 }

 @Override
 public void onEnable() {
  final Stopwatch stopwatch = Stopwatch.createStarted();
  this.getLogger().info("Starting AlexxAutoWarn...");

  // FIX: Ensure Settings and ZoneManager are initialized BEFORE reloadConfig()
  // This ensures 'settings' and 'zoneManager' objects exist when reloadConfig() calls their reload/load methods.
  this.settings = new Settings(this);
  this.zoneManager = new ZoneManager(this);

  // Ensure default config is saved and loaded
  saveDefaultConfig();
  // Reload config. This will now correctly call settings.reload() and zoneManager.loadZones()
  // because 'settings' and 'zoneManager' are already initialized.
  reloadConfig();

  // Asynchronously load zones from config (this call in onEnable becomes redundant if reloadConfig() does it)
  // However, keep it here for explicit asynchronous loading after the initial sync load via reloadConfig()
  // or if zones need to be reloaded after some initial sync setup in onEnable.
  // No, it's better to let reloadConfig() handle the initial load,
  // and only call zoneManager.loadZones() directly if it's a separate async operation later.
  // For initial setup, reloadConfig() handles it. Removed this redundant call.
  // this.zoneManager.loadZones().thenRun(() -> {
  //     this.getLogger().info("All zones loaded successfully.");
  // });

  // Setup CoreProtect API hook
  setupCoreProtect();

  // Initialize and register commands
  this.autoWarnCommand = new AutoWarnCommand(this); // Initialize the command instance

  // Get the command from plugin.yml and set its executor and tab completer.
  // Add explicit null check and logging for command registration.
  PluginCommand command = this.getCommand("autowarn");
  if (command == null) {
   getLogger().severe("Command 'autowarn' not found in plugin.yml! Commands will not work. Ensure 'autowarn' is defined in your plugin.yml under the 'commands' section.");
  } else {
   command.setExecutor(this.autoWarnCommand); // Set the executor to our specific instance
   command.setTabCompleter(this.autoWarnCommand); // Set the tab completer to our specific instance
   getLogger().info("Command 'autowarn' registered successfully.");
  }

  // Register event listeners
  this.getServer ( ).getPluginManager ( ).registerEvents ( new ZoneListener ( this ) , this );

  api = new AutoWarnAPIImpl ( this.zoneManager );

  long time = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS);
  this.getLogger().log(Level.INFO, "AlexxAutoWarn enabled successfully in {0}ms.", time);
 }
}