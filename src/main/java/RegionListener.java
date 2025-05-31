package net.Alexxiconify.alexxAutoWarn.region;

import net.Alexxiconify.alexxAutoWarn.AlexxAutoWarn;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;

public class RegionListener implements Listener {

 private final AlexxAutoWarn plugin;

 public RegionListener(AlexxAutoWarn plugin) {
  this.plugin = plugin;
 }

 // --- Event: Block Placement ---
 @EventHandler
 public void onBlockPlace(BlockPlaceEvent event) {
  Player player = event.getPlayer();
  Block placedBlock = event.getBlockPlaced();

  // Allow players with bypass permission
  if (player.hasPermission("alexxautowarn.region.bypass")) {
   return;
  }

  // Iterate through all defined regions
  for (Region region : plugin.getRegions().values()) {
   // Check if the placed block is within this region
   if (region.contains(placedBlock.getLocation())) {
    // Check if this material is banned for placement in this region
    if (region.getBannedBlockPlacement().contains(placedBlock.getType())) {
     player.sendMessage(plugin.getMessage("region-banned-block-placement")
             .replace("{block}", placedBlock.getType().name())
             .replace("{region}", region.getName()));
     event.setCancelled(true); // Cancel the block placement
     return; // Only check one region per event
    }
   }
  }
 }

 // --- Event: Player Interact (for chests and item usage) ---
 @EventHandler
 public void onPlayerInteract(PlayerInteractEvent event) {
  Player player = event.getPlayer();
  Material itemInHand = player.getInventory().getItemInMainHand().getType(); // Item being held
  Block clickedBlock = event.getClickedBlock(); // Block being clicked

  // Allow players with bypass permission
  if (player.hasPermission("alexxautowarn.region.bypass")) {
   return;
  }

  // --- Handle Chest Interactions ---
  if (event.getAction().name().contains("RIGHT_CLICK") && clickedBlock != null) {
   InventoryHolder holder = null;
   if (clickedBlock.getState() instanceof Chest) {
    holder = ((Chest) clickedBlock.getState());
   } else if (clickedBlock.getState() instanceof org.bukkit.block.Container) { // Covers all containers like barrels, shulker boxes, etc.
    holder = (org.bukkit.block.Container) clickedBlock.getState();
   }

   if (holder != null) { // It's a container
    // Handle double chests specifically
    if (holder instanceof DoubleChest) {
     holder = ((DoubleChest) holder).getRightSide(); // Or getLeftSide(), doesn't matter for location check
    }

    // Check if the container is within any banned region
    for (Region region : plugin.getRegions().values()) {
     if (region.contains(clickedBlock.getLocation())) {
      if (region.isBanChestInteraction()) {
       player.sendMessage(plugin.getMessage("region-banned-chest-interaction")
               .replace("{region}", region.getName()));
       event.setCancelled(true); // Cancel the interaction
       return; // Only check one region per event
      }
     }
    }
   }
  }

  // --- Handle Item Usage (e.g., eating, ender pearl throws, potion drinking) ---
  // This generally applies to right-clicks with an item.
  if (event.getAction().name().contains("RIGHT_CLICK") && itemInHand != Material.AIR) {
   // Check if the item is in the banned usage list for any region
   for (Region region : plugin.getRegions().values()) {
    if (region.contains(player.getLocation())) { // Check if player is *in* the region
     if (region.getBannedItemUsage().contains(itemInHand)) {
      player.sendMessage(plugin.getMessage("region-banned-item-usage")
              .replace("{item}", itemInHand.name())
              .replace("{region}", region.getName()));
      event.setCancelled(true); // Cancel the item usage
      return; // Only check one region per event
     }
    }
   }
  }
 }

 // --- Event: Player Quit (clean up selections) ---
 @EventHandler
 public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
  plugin.getPlayerSelections().remove(event.getPlayer().getUniqueId());
 }
}