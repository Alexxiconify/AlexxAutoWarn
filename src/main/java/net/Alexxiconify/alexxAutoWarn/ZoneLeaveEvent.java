package net.alexxiconify.alexxautowarn;

import org.bukkit.entity.Player;

public record ZoneLeaveEvent(Player player, Zone zone) implements ZoneEvent {
    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public Zone getZone() {
        return zone;
    }
}