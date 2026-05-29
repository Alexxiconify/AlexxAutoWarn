package net.alexxiconify.alexxautowarn;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.logging.Level;

public class Settings {
    private final AlexxAutoWarn plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private boolean monitorChestAccess;
    private boolean debugLogAllowedActions;
    private Component pluginPrefix = miniMessage.deserialize("<gray>[<gold>AutoWarn</gold>]</gray> ");
    private final Map<String, String> messages = new HashMap<>();
    private Set<Material> globallyBannedMaterials = EnumSet.noneOf(Material.class);

    public Settings(AlexxAutoWarn plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        monitorChestAccess = config.getBoolean("settings.monitor-chest-access", false);
        debugLogAllowedActions = config.getBoolean("settings.debug-log-allowed-actions", false);
        pluginPrefix = miniMessage.deserialize(config.getString("messages.plugin-prefix", "<gray>[<gold>AutoWarn</gold>]</gray> "));

        // Cache raw message strings to avoid repeated config lookups during runtime.
        messages.clear();
        org.bukkit.configuration.ConfigurationSection sec = config.getConfigurationSection("messages");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                String raw = sec.getString(key);
                if (raw != null) {
                    messages.put(key, raw);
                }
            }
        }

        globallyBannedMaterials = EnumSet.noneOf(Material.class);
        var logger = plugin.getLogger();
        for (String materialName : config.getStringList("settings.globally-banned-materials")) {
            Material material = Material.matchMaterial(materialName.trim());
            if (material == null) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.warning(String.format("Invalid globally banned material '%s' found in config.yml. Skipping.", materialName));
                }
            } else {
                globallyBannedMaterials.add(material);
            }
        }
        logger.log(Level.INFO, "Reloaded {0} globally banned materials.", globallyBannedMaterials.size());
    }

    public Component getMessage(@NotNull String key, TagResolver... resolvers) {
        String raw = messages.get(key);
        if (raw == null) {
            raw = "<red>Message not found: " + key + "</red>";
        }
        return pluginPrefix.append(miniMessage.deserialize(raw, resolvers));
    }

    public void log(Level level, String message) {
        if (plugin.getLogger().isLoggable(level)) {
            plugin.getLogger().log(level, miniMessage.stripTags(message));
        }
    }

    public boolean isMonitorChestAccess() {
        return monitorChestAccess;
    }

    public boolean isDebugLogAllowedActions() {
        return debugLogAllowedActions;
    }

    @NotNull
    public Set<Material> getGloballyBannedMaterials() {
        return Collections.unmodifiableSet(globallyBannedMaterials);
    }

    public void addGloballyBannedMaterial(@NotNull Material material) {
        if (globallyBannedMaterials.add(material)) {
            saveGloballyBannedMaterials();
        }
    }

    public void removeGloballyBannedMaterial(@NotNull Material material) {
        if (globallyBannedMaterials.remove(material)) {
            saveGloballyBannedMaterials();
        }
    }

    private void saveGloballyBannedMaterials() {
        List<String> bannedNames = globallyBannedMaterials.stream().map(Material::name).sorted().toList();
        plugin.getConfig().set("settings.globally-banned-materials", bannedNames);
        plugin.saveConfig();
    }
}