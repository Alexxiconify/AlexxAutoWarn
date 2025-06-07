package net.Alexxiconify.alexxAutoWarn.listeners;

import net.Alexxiconify.alexxAutoWarn.AlexxAutoWarn;
import net.Alexxiconify.alexxAutoWarn.managers.ZoneManager;
import net.Alexxiconify.alexxAutoWarn.objects.AutoInformZone;
import net.Alexxiconify.alexxAutoWarn.objects.ZoneAction;
import net.Alexxiconify.alexxAutoWarn.utils.MessageUtil;
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
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
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
 private final @Nullable InputStream coreProtectAPI; // Can be null if CoreProtect is not found

 public AutoInformEventListener(AlexxAutoWarn plugin) {
  this.plugin = plugin;
  this.zoneManager = plugin.getZoneManager();
  this.messageUtil = plugin.getMessageUtil();
  this.wandKey = new NamespacedKey(plugin, "autoinform_wand");
  // Attempt to get CoreProtect API on construction
  this.coreProtectAPI = plugin.getResource();
 }

 /**
  * Handles block placement events to check against zone rules.
  * Checks if the placed block is within a defined zone and applies the corresponding action.
  *
  * @param event The BlockPlaceEvent.
  */
 @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
 public void onBlockPlace(BlockPlaceEvent event) {
  Player player = event.getPlayer();
  // Bypass for players with alexxautowarn.bypass permission
  if (player.hasPermission("alexxautowarn.bypass")) {
   messageUtil.log(Level.FINE, "debug-bypass-active", "{player}", player.getName(), "{event_type}", "BlockPlaceEvent");
   return;
  }

  Location location = event.getBlock().getLocation();
  Material material = event.getBlock().getType();
  handleZoneAction(player, location, material, event, "place");
 }

 /**
  * Handles player bucket empty events to check against zone rules.
  * Checks if the bucket liquid placement is within a defined zone and applies the corresponding action.
  *
  * @param event The PlayerBucketEmptyEvent.
  */
 @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
 public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
  Player player = event.getPlayer();
  // Bypass for players with alexxautowarn.bypass permission
  if (player.hasPermission("alexxautowarn.bypass")) {
   messageUtil.log(Level.FINE, "debug-bypass-active", "{player}", player.getName(), "{event_type}", "PlayerBucketEmptyEvent");
   return;
  }

  Location location = event.getBlockClicked().getLocation(); // Get location where bucket was emptied
  Material material = event.getBucket(); // Get the type of bucket being emptied (e.g., LAVA_BUCKET)
  handleZoneAction(player, location, material, event, "empty");
 }

 /**
  * Handles player interaction events, primarily for the selection wand and container access.
  * This method is crucial for players to define zones with the wand.
  *
  * @param event The PlayerInteractEvent.
  */
 @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false) // Do not ignore cancelled to allow wand usage
 public void onPlayerInteract(PlayerInteractEvent event) {
  Player player = event.getPlayer();
  ItemStack itemInHand = player.getInventory().getItemInMainHand();

  // Check if the item in hand is the custom wand
  if (itemInHand.hasItemMeta() && itemInHand.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE)) {
   Block clickedBlock = event.getClickedBlock();
   if (clickedBlock == null) {
    return; // Nothing clicked, possibly air or similar
   }

   // Cancel the event to prevent block interaction with the wand
   event.setCancelled(true);

   if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
    // Set position 1 with right click
    zoneManager.getPlayerSelections().setSelection(player, "pos1", clickedBlock.getLocation());
    messageUtil.sendMessage(player, "wand-pos1-set",
            "{location}", String.format("[%d, %d, %d]",
                    clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ()));
    messageUtil.log(Level.FINE, "debug-wand-pos-set", "{player}", player.getName(), "{position}", "pos1",
            "{location_string}", String.format("%s [%d, %d, %d]", clickedBlock.getWorld().getName(), clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ()));
   } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
    // Set position 2 with left click
    zoneManager.getPlayerSelections().setSelection(player, "pos2", clickedBlock.getLocation());
    messageUtil.sendMessage(player, "wand-pos2-set",
            "{location}", String.format("[%d, %d, %d]",
                    clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ()));
    messageUtil.log(Level.FINE, "debug-wand-pos-set", "{player}", player.getName(), "{position}", "pos2",
            "{location_string}", String.format("%s [%d, %d, %d]", clickedBlock.getWorld().getName(), clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ()));
   }
   return; // Wand action handled, prevent further processing
  }

  // Check for container access for logging/alerts
  if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
   Block clickedBlock = event.getClickedBlock();
   if (clickedBlock != null && clickedBlock.getState() instanceof Container) {
    // Bypass for players with alexxautowarn.bypass permission
    if (player.hasPermission("alexxautowarn.bypass")) {
     messageUtil.log(Level.FINE, "debug-bypass-active", "{player}", player.getName(), "{event_type}", "ContainerAccessEvent");
     return;
    }
    Location location = clickedBlock.getLocation();
    Material material = clickedBlock.getType(); // Material of the container itself
    handleContainerAccess(player, location, material);
   }
  }
 }

 /**
  * Centralized method to handle zone-related actions (DENY, ALERT, ALLOW) for block changes.
  *
  * @param player     The player performing the action.
  * @param location   The location where the action occurred.
  * @param material   The material involved in the action.
  * @param event      The cancellable event.
  * @param actionType Description of the action (e.g., "place", "empty").
  */
 private void handleZoneAction(Player player, Location location, Material material, Cancellable event, String actionType) {
  AutoInformZone zone = zoneManager.getZoneAtLocation(location);

  if (zoneManager.isGloballyBanned(material)) {
   event.setCancelled(true);
   messageUtil.sendMessage(player, "action-denied-globally",
           "{material}", material.name(),
           "{world}", location.getWorld().getName(),
           "{x}", String.valueOf(location.getBlockX()),
           "{y}", String.valueOf(location.getBlockY()),
           "{z}", String.valueOf(location.getBlockZ()));
   messageUtil.log(Level.INFO, "action-denied-globally-console",
           "{player}", player.getName(),
           "{material}", material.name(),
           "{world}", location.getWorld().getName(),
           "{x}", String.valueOf(location.getBlockX()),
           "{y}", String.valueOf(location.getBlockY()),
           "{z}", String.valueOf(location.getBlockZ()));
   if (coreProtectAPI != null) {
    // Log the action as rollbackable
    coreProtectAPI.logPlacement(player.getName(), location, Material.AIR, material.createBlockData()); // Log as air becoming material
   }
   return;
  }

  if (zone != null) {
   ZoneAction effectiveAction = zone.getDefaultAction(material);
   String zoneName = zone.getName();

   switch (effectiveAction) {
    case DENY:
     event.setCancelled(true);
     messageUtil.sendMessage(player, "action-denied",
             "{material}", material.name(),
             "{zone_name}", zoneName,
             "{world}", location.getWorld().getName(),
             "{x}", String.valueOf(location.getBlockX()),
             "{y}", String.valueOf(location.getBlockY()),
             "{z}", String.valueOf(location.getBlockZ()));
     messageUtil.log(Level.INFO, "action-denied-console",
             "{player}", player.getName(),
             "{material}", material.name(),
             "{zone_name}", zoneName,
             "{world}", location.getWorld().getName(),
             "{x}", String.valueOf(location.getBlockX()),
             "{y}", String.valueOf(location.getBlockY()),
             "{z}", String.valueOf(location.getBlockZ()));
     if (coreProtectAPI != null) {
      // Log the action as rollbackable for CoreProtect
      // Log placement of material becoming air, for undoing the denied action
      coreProtectAPI.logPlacement(player.getName(), location, Material.AIR, material);
     }
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
      coreProtectAPI.logPlacement(player.getName(), location, material, null); // Log the actual placement
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
     if (coreProtectAPI != null) {
      coreProtectAPI.logPlacement(player.getName(), location, material, null); // Log the actual placement
     }
     break;
    default:
     throw new IllegalStateException("Unexpected value: " + effectiveAction);
   }
  } else {
   // If not in any defined zone, check for global material bans (already handled above, but keeping structure)
   // No action needed here, as global ban check is before zone check.
  }
 }

 /**
  * Handles container access for logging/alerts.
  *
  * @param player   The player accessing the container.
  * @param location The location of the container.
  * @param material The material of the container.
  */
 private void handleContainerAccess(Player player, Location location, Material material) {
  AutoInformZone zone = zoneManager.getZoneAtLocation(location);
  if (zone != null) {
   // Check if the container material has a specific action defined
   ZoneAction effectiveAction = zone.getDefaultAction();
   String zoneName = zone.getName();

   // Only alert for container access, DENY/ALLOW don't make sense for simple interaction
   if (effectiveAction == ZoneAction.ALERT) {
    messageUtil.sendAlert(player, "alert-container-access",
            "{player}", player.getName(),
            "{material}", material.name(),
            "{zone_name}", zoneName,
            "{world}", location.getWorld().getName(),
            "{x}", String.valueOf(location.getBlockX()),
            "{y}", String.valueOf(location.getBlockY()),
            "{z}", String.valueOf(location.getBlockZ()));
    messageUtil.log(Level.INFO, "action-alert-container-access-console",
            "{player}", player.getName(),
            "{material}", material.name(),
            "{zone_name}", zoneName,
            "{world}", location.getWorld().getName(),
            "{x}", String.valueOf(location.getBlockX()),
            "{y}", String.valueOf(location.getBlockY()),
            "{z}", String.valueOf(location.getBlockZ()));
    if (coreProtectAPI != null) {
     // Log container access with CoreProtect
     coreProtectAPI.logContainerTransaction(player.getName(), location);
    }
   } else if (effectiveAction == ZoneAction.DENY) {
    // Deny makes less sense for container access, but if explicitly set, can cancel
    // For now, we'll treat DENY for containers like ALERT or ALLOW, or ignore.
    // Depending on desired behavior, could add event.setCancelled(true);
    messageUtil.log(Level.FINE, "debug-container-denied-ignored", "{player}", player.getName(), "{material}", material.name(), "{zone_name}", zoneName);
   }
  }
 }
}