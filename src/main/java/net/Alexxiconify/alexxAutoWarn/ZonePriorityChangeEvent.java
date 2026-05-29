package net.alexxiconify.alexxautowarn;

import org.bukkit.entity.Player;

public record ZonePriorityChangeEvent(Player player, Zone fromZone, Zone toZone) implements ZoneEvent {
    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public Zone getZone() {
        return toZone;
    }

    public Zone getFromZone() {
        return fromZone;
    }

    public Zone getToZone() {
        return toZone;
    }
}