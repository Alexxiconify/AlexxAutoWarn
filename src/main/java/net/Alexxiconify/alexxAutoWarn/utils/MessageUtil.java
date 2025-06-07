package net.Alexxiconify.alexxAutoWarn.utils;

import net.Alexxiconify.alexxAutoWarn.AlexxAutoWarn;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

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

 public MessageUtil(AlexxAutoWarn plugin) {
  this.plugin = plugin;
  this.logger = plugin.getLogger();
  loadMessages(); // Load messages initially during construction
 }

 /**
  * Loads/reloads messages from the plugin's config. This should be called
  * when the config is reloaded.
  */
 public void loadMessages() {
  plugin.reloadConfig(); // Ensure latest config is loaded
  this.pluginPrefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("plugin-prefix", "&c[AutoInform] &e"));
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
  String message = plugin.getConfig().getString(key, "Message not found for key: " + key);
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
  String message = plugin.getConfig().getString(key, "Log message not found for key: " + key);
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
  sendMessage(sender, "main-help-header", "{command}", commandLabel);
  sendMessage(sender, "main-help-wand", "{plugin-prefix}", pluginPrefix, "{command}", commandLabel);
  sendMessage(sender, "main-help-pos1", "{plugin-prefix}", pluginPrefix, "{command}", commandLabel);
  sendMessage(sender, "main-help-pos2", "{plugin-prefix}", pluginPrefix, "{command}", commandLabel);
  sendMessage(sender, "main-help-define", "{plugin-prefix}", pluginPrefix, "{command}", commandLabel);
  sendMessage(sender, "main-help-defaultaction", "{plugin-prefix}", pluginPrefix, "{command}", commandLabel);
  sendMessage(sender, "main-help-setaction", "{plugin-prefix}", pluginPrefix, "{command}", commandLabel);
  sendMessage(sender, "main-help-removeaction", "{plugin-prefix}", pluginPrefix, "{command}", commandLabel);
  sendMessage(sender, "main-help-remove", "{plugin-prefix}", pluginPrefix, "{command}", commandLabel);
  sendMessage(sender, "main-help-info", "{plugin-prefix}", pluginPrefix, "{command}", commandLabel);
  sendMessage(sender, "main-help-list", "{plugin-prefix}", pluginPrefix, "{command}", commandLabel);
  sendMessage(sender, "main-help-clearwand", "{plugin-prefix}", pluginPrefix, "{command}", commandLabel);
  sendMessage(sender, "main-help-reload", "{plugin-prefix}", pluginPrefix, "{command}", commandLabel);
  sendMessage(sender, "main-help-banned", "{plugin-prefix}", pluginPrefix, "{command}", commandLabel);
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