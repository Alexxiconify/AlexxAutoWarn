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
 private FileConfiguration messagesConfig;
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
  * Translates '&' color codes to Minecraft ChatColor.
  *
  * @param message The message string with '&' color codes.
  * @return The message string with ChatColor applied.
  */
 public static String colorize(String message) {
  return ChatColor.translateAlternateColorCodes('&', message);
 }

 /**
  * Loads messages from the messages.yml file.
  * This method should be called on plugin enable and reload.
  */
 public void loadMessages() {
  // Ensure messages.yml exists, otherwise create it from resources
  File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
  if (!messagesFile.exists()) {
   plugin.saveResource("messages.yml", false);
  }

  // Load the messages.yml file
  messagesConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(messagesFile);

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
   this.pluginPrefix = "&c[AutoInform] &e"; // Fallback prefix if not found
   plugin.getLogger().warning("Plugin prefix not found in messages.yml. Using default.");
  }

  // Log config reload message
  plugin.getLogger().log(Level.INFO, colorize(pluginPrefix + getRawMessage("plugin-config-reloaded")));
 }

 /**
  * Gets a raw message string from the cache, without prefix or color translation.
  *
  * @param key The key of the message.
  * @return The raw message string, or null if not found.
  */
 private String getRawMessage(String key) {
  return cachedMessages.get(key);
 }

 /**
  * Sends a formatted message to a command sender (player or console).
  * Automatically applies the plugin prefix and color codes.
  *
  * @param sender       The recipient of the message.
  * @param key          The key of the message in messages.yml.
  * @param placeholders Optional key-value pairs for placeholder replacement (e.g., "{player}", "Alexx").
  */
 public void sendMessage(CommandSender sender, String key, String... placeholders) {
  String message = getRawMessage(key);
  if (message == null) {
   plugin.getLogger().warning("Message key '" + key + "' not found in messages.yml!");
   message = "&cError: Message key '" + key + "' not found."; // Fallback message
  }

  // Apply placeholders
  if (placeholders.length % 2 == 0) { // Ensure even number of placeholder arguments
   for (int i = 0; i < placeholders.length; i += 2) {
    message = message.replace(placeholders[i], placeholders[i + 1]);
   }
  } else {
   plugin.getLogger().warning("Invalid number of placeholders for message key '" + key + "'. Must be key-value pairs.");
  }

  // Add plugin prefix unless the message already starts with '{plugin-prefix}' or is a help header
  // Help headers manage their own {plugin-prefix} explicitly for formatting flexibility
  if (!key.startsWith("main-help-") && !message.startsWith("{plugin-prefix}")) {
   message = pluginPrefix + message;
  }

  // Translate color codes
  message = colorize(message);
  sender.sendMessage(message);
 }

 /**
  * Sends an alert message to all players with the 'autoinform.alert.receive' permission.
  * Automatically applies the plugin prefix and color codes.
  *
  * @param triggeringPlayer The player who triggered the alert (used for context in message placeholders).
  * @param key              The key of the alert message in messages.yml.
  * @param placeholders     Optional key-value pairs for placeholder replacement.
  */
 public void sendAlert(Player triggeringPlayer, String key, String... placeholders) {
  String message = getRawMessage(key);
  if (message == null) {
   plugin.getLogger().warning("Alert message key '" + key + "' not found in messages.yml!");
   message = "&cError: Alert message key '" + key + "' not found.";
  }

  // Apply placeholders
  if (placeholders.length % 2 == 0) {
   for (int i = 0; i < placeholders.length; i += 2) {
    message = message.replace(placeholders[i], placeholders[i + 1]);
   }
  } else {
   plugin.getLogger().warning("Invalid number of placeholders for alert message key '" + key + "'. Must be key-value pairs.");
  }

  // Add plugin prefix
  message = pluginPrefix + message;
  // Translate color codes
  message = colorize(message);

  // Send to players with permission
  for (Player p : Bukkit.getOnlinePlayers()) {
   if (p.hasPermission("autoinform.alert.receive")) {
    p.sendMessage(message);
   }
  }
  // Also log to console for record-keeping
  plugin.getLogger().log(Level.INFO, ChatColor.stripColor(message)); // Strip colors for console log readability
 }

 /**
  * Logs a message to the plugin's console.
  *
  * @param level                            The logging level (e.g., Level.INFO, Level.WARNING, Level.SEVERE).
  * @param key                              The key of the message in messages.yml.
  * @param placeholdersAndOptionalThrowable Optional key-value pairs for placeholder replacement,
  *                                         followed by an optional Throwable object if logging an exception.
  */
 public void log(Level level, String key, Object... placeholdersAndOptionalThrowable) {
  String message = getRawMessage(key);
  if (message == null) {
   plugin.getLogger().warning("Log message key '" + key + "' not found in messages.yml!");
   message = "&cError: Log message key '" + key + "' not found.";
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
   plugin.getLogger().warning("Invalid number of placeholders for log message key '" + key + "'. Must be key-value pairs.");
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