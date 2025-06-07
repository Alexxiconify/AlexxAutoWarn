package net.Alexxiconify.alexxAutoWarn;

import net.Alexxiconify.alexxAutoWarn.commands.AutoInformCommandExecutor;
import net.Alexxiconify.alexxAutoWarn.listeners.AutoInformEventListener;
import net.Alexxiconify.alexxAutoWarn.managers.ZoneManager;
import net.Alexxiconify.alexxAutoWarn.utils.MessageUtil;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Main class for the AlexxAutoWarn plugin.
 * Handles plugin lifecycle, configuration loading, CoreProtect integration,
 * command and event registration, and provides utility methods.
 */
public class AlexxAutoWarn extends JavaPlugin implements CommandExecutor, TabCompleter {

 // Configuration constants for the wand
 private static final Material WAND_MATERIAL = Material.BLAZE_ROD; // Default material
 private MessageUtil messageUtil;
 private ZoneManager zoneManager;
 private static final String WAND_DISPLAY_NAME = ChatColor.RESET + "" + ChatColor.GOLD + "AutoInform Wand"; // Display name
 private static final List<String> WAND_LORE = Arrays.asList(ChatColor.GRAY + "Left-Click to set Pos1", ChatColor.GRAY + "Right-Click to set Pos2"); // Lore
 private CoreProtectAPI coreProtectAPI;
 private FileConfiguration config;
 private AutoInformCommandExecutor commandExecutor;
 private AutoInformEventListener eventListener;
 // NamespacedKey for identifying the custom wand item
 private NamespacedKey wandKey;

 @Override
 public void onEnable() {
  // Plugin startup logic
  getLogger().log(Level.INFO, "Plugin starting...");

  // Load configuration
  saveDefaultConfig(); // Creates config.yml if it doesn't exist
  this.config = getConfig();

  // Initialize utility and manager classes
  this.messageUtil = new MessageUtil(this);
  this.zoneManager = new ZoneManager(this, messageUtil);

  // Load messages for messageUtil after config is loaded
  messageUtil.loadMessages();

  // Initialize CoreProtectAPI
  setupCoreProtect();

  // Initialize NamespacedKey for the wand
  this.wandKey = new NamespacedKey(this, "autoinform_wand");

  // Initialize command executor and event listener
  this.commandExecutor = new AutoInformCommandExecutor(this, zoneManager, messageUtil);
  this.eventListener = new AutoInformEventListener(this, zoneManager, messageUtil);

  // Register commands and tab completer
  PluginCommand command = getCommand("autoinform");
  if (command != null) {
   command.setExecutor(commandExecutor);
   command.setTabCompleter(commandExecutor); // AutoInformCommandExecutor also implements TabCompleter
   getLogger().log(Level.INFO, "Command 'autoinform' registered.");
  } else {
   getLogger().log(Level.SEVERE, "Failed to register command 'autoinform'. Is it defined in plugin.yml?");
  }

  // Register event listener
  Bukkit.getPluginManager().registerEvents(eventListener, this);
  getLogger().log(Level.INFO, "Event listener registered.");

  // Load zones after all components are initialized
  zoneManager.loadZones();

  getLogger().log(Level.INFO, "Plugin enabled!");
 }

 @Override
 public void onDisable() {
  // Plugin shutdown logic
  getLogger().log(Level.INFO, "Plugin shutting down...");

  // Save zones to config
  zoneManager.saveZones();

  getLogger().log(Level.INFO, "Plugin disabled!");
 }

 /**
  * Reloads the plugin's configuration and associated managers.
  */
 public void reloadPluginConfig() {
  reloadConfig(); // Reloads the config.yml from disk
  this.config = getConfig(); // Update the config object
  messageUtil.loadMessages(); // Reload messages in MessageUtil
  zoneManager.loadZones(); // Reload zones from the updated config
  getLogger().log(Level.INFO, "Configuration and zones reloaded.");
 }

 /**
  * Sets up CoreProtect API if the plugin is available.
  */
 private void setupCoreProtect() {
  Plugin coreProtectPlugin = Bukkit.getPluginManager().getPlugin("CoreProtect");
  if (coreProtectPlugin instanceof CoreProtect) {
   coreProtectAPI = ((CoreProtect) coreProtectPlugin).getAPI();
   if (coreProtectAPI.isEnabled()) {
    getLogger().log(Level.INFO, "CoreProtect found! Logging enabled.");
   } else {
    getLogger().log(Level.WARNING, "CoreProtect found, but API is not enabled. Logging features will be disabled.");
    coreProtectAPI = null;
   }
  } else {
   getLogger().log(Level.WARNING, "CoreProtect not found! Logging features will be disabled.");
   coreProtectAPI = null;
  }
 }

 /**
  * Retrieves the CoreProtectAPI instance.
  *
  * @return The CoreProtectAPI instance, or null if not available.
  */
 @Nullable
 public CoreProtectAPI getCoreProtectAPI() {
  return coreProtectAPI;
 }

 /**
  * Retrieves the MessageUtil instance.
  *
  * @return The MessageUtil instance.
  */
 public MessageUtil getMessageUtil() {
  return messageUtil;
 }

 /**
  * Retrieves the ZoneManager instance.
  *
  * @return The ZoneManager instance.
  */
 public ZoneManager getZoneManager() {
  return zoneManager;
 }

 /**
  * Retrieves the AutoInformCommandExecutor instance.
  *
  * @return The AutoInformCommandExecutor instance.
  */
 public AutoInformCommandExecutor getCommandExecutor() {
  return commandExecutor;
 }

 /**
  * Checks if chest access monitoring is enabled in the config.
  *
  * @return true if monitor-chest-access is true, false otherwise.
  */
 public boolean isMonitorChestAccess() {
  return getConfig().getBoolean("monitor-chest-access", false);
 }

 /**
  * Gives the player the custom AutoInform wand item.
  *
  * @param player The player to give the wand to.
  */
 public void giveSelectionWand(Player player) {
  ItemStack wand = new ItemStack(WAND_MATERIAL);
  ItemMeta meta = wand.getItemMeta();
  if (meta != null) {
   meta.setDisplayName(WAND_DISPLAY_NAME);
   meta.setLore(WAND_LORE);
   meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
   wand.setItemMeta(meta);
  }
  player.getInventory().addItem(wand);
 }

 /**
  * Formats a set of Materials into a comma-separated string.
  * Made public for access from AutoInformCommandExecutor.
  */
 public String formatMaterialList(Set<Material> materials) {
  if (materials.isEmpty()) return "None";
  return materials.stream().map(Enum::name).collect(Collectors.joining(", "));
 }

 // --- Dummy/Placeholder implementations for Listener and CommandExecutor for this main class ---
 // The actual logic is in AutoInformEventListener and AutoInformCommandExecutor

 @Override
 public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
  // This is handled by AutoInformCommandExecutor, but JavaPlugin requires this method if it implements CommandExecutor.
  // It should delegate or simply return true as the actual executor is set.
  return true;
 }

 @Override
 public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
  // This is handled by AutoInformCommandExecutor, but JavaPlugin requires this method if it implements TabCompleter.
  // It should delegate or simply return an empty list as the actual tab completer is set.
  return Collections.emptyList();
 }
}