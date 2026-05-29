package net.alexxiconify.alexxautowarn;

import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class ZoneActionEvent implements ZoneEvent {
    private final Player player;
    private final Zone zone;
    private final Material material;
    private final String action;
    private boolean cancelled;

    public ZoneActionEvent(Player player, Zone zone, Material material, String action) {
        this.player = player;
        this.zone = zone;
        this.material = material;
        this.action = action;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public Zone getZone() {
        return zone;
    }

    public Material getMaterial() {
        return material;
    }

    public String getAction() {
        return action;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}