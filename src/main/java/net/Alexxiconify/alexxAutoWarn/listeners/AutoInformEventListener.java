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
  this.coreProtectAPI = plugin.getCoreProtectAPI();
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
  if (player.hasPermission("alexxautowarn.bypass")) {
   return;
  }

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
  if (player.hasPermission("alexxautowarn.bypass")) {
   return;
  }

  Block affectedBlock = event.getBlockClicked().getRelative(event.getBlockFace());
  Location location = affectedBlock.getLocation();
  Material emptiedMaterial = event.getBucket();

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
    Block clickedBlock = event.getClickedBlock();
    Location loc = clickedBlock.getLocation();
    event.setCancelled(true); // Prevent block breaking with wand
    messageUtil.sendMessage(player, "wand-pos2-selected", "{location}", String.format("%.0f, %.0f, %.0f", loc.getX(), loc.getY(), loc.getZ()));
   } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
    Block clickedBlock = event.getClickedBlock();
    Location loc = clickedBlock.getLocation();
    event.setCancelled(true); // Prevent block interaction (e.g., opening chest) with wand
    messageUtil.sendMessage(player, "wand-pos1-selected", "{location}", String.format("%.0f, %.0f, %.0f", loc.getX(), loc.getY(), loc.getZ()));
   }
   return;
  }

  // --- Container Monitoring ---
  if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null && plugin.isMonitorChestAccess()) { // Check global setting
   Block clickedBlock = event.getClickedBlock();
   if (clickedBlock.getState() instanceof Container) {
    Location location = clickedBlock.getLocation();
    AutoInformZone zone = zoneManager.getZoneAtLocation(location);

    if (zone != null) {
     ZoneAction effectiveAction = getEffectiveAction(location, clickedBlock.getType());

     if (effectiveAction == ZoneAction.ALERT && player.hasPermission("alexxautowarn.alert.receive")) {
      messageUtil.sendAlert(player, "alert-container-access",
              "{player}", player.getName(),
              "{material}", clickedBlock.getType().name(),
              "{zone_name}", zone.getName(),
              "{world}", location.getWorld().getName(),
              "{x}", String.valueOf(location.getBlockX()),
              "{y}", String.valueOf(location.getBlockY()),
              "{z}", String.valueOf(location.getBlockZ()));
                        /*
                        // Temporarily commented out CoreProtect logging
                        if (coreProtectAPI != null) {
                            coreProtectAPI.logPlacement(player.getName(), location, clickedBlock.getType(), null);
                            plugin.getLogger().log(Level.INFO, "Container access logged to CoreProtect for " + player.getName() + " at " + location);
                        }
                        */
     } else if (effectiveAction == ZoneAction.DENY) {
      event.setCancelled(true); // Deny chest access
      messageUtil.sendMessage(player, "action-denied-container-access",
              "{material}", clickedBlock.getType().name(),
              "{zone_name}", zone.getName(),
              "{world}", location.getWorld().getName(),
              "{x}", String.valueOf(location.getBlockX()),
              "{y}", String.valueOf(location.getBlockY()),
              "{z}", String.valueOf(location.getBlockZ()));
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
  * @param location The location where the action is occurring.
  * @param material The material involved in the action.
  * @return The determined ZoneAction.
  */
 private ZoneAction getEffectiveAction(Location location, Material material) {
  // 1. Check for specific zone material action
  AutoInformZone zone = zoneManager.getZoneAtLocation(location);
  if (zone != null) {
   if (zone.getMaterialSpecificActions().containsKey(material)) {
    return zone.getMaterialSpecificActions().get(material);
   }
   // 2. If no specific material action, use the zone's default action
   return zone.getDefaultAction();
  }

  // 3. If not in a zone, check global banned materials list
  if (zoneManager.isGloballyBanned(material)) {
   return ZoneAction.DENY; // Globally banned materials are always denied outside of specific zone overrides
  }

  // 4. Default to ALLOW if none of the above conditions are met
  return ZoneAction.ALLOW;
 }

 /**
  * Handles the outcome of a zone action (DENY, ALERT, ALLOW).
  *
  * @param player The player performing the action.
  * @param location The location of the action.
  * @param material The material involved in the action.
  * @param action The ZoneAction to apply.
  * @param event The event to potentially cancel (must be Cancellable).
  */
 private void handleZoneAction(Player player, Location location, Material material, ZoneAction action, Cancellable event) {
  AutoInformZone zone = zoneManager.getZoneAtLocation(location);
  String zoneName = (zone != null) ? zone.getName() : "N/A";

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
                /*
                // Temporarily commented out CoreProtect logging
                if (coreProtectAPI != null) {
                    coreProtectAPI.logPlacement(player.getName(), location, material, null);
                }
                */
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
                /*
                // Temporarily commented out CoreProtect logging
                if (coreProtectAPI != null) {
                    coreProtectAPI.logPlacement(player.getName(), location, material, null);
                }
                */
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
}