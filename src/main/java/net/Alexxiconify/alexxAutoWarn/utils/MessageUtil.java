package net.Alexxiconify.alexxAutoWarn.utils;

import net.Alexxiconify.alexxAutoWarn.AlexxAutoWarn;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Utility class for handling all plugin messages.
 * Loads messages from messages.yml and provides methods for sending formatted messages
 * to players, console, and broadcasting alerts.
 */
public class MessageUtil {

 private final AlexxAutoWarn plugin;
 private final Map<String, String> cachedMessages = new HashMap<>();
 private String pluginPrefix;

 /**
  * Constructor for MessageUtil.
  *
  * @param plugin The main plugin instance.
  */
 public MessageUtil(AlexxAutoWarn plugin) {
  this.plugin = plugin;
  loadMessages(); // Load messages on initialization
 }

 /**
  * Loads messages from the messages.yml file.
  * This method should be called on plugin enabled and reload.
  */
 public void loadMessages() {
  // Ensure messages.yml exists, otherwise create it from resources
  File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
  if (!messagesFile.exists()) {
   plugin.saveResource("messages.yml", false);
  }

  // Load the messages.yml file
  FileConfiguration messagesConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(messagesFile);

  // Cache all messages for faster access
  cachedMessages.clear();
  Set<String> keys = messagesConfig.getKeys(true); // Get all keys, including nested ones
  for (String key : keys) {
   String message = messagesConfig.getString(key);
   if (message != null) {
    cachedMessages.put(key, message);
   }
  }

  // Get and cache the plugin prefix
  this.pluginPrefix = getRawMessage("plugin-prefix");
  if (this.pluginPrefix == null) {
   // Updated fallback prefix to match plugin name
   this.pluginPrefix = "&c[AlexxAutoWarn] &e";
   plugin.getLogger().warning("Plugin prefix not found in messages.yml. Using default.");
  }

  // Log config reload message
  plugin.getLogger().log(Level.INFO, colorize(pluginPrefix + getRawMessage("plugin-config-reloaded")));
 }

 /**
  * Gets a raw message string from the cache, without a prefix or color translation.
  *
  * @param key The key of the message.
  * @return The raw message string, or null if not found.
  */
 private String getRawMessage(String key) {
  return cachedMessages.get(key);
 }

 /**
  * Translates '&' color codes to Minecraft ChatColor.
  *
  * @param message The message string with '&' color codes.
  * @return The message string with ChatColor applied.
  */
 public static String colorize(String message) {
  return ChatColor.translateAlternateColorCodes('&', message);
 }

 /**
  * Sends a formatted message to a command sender (player or console).
  * Automatically applies the plugin prefix and color codes.
  *
  * @param sender The recipient of the message.
  * @param key The key of the message in messages.yml.
  * @param placeholders Optional key-value pairs for placeholder replacement (e.g., "{player}", "Alexx").
  */
 public void sendMessage(CommandSender sender, String key, String... placeholders) {
  String message = getRawMessage(key);
  if (message == null) {
   plugin.getLogger().warning(String.format("Message key '%s' not found in messages.yml!", key));
   message = String.format("&cError: Message key '%s' not found.", key); // Fallback message
  }

  // Apply placeholders
  if (placeholders.length % 2 == 0) { // Ensure even number of placeholder arguments
   for (int i = 0; i < placeholders.length; i += 2) {
    message = message.replace(placeholders[i], placeholders[i + 1]);
   }
  } else {
   plugin.getLogger().warning(String.format("Invalid number of placeholders for message key '%s'. Must be key-value pairs.", key));
  }

  // Add plugin prefix unless the message already starts with '{plugin-prefix}' or is a help header
  // Help with headers manage their own {plugin-prefix} explicitly for formatting flexibility
  if (!key.startsWith("main-help-") && !message.startsWith("{plugin-prefix}")) {
   message = pluginPrefix + message;
  }

  // Translate color codes
  message = colorize(message);
  sender.sendMessage(message);
 }

 /**
  * Sends an alert message to all players with the 'alexxautowarn.alert.receive' permission.
  * Automatically applies the plugin prefix and color codes.
  *
  * @param triggeringPlayer The player who triggered the alert (used for context in message placeholders).
  * @param key The key of the alert message in messages.yml.
  * @param placeholders Optional key-value pairs for placeholder replacement.
  */
 public void sendAlert(Player triggeringPlayer, String key, String... placeholders) {
  String message = getRawMessage(key);
  if (message == null) {
   plugin.getLogger().warning(String.format("Alert message key '%s' not found in messages.yml!", key));
   message = String.format("&cError: Alert message key '%s' not found.", key);
  }

  // Apply placeholders
  if (placeholders.length % 2 == 0) {
   for (int i = 0; i < placeholders.length; i += 2) {
    message = message.replace(placeholders[i], placeholders[i + 1]);
   }
  } else {
   plugin.getLogger().warning(String.format("Invalid number of placeholders for alert message key '%s'. Must be key-value pairs.", key));
  }

  // Add plugin prefix
  message = pluginPrefix + message;
  // Translate color codes
  message = colorize(message);

  // Send it to players with permission
  for (Player p : Bukkit.getOnlinePlayers()) {
   // Updated permission node to match plugin name
   if (p.hasPermission("alexxautowarn.alert.receive")) {
    p.sendMessage(message);
   }
  }
  // Also log to console for record-keeping
  plugin.getLogger().log(Level.INFO, ChatColor.stripColor(colorize(message))); // Strip colors for console log readability
 }

 /**
  * Logs a message to the plugin's console.
  *
  * @param level The logging level (e.g., Level.INFO, Level. WARNING, Level. SEVERE).
  * @param key The key of the message in messages.yml.
  * @param placeholdersAndOptionalThrowable Optional key-value pairs for placeholder replacement,
  * followed by an optional Throwable object if logging an exception.
  */
 public void log(Level level, String key, Object... placeholdersAndOptionalThrowable) {
  String message = getRawMessage(key);
  if (message == null) {
   plugin.getLogger().warning(String.format("Log message key '%s' not found in messages.yml!", key));
   message = String.format("&cError: Log message key '%s' not found.", key);
  }

  Throwable throwable = null;
  int placeholderCount = placeholdersAndOptionalThrowable.length;

  // Check if the last argument is a Throwable
  if (placeholderCount > 0 && placeholdersAndOptionalThrowable[placeholderCount - 1] instanceof Throwable) {
   throwable = (Throwable) placeholdersAndOptionalThrowable[placeholderCount - 1];
   // Reduce placeholderCount to exclude the Throwable for message formatting
   placeholderCount--;
  }

  // Apply placeholders (up to placeholderCount)
  if (placeholderCount % 2 == 0) {
   for (int i = 0; i < placeholderCount; i += 2) {
    if (i + 1 < placeholdersAndOptionalThrowable.length) { // Ensure value exists
     message = message.replace(String.valueOf(placeholdersAndOptionalThrowable[i]), String.valueOf(placeholdersAndOptionalThrowable[i + 1]));
    }
   }
  } else {
   plugin.getLogger().warning(String.format("Invalid number of placeholders for log message key '%s'. Must be key-value pairs.", key));
  }

  // Add plugin prefix
  message = pluginPrefix + message;
  // Translate color codes and strip for console readability
  message = ChatColor.stripColor(colorize(message));

  // Log with or without the throwable
  if (throwable != null) {
   plugin.getLogger().log(level, message, throwable);
  } else {
   plugin.getLogger().log(level, message);
  }
 }
}