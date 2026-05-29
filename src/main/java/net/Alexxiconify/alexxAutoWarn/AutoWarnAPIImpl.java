package net.alexxiconify.alexxautowarn;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

final class AutoWarnAPIImpl implements AutoWarnAPI {
    private final ZoneManager zoneManager;
    private final Map<String, ZoneCustomAction> customActions = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<ZoneActionEvent>> zoneActionListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<ZoneEnterEvent>> zoneEnterListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<ZoneLeaveEvent>> zoneLeaveListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<ZonePriorityChangeEvent>> zonePriorityChangeListeners = new CopyOnWriteArrayList<>();

    AutoWarnAPIImpl(ZoneManager zoneManager) {
        this.zoneManager = Objects.requireNonNull(zoneManager, "zoneManager");
    }

    @Override
    public Zone getZoneAt(Location location) {
        return zoneManager.getZoneAt(location);
    }

    @Override
    public Collection<Zone> getZonesAt(Location location) {
        return zoneManager.getZonesAt(location);
    }

    @Override
    public Collection<Zone> getAllZones() {
        return zoneManager.getAllZones();
    }

    @Override
    public void registerZoneActionListener(Consumer<ZoneActionEvent> listener) {
        if (listener != null) {
            zoneActionListeners.add(listener);
        }
    }

    @Override
    public void registerCustomAction(String actionName, ZoneCustomAction action) {
        if (actionName != null && action != null) {
            customActions.put(normalize(actionName), action);
        }
    }

    @Override
    public boolean isActionAllowed(Player player, Location location, Material material, String action) {
        Zone zone = zoneManager.getHighestPriorityZoneAt(location);
        if (zone == null) {
            return true;
        }

        ZoneActionEvent zoneActionEvent = new ZoneActionEvent(player, zone, material, action);
        fireZoneAction(zoneActionEvent);
        if (zoneActionEvent.isCancelled()) {
            return false;
        }

        ZoneCustomAction customAction = (action == null) ? null : customActions.get(normalize(action));
        if (customAction != null) {
            customAction.execute(player, zone, material, action);
        }

        return switch (zone.getActionFor(material)) {
            case DENY -> false;
            case ALERT, ALLOW -> true;
        };
    }

    @Override
    public Zone getHighestPriorityZoneAt(Location location) {
        return zoneManager.getHighestPriorityZoneAt(location);
    }

    @Override
    public void registerZoneEnterListener(Consumer<ZoneEnterEvent> listener) {
        if (listener != null) {
            zoneEnterListeners.add(listener);
        }
    }

    @Override
    public void registerZoneLeaveListener(Consumer<ZoneLeaveEvent> listener) {
        if (listener != null) {
            zoneLeaveListeners.add(listener);
        }
    }

    @Override
    public void registerZonePriorityChangeListener(Consumer<ZonePriorityChangeEvent> listener) {
        if (listener != null) {
            zonePriorityChangeListeners.add(listener);
        }
    }

    @Override
    public void fireZoneEnter(Player player, Zone zone) {
        ZoneEnterEvent event = new ZoneEnterEvent(player, zone);
        zoneEnterListeners.forEach(listener -> safeAccept(listener, event));
    }

    @Override
    public void fireZoneLeave(Player player, Zone zone) {
        ZoneLeaveEvent event = new ZoneLeaveEvent(player, zone);
        zoneLeaveListeners.forEach(listener -> safeAccept(listener, event));
    }

    @Override
    public void fireZonePriorityChange(Player player, Zone fromZone, Zone toZone) {
        ZonePriorityChangeEvent event = new ZonePriorityChangeEvent(player, fromZone, toZone);
        zonePriorityChangeListeners.forEach(listener -> safeAccept(listener, event));
    }

    void fireZoneAction(ZoneActionEvent event) {
        zoneActionListeners.forEach(listener -> safeAccept(listener, event));
    }

    private <T> void safeAccept(Consumer<T> listener, T event) {
        try {
            listener.accept(event);
        } catch (RuntimeException ex) {
            // Listener failures should not break the plugin. Log at FINE to help debugging without spamming.
            try {
                zoneManager.getPlugin().getLogger().log(java.util.logging.Level.WARNING, "Zone listener threw exception", ex);
            } catch (Exception ignore2) {
                // swallow
            }
        }
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT);
    }
}