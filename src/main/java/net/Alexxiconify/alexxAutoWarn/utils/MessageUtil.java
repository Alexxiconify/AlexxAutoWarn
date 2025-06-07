package net.Alexxiconify.alexxAutoWarn.utils;

import net.Alexxiconify.alexxAutoWarn.AlexxAutoWarn;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for sending formatted messages to players and logging to console.
 * Handles prefixing and color code translation.
 */
public class MessageUtil {

 private final AlexxAutoWarn plugin;
 private final Logger logger;
 private String pluginPrefix;
 private final Map<String, String> messages; // Map to cache loaded messages

 public MessageUtil(AlexxAutoWarn plugin) {
  this.plugin = plugin;
  this.logger = plugin.getLogger();
  this.messages = new HashMap<>(); // Initialize the map
  loadMessages(); // Load messages initially during construction
 }

 /**
  * Loads/reloads messages from the plugin's config. This should be called
  * when the config is reloaded.
  */
 public void loadMessages() {
  plugin.reloadConfig(); // Ensure latest config is loaded
  this.pluginPrefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.plugin-prefix", "&c[AutoInform] &e"));

  // Clear existing messages and load them from the config's "messages" section
  messages.clear();
  ConfigurationSection messagesSection = plugin.getConfig().getConfigurationSection("messages");
  if (messagesSection != null) {
   for (String key : messagesSection.getKeys(true)) { // Use true to get all nested keys
    if (messagesSection.isString(key)) { // Only load string values
     messages.put(key, messagesSection.getString(key));
    }
   }
  }
  // Also load direct help messages not under "messages" section, if any.
  // Assuming help messages are at the root or a separate top-level section if not under "messages".
  // If your help messages are directly under 'messages:' section, you can remove this block.
  // But based on `messages.yml` where help messages are top level, this is needed.
  ConfigurationSection rootSection = plugin.getConfig();
  String[] helpKeys = {
          "main-help-header", "main-help-wand", "main-help-pos1", "main-help-pos2",
          "main-help-define", "main-help-defaultaction", "main-help-setaction", "main-help-removeaction",
          "main-help-remove", "main-help-info", "main-help-list", "main-help-clearwand",
          "main-help-reload", "main-help-banned"
  };
  for (String key : helpKeys) {
   if (rootSection.isString(key)) {
    messages.put(key, rootSection.getString(key));
   }
  }

  logger.log(Level.INFO, "Messages loaded from messages.yml. Prefix: " + ChatColor.stripColor(pluginPrefix));
 }


 /**
  * Sends a message from messages.yml to a CommandSender (Player or Console).
  * Automatically applies plugin prefix and color codes.
  *
  * @param sender The recipient of the message.
  * @param key    The key for the message in messages.yml.
  * @param replacements Optional key-value pairs for placeholders (e.g., "{player}", "Alex").
  */
 public void sendMessage(CommandSender sender, String key, Object... replacements) {
  String message = messages.get(key); // Get from cached map
  if (message == null) {
   // Fallback if key not found in cached map, but also log warning
   logger.warning("[AlexxAutoWarn] Message key '" + key + "' not found in messages.yml!");
   message = "Message not found for key: " + key;
  }

  message = replacePlaceholders(message, replacements);
  sender.sendMessage(ChatColor.translateAlternateColorCodes('&', pluginPrefix + message));
 }

 /**
  * Logs a message to the console with a specific logging level.
  * Automatically applies plugin prefix and removes color codes for console readability.
  *
  * @param level The logging level (e.g., Level.INFO, Level.WARNING).
  * @param key   The key for the log message in messages.yml.
  * @param replacements Optional key-value pairs for placeholders.
  */
 public void log(Level level, String key, Object... replacements) {
  String message = messages.get(key); // Get from cached map
  if (message == null) {
   // Fallback if key not found in cached map, but also log warning
   logger.warning("[AlexxAutoWarn] Log message key '" + key + "' not found in messages.yml!");
   message = "Log message not found for key: " + key;
  }

  message = replacePlaceholders(message, replacements);
  // Strip color codes for console readability
  logger.log(level, ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', pluginPrefix + message)));
 }

 /**
  * Sends a structured help message to the player, gathering all help entries from messages.yml.
  *
  * @param sender The player to send the help message to.
  * @param commandLabel The base command label (e.g., "autoinform").
  */
 public void sendHelpMessage(CommandSender sender, String commandLabel) {
  // These now retrieve from the 'messages' map.
  // No need for pluginPrefix placeholder in these specific messages as it's added by sendMessage
  sendMessage(sender, "main-help-header", "{command}", commandLabel);
  sendMessage(sender, "main-help-wand", "{command}", commandLabel);
  sendMessage(sender, "main-help-pos1", "{command}", commandLabel);
  sendMessage(sender, "main-help-pos2", "{command}", commandLabel);
  sendMessage(sender, "main-help-define", "{command}", commandLabel);
  sendMessage(sender, "main-help-defaultaction", "{command}", commandLabel);
  sendMessage(sender, "main-help-setaction", "{command}", commandLabel);
  sendMessage(sender, "main-help-removeaction", "{command}", commandLabel);
  sendMessage(sender, "main-help-remove", "{command}", commandLabel);
  sendMessage(sender, "main-help-info", "{command}", commandLabel);
  sendMessage(sender, "main-help-list", "{command}", commandLabel);
  sendMessage(sender, "main-help-clearwand", "{command}", commandLabel);
  sendMessage(sender, "main-help-reload", "{command}", commandLabel);
  sendMessage(sender, "main-help-banned", "{command}", commandLabel);
 }

 /**
  * Replaces placeholders in a message string.
  * Expected format for replacements: "{placeholder1}", "value1", "{placeholder2}", "value2", etc.
  */
 private String replacePlaceholders(String message, Object... replacements) {
  if (replacements.length % 2 != 0) {
   logger.warning("MessageUtil: Odd number of replacements provided for message: " + message);
   return message;
  }
  for (int i = 0; i < replacements.length; i += 2) {
   String placeholder = String.valueOf(replacements[i]);
   String value = String.valueOf(replacements[i + 1]);
   message = message.replace(placeholder, value);
  }
  return message;
 }
}