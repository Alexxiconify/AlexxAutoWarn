package net.alexxiconify.alexxautowarn;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class Settings {
    private final AlexxAutoWarn plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private boolean monitorChestAccess;
    private boolean debugLogAllowedActions;
    private Component pluginPrefix = miniMessage.deserialize("<gray>[<gold>AutoWarn</gold>]</gray> ");
    private Set<Material> globallyBannedMaterials = EnumSet.noneOf(Material.class);

    public Settings(AlexxAutoWarn plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        monitorChestAccess = config.getBoolean("settings.monitor-chest-access", false);
        debugLogAllowedActions = config.getBoolean("settings.debug-log-allowed-actions", false);
        pluginPrefix = miniMessage.deserialize(config.getString("messages.plugin-prefix", "<gray>[<gold>AutoWarn</gold>]</gray> "));

        globallyBannedMaterials = EnumSet.noneOf(Material.class);
        for (String materialName : config.getStringList("settings.globally-banned-materials")) {
            Material material = Material.matchMaterial(materialName.trim());
            if (material == null) {
                plugin.getLogger().warning("Invalid globally banned material '" + materialName + "' found in config.yml. Skipping.");
            } else {
                globallyBannedMaterials.add(material);
            }
        }
        plugin.getLogger().log(Level.INFO, "Reloaded {0} globally banned materials.", globallyBannedMaterials.size());
    }

    public Component getMessage(@NotNull String key, TagResolver... resolvers) {
        String rawMessage = plugin.getConfig().getString("messages." + key, "<red>Message not found: " + key + "</red>");
        return pluginPrefix.append(miniMessage.deserialize(rawMessage, resolvers));
    }

    public void log(Level level, String message) {
        plugin.getLogger().log(level, MiniMessage.miniMessage().stripTags(message));
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
        List<String> bannedNames = globallyBannedMaterials.stream().map(Material::name).sorted().collect(Collectors.toList());
        plugin.getConfig().set("settings.globally-banned-materials", bannedNames);
        plugin.saveConfig();
    }
}