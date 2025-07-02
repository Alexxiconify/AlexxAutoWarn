package net.Alexxiconify.alexxAutoWarn;

import com.google.common.base.Stopwatch;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
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

// API Interface and Implementation
interface AutoWarnAPI {
    Zone getZoneAt ( Location loc );

    Collection < Zone > getZonesAt ( Location loc ); // For nested/prioritized zones

    Collection < Zone > getAllZones ( );

    void registerZoneActionListener ( Consumer < ZoneActionEvent > listener );

    void registerCustomAction ( String actionName , ZoneCustomAction action );

    boolean isActionAllowed ( Player player , Location loc , Material mat , String action );

    Zone getHighestPriorityZoneAt ( Location loc );

    void registerZoneEnterListener ( Consumer < ZoneEnterEvent > listener );

    void registerZoneLeaveListener ( Consumer < ZoneLeaveEvent > listener );

    void registerZonePriorityChangeListener ( Consumer < ZonePriorityChangeEvent > listener );
}

/**
 * Main class for the AlexxAutoWarn plugin.
 * Handles plugin lifecycle, configuration loading, CoreProtect integration,
 * and registration of commands and event listeners.
 */
public final class AlexxAutoWarn extends JavaPlugin {

    private static AutoWarnAPI api;
    private Settings settings;
    private ZoneManager zoneManager;
    private CoreProtectAPI coreProtectAPI;
    private AutoWarnCommand autoWarnCommand; // Added field to hold the command instance

    public static AutoWarnAPI getAPI ( ) { return api; }

    @Override
    public void onDisable ( ) {
        this.getLogger ( ).info ( "Disabling AlexxAutoWarn..." );
        // Synchronously save zones on disable to ensure data is written before shutdown
        if ( this.zoneManager != null ) {
            this.zoneManager.saveZones ( false ); // Perform a blocking save on disable
        }
        this.getLogger ( ).info ( "AlexxAutoWarn has been disabled." );
    }

    /**
     * Reloads the plugin's configuration and all associated components.
     * This method overrides JavaPlugin's reloadConfig() and should be used
     * as the primary entry point for plugin reloads.
     */
    @Override
    public void reloadConfig ( ) {
        super.reloadConfig ( ); // Call the parent method to reload the underlying configuration file
        // Reload custom settings and zones after the base config has been reloaded.
        // These checks are now guaranteed to be non-null due to initialization order in onEnable.
        if ( this.settings != null ) {
            this.settings.reload ( ); // Tell your custom Settings class to reload its cached data
        }
        if ( this.zoneManager != null ) {
            this.zoneManager.loadZones ( ); // Reload zones after config is reloaded
        }
    }

    /**
     * Sets up the CoreProtect API hook.
     * Checks if CoreProtect plugin is present, enabled, and compatible.
     */
    private void setupCoreProtect ( ) {
        final Plugin coreProtectPlugin = getServer ( ).getPluginManager ( ).getPlugin ( "CoreProtect" );
        if ( !( coreProtectPlugin instanceof CoreProtect ) ) {
            getLogger ( ).warning ( "CoreProtect not found! Logging features will be disabled." );
            this.coreProtectAPI = null;
            return;
        }

        final CoreProtectAPI api = ( ( CoreProtect ) coreProtectPlugin ).getAPI ( );
        if ( !api.isEnabled ( ) ) {
            getLogger ( ).warning ( "CoreProtect found, but the API is not enabled. Logging disabled." );
            this.coreProtectAPI = null;
            return;
        }

        // Check for a compatible CoreProtect version (API Version 9 is minimum for modern features)
        if ( api.APIVersion ( ) < 9 ) {
            getLogger ( ).warning ( "Unsupported CoreProtect version found (API v" + api.APIVersion ( ) + "). Please " +
                                      "update CoreProtect to at least API v9. Logging disabled." );
            this.coreProtectAPI = null;
            return;
        }

        this.coreProtectAPI = api;
        getLogger ( ).info ( "Successfully hooked into CoreProtect API." );
    }

    // --- Getters ---

    /**
     * Provides access to the plugin's settings manager.
     *
     * @return The Settings instance.
     */
    @NotNull
    public Settings getSettings ( ) {
        return settings;
    }

    /**
     * Provides access to the plugin's zone manager.
     * @return The ZoneManager instance.
     */
    @NotNull
    public ZoneManager getZoneManager ( ) {
        return zoneManager;
    }

    /**
     * Provides access to the CoreProtect API instance.
     * @return The CoreProtectAPI instance, or null if not hooked.
     */
    @Nullable
    public CoreProtectAPI getCoreProtectAPI ( ) {
        return coreProtectAPI;
    }

    /**
     * Provides access to the plugin's command instance.
     *
     * @return The AutoWarnCommand instance.
     */
    @NotNull
    public AutoWarnCommand getAutoWarnCommand ( ) {
        return autoWarnCommand;
    }

    @Override
    public void onEnable ( ) {
        final Stopwatch stopwatch = Stopwatch.createStarted ( );
        this.getLogger ( ).info ( "Starting AlexxAutoWarn..." );

        // FIX: Ensure Settings and ZoneManager are initialized BEFORE reloadConfig()
        // This ensures 'settings' and 'zoneManager' objects exist when reloadConfig() calls their reload/load methods.
        this.settings = new Settings ( this );
        this.zoneManager = new ZoneManager ( this );

        // Ensure default config is saved and loaded
        saveDefaultConfig ( );
        // Reload config. This will now correctly call settings.reload() and zoneManager.loadZones()
        // because 'settings' and 'zoneManager' are already initialized.
        reloadConfig ( );

        // Asynchronously load zones from config (this call in onEnable becomes redundant if reloadConfig() does it)
        // However, keep it here for explicit asynchronous loading after the initial sync load via reloadConfig()
        // or if zones need to be reloaded after some initial sync setup in onEnable.
        // No, it's better to let reloadConfig() handle the initial load,
        // and only call zoneManager.loadZones() directly if it's a separate async operation later.
        // For initial setup, reloadConfig() handles it. Removed this redundant call.
        // this.zoneManager.loadZones().thenRun(() -> {
        //     this.getLogger().info("All zones loaded successfully.");
        // });

        // Setup CoreProtect API hook
        setupCoreProtect ( );

        // Initialize and register commands
        this.autoWarnCommand = new AutoWarnCommand ( this ); // Initialize the command instance

        // Get the command from plugin.yml and set its executor and tab completer.
        // Add explicit null check and logging for command registration.
        PluginCommand command = this.getCommand ( "autowarn" );
        if ( command == null ) {
            getLogger ( ).severe ( "Command 'autowarn' not found in plugin.yml! Commands will not work. Ensure " +
                                     "'autowarn' is defined in your plugin.yml under the 'commands' section." );
        } else {
            command.setExecutor ( this.autoWarnCommand ); // Set the executor to our specific instance
            command.setTabCompleter ( this.autoWarnCommand ); // Set the tab completer to our specific instance
            getLogger ( ).info ( "Command 'autowarn' registered successfully." );
        }

        // Register event listeners
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