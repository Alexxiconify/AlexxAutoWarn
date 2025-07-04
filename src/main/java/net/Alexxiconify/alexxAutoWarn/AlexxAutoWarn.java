package net.Alexxiconify.alexxAutoWarn;

import com.google.common.base.Stopwatch;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

interface AutoWarnAPI {
    Zone getZoneAt ( Location loc );

    Collection < Zone > getZonesAt ( Location loc );

    Collection < Zone > getAllZones ( );

    void registerZoneActionListener ( Consumer < ZoneActionEvent > listener );

    void registerCustomAction ( String actionName , ZoneCustomAction action );

    boolean isActionAllowed ( Player player , Location loc , Material mat , String action );

    Zone getHighestPriorityZoneAt ( Location loc );

    void registerZoneEnterListener ( Consumer < ZoneEnterEvent > listener );

    void registerZoneLeaveListener ( Consumer < ZoneLeaveEvent > listener );

    void registerZonePriorityChangeListener ( Consumer < ZonePriorityChangeEvent > listener );
}

public final class AlexxAutoWarn extends JavaPlugin {
    private static AutoWarnAPI api;
    private Settings settings;
    private ZoneManager zoneManager;
    private Object coreProtectAPI;
    private AutoWarnCommand autoWarnCommand;

    public static AutoWarnAPI getAPI ( ) { return api; }

    @Override
    public void onDisable ( ) {
        this.getLogger ( ).info ( "Disabling AlexxAutoWarn..." );
        if ( this.zoneManager != null ) {
            this.zoneManager.saveZones ( false );
        }
        this.getLogger ( ).info ( "AlexxAutoWarn has been disabled." );
    }

    @Override
    public void reloadConfig ( ) {
        super.reloadConfig ( );
        if ( this.settings != null ) {
            this.settings.reload ( );
        }
        if ( this.zoneManager != null ) {
            this.zoneManager.loadZones ( );
        }
    }

    private void setupCoreProtect ( ) {
        final Plugin coreProtectPlugin = getServer ( ).getPluginManager ( ).getPlugin ( "CoreProtect" );
        if ( coreProtectPlugin == null ) {
            getLogger ( ).warning ( "CoreProtect not found! Logging features will be disabled." );
            this.coreProtectAPI = null;
            return;
        }

        try {
            // Use reflection to access CoreProtect classes
            Class<?> coreProtectClass = Class.forName ( "net.coreprotect.CoreProtect" );
            if ( !coreProtectClass.isInstance ( coreProtectPlugin ) ) {
                getLogger ( ).warning ( "CoreProtect plugin found but is not the expected type. Logging disabled." );
                this.coreProtectAPI = null;
                return;
            }

            // Get the API using reflection
            Object api = coreProtectClass.getMethod ( "getAPI" ).invoke ( coreProtectPlugin );
            if ( api == null ) {
                getLogger ( ).warning ( "CoreProtect found, but the API is not available. Logging disabled." );
                this.coreProtectAPI = null;
                return;
            }

            // Check if API is enabled
            Boolean isEnabled = ( Boolean ) api.getClass ( ).getMethod ( "isEnabled" ).invoke ( api );
            if ( !isEnabled ) {
                getLogger ( ).warning ( "CoreProtect found, but the API is not enabled. Logging disabled." );
                this.coreProtectAPI = null;
                return;
            }

            // Check API version
            Integer apiVersion = ( Integer ) api.getClass ( ).getMethod ( "APIVersion" ).invoke ( api );
            if ( apiVersion < 9 ) {
                getLogger ( ).warning ( "Unsupported CoreProtect version found (API v" + apiVersion +
                                          "). Please update CoreProtect to at least API v9. Logging disabled." );
                this.coreProtectAPI = null;
                return;
            }

            this.coreProtectAPI = api;
            getLogger ( ).info ( "Successfully hooked into CoreProtect API." );
        } catch ( Exception e ) {
            getLogger ( ).warning ( "Failed to initialize CoreProtect API: " + e.getMessage ( ) + ". Logging disabled." );
            this.coreProtectAPI = null;
        }
    }

    @NotNull
    public Settings getSettings ( ) { return settings; }

    @NotNull
    public ZoneManager getZoneManager ( ) { return zoneManager; }

    @Nullable
    public Object getCoreProtectAPI ( ) { return coreProtectAPI; }

    @NotNull
    public AutoWarnCommand getAutoWarnCommand ( ) { return autoWarnCommand; }

    @Override
    public void onEnable ( ) {
        final Stopwatch stopwatch = Stopwatch.createStarted ( );
        this.getLogger ( ).info ( "Starting AlexxAutoWarn..." );

        this.settings = new Settings ( this );
        this.zoneManager = new ZoneManager ( this );

        saveDefaultConfig ( );
        reloadConfig ( );

        setupCoreProtect ( );

        this.autoWarnCommand = new AutoWarnCommand ( this );

        PluginCommand command = this.getCommand ( "autowarn" );
        if ( command == null ) {
            getLogger ( ).severe ( "Command 'autowarn' not found in plugin.yml! Commands will not work." );
        } else {
            command.setExecutor ( this.autoWarnCommand );
            command.setTabCompleter ( this.autoWarnCommand );
            getLogger ( ).info ( "Command 'autowarn' registered successfully." );
        }

        this.getServer ( ).getPluginManager ( ).registerEvents ( new ZoneListener ( this ) , this );

        api = new AutoWarnAPIImpl ( this.zoneManager );

        long time = stopwatch.stop ( ).elapsed ( TimeUnit.MILLISECONDS );
        this.getLogger ( ).log ( Level.INFO , "AlexxAutoWarn enabled successfully in {0}ms." , time );
    }
}

class AutoWarnAPIImpl implements AutoWarnAPI {
    private final ZoneManager zoneManager;
    private final List < Consumer < ZoneActionEvent > > actionListeners = new CopyOnWriteArrayList <> ( );
    private final Map < String, ZoneCustomAction > customActions = new HashMap <> ( );
    private final List < Consumer < ZoneEnterEvent > > enterListeners = new CopyOnWriteArrayList <> ( );
    private final List < Consumer < ZoneLeaveEvent > > leaveListeners = new CopyOnWriteArrayList <> ( );
    private final List < Consumer < ZonePriorityChangeEvent > > priorityChangeListeners =
      new CopyOnWriteArrayList <> ( );

    public AutoWarnAPIImpl ( ZoneManager zoneManager ) {
        this.zoneManager = zoneManager;
    }

    @Override
    public Zone getZoneAt ( Location loc ) {
        return zoneManager.getZoneAt ( loc );
    }

    @Override
    public Collection < Zone > getZonesAt ( Location loc ) {
        return zoneManager.getZonesAt ( loc );
    }

    @Override
    public Collection < Zone > getAllZones ( ) {
        return zoneManager.getAllZones ( );
    }

    @Override
    public void registerZoneActionListener ( Consumer < ZoneActionEvent > listener ) {
        actionListeners.add ( listener );
    }

    @Override
    public void registerCustomAction ( String actionName , ZoneCustomAction action ) {
        customActions.put ( actionName.toUpperCase ( Locale.ROOT ) , action );
    }

    @Override
    public boolean isActionAllowed ( Player player , Location loc , Material mat , String action ) {
        Zone zone = getZoneAt ( loc );
        if ( zone == null ) return true;

        ZoneActionEvent event = new ZoneActionEvent ( player , zone , mat , action );
        for ( Consumer < ZoneActionEvent > listener : actionListeners ) {
            listener.accept ( event );
        }

        ZoneCustomAction custom = customActions.get ( action.toUpperCase ( Locale.ROOT ) );
        if ( custom != null ) {
            custom.execute ( player , zone , mat , action );
        }

        return !event.isCancelled ( );
    }

    @Override
    public Zone getHighestPriorityZoneAt ( Location loc ) {
        return zoneManager.getHighestPriorityZoneAt ( loc );
    }

    @Override
    public void registerZoneEnterListener ( Consumer < ZoneEnterEvent > listener ) {
        enterListeners.add ( listener );
    }

    @Override
    public void registerZoneLeaveListener ( Consumer < ZoneLeaveEvent > listener ) {
        leaveListeners.add ( listener );
    }

    @Override
    public void registerZonePriorityChangeListener ( Consumer < ZonePriorityChangeEvent > listener ) {
        priorityChangeListeners.add ( listener );
    }

    public void fireZoneEnter ( Player player , Zone zone ) {
        ZoneEnterEvent event = new ZoneEnterEvent ( player , zone );
        for ( var l : enterListeners ) l.accept ( event );
    }

    public void fireZoneLeave ( Player player , Zone zone ) {
        ZoneLeaveEvent event = new ZoneLeaveEvent ( player , zone );
        for ( var l : leaveListeners ) l.accept ( event );
    }

    public void fireZonePriorityChange ( Player player , Zone from , Zone to ) {
        ZonePriorityChangeEvent event = new ZonePriorityChangeEvent ( player , from , to );
        for ( var l : priorityChangeListeners ) l.accept ( event );
    }
}