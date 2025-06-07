package net.Alexxiconify.alexxAutoWarn.listeners;

import net.Alexxiconify.alexxAutoWarn.AlexxAutoWarn;
import net.Alexxiconify.alexxAutoWarn.managers.ZoneManager;
import net.Alexxiconify.alexxAutoWarn.objects.AutoInformZone;
import net.Alexxiconify.alexxAutoWarn.objects.ZoneAction;
import net.Alexxiconify.alexxAutoWarn.utils.MessageUtil;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.logging.Level;


/**
 * Handles all event listeners for the AlexxAutoWarn plugin.
 * This includes monitoring block placements, bucket empties, and player interactions
 * to enforce zone rules and wand functionality.
 */
public class AutoInformEventListener implements Listener {

 private final AlexxAutoWarn plugin;
 private final ZoneManager zoneManager;
 private final MessageUtil messageUtil;
 private final NamespacedKey wandKey;
 private final CoreProtectAPI coreProtectAPI;

 /**
  * Constructor for AutoInformEventListener.
  *
  * @param plugin      The main plugin instance.
  * @param zoneManager The ZoneManager instance.
  * @param messageUtil The MessageUtil instance.
  */
 public AutoInformEventListener(AlexxAutoWarn plugin, ZoneManager zoneManager, MessageUtil messageUtil) {
  this.plugin = plugin;
  this.zoneManager = zoneManager;
  this.messageUtil = messageUtil;
  this.wandKey = new NamespacedKey(plugin, "autoinform_wand");
  this.coreProtectAPI = plugin.getCoreProtectAPI();
 }

 /**
  * Handles block placement events to enforce zone rules.
  *
  * @param event The BlockPlaceEvent.
  */
 @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
 public void onBlockPlace(BlockPlaceEvent event) {
  Player player = event.getPlayer();
  // Bypass if player has bypass permission
  if (player.hasPermission("alexxautowarn.bypass")) {
   return;
  }

  Block placedBlock = event.getBlockPlaced();
  Material material = placedBlock.getType();
  Location location = placedBlock.getLocation();

  // Check globally banned materials first
  if (zoneManager.isGloballyBanned(material)) {
   handleAction(player, material, location, ZoneAction.DENY, event, "action",
           "{player}", player.getName(),
           "{material}", material.name(),
           "{zone_name}", "globally-banned-area",
           "{world}", location.getWorld().getName(),
           "{x}", location.getX(), "{y}", location.getY(), "{z}", location.getZ());
   return;
  }

  // Check zone-specific actions
  AutoInformZone zone = zoneManager.getZoneAtLocation(location);
  if (zone != null) {
   handleMaterialAction(player, material, location, zone, event);
  }
  // If no zone applies, the action is implicitly allowed by the plugin
 }

 /**
  * Handles player bucket empty events (e.g., placing lava/water) to enforce zone rules.
  *
  * @param event The PlayerBucketEmptyEvent.
  */
 @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
 public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
  Player player = event.getPlayer();
  // Bypass if player has bypass permission
  if (player.hasPermission("alexxautowarn.bypass")) {
   return;
  }

  Material material = event.getBucket();
  Location location = event.getBlockClicked().getLocation();

  // Map bucket material to placed material
  Material placedMaterial;
  if (material == Material.LAVA_BUCKET) {
   placedMaterial = Material.LAVA;
  } else if (material == Material.WATER_BUCKET) {
   placedMaterial = Material.WATER;
  } else {
   return;
  }

  // Check globally banned materials first
  if (zoneManager.isGloballyBanned(placedMaterial)) {
   handleAction(player, placedMaterial, location, ZoneAction.DENY, event, "action",
           "{player}", player.getName(),
           "{material}", placedMaterial.name(),
           "{zone_name}", "globally-banned-area",
           "{world}", location.getWorld().getName(),
           "{x}", location.getX(), "{y}", location.getY(), "{z}", location.getZ());
   return;
  }

  // Check zone-specific actions
  AutoInformZone zone = zoneManager.getZoneAtLocation(location);
  if (zone != null) {
   handleMaterialAction(player, placedMaterial, location, zone, event);
  }
 }

 /**
  * Handles player interaction events, specifically for wand functionality and container access.
  *
  * @param event The PlayerInteractEvent.
  */
 @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
 public void onPlayerInteract(PlayerInteractEvent event) {
  Player player = event.getPlayer();
  ItemStack handItem = event.getItem();

  // Check for wand usage
  if (handItem != null && handItem.hasItemMeta() && handItem.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE)) {
   Block clickedBlock = event.getClickedBlock();
   if (clickedBlock != null) {
    Location location = clickedBlock.getLocation();
    String locationString = String.format("%s,%.0f,%.0f,%.0f", location.getWorld().getName(), location.getX(), location.getY(), location.getZ());

    if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
     plugin.getCommandExecutor().setPlayerSelection(player, "pos1", locationString);
     messageUtil.sendMessage(player, "command-pos1-selected", "{location}", locationString);
     messageUtil.log(Level.FINE, "debug-pos-saved", "{player}", player.getName(), "{position}", "Pos1", "{zone_name}", "N/A (wand)", "{location_string}", locationString);
    } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
     plugin.getCommandExecutor().setPlayerSelection(player, "pos2", locationString);
     messageUtil.sendMessage(player, "command-pos2-selected", "{location}", locationString);
     messageUtil.log(Level.FINE, "debug-pos-saved", "{player}", player.getName(), "{position}", "Pos2", "{zone_name}", "N/A (wand)", "{location_string}", locationString);
    }
    event.setCancelled(true);
   }
   return;
  }

  // --- Container Access Monitoring ---
  if (!plugin.isMonitorChestAccess()) {
   return;
  }

  if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
   Block clickedBlock = event.getClickedBlock();
   if (clickedBlock != null && clickedBlock.getState() instanceof Container) {
    if (player.hasPermission("alexxautowarn.bypass")) {
     return;
    }

    Location location = clickedBlock.getLocation();
    Material material = clickedBlock.getType();

    AutoInformZone zone = zoneManager.getZoneAtLocation(location);
    if (zone != null) {
     ZoneAction action = zone.getMaterialAction(material);
     if (action == ZoneAction.DENY) {
      handleAction(player, material, location, ZoneAction.DENY, event, "action-denied-container-access",
              "{player}", player.getName(),
              "{material}", material.name(),
              "{zone_name}", zone.getName(),
              "{world}", location.getWorld().getName(),
              "{x}", location.getX(), "{y}", location.getY(), "{z}", location.getZ());
     } else if (action == ZoneAction.ALERT) {
      handleAction(player, material, location, ZoneAction.ALERT, event, "alert-container-access",
              "{player}", player.getName(),
              "{material}", material.name(),
              "{zone_name}", zone.getName(),
              "{world}", location.getWorld().getName(),
              "{x}", location.getX(), "{y}", location.getY(), "{z}", location.getZ());
     }
    }
   }
  }
 }

 /**
  * Determines and handles the appropriate action for a material within a specific zone.
  *
  * @param player   The player performing the action.
  * @param material The material involved in the action.
  * @param location The location of the action.
  * @param zone     The zone where the action occurred.
  * @param event    The Cancellable event.
  */
 private void handleMaterialAction(Player player, Material material, Location location, AutoInformZone zone, Cancellable event) {
  ZoneAction action = zone.getMaterialAction(material);

  Object[] placeholders = new Object[]{
          "{player}", player.getName(),
          "{material}", material.name(),
          "{zone_name}", zone.getName(),
          "{world}", location.getWorld().getName(),
          "{x}", location.getX(), "{y}", location.getY(), "{z}", location.getZ()
  };

  handleAction(player, material, location, action, event, "action", placeholders);
 }

 /**
  * Executes the specified action (DENY, ALERT, ALLOW) and logs the result.
  *
  * @param player       The player involved.
  * @param material     The material involved.
  * @param location     The location of the event.
  * @param action       The ZoneAction to perform (DENY, ALERT, ALLOW).
  * @param event        The Cancellable event.
  * @param messageKey   The base key for messages in messages.yml (e.g., "action").
  * @param placeholders Optional key-value pairs for message replacement.
  */
 private void handleAction(Player player, Material material, Location location, ZoneAction action, Cancellable event, String messageKey, Object... placeholders) {
  String fullMessageKey;
  String consoleMessageKey;

  switch (action) {
   case DENY:
    event.setCancelled(true);
    fullMessageKey = messageKey + "-denied";
    consoleMessageKey = messageKey + "-denied-console";
    messageUtil.sendMessage(player, fullMessageKey, placeholders);

    if (coreProtectAPI != null) {
     coreProtectAPI.logPlacement(player.getName(), location, material, null);
    }
    messageUtil.log(Level.INFO, consoleMessageKey, placeholders);
    break;
   case ALERT:
    fullMessageKey = messageKey + "-alert";
    consoleMessageKey = messageKey + "-alert-console";
    messageUtil.sendMessage(player, fullMessageKey, placeholders);

    if (coreProtectAPI != null) {
     coreProtectAPI.logPlacement(player.getName(), location, material, null);
    }
    messageUtil.log(Level.INFO, consoleMessageKey, placeholders);
    break;
   case ALLOW:
    fullMessageKey = messageKey + "-allowed";
    consoleMessageKey = messageKey + "-allowed-console";
    if (plugin.getConfig().getBoolean("debug-log-allowed-actions", false)) {
     if (coreProtectAPI != null) {
      coreProtectAPI.logPlacement(player.getName(), location, material, null);
     }
     messageUtil.log(Level.INFO, consoleMessageKey, placeholders);
    }
    break;
  }
 }
}