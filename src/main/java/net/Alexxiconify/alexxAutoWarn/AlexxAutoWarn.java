package net.Alexxiconify.alexxAutoWarn;

import net.Alexxiconify.alexxAutoWarn.commands.AutoInformCommandExecutor;
import net.Alexxiconify.alexxAutoWarn.listeners.AutoInformEventListener;
import net.Alexxiconify.alexxAutoWarn.managers.ZoneManager;
import net.Alexxiconify.alexxAutoWarn.util.MessageUtil;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;


/**
 * The main class for the AlexxAutoWarn plugin.
 * This plugin allows server administrators to define protected zones where certain material interactions
 * can be denied, alerted to staff, or explicitly allowed. It integrates with CoreProtect for logging.
 *
 * This class handles plugin startup/shutdown, loads configurations, initializes managers and listeners,
 * and provides access to the CoreProtect API. It runs on a PaperMC server, leveraging the Bukkit API
 * that Paper implements and enhances.
 */
@SuppressWarnings("ALL")
public final class AlexxAutoWarn extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

 // --- Plugin Constants ---
 private static final String COMMAND_NAME = "autoinform";
 private static final String WAND_KEY_STRING = "autoinform_wand";
 private static final String WAND_DISPLAY_NAME = ChatColor.GOLD + "" + ChatColor.BOLD + "AutoInform Zone Selector Wand";
 private static final List<String> WAND_LORE = Arrays.asList(
         ChatColor.GRAY + "Left-click: Set Position 1",
         ChatColor.GRAY + "Right-click: Set Position 2"
 );
 // Static plugin instance (initialized in onEnable)
 private static AlexxAutoWarn plugin;

 // --- Instance Variables ---
 private CoreProtectAPI coreProtectAPI;
 private ZoneManager zoneManager;
 // --- Global Settings ---
 private boolean monitorChestAccess;
 private NamespacedKey wandKey;
 private MessageUtil messageUtil;

 /**
  * Provides static access to the plugin instance.
  */
 public static AlexxAutoWarn getPlugin() {
  return plugin;
 }

 @Override
 public void onEnable() {
  // Set the static plugin instance first
  plugin = this;

  // Load or create the default configuration file
  saveDefaultConfig(); // This saves config.yml if not present
  // Ensure messages.yml is also saved from resources if not present
  saveResource("messages.yml", false);


  // Initialize MessageUtil. It loads messages from config.
  this.messageUtil = new MessageUtil(this);
  messageUtil.log(Level.INFO, "plugin-startup");

  // Initialize managers
  this.zoneManager = new ZoneManager(this);
  zoneManager.loadZonesFromConfig(); // Attempt to load zones and banned materials from config.yml

  // Load global settings
  this.monitorChestAccess = getConfig().getBoolean("monitor-chest-access", false);
  if (this.monitorChestAccess) {
   messageUtil.log(Level.INFO, "plugin-monitor-chest-access-enabled");
  } else {
   messageUtil.log(Level.INFO, "plugin-monitor-chest-access-disabled");
  }

  // Initialize wand key (used in event listener and command executor)
  this.wandKey = new NamespacedKey(this, WAND_KEY_STRING);

  // Attempt to hook into CoreProtect
  if (!setupCoreProtect()) {
   messageUtil.log(Level.WARNING, "plugin-coreprotect-not-found");
  } else {
   messageUtil.log(Level.INFO, "plugin-coreprotect-found");
  }

  // Register commands and their executors
  AutoInformCommandExecutor commandExecutor = new AutoInformCommandExecutor(this);
  Objects.requireNonNull(getCommand(COMMAND_NAME)).setExecutor(commandExecutor);
  Objects.requireNonNull(getCommand(COMMAND_NAME)).setTabCompleter(commandExecutor);

  // Register event listeners to monitor player actions
  getServer().getPluginManager().registerEvents(new AutoInformEventListener(this), this);

  messageUtil.log(Level.INFO, "plugin-enabled");
  if (zoneManager.getDefinedZones().isEmpty()) {
   messageUtil.log(Level.WARNING, "plugin-no-zones-defined", "{command}", COMMAND_NAME);
  } else {
   messageUtil.log(Level.INFO, "plugin-zones-loaded", "{count}", String.valueOf(zoneManager.getDefinedZones().size()));
  }
  messageUtil.log(Level.INFO, "plugin-current-banned-materials", "{materials}", formatMaterialList(zoneManager.getGloballyBannedMaterials()));
 }

 /**
  * Attempts to get the CoreProtect API instance.
  */
 private boolean setupCoreProtect() {
  Plugin coreProtectPlugin = Bukkit.getPluginManager().getPlugin("CoreProtect");
  if (coreProtectPlugin instanceof CoreProtect) {
   coreProtectAPI = ((CoreProtect) coreProtectPlugin).getAPI();
   return coreProtectAPI != null;
  }
  return false;
 }

 @Override
 public void onDisable() {
  messageUtil.log(Level.INFO, "plugin-shutting-down");
  if (zoneManager != null) {
   zoneManager.clearZones();
  }
  messageUtil.log(Level.INFO, "plugin-disabled");
 }

 /** Gets the ZoneManager instance. */
 public ZoneManager getZoneManager() {
  return zoneManager;
 }

 /** Gets the MessageUtil instance. */
 public MessageUtil getMessageUtil() {
  return messageUtil;
 }

 /** Gets the CoreProtectAPI instance. */
 @Nullable
 public CoreProtectAPI getCoreProtectAPI() {
  return coreProtectAPI;
 }

 /** Gets the global monitorChestAccess setting. */
 public boolean isMonitorChestAccess() {
  return monitorChestAccess;
 }

 /** Sets the global monitorChestAccess setting and saves to config. */
 public void setMonitorChestAccess(boolean monitorChestAccess) {
  this.monitorChestAccess = monitorChestAccess;
  getConfig().set("monitor-chest-access", monitorChestAccess);
  saveConfig();
  messageUtil.log(Level.INFO, "plugin-toggle-chest-monitor-success", "{status}", String.valueOf(monitorChestAccess));
 }

 /**
  * Gives the specified player the AutoInform wand.
  *
  * @param player The player to give the wand to.
  */
 public void giveAutoInformWand(Player player) {
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

 /** Formats a set of Materials into a comma-separated string. */
 private String formatMaterialList(Set<Material> materials) {
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