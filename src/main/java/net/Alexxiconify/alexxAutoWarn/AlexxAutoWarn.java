package net.alexxiconify.alexxautowarn;

import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class AlexxAutoWarn extends JavaPlugin {
    private static AlexxAutoWarn instance;
    private static AutoWarnAPI api;

    private final Settings settings = new Settings(this);
    private final ZoneManager zoneManager = new ZoneManager(this);
    private final NamespacedKey wandKey = new NamespacedKey(this, "selection_wand");

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        settings.reload();
        zoneManager.loadZones();
        api = new AutoWarnAPIImpl(zoneManager);

        PluginCommand command = Objects.requireNonNull(getCommand("autowarn"), "Command 'autowarn' is missing from plugin.yml");
        AutoWarnCommand autoWarnCommand = new AutoWarnCommand(this);
        command.setExecutor(autoWarnCommand);
        command.setTabCompleter(autoWarnCommand);

        getServer().getPluginManager().registerEvents(new ZoneListener(this), this);
        getLogger().info("AutoWarn enabled.");
    }

    @Override
    public void onDisable() {
        zoneManager.saveZones(true);
        api = null;
        instance = null;
    }

    public void reloadPlugin() {
        reloadConfig();
        settings.reload();
        zoneManager.loadZones();
    }

    public static AlexxAutoWarn getInstance() {
        return instance;
    }

    public static AutoWarnAPI getAPI() {
        return api;
    }

    public Settings getSettings() {
        return settings;
    }

    public ZoneManager getZoneManager() {
        return zoneManager;
    }

    public Object getCoreProtectAPI() {
        return null;
    }

    public NamespacedKey getWandKey() {
        return wandKey;
    }
}