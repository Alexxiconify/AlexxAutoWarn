package net.alexxiconify.alexxautowarn;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.function.Consumer;

@SuppressWarnings("unused") // Public API surface kept for other plugins; some methods are unused internally but required.
public interface AutoWarnAPI {
    Zone getZoneAt(Location location);

    Collection<Zone> getZonesAt(Location location);

    Collection<Zone> getAllZones();

    void registerZoneActionListener(Consumer<ZoneActionEvent> listener);

    void registerCustomAction(String actionName, ZoneCustomAction action);

    boolean isActionAllowed(Player player, Location location, Material material, String action);

    Zone getHighestPriorityZoneAt(Location location);

    void registerZoneEnterListener(Consumer<ZoneEnterEvent> listener);

    void registerZoneLeaveListener(Consumer<ZoneLeaveEvent> listener);

    void registerZonePriorityChangeListener(Consumer<ZonePriorityChangeEvent> listener);

    void fireZoneEnter(Player player, Zone zone);

    void fireZoneLeave(Player player, Zone zone);

    void fireZonePriorityChange(Player player, Zone fromZone, Zone toZone);
}