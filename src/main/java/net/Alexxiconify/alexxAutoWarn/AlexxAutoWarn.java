package net.alexxiconify.alexxautowarn;

import com.google.common.base.Stopwatch;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class AlexxAutoWarn extends JavaPlugin {
    private static AutoWarnAPI api;
    private Settings settings;
    private ZoneManager zoneManager;
    private Object coreProtectAPI;
    private AutoWarnCommand autoWarnCommand;

    public static AutoWarnAPI getAPI() { return api; }

    @Override
    public void onEnable() {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        getLogger().info("Starting alexxautowarn...");

        this.settings = new Settings(this);
        this.zoneManager = new ZoneManager(this);

        saveDefaultConfig();
        reloadConfig();

        setupCoreProtect();

        this.autoWarnCommand = new AutoWarnCommand(this);

        // Register command
        Command command = new Command("autowarn") {
            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String @NotNull [] args) {
                return autoWarnCommand.onCommand(sender, this, commandLabel, args);
            }

            @Override
            public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String @NotNull [] args) throws IllegalArgumentException {
                return autoWarnCommand.onTabComplete(sender, this, alias, args);
            }
        };
        command.setDescription("Main command for the AutoWarn plugin.");
        command.setUsage("/<command> [subcommand] [args]");
        command.setAliases(Arrays.asList("aw"));
        
        getServer().getCommandMap().register("autowarn", command);
        getLogger().info("Command 'autowarn' registered successfully.");

        getServer().getPluginManager().registerEvents(new ZoneListener(this), this);

        api = new AutoWarnAPIImpl(this.zoneManager);

        long time = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS);
        getLogger().log(Level.INFO, "alexxautowarn enabled successfully in {0}ms.", time);
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling alexxautowarn...");
        if (this.zoneManager != null) {
            this.zoneManager.saveZones(false);
        }
        getLogger().info("alexxautowarn has been disabled.");
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        if (this.settings != null) this.settings.reload();
        if (this.zoneManager != null) this.zoneManager.loadZones();
    }

    private void setupCoreProtect() {
        final Plugin coreProtectPlugin = getServer().getPluginManager().getPlugin("CoreProtect");
        if (coreProtectPlugin == null) {
            getLogger().warning("CoreProtect not found! Logging features will be disabled.");
            this.coreProtectAPI = null;
            return;
        }

        try {
            Class<?> coreProtectClass = Class.forName("net.coreprotect.CoreProtect");
            if (!coreProtectClass.isInstance(coreProtectPlugin)) {
                getLogger().warning("CoreProtect plugin found but is not the expected type. Logging disabled.");
                this.coreProtectAPI = null;
                return;
            }

            Object cpApi = coreProtectClass.getMethod("getAPI").invoke(coreProtectPlugin);
            if (cpApi == null) {
                getLogger().warning("CoreProtect found, but the API is not available. Logging disabled.");
                this.coreProtectAPI = null;
                return;
            }

            Boolean isEnabled = (Boolean) cpApi.getClass().getMethod("isEnabled").invoke(cpApi);
            if (!Boolean.TRUE.equals(isEnabled)) {
                getLogger().warning("CoreProtect found, but the API is not enabled. Logging disabled.");
                this.coreProtectAPI = null;
                return;
            }

            Integer apiVersion = (Integer) cpApi.getClass().getMethod("APIVersion").invoke(cpApi);
            if (apiVersion < 9) {
                getLogger().warning("Unsupported CoreProtect version found (API v" + apiVersion + "). Please update CoreProtect to at least API v9. Logging disabled.");
                this.coreProtectAPI = null;
                return;
            }

            this.coreProtectAPI = cpApi;
            getLogger().info("Successfully hooked into CoreProtect API.");
        } catch (Exception e) {
            getLogger().warning("Failed to initialize CoreProtect API: " + e.getMessage() + ". Logging disabled.");
            this.coreProtectAPI = null;
        }
    }

    @NotNull public Settings getSettings() { return settings; }
    @NotNull public ZoneManager getZoneManager() { return zoneManager; }
    @Nullable public Object getCoreProtectAPI() { return coreProtectAPI; }
    @NotNull public AutoWarnCommand getAutoWarnCommand() { return autoWarnCommand; }
}