package net.alexxiconify.alexxautowarn;

import org.bukkit.Material;
import org.bukkit.entity.Player;

@FunctionalInterface
public interface ZoneCustomAction {
    void execute(Player player, Zone zone, Material material, String context);
}