package net.Alexxiconify.alexxAutoWarn;

import net.Alexxiconify.alexxAutoWarn.commands.AutoInformCommandExecutor;
import net.Alexxiconify.alexxAutoWarn.listeners.AutoInformEventListener;
import net.Alexxiconify.alexxAutoWarn.managers.PlayerSelectionManager;
import net.Alexxiconify.alexxAutoWarn.managers.ZoneManager;
import net.Alexxiconify.alexxAutoWarn.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class AlexxAutoWarn extends JavaPlugin implements CommandExecutor, TabCompleter {

 private MessageUtil messageUtil;
 private ZoneManager zoneManager;
 // Configuration constants for the wand
 private static final String WAND_DISPLAY_NAME_KEY = "wand.display-name";
 private NamespacedKey wandKey;
 private static final String WAND_LORE_KEY = "wand.lore";
 private static final String WAND_MATERIAL_KEY = "wand.material";
 private PlayerSelectionManager playerSelectionManager;
 private String WAND_DISPLAY_NAME;
 private List<String> WAND_LORE;
 private Material WAND_MATERIAL;

 @Override
 public void onEnable() {
  // Load default config
  saveDefaultConfig();

  // Initialize utility and manager classes
  this.messageUtil = new MessageUtil(this);
  this.playerSelectionManager = new PlayerSelectionManager(); // Initialize PlayerSelectionManager
  this.zoneManager = new ZoneManager(this, messageUtil); // ZoneManager no longer needs PlayerSelectionManager directly

  this.wandKey = new NamespacedKey(this, "autoinform_wand");

  // Load wand properties from config
  loadWandProperties();

  // Register event listener
  Bukkit.getPluginManager().registerEvents(new AutoInformEventListener(this), this);

  // Register command executor and tab completer
  getCommand("autoinform").setExecutor(new AutoInformCommandExecutor(this));
  getCommand("autoinform").setTabCompleter(new AutoInformCommandExecutor(this));

  messageUtil.log(Level.INFO, "plugin-enabled");
 }

 @Override
 public void onDisable() {
  messageUtil.log(Level.INFO, "plugin-disabled");
 }

 /**
  * Reloads the plugin's configuration and messages.
  * This method is typically called by a command.
  */
 public void reloadPluginConfig() {
  reloadConfig(); // Reloads config.yml
  messageUtil.loadMessages(); // Reloads messages.yml
  loadWandProperties(); // Reload wand properties
  zoneManager.loadZones(); // Reload zones (which also reloads banned materials)
  messageUtil.log(Level.INFO, "plugin-config-reloaded");
 }

 private void loadWandProperties() {
  FileConfiguration config = getConfig();
  this.WAND_DISPLAY_NAME = ChatColor.translateAlternateColorCodes('&', config.getString(WAND_DISPLAY_NAME_KEY, "&bAutoInform Wand"));
  this.WAND_LORE = config.getStringList(WAND_LORE_KEY).stream()
          .map(line -> ChatColor.translateAlternateColorCodes('&', line))
          .collect(Collectors.toList());
  try {
   this.WAND_MATERIAL = Material.valueOf(config.getString(WAND_MATERIAL_KEY, "BLAZE_ROD").toUpperCase());
  } catch (IllegalArgumentException e) {
   getLogger().log(Level.WARNING, "Invalid material specified for wand in config.yml. Defaulting to BLAZE_ROD.", e);
   this.WAND_MATERIAL = Material.BLAZE_ROD;
  }
 }

 // --- Getters for other classes to access managers and plugin resources ---
 public MessageUtil getMessageUtil() {
  return messageUtil;
 }

 public ZoneManager getZoneManager() {
  return zoneManager;
 }

 public PlayerSelectionManager getPlayerSelectionManager() {
  return playerSelectionManager;
 }

 public NamespacedKey getWandKey() {
  return wandKey;
 }

 /**
  * Provides the custom wand item stack.
  *
  * @return An ItemStack representing the AutoInform wand.
  */
 public ItemStack getAutoInformWand() {
  ItemStack wand = new ItemStack(WAND_MATERIAL);
  ItemMeta meta = wand.getItemMeta();
  if (meta != null) {
   meta.setDisplayName(WAND_DISPLAY_NAME);
   meta.setLore(WAND_LORE);
   meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
   wand.setItemMeta(meta);
  }
  return wand;
 }

 /**
  * Grants the custom wand to a player.
  *
  * @param player The player to give the wand to.
  */
 public void giveWand(@NotNull Player player) {
  ItemStack wand = getAutoInformWand();
  player.getInventory().addItem(wand);
 }

 /**
  * Formats a set of Materials into a comma-separated string.
  *
  * @param materials The set of materials to format.
  * @return A comma-separated string of material names, or "None" if empty.
  */
 public String formatMaterialList(Set<Material> materials) {
  if (materials.isEmpty()) return "None";
  return materials.stream().map(Enum::name).collect(Collectors.joining(", "));
 }

 @Override
 public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
  // This is handled by AutoInformCommandExecutor, but JavaPlugin requires this method if it implements CommandExecutor.
  return true;
 }

 @Override
 public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
  // This is handled by AutoInformCommandExecutor, but JavaPlugin requires this method if it implements TabCompleter.
  return null;
 }
}