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
 private final CoreProtectAPI coreProtectAPI; // Store reference to CoreProtectAPI

 /**
  * Constructor for AutoInformEventListener.
  *
  * @param plugin The main plugin instance.
  */
 public AutoInformEventListener(AlexxAutoWarn plugin) {
  this.plugin = plugin;
  this.zoneManager = zoneManager;
  this.messageUtil = messageUtil;
  this.wandKey = new NamespacedKey(plugin, "autoinform_wand");
  this.coreProtectAPI = plugin.getCoreProtectAPI(); // Get API from main class
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
   handleAction(player, material, location, ZoneAction.DENY, event, "globally-banned", "{material}", material.name());
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

  Material material = event.getBucket(); // Material of the bucket (e.g., LAVA_BUCKET, WATER_BUCKET)
  Location location = event.getBlockClicked().getLocation(); // Location where the liquid will be placed

  // Map bucket material to placed material
  Material placedMaterial;
  if (material == Material.LAVA_BUCKET) {
   placedMaterial = Material.LAVA;
  } else if (material == Material.WATER_BUCKET) {
   placedMaterial = Material.WATER;
  } else {
   return; // Not a bucket we're interested in
  }

  // Check globally banned materials first
  if (zoneManager.isGloballyBanned(placedMaterial)) {
   handleAction(player, placedMaterial, location, ZoneAction.DENY, event, "globally-banned", "{material}", placedMaterial.name());
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
   // Player is holding the wand
   Block clickedBlock = event.getClickedBlock();
   if (clickedBlock != null) {
    Location location = clickedBlock.getLocation();
    String locationString = String.format("%s,%.0f,%.0f,%.0f", location.getWorld().getName(), location.getX(), location.getY(), location.getZ()); // Simplified for better readability

    if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
     plugin.getCommandExecutor().setPlayerSelection(player, "pos1", locationString);
     messageUtil.sendMessage(player, "command-define-pos1-set", "{location}", locationString);
     messageUtil.log(Level.FINE, "debug-pos-saved", "{player}", player.getName(), "{position}", "Pos1", "{zone_name}", "N/A (wand)", "{location_string}", locationString);
    } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
     plugin.getCommandExecutor().setPlayerSelection(player, "pos2", locationString);
     messageUtil.sendMessage(player, "command-define-pos2-set", "{location}", locationString);
     messageUtil.log(Level.FINE, "debug-pos-saved", "{player}", player.getName(), "{position}", "Pos2", "{zone_name}", "N/A (wand)", "{location_string}", locationString);
    }
    event.setCancelled(true); // Cancel the interaction to prevent block breaking/placement
   }
   return; // Stop processing if it's the wand
  }

  // Handle container access within zones
  if (player.hasPermission("alexxautowarn.bypass")) {
   return; // Bypass if player has bypass permission
  }

  if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
   Block clickedBlock = event.getClickedBlock();
   if (clickedBlock != null && clickedBlock.getState() instanceof Container) {
    Location location = clickedBlock.getLocation();
    Material material = clickedBlock.getType(); // Material of the container itself

    AutoInformZone zone = zoneManager.getZoneAtLocation(location);
    if (zone != null) {
     ZoneAction action = zone.getMaterialAction(material);
     // Only alert for container access if action is ALERT (DENY/ALLOW don't apply to access)
     if (action == ZoneAction.ALERT) {
      messageUtil.sendAlert(player, "alert-container-access",
              "{player}", player.getName(),
              "{material}", material.name(),
              "{zone_name}", zone.getName(),
              "{world}", location.getWorld().getName(),
              "{x}", String.valueOf(location.getBlockX()),
              "{y}", String.valueOf(location.getBlockY()),
              "{z}", String.valueOf(location.getBlockZ()));

      // Log container access with CoreProtect
      if (coreProtectAPI != null) {
       coreProtectAPI.logContainerTransaction(player.getName(), location); // Corrected method
      }
     }
    }
   }
  }
 }

 /**
  * Helper method to determine the action for a material within a zone and handle it.
  *
  * @param player   The player performing the action.
  * @param material The material involved.
  * @param location The location of the action.
  * @param zone     The AutoInformZone the action is in.
  * @param event    The Cancellable event.
  */
 private void handleMaterialAction(Player player, Material material, Location location, AutoInformZone zone, Cancellable event) {
  String zoneName = zone.getName();
  ZoneAction action = zone.getMaterialAction(material); // Get specific action, falls back to default

  switch (action) {
   case DENY:
    event.setCancelled(true);
    messageUtil.sendMessage(player, "action-denied",
            "{material}", material.name(),
            "{zone_name}", zoneName,
            "{world}", location.getWorld().getName(),
            "{x}", String.valueOf(location.getBlockX()),
            "{y}", String.valueOf(location.getBlockY()),
            "{z}", String.valueOf(location.getBlockZ()));

    // Log denied action with CoreProtect (as a removal that didn't happen)
    if (coreProtectAPI != null) {
     coreProtectAPI.logPlacement(player.getName(), location, material, null); // Log as attempt, can't cancel CP log
    }
    messageUtil.log(Level.INFO, "action-denied-console",
            "{player}", player.getName(),
            "{material}", material.name(),
            "{zone_name}", zoneName,
            "{world}", location.getWorld().getName(),
            "{x}", String.valueOf(location.getBlockX()),
            "{y}", String.valueOf(location.getBlockY()),
            "{z}", String.valueOf(location.getBlockZ()));
    break;
   case ALERT:
    messageUtil.sendAlert(player, "action-alert",
            "{player}", player.getName(),
            "{material}", material.name(),
            "{zone_name}", zoneName,
            "{world}", location.getWorld().getName(),
            "{x}", String.valueOf(location.getBlockX()),
            "{y}", String.valueOf(location.getBlockY()),
            "{z}", String.valueOf(location.getBlockZ()));
    if (coreProtectAPI != null) {
     coreProtectAPI.logPlacement(player.getName(), location, material, location.getBlock().getBlockData()); // Corrected signature
    }
    messageUtil.log(Level.INFO, "action-alert-console",
            "{player}", player.getName(),
            "{material}", material.name(),
            "{zone_name}", zoneName,
            "{world}", location.getWorld().getName(),
            "{x}", String.valueOf(location.getBlockX()),
            "{y}", String.valueOf(location.getBlockY()),
            "{z}", String.valueOf(location.getBlockZ()));
    break;
   case ALLOW:
    // No action needed, the event proceeds as normal.
    messageUtil.log(Level.FINE, "action-allowed-console",
            "{player}", player.getName(),
            "{material}", material.name(),
            "{zone_name}", zoneName,
            "{world}", location.getWorld().getName(),
            "{x}", String.valueOf(location.getBlockX()),
            "{y}", String.valueOf(location.getBlockY()),
            "{z}", String.valueOf(location.getBlockZ()));
    break;
  }
 }

 /**
  * Helper method to handle actions based on a determined ZoneAction.
  * This is used for globally banned materials as well as zone-specific actions.
  *
  * @param player The player performing the action.
  * @param material The material involved.
  * @param location The location of the action.
  * @param action The ZoneAction to perform (DENY, ALERT, ALLOW).
  * @param event The Cancellable event.
  * @param messageKey The base key for messages in messages.yml (e.g., "globally-banned").
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

    // Log denied action with CoreProtect
    if (coreProtectAPI != null) {
     coreProtectAPI.logPlacement(player.getName(), location, material, null); // Log as attempt, can't cancel CP log
    }
    messageUtil.log(Level.INFO, consoleMessageKey, placeholders);
    break;
   case ALERT: // ALERT and ALLOW actions are generally not expected for globally banned items unless specifically designed.
    // If it were an ALERT, you'd send an alert message and log.
    // For now, globally banned only results in DENY or implicit ALLOW if not banned.
    break;
   case ALLOW:
    // No action needed, the event proceeds as normal.
    break;
  }
 }
}