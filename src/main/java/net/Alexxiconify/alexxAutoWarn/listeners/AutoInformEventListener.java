package net.Alexxiconify.alexxAutoWarn.listeners;

import net.Alexxiconify.alexxAutoWarn.AlexxAutoWarn;
import net.Alexxiconify.alexxAutoWarn.managers.ZoneManager;
import net.Alexxiconify.alexxAutoWarn.objects.AutoInformZone;
import net.Alexxiconify.alexxAutoWarn.objects.ZoneAction;
import net.Alexxiconify.alexxAutoWarn.utils.MessageUtil;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
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
 private final CoreProtectAPI coreProtectAPI;

 // NamespacedKey to identify the AutoInform wand
 private final NamespacedKey wandKey;

 /**
  * Constructor for AutoInformEventListener.
  *
  * @param plugin The main plugin instance.
  */
 public AutoInformEventListener(AlexxAutoWarn plugin) {
  this.plugin = plugin;
  this.zoneManager = plugin.getZoneManager();
  this.messageUtil = plugin.getMessageUtil();
  this.coreProtectAPI = plugin.getCoreProtectAPI(); // Now accessible as public
  this.wandKey = new NamespacedKey(plugin, "autoinform_wand");
 }

 /**
  * Handles BlockPlaceEvent to prevent or warn about block placements in protected zones.
  * EventPriority.HIGH ensures this listener runs after most other plugins but before monitor plugins.
  * ignoreCancelled = true means this listener will not run if another plugin has already cancelled the event.
  *
  * @param event The BlockPlaceEvent.
  */
 @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
 public void onBlockPlace(BlockPlaceEvent event) {
  Player player = event.getPlayer();
  // Players with bypass permission are not affected by zone restrictions
  if (player.hasPermission("autoinform.bypass")) {
   return;
  }

  // event.getBlock() refers to the Block object that existed at the location
  // BEFORE the new block was placed (or the block being replaced).
  // Explicitly assigning to a Block variable to help compiler resolve.
  Block affectedBlock = event.getBlock();
  Location location = affectedBlock.getLocation();
  Material placedMaterial = event.getBlockPlaced().getType();

  // Determine the action for the given material at the location
  ZoneAction effectiveAction = getEffectiveAction(location, placedMaterial);

  // Apply the determined action
  handleZoneAction(player, location, placedMaterial, effectiveAction, event);
 }

 /**
  * Handles PlayerBucketEmptyEvent (e.g., placing water or lava) to prevent or warn.
  *
  * @param event The PlayerBucketEmptyEvent.
  */
 @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
 public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
  Player player = event.getPlayer();
  if (player.hasPermission("autoinform.bypass")) {
   return;
  }

  // event.getBlockClicked() gets the block that was right-clicked.
  // .getRelative(event.getBlockFace()) gets the block ADJACENT to it where the liquid will be placed.
  // Explicitly assigning to a Block variable.
  Block affectedBlock = event.getBlockClicked().getRelative(event.getBlockFace());
  Location location = affectedBlock.getLocation();
  Material emptiedMaterial = event.getBucket(); // BUCKET, LAVA_BUCKET, WATER_BUCKET, POWDER_SNOW_BUCKET

  // Adjust material to the actual block placed (e.g., LAVA for LAVA_BUCKET)
  Material placedMaterial;
  switch (emptiedMaterial) {
   case LAVA_BUCKET:
    placedMaterial = Material.LAVA;
    break;
   case WATER_BUCKET:
    placedMaterial = Material.WATER;
    break;
   case POWDER_SNOW_BUCKET:
    placedMaterial = Material.POWDER_SNOW;
    break;
   default:
    // If it's just a regular BUCKET or other unknown, do nothing
    return;
  }

  // Determine the action for the given material at the location
  ZoneAction effectiveAction = getEffectiveAction(location, placedMaterial);

  // Apply the determined action
  handleZoneAction(player, location, placedMaterial, effectiveAction, event);
 }

 /**
  * Handles PlayerInteractEvent to monitor wand usage and container interactions.
  *
  * @param event The PlayerInteractEvent.
  */
 @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
 public void onPlayerInteract(PlayerInteractEvent event) {
  Player player = event.getPlayer();
  ItemStack itemInHand = player.getInventory().getItemInMainHand();

  // Check if the item in hand is the AutoInform wand
  if (itemInHand.hasItemMeta() && itemInHand.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE)) {
   // Wand interaction
   if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
    // event.getClickedBlock() refers to the block that was clicked.
    Block clickedBlock = event.getClickedBlock(); // Explicitly assigning to a Block variable
    Location loc = clickedBlock.getLocation();
    // This logic is handled by the command executor directly via a custom "PlayerSelections" PDC,
    // but for a true in-game wand, you'd store it temporarily or apply it to a named zone here.
    // For now, this just prevents the block from being broken if the wand is used.
    event.setCancelled(true); // Prevent block breaking with wand
    messageUtil.sendMessage(player, "wand-pos2-selected", "{location}", String.format("%.0f, %.0f, %.0f", loc.getX(), loc.getY(), loc.getZ()));
   } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
    // event.getClickedBlock() refers to the block that was clicked.
    Block clickedBlock = event.getClickedBlock(); // Explicitly assigning to a Block variable
    Location loc = clickedBlock.getLocation();
    event.setCancelled(true); // Prevent block interaction (e.g., opening chest) with wand
    messageUtil.sendMessage(player, "wand-pos1-selected", "{location}", String.format("%.0f, %.0f, %.0f", loc.getX(), loc.getY(), loc.getZ()));
   }
   return; // Don't process other actions if it's the wand
  }

  // --- Container Monitoring ---
  // This part monitors opening of containers, which might be useful for alerting
  // if players open chests in a monitored zone.
  if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
   Block clickedBlock = event.getClickedBlock(); // Explicitly assigning to a Block variable
   if (clickedBlock.getState() instanceof Container) {
    Location location = clickedBlock.getLocation();
    // Check if the container is within any defined zone
    AutoInformZone zone = zoneManager.getZoneAtLocation(location);

    if (zone != null) {
     // Check if opening containers should trigger an alert for this zone
     // Currently, the plugin configures actions for materials.
     // If container opening should be specifically monitored, it needs a material/action in config.
     // For example, if "CHEST" or "BARREL" is set to ALERT, then trigger.
     // For now, we'll just check the default action if no specific material action for the container type.
     ZoneAction effectiveAction = getEffectiveAction(location, clickedBlock.getType());

     if (effectiveAction == ZoneAction.ALERT && player.hasPermission("autoinform.alert.receive")) {
      messageUtil.sendAlert(player, "alert-container-access",
              "{player}", player.getName(),
              "{material}", clickedBlock.getType().name(),
              "{zone_name}", zone.getName(),
              "{world}", location.getWorld().getName(),
              "{x}", String.valueOf(location.getBlockX()),
              "{y}", String.valueOf(location.getBlockY()),
              "{z}", String.valueOf(location.getBlockZ()));
      // Log to CoreProtect if available
      if (coreProtectAPI != null) {
       // The method logContainerAccess might not be present or have a different signature
       // in your CoreProtect API version. Commenting it out for compilation.
       // Consider using coreProtectAPI.logAction() for a more generic log
       // or consult CoreProtect API docs for container access logging.
       // coreProtectAPI.logContainerAccess(player.getName(), location);
       plugin.getLogger().log(Level.INFO, "CoreProtect logging for container access skipped due to potential API mismatch.");
      }
     }
    }
   }
  }
 }

 /**
  * Determines the effective ZoneAction for a given location and material.
  * This considers specific zone material actions, default zone actions,
  * and global banned materials.
  *
  * @param location The location to check.
  * @param material The material being interacted with.
  * @return The effective ZoneAction (DENY, ALERT, ALLOW).
  */
 private ZoneAction getEffectiveAction(Location location, Material material) {
  // 1. Check if the location is within any defined zone
  AutoInformZone zone = zoneManager.getZoneAtLocation(location);

  if (zone != null) {
   // 2. If in a zone, check for material-specific action within that zone
   ZoneAction materialSpecificAction = zone.getMaterialSpecificActions().get(material);
   if (materialSpecificAction != null) {
    return materialSpecificAction; // Zone-specific material action overrides everything
   } else {
    // 3. If no material-specific action, use the zone's default action
    return zone.getDefaultAction();
   }
  } else {
   // 4. If not in any zone, check if the material is globally banned
   if (zoneManager.isGloballyBanned(material)) {
    return ZoneAction.DENY; // Globally banned materials are denied outside zones
   }
  }
  // 5. If not in a zone and not globally banned, implicitly allow
  return ZoneAction.ALLOW;
 }

 /**
  * Applies the determined zone action to the player and event.
  *
  * @param player The player performing the action.
  * @param location The location of the action.
  * @param material The material involved in the action.
  * @param action The determined ZoneAction.
  * @param event The event that triggered the action.
  */
 private void handleZoneAction(Player player, Location location, Material material, ZoneAction action, org.bukkit.event.Event event) {
  switch (action) {
   case DENY:
    // Cancel the event to prevent the action
    if (event instanceof BlockPlaceEvent) {
     ((BlockPlaceEvent) event).setCancelled(true);
     // Re-send the block to the player to immediately revert the client-side change
     // event.getBlock() is used here for its location and current state in the world
     Block blockToRevert = ((BlockPlaceEvent) event).getBlock(); // Explicitly get Block
     player.sendBlockChange(location, blockToRevert.getBlockData());
    } else if (event instanceof PlayerBucketEmptyEvent) {
     ((PlayerBucketEmptyEvent) event).setCancelled(true);
    }

    // Send a denial message to the player
    messageUtil.sendMessage(player, "action-denied",
            "{material}", material.name(),
            "{x}", String.valueOf(location.getBlockX()),
            "{y}", String.valueOf(location.getBlockY()),
            "{z}", String.valueOf(location.getBlockZ()));

    // Log the action to CoreProtect if enabled
    if (coreProtectAPI != null) {
     // CoreProtect log for block placement
     if (event instanceof BlockPlaceEvent) {
      // event.getBlock() is used here for its location and current state in the world
      Block blockForLogging = ((BlockPlaceEvent) event).getBlock(); // Explicitly get Block
      coreProtectAPI.logPlacement(player.getName(), location, material, blockForLogging.getBlockData());
     } else if (event instanceof PlayerBucketEmptyEvent) {
      coreProtectAPI.logPlacement(player.getName(), location, material, Bukkit.createBlockData(material));
     }
     // Console log for denied action
     plugin.getLogger().log(Level.INFO, player.getName() + " was DENIED from placing " + material.name() + " at " + location);
    }
    break;
   case ALERT:
    // Send an alert message to all staff with the alert permission
    messageUtil.sendAlert(player, "action-alert",
            "{player}", player.getName(),
            "{material}", material.name(),
            "{world}", location.getWorld().getName(),
            "{x}", String.valueOf(location.getBlockX()),
            "{y}", String.valueOf(location.getBlockY()),
            "{z}", String.valueOf(location.getBlockZ()));

    // Log the action to CoreProtect if enabled
    if (coreProtectAPI != null) {
     // CoreProtect log for block placement
     if (event instanceof BlockPlaceEvent) {
      Block blockForLogging = ((BlockPlaceEvent) event).getBlock(); // Explicitly get Block
      coreProtectAPI.logPlacement(player.getName(), location, material, blockForLogging.getBlockData());
     } else if (event instanceof PlayerBucketEmptyEvent) {
      coreProtectAPI.logPlacement(player.getName(), location, material, Bukkit.createBlockData(material));
     }
     // Console log for alerted action
     plugin.getLogger().log(Level.INFO, player.getName() + " triggered an ALERT placing " + material.name() + " at " + location);
    }
    break;
   case ALLOW:
    // No action needed, the event proceeds as normal.
    // Console log for allowed action (optional, for debugging)
    plugin.getLogger().log(Level.FINE, player.getName() + " was ALLOWED to place " + material.name() + " at + location.toString());
    break;
  }
 }
}