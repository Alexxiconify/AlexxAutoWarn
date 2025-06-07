package net.Alexxiconify.alexxAutoWarn.utils; // Corrected package to .utils

import net.Alexxiconify.alexxAutoWarn.AlexxAutoWarn;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Utility class for handling all plugin messages.
 * Loads messages from messages.yml and provides methods for sending formatted messages
 * to players, console, and broadcasting alerts.
 */
@SuppressWarnings("ALL")
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

  FileConfiguration messagesConfig = plugin.getConfig().options().copyDefaults(true).configuration(); // Use the plugin's config to load messages.yml
  try {
   messagesConfig.load(messagesFile);
   cachedMessages.clear(); // Clear existing messages
   for (String key : messagesConfig.getKeys(true)) {
    cachedMessages.put(key, messagesConfig.getString(key));
   }
   this.pluginPrefix = colorize(cachedMessages.getOrDefault("plugin-prefix", "&c[AutoInform] &e"));
   plugin.getLogger().log(Level.INFO, "Messages loaded from messages.yml. Prefix: " + ChatColor.stripColor(pluginPrefix));
  } catch (Exception e) {
   plugin.getLogger().log(Level.SEVERE, "Could not load messages from messages.yml", e);
   // Fallback to default prefix if loading fails
   this.pluginPrefix = ChatColor.RED + "[AutoInform] " + ChatColor.YELLOW;
  }
 }

 /**
  * Translates color codes in a string.
  *
  * @param message The message to colorize.
  * @return The colorized message.
  */
 private String colorize(String message) {
  return ChatColor.translateAlternateColorCodes('&', message);
 }

 /**
  * Sends a formatted message to a command sender (player or console).
  * The message is retrieved from messages.yml based on the key.
  * Placeholders in the message are replaced dynamically.
  *
  * @param sender The recipient of the message.
  * @param key The key of the message in messages.yml.
  * @param placeholdersAndOptionalThrowable Optional alternating key-value pairs for placeholders,
  * followed by an optional Throwable at the very end.
  */
 public void sendMessage(@NotNull CommandSender sender, @NotNull String key, Object... placeholdersAndOptionalThrowable) {
  String message = cachedMessages.get(key);
  if (message == null) {
   plugin.getLogger().warning(String.format("Message key '%s' not found in messages.yml!", key));
   message = String.format("&cError: Message key '%s' not found.", key);
  }

  // Apply placeholders
  if (placeholdersAndOptionalThrowable.length % 2 == 0) { // Check for even number (key-value pairs)
   for (int i = 0; i < placeholdersAndOptionalThrowable.length; i += 2) {
    // Ensure both key and value exist
    if (i + 1 < placeholdersAndOptionalThrowable.length) {
     message = message.replace(String.valueOf(placeholdersAndOptionalThrowable[i]), String.valueOf(placeholdersAndOptionalThrowable[i + 1]));
    }
   }
  } else {
   plugin.getLogger().warning(String.format("Invalid number of placeholders for message key '%s'. Must be key-value pairs.", key));
  }

  // Add plugin prefix and colorize
  message = pluginPrefix + message;
  sender.sendMessage(colorize(message));
 }

 /**
  * Sends a formatted help message to a player.
  * This method iterates through predefined help message keys in messages.yml.
  *
  * @param player The player to send the help message to.
  * @param commandAlias The alias used to invoke the main command (e.g., "autoinform", "ainform").
  */
 public void sendHelpMessage(@NotNull Player player, @NotNull String commandAlias) {
  // Retrieve and send header
  sendMessage(player, "main-help-header");

  // List of help message keys (can be dynamically loaded or hardcoded based on needs)
  String[] helpKeys = {
          "main-help-wand",
          "main-help-pos1",
          "main-help-pos2",
          "main-help-define",
          "main-help-defaultaction",
          "main-help-setaction",
          "main-help-removeaction",
          "main-help-remove",
          "main-help-info",
          "main-help-list",
          "main-help-clearwand",
          "main-help-reload",
          "main-help-banned"
  };

  for (String key : helpKeys) {
   String message = cachedMessages.get(key);
   if (message != null) {
    message = message.replace("{command}", commandAlias);
    sendMessage(player, key, "{command}", commandAlias); // Use sendMessage to apply prefix and color
   } else {
    plugin.getLogger().warning(String.format("Help message key '%s' not found in messages.yml!", key));
   }
  }
 }


 /**
  * Logs a formatted message to the console.
  * The message is retrieved from messages.yml based on the key.
  * Placeholders in the message are replaced dynamically.
  * Optional Throwable can be appended for stack trace logging.
  *
  * @param level The logging level.
  * @param key The key of the message in messages.yml.
  * @param placeholdersAndOptionalThrowable Optional alternating key-value pairs for placeholders,
  * followed by an optional Throwable at the very end.
  */
 public void log(@NotNull Level level, @NotNull String key, Object... placeholdersAndOptionalThrowable) {
  String message = cachedMessages.get(key);
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

 /**
  * Sends an alert message to all players with the "autoinform.alert.receive" permission.
  * The message is retrieved from messages.yml based on the key.
  *
  * @param sender       The player who triggered the alert (used for context).
  * @param key          The key of the alert message in messages.yml.
  * @param placeholders Key-value pairs for placeholders.
  */
 public void sendAlert(@NotNull Player sender, @NotNull String key, Object... placeholders) {
  String message = cachedMessages.get(key);
  if (message == null) {
   plugin.getLogger().warning(String.format("Alert message key '%s' not found in messages.yml!", key));
   message = String.format("&cError: Alert message key '%s' not found.", key);
  }

  // Apply placeholders
  if (placeholders.length % 2 == 0) { // Check for even number (key-value pairs)
   for (int i = 0; i < placeholders.length; i += 2) {
    if (i + 1 < placeholders.length) {
     message = message.replace(String.valueOf(placeholders[i]), String.valueOf(placeholders[i + 1]));
    }
   }
  } else {
   plugin.getLogger().warning(String.format("Invalid number of placeholders for alert message key '%s'. Must be key-value pairs.", key));
  }

  // Add plugin prefix and colorize
  message = pluginPrefix + message;
  String colorizedMessage = colorize(message);

  // Send to all players with permission
  for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
   if (onlinePlayer.hasPermission("autoinform.alert.receive")) {
    onlinePlayer.sendMessage(colorizedMessage);
   }
  }
  // Also log to console for auditing
  plugin.getLogger().log(Level.INFO, ChatColor.stripColor(colorizedMessage));
 }
}