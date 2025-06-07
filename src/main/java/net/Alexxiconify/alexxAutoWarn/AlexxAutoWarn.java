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
 * and initializes managers, listeners, and command executors.
 */
public class AlexxAutoWarn extends JavaPlugin implements CommandExecutor, TabCompleter {

 private MessageUtil messageUtil;
 private ZoneManager zoneManager;
 // Constants for the wand item
 private static final String WAND_DISPLAY_NAME = ChatColor.GOLD + "" + ChatColor.BOLD + "Zone Selection Wand";
 private static final List<String> WAND_LORE = Arrays.asList(
         ChatColor.GRAY + "Left-Click: " + ChatColor.WHITE + "Set Pos1",
         ChatColor.GRAY + "Right-Click: " + ChatColor.WHITE + "Set Pos2"
 );
 private AutoInformCommandExecutor autoInformCommandExecutor; // Store a reference to the executor
 private CoreProtectAPI coreProtectAPI;
 private NamespacedKey wandKey; // Make this field accessible for other classes if needed

 @Override
 public void onEnable() {
  // --- Plugin Initialization Start ---
  // Log startup message
  getLogger().log(Level.INFO, "AlexxAutoWarn is starting...");

  // Save default config.yml and messages.yml if they don't exist
  saveDefaultConfig();
  saveResource("messages.yml", false);

  // Initialize MessageUtil first as it's a dependency for others
  // Corrected path to MessageUtil as it's in 'utils'
  this.messageUtil = new MessageUtil(this);
  messageUtil.log(Level.INFO, "plugin-startup"); // Use messageUtil for logging

  // Initialize CoreProtectAPI
  setupCoreProtect();

  // Initialize Managers
  this.zoneManager = new ZoneManager(this, messageUtil);
  zoneManager.loadZones(); // Load zones from config
  zoneManager.loadGloballyBannedMaterials(); // Load globally banned materials

  // Initialize Command Executor and register it
  this.autoInformCommandExecutor = new AutoInformCommandExecutor(this, zoneManager, messageUtil);
  PluginCommand command = getCommand("autoinform");
  if (command != null) {
   command.setExecutor(autoInformCommandExecutor);
   command.setTabCompleter(autoInformCommandExecutor);
  } else {
   getLogger().log(Level.SEVERE, "Could not get command 'autoinform'. Is it defined in plugin.yml?");
  }


  // Initialize Event Listener and register it
  getServer().getPluginManager().registerEvents(new AutoInformEventListener(this, zoneManager, messageUtil), this);

  // Initialize NamespacedKey for the wand
  this.wandKey = new NamespacedKey(this, "autoinform_wand");

  messageUtil.log(Level.INFO, "plugin-enabled"); // Plugin enabled message
  // --- Plugin Initialization End ---
 }

 @Override
 public void onDisable() {
  messageUtil.log(Level.INFO, "plugin-shutting-down"); // Plugin shutting down message
  // Save any pending data if necessary
  zoneManager.saveZones(); // Ensure zones are saved on disable
  zoneManager.saveGloballyBannedMaterials(); // Ensure banned materials are saved
  messageUtil.log(Level.INFO, "plugin-disabled"); // Plugin disabled message
 }

 /**
  * Attempts to set up CoreProtect API integration.
  */
 private void setupCoreProtect() {
  Plugin coreProtectPlugin = Bukkit.getPluginManager().getPlugin("CoreProtect");
  if (coreProtectPlugin instanceof CoreProtect) {
   CoreProtectAPI api = ((CoreProtect) coreProtectPlugin).getAPI();
   if (api.isEnabled() && api.APIVersion() >= 9) { // Check API version
    this.coreProtectAPI = api;
    messageUtil.log(Level.INFO, "plugin-coreprotect-found");
   } else {
    messageUtil.log(Level.WARNING, "plugin-coreprotect-api-disabled-or-outdated");
   }
  } else {
   messageUtil.log(Level.WARNING, "plugin-coreprotect-not-found");
  }
 }

 /**
  * Reloads the plugin's configuration and re-initializes managers.
  */
 public void reloadPluginConfig() {
  reloadConfig(); // Reloads the config.yml file
  messageUtil.loadMessages(); // Reloads messages.yml
  zoneManager.loadZones(); // Reloads zones from config
  zoneManager.loadGloballyBannedMaterials(); // Reloads globally banned materials
  messageUtil.log(Level.INFO, "plugin-config-reloaded");
 }

 /**
  * Gets the CoreProtectAPI instance.
  *
  * @return The CoreProtectAPI instance, or null if not available.
  */
 @Nullable
 public CoreProtectAPI getCoreProtectAPI() {
  return coreProtectAPI;
 }

 /**
  * Gets the AutoInformCommandExecutor instance.
  *
  * @return The AutoInformCommandExecutor instance.
  */
 public AutoInformCommandExecutor getCommandExecutor() {
  return autoInformCommandExecutor;
 }

 /**
  * Provides the MessageUtil instance.
  *
  * @return The MessageUtil instance.
  */
 public MessageUtil getMessageUtil() {
  return messageUtil;
 }

 /**
  * Provides the ZoneManager instance.
  *
  * @return The ZoneManager instance.
  */
 public ZoneManager getZoneManager() {
  return zoneManager;
 }

 /**
  * Provides the NamespacedKey for the wand.
  *
  * @return The NamespacedKey for the wand.
  */
 public NamespacedKey getWandKey() {
  return wandKey;
 }

 /**
  * Gives the selection wand to the player.
  *
  * @param player The player to give the wand to.
  */
 public void giveSelectionWand(Player player) {
  ItemStack wand = new ItemStack(Material.BLAZE_ROD);
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