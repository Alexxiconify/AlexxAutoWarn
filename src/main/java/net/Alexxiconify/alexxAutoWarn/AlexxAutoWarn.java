package net.Alexxiconify.alexxAutoWarn;

import net.Alexxiconify.alexxAutoWarn.commands.AutoInformCommandExecutor;
import net.Alexxiconify.alexxAutoWarn.listeners.AutoInformEventListener;
import net.Alexxiconify.alexxAutoWarn.managers.ZoneManager;
import net.Alexxiconify.alexxAutoWarn.utils.MessageUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
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
 * Handles plugin lifecycle (onEnable, onDisable), configuration loading,
 * and initializes managers, listeners, and command executors.
 */
@SuppressWarnings("ALL") // Suppress all warnings for brevity during iterative debugging
public final class AlexxAutoWarn extends JavaPlugin implements CommandExecutor, TabCompleter {

 // Wand properties
 private static final String WAND_DISPLAY_NAME = ChatColor.GOLD + "" + ChatColor.BOLD + "AutoInform Selection Wand";
 private static final List<String> WAND_LORE = Arrays.asList(
         ChatColor.GRAY + "Left-click a block to set Pos2",
         ChatColor.GRAY + "Right-click a block to set Pos1"
 );
 // Added static plugin instance
 private static AlexxAutoWarn plugin;
 private MessageUtil messageUtil;
 private ZoneManager zoneManager;
 private NamespacedKey wandKey;
 private boolean monitorChestAccess;

 public static AlexxAutoWarn getPlugin() {
  return plugin;
 }

 @Override
 public void onEnable() {
  // Set the static plugin instance first
  plugin = this; // Ensure 'plugin' static field is set

  // Set the logger level to FINE (DEBUG) by default for better troubleshooting
  getLogger().setLevel(Level.FINE); // <--- ADD THIS LINE

  // Plugin startup logic
  messageUtil = new MessageUtil(this);
  messageUtil.log(Level.INFO, "plugin-startup");

  // Load configuration and initialize managers
  saveDefaultConfig(); // Creates config.yml if it doesn't exist
  reloadConfigInternal(); // Load configuration into memory

  this.wandKey = new NamespacedKey(this, "autoinform_wand");

  // Register event listener
  getServer().getPluginManager().registerEvents(new AutoInformEventListener(this), this);

  // Set command executor and tab completer
  // We set 'this' as the executor/completer in plugin.yml, but delegate to AutoInformCommandExecutor
  // This is a common pattern for separating command logic from the main plugin class.
  AutoInformCommandExecutor commandExecutor = new AutoInformCommandExecutor(this);
  getCommand("autoinform").setExecutor(commandExecutor);
  getCommand("autoinform").setTabCompleter(commandExecutor);

  messageUtil.log(Level.INFO, "plugin-enabled");
 }

 @Override
 public void onDisable() {
  // Plugin shutdown logic
  messageUtil.log(Level.INFO, "plugin-shutting-down");
  // Save any pending data if necessary (zones are saved by ZoneManager as they are modified)

  messageUtil.log(Level.INFO, "plugin-disabled");
 }

 /**
  * Reloads the plugin's configuration from disk.
  * This method is called internally on plugin enable and via the reload command.
  */
 public void reloadConfigInternal() {
  reloadConfig(); // Reloads config.yml from disk
  messageUtil.loadMessages(); // Reload messages from messages.yml

  // Re-initialize ZoneManager to load zones from the reloaded config
  this.zoneManager = new ZoneManager(this, messageUtil);
  this.zoneManager.loadZones(); // Load zones defined in config.yml
  this.zoneManager.loadGloballyBannedMaterials(); // Load globally banned materials

  // Update global settings
  this.monitorChestAccess = getConfig().getBoolean("settings.monitor-chest-access", true); // Default to true

  // Ensure logger level is maintained after reload
  getLogger().setLevel(Level.FINE); // <--- ADD THIS LINE (again, in reload)

  messageUtil.log(Level.INFO, "plugin-config-reloaded");
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
  * Checks if chest access monitoring is enabled in the configuration.
  * @return true if enabled, false otherwise.
  */
 public boolean isMonitorChestAccess() {
  return monitorChestAccess;
 }

 // --- Dummy/Placeholder implementations for Listener and CommandExecutor for this main class ---
 // The actual logic is in AutoInformEventListener and AutoInformCommandExecutor

 /**
  * Gives the AutoInform selection wand to the specified player.
  *
  * @param player The player to give the wand to.
  */
 public void giveWand(Player player) {
  ItemStack wand = new ItemStack(Material.BLAZE_ROD); // Blaze Rod as the wand item
  ItemMeta meta = wand.getItemMeta();
  if (meta != null) {
   meta.setDisplayName(WAND_DISPLAY_NAME);
   meta.setLore(WAND_LORE);
   meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1); // Mark as wand
   wand.setItemMeta(meta);
  }
  player.getInventory().addItem(wand);
 }

 /**
  * Formats a set of Materials into a comma-separated string.
  */
 private String formatMaterialList(Set<Material> materials) {
  if (materials.isEmpty()) return "None";
  return materials.stream().map(Enum::name).collect(Collectors.joining(", "));
 }

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