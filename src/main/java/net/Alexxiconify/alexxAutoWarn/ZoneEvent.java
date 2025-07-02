package net.Alexxiconify.alexxAutoWarn;

import net.coreprotect.CoreProtectAPI;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.logging.Level;

public interface ZoneEvent {
    Player getPlayer ( );

    Zone getZone ( );
}

class ZoneEnterEvent implements ZoneEvent {
    private final Player player;
    private final Zone zone;

    public ZoneEnterEvent ( Player player , Zone zone ) {
        this.player = player;
        this.zone = zone;
    }

    public Player getPlayer ( ) { return player; }

    public Zone getZone ( ) { return zone; }
}

class ZoneLeaveEvent implements ZoneEvent {
    private final Player player;
    private final Zone zone;

    public ZoneLeaveEvent ( Player player , Zone zone ) {
        this.player = player;
        this.zone = zone;
    }

    public Player getPlayer ( ) { return player; }

    public Zone getZone ( ) { return zone; }
}

class ZonePriorityChangeEvent implements ZoneEvent {
    private final Player player;
    private final Zone from;
    private final Zone to;

    public ZonePriorityChangeEvent ( Player player , Zone from , Zone to ) {
        this.player = player;
        this.from = from;
        this.to = to;
    }

    public Player getPlayer ( ) { return player; }

    public Zone getZone ( ) { return to; }

    public Zone getFromZone ( ) { return from; }

    public Zone getToZone ( ) { return to; }
}

interface ZoneCustomAction {
    void execute ( Player player , Zone zone , Material mat , String context );
}

class ZoneActionEvent {
    private final Player player;
    private final Zone zone;
    private final Material material;
    private final String action;
    private boolean cancelled = false;

    public ZoneActionEvent ( Player player , Zone zone , Material material , String action ) {
        this.player = player;
        this.zone = zone;
        this.material = material;
        this.action = action;
    }

    public Player getPlayer ( ) { return player; }

    public Zone getZone ( ) { return zone; }

    public Material getMaterial ( ) { return material; }

    public String getAction ( ) { return action; }

    public boolean isCancelled ( ) { return cancelled; }

    public void setCancelled ( boolean cancelled ) { this.cancelled = cancelled; }
}

/**
 * Handles all event listeners for the AutoWarn plugin.
 * This includes monitoring block placements and player interactions to enforce zone rules and wand functionality.
 */
class ZoneListener implements Listener {

    private final Settings settings;
    private final ZoneManager zoneManager;
    private final AlexxAutoWarn plugin;
    private final NamespacedKey wandKey;
    private final CoreProtectAPI coreProtectAPI;

    /**
     * Constructor for ZoneListener.
     *
     * @param plugin The main AlexxAutoWarn plugin instance.
     */
    public ZoneListener ( AlexxAutoWarn plugin ) {
        this.plugin = plugin;
        this.settings = plugin.getSettings ( );
        this.zoneManager = plugin.getZoneManager ( );
        this.coreProtectAPI = plugin.getCoreProtectAPI ( );
        this.wandKey = plugin.getAutoWarnCommand ( ).getWandKey ( );
    }

    @EventHandler ( priority = EventPriority.HIGH, ignoreCancelled = true )
    public void onBlockPlace ( BlockPlaceEvent event ) {
        handleAction (
          event.getPlayer ( ) , event.getBlock ( ).getLocation ( ) , event.getBlock ( ).getType ( ) ,
          event
        );
    }

    @EventHandler ( priority = EventPriority.HIGH, ignoreCancelled = true )
    public void onPlayerBucketEmpty ( PlayerBucketEmptyEvent event ) {
        Material placedMaterial = event.getBucket ( ) == Material.LAVA_BUCKET ? Material.LAVA : Material.WATER;
        handleAction ( event.getPlayer ( ) , event.getBlockClicked ( ).getLocation ( ) , placedMaterial , event );
    }

    @EventHandler ( priority = EventPriority.NORMAL ) // Not ignoring cancelled to handle wand clicks
    public void onPlayerInteract ( PlayerInteractEvent event ) {
        Player player = event.getPlayer ( );
        ItemStack handItem = event.getItem ( );

        // --- Wand Functionality ---
        // Check if the item in hand is the AutoWarn wand
        if ( handItem != null && handItem.hasItemMeta ( ) ) {
            ItemMeta meta = handItem.getItemMeta ( );
            // FIX: Check for PersistentDataType.STRING as set in AutoWarnCommand
            if ( meta.getPersistentDataContainer ( ).has ( wandKey , PersistentDataType.STRING ) ) {
                event.setCancelled ( true ); // Always cancel event when using the wand to prevent unintended block
                // interactions
                Block clickedBlock = event.getClickedBlock ( );
                if ( clickedBlock == null ) return; // Ensure a block was actually clicked

                // Store the block location as a Vector
                // Using .toBlockVector() to ensure coordinates are integer-based for block selection
                Vector clickedBlockVector = clickedBlock.getLocation ( ).toVector ( ).toBlockVector ( );

                // Handle left-click for pos1 and right-click for pos2
                if ( event.getAction ( ) == Action.LEFT_CLICK_BLOCK ) {
                    plugin.getAutoWarnCommand ( ).setPos1 ( player.getUniqueId ( ) , clickedBlockVector ); // Pass
                    // Vector
                    player.sendMessage ( settings.getMessage (
                      "wand.pos1-set" , Placeholder.unparsed (
                        "coords" ,
                        formatLocation ( clickedBlock.getLocation ( ) )
                      )
                    ) );
                } else if ( event.getAction ( ) == Action.RIGHT_CLICK_BLOCK ) {
                    plugin.getAutoWarnCommand ( ).setPos2 ( player.getUniqueId ( ) , clickedBlockVector ); // Pass
                    // Vector
                    player.sendMessage ( settings.getMessage (
                      "wand.pos2-set" , Placeholder.unparsed (
                        "coords" ,
                        formatLocation ( clickedBlock.getLocation ( ) )
                      )
                    ) );
                }
                return; // Stop processing further if the wand was used
            }
        }

        // --- Container Access Monitoring ---
        // Check if chest access monitoring is enabled and the action is a right-click on a container
        if ( settings.isMonitorChestAccess ( ) && event.getAction ( ) == Action.RIGHT_CLICK_BLOCK ) {
            Block clickedBlock = event.getClickedBlock ( );
            // Ensure the clicked block is a container (e.g., chest, furnace, barrel)
            if ( clickedBlock != null && clickedBlock.getState ( ) instanceof Container ) {
                handleAction ( player , clickedBlock.getLocation ( ) , clickedBlock.getType ( ) , event );
            }
        }
    }

    /**
     * Centralized method to handle a player action at a specific location.
     * Applies global bans and zone-specific rules.
     *
     * @param player   The player performing the action.
     * @param location The location where the action occurred.
     * @param material The material involved in the action.
     * @param event    The cancellable event associated with the action.
     */
    private void handleAction ( Player player , Location location , Material material , Cancellable event ) {
        // Bypass all checks if the player has the bypass permission
        if ( player.hasPermission ( "autowarn.bypass" ) ) {
            return;
        }

        // Fire ZoneActionEvent for plugin hooks
        Zone zone = zoneManager.getZoneAt ( location );
        if ( zone != null ) {
            ZoneActionEvent actionEvent = new ZoneActionEvent ( player , zone , material , "INTERACT" );
            AlexxAutoWarn.getAPI ( ).registerZoneActionListener ( e -> { } ); // No-op registration to ensure API is
            // initialized
            // Fire event to all listeners
            AlexxAutoWarn.getAPI ( ).isActionAllowed ( player , location , material , "INTERACT" );
            if ( actionEvent.isCancelled ( ) ) {
                event.setCancelled ( true );
                return;
            }
        }

        // Check globally banned materials first
        if ( settings.getGloballyBannedMaterials ( ).contains ( material ) ) {
            processAction ( Zone.Action.DENY , player , location , material , "Global" , event );
            return;
        }

        // Check for zone-specific rules if the location is within a defined zone
        if ( zone != null ) {
            Zone.Action action = zone.getActionFor ( material );
            processAction ( action , player , location , material , zone.getName ( ) , event );
        }
    }

    /**
     * Processes the determined action (DENY, ALERT, ALLOW), sends messages to the player,
     * logs the action, and integrates with CoreProtect if available.
     *
     * @param action   The action to perform (DENY, ALERT, ALLOW).
     * @param player   The player involved in the action.
     * @param loc      The location of the action.
     * @param mat      The material involved.
     * @param zoneName The name of the zone (or "Global" for global bans).
     * @param event    The cancellable event.
     */
    private void processAction (
      Zone.Action action , Player player , Location loc , Material mat , String zoneName ,
      Cancellable event
    ) {
        // Prepare placeholders for MiniMessage messages
        var placeholders = new TagResolver[] {
          Placeholder.unparsed ( "player" , player.getName ( ) ) ,
          Placeholder.unparsed ( "material" , mat.name ( ).toLowerCase ( ).replace ( '_' , ' ' ) ) ,
          Placeholder.unparsed ( "zone" , zoneName ) ,
          Placeholder.unparsed ( "location" , formatLocation ( loc ) )
        };

        // Create a log message for the console/plugin logs
        String logMessage = String.format (
          "%s performed %s with %s in %s at %s" , player.getName ( ) , action.name ( ) ,
          mat.name ( ) , zoneName , formatLocation ( loc )
        );

        switch ( action ) {
            case DENY:
                event.setCancelled ( true ); // Cancel the event (e.g., block placement)
                player.sendMessage ( settings.getMessage ( "action.denied" , placeholders ) ); // Send denial message
                // to player
                settings.log ( Level.INFO , "[DENIED] " + logMessage ); // Log to plugin console
                logToCoreProtect ( player.getName ( ) , loc , mat ); // Log to CoreProtect
                break;
            case ALERT:
                // Allow the action, but alert staff with permission
                Bukkit.getOnlinePlayers ( ).forEach ( p -> {
                    if ( p.hasPermission ( "autowarn.notify" ) ) {
                        p.sendMessage ( settings.getMessage ( "action.alert" , placeholders ) ); // Send alert
                        // message to authorized staff
                    }
                } );
                settings.log ( Level.INFO , "[ALERT] " + logMessage ); // Log to plugin console
                logToCoreProtect ( player.getName ( ) , loc , mat ); // Log to CoreProtect
                break;
            case ALLOW:
                // If allowed actions logging is enabled, log the action
                if ( settings.isDebugLogAllowedActions ( ) ) {
                    settings.log ( Level.INFO , "[ALLOWED] " + logMessage );
                    logToCoreProtect ( player.getName ( ) , loc , mat ); // Log to CoreProtect
                }
                break;
        }
    }

    /**
     * Logs an action to the CoreProtect API if it's available.
     *
     * @param user     The user performing the action.
     * @param location The location of the action.
     * @param material The material involved.
     */
    private void logToCoreProtect ( String user , Location location , Material material ) {
        if ( coreProtectAPI != null ) {
            coreProtectAPI.logPlacement ( user , location , material , null ); // Logs a block placement/interaction
        }
    }

    /**
     * Formats a Location object into a human-readable string (World: X, Y, Z).
     *
     * @param loc The Location to format.
     * @return A formatted string.
     */
    private String formatLocation ( Location loc ) {
        return String.format (
          "%s: %d, %d, %d" , loc.getWorld ( ).getName ( ) , loc.getBlockX ( ) , loc.getBlockY ( ) ,
          loc.getBlockZ ( )
        );
    }
}