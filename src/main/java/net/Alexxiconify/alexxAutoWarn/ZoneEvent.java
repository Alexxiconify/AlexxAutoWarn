package net.alexxiconify.alexxautowarn;

import org.bukkit.entity.Player;

public interface ZoneEvent {
    Player getPlayer();

    Zone getZone();
}