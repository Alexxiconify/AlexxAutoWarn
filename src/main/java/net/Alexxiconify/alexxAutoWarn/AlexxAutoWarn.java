package net.alexxiconify.autowarn;

import com.google.common.base.Stopwatch;
import net.alexxiconify.autowarn.commands.AutoWarnCommand;
import net.alexxiconify.autowarn.listeners.ZoneListener;
import net.alexxiconify.autowarn.managers.ZoneManager;
import net.alexxiconify.autowarn.util.Settings;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Main class for the AutoWarn plugin.
 * Handles plugin lifecycle, configuration loading, CoreProtect integration,
 * and registration of commands and event listeners.
 */
public final .

class AutoWarnPlugin extends JavaPlugin {

 private Settings settings;
 private ZoneManager zoneManager;
 private CoreProtectAPI coreProtectAPI;

 @Override
 public void onEnable() {
  final Stopwatch stopwatch = Stopwatch.createStarted();
  this.getLogger().info("Starting AutoWarn...");

  // Initialize settings and managers
  this.settings = new Settings(this);
  this.zoneManager = new ZoneManager(this);

  // Asynchronously load zones from config
  this.zoneManager.loadZones().thenRun(() -> {
   this.getLogger().info("All zones loaded successfully.");
  });

  // Setup CoreProtect API
  setupCoreProtect();

  // Register commands and listeners
  this.getServer().getPluginManager().registerEvents(new ZoneListener(this), this);
  AutoWarnCommand commandManager = new AutoWarnCommand(this);

  // Using Paper's modern command registration
  var command = Objects.requireNonNull(this.getCommand("autowarn"));
  command.setExecutor(commandManager);
  command.setTabCompleter(commandManager);


  long time = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS);
  this.getLogger().log(Level.INFO, "AutoWarn enabled successfully in {0}ms.", time);
 }

 @Override
 public void onDisable() {
  this.getLogger().info("Disabling AutoWarn...");
  // Synchronously save zones on disable to ensure data is written before shutdown
  if (this.zoneManager != null) {
   this.zoneManager.saveZones(false); // Perform a blocking save on disable
  }
  this.getLogger().info("AutoWarn has been disabled.");
 }

 /**
  * Reloads the plugin's configuration and all associated components.
  */
 public void reload() {
  this.settings.reload();
  this.zoneManager.loadZones();
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