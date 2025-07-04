package net.Alexxiconify.alexxAutoWarn;


import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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

class ZoneListener implements Listener {
    private final Settings settings;
    private final ZoneManager zoneManager;
    private final AlexxAutoWarn plugin;
    private final NamespacedKey wandKey;
    private final Object coreProtectAPI;

    public ZoneListener ( AlexxAutoWarn plugin ) {
        this.plugin = plugin;
        this.settings = plugin.getSettings ( );
        this.zoneManager = plugin.getZoneManager ( );
        this.coreProtectAPI = plugin.getCoreProtectAPI ( );
        this.wandKey = plugin.getAutoWarnCommand ( ).getWandKey ( );
    }

    @EventHandler ( priority = EventPriority.HIGH, ignoreCancelled = true )
    public void onBlockPlace ( BlockPlaceEvent event ) {
        handleAction ( event.getPlayer ( ) , event.getBlock ( ).getLocation ( ) , event.getBlock ( ).getType ( ) ,
                       event );
    }

    @EventHandler ( priority = EventPriority.HIGH, ignoreCancelled = true )
    public void onPlayerBucketEmpty ( PlayerBucketEmptyEvent event ) {
        Material placedMaterial = event.getBucket ( ) == Material.LAVA_BUCKET ? Material.LAVA : Material.WATER;
        handleAction ( event.getPlayer ( ) , event.getBlockClicked ( ).getLocation ( ) , placedMaterial , event );
    }

    @EventHandler ( priority = EventPriority.NORMAL )
    public void onPlayerInteract ( PlayerInteractEvent event ) {
        Player player = event.getPlayer ( );
        ItemStack handItem = event.getItem ( );

        if ( handItem != null && handItem.hasItemMeta ( ) ) {
            ItemMeta meta = handItem.getItemMeta ( );
            if ( meta.getPersistentDataContainer ( ).has ( wandKey , PersistentDataType.STRING ) ) {
                event.setCancelled ( true );
                Block clickedBlock = event.getClickedBlock ( );
                if ( clickedBlock == null ) return;

                Vector clickedBlockVector = clickedBlock.getLocation ( ).toVector ( ).toBlockVector ( );

                if ( event.getAction ( ) == Action.LEFT_CLICK_BLOCK ) {
                    plugin.getAutoWarnCommand ( ).setPos1 ( player.getUniqueId ( ) , clickedBlockVector );
                    player.sendMessage ( settings.getMessage (
                      "wand.pos1-set" ,
                      Placeholder.unparsed ( "coords" , formatLocation ( clickedBlock.getLocation ( ) ) )
                    ) );
                } else if ( event.getAction ( ) == Action.RIGHT_CLICK_BLOCK ) {
                    plugin.getAutoWarnCommand ( ).setPos2 ( player.getUniqueId ( ) , clickedBlockVector );
                    player.sendMessage ( settings.getMessage (
                      "wand.pos2-set" ,
                      Placeholder.unparsed ( "coords" , formatLocation ( clickedBlock.getLocation ( ) ) )
                    ) );
                }
                return;
            }
        }

        if ( settings.isMonitorChestAccess ( ) && event.getAction ( ) == Action.RIGHT_CLICK_BLOCK ) {
            Block clickedBlock = event.getClickedBlock ( );
            if ( clickedBlock != null && clickedBlock.getState ( ) instanceof Container ) {
                handleAction ( player , clickedBlock.getLocation ( ) , clickedBlock.getType ( ) , event );
            }
        }
    }

    private void handleAction ( Player player , Location location , Material material , Cancellable event ) {
        if ( player.hasPermission ( "autowarn.bypass" ) ) {
            return;
        }

        Zone zone = zoneManager.getZoneAt ( location );
        if ( zone != null ) {
            ZoneActionEvent actionEvent = new ZoneActionEvent ( player , zone , material , "INTERACT" );
            AutoWarnAPI api = AlexxAutoWarn.getAPI ( );
            if ( api != null ) {
                api.isActionAllowed ( player , location , material , "INTERACT" );
                if ( actionEvent.isCancelled ( ) ) {
                    event.setCancelled ( true );
                    return;
                }
            }
        }

        if ( settings.getGloballyBannedMaterials ( ).contains ( material ) ) {
            processAction ( Zone.Action.DENY , player , location , material , "Global" , event );
            return;
        }

        if ( zone != null ) {
            Zone.Action action = zone.getActionFor ( material );
            processAction ( action , player , location , material , zone.getName ( ) , event );
        }
    }

    private void processAction ( Zone.Action action , Player player , Location loc , Material mat , String zoneName ,
                                 Cancellable event ) {
        var placeholders = new TagResolver[] {
          Placeholder.unparsed ( "player" , player.getName ( ) ) ,
          Placeholder.unparsed ( "material" , mat.name ( ).toLowerCase ( ).replace ( '_' , ' ' ) ) ,
          Placeholder.unparsed ( "zone" , zoneName ) ,
          Placeholder.unparsed ( "location" , formatLocation ( loc ) )
        };

        String logMessage = String.format (
          "%s performed %s with %s in %s at %s" ,
          player.getName ( ) , action.name ( ) , mat.name ( ) , zoneName , formatLocation ( loc )
        );

        switch ( action ) {
            case DENY:
                event.setCancelled ( true );
                player.sendMessage ( settings.getMessage ( "action.denied" , placeholders ) );
                settings.log ( Level.INFO , "[DENIED] " + logMessage );
                logToCoreProtect ( player.getName ( ) , loc , mat );
                break;
            case ALERT:
             plugin.getServer ( ).getOnlinePlayers ( ).forEach ( p -> {
                    if ( p.hasPermission ( "autowarn.notify" ) ) {
                        p.sendMessage ( settings.getMessage ( "action.alert" , placeholders ) );
                    }
                } );
                settings.log ( Level.INFO , "[ALERT] " + logMessage );
                logToCoreProtect ( player.getName ( ) , loc , mat );
                break;
            case ALLOW:
                if ( settings.isDebugLogAllowedActions ( ) ) {
                    settings.log ( Level.INFO , "[ALLOWED] " + logMessage );
                    logToCoreProtect ( player.getName ( ) , loc , mat );
                }
                break;
        }
    }

    private void logToCoreProtect ( String user , Location location , Material material ) {
        if ( coreProtectAPI != null ) {
            try {
                coreProtectAPI.getClass ( ).getMethod ( "logPlacement" , String.class , Location.class , Material.class , Object.class )
                    .invoke ( coreProtectAPI , user , location , material , null );
            } catch ( Exception e ) {
                // CoreProtect logging failed, but we don't want to break the plugin
                plugin.getLogger ( ).fine ( "Failed to log to CoreProtect: " + e.getMessage ( ) );
            }
        }
    }

    private String formatLocation ( Location loc ) {
        return String.format (
          "%s: %d, %d, %d" , loc.getWorld ( ).getName ( ) ,
          loc.getBlockX ( ) , loc.getBlockY ( ) , loc.getBlockZ ( )
        );
    }
}