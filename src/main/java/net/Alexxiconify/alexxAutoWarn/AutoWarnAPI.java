package net.Alexxiconify.alexxAutoWarn;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public interface AutoWarnAPI {
    Zone getZoneAt(Location loc);

    Collection<Zone> getZonesAt(Location loc); // For nested/prioritized zones

    Collection<Zone> getAllZones();

    void registerZoneActionListener(Consumer<ZoneActionEvent> listener);

    void registerCustomAction(String actionName, ZoneCustomAction action);

    boolean isActionAllowed(Player player, Location loc, Material mat, String action);

    Zone getHighestPriorityZoneAt(Location loc);

    void registerZoneEnterListener(Consumer<ZoneEnterEvent> listener);

    void registerZoneLeaveListener(Consumer<ZoneLeaveEvent> listener);

    void registerZonePriorityChangeListener(Consumer<ZonePriorityChangeEvent> listener);
}

class AutoWarnAPIImpl implements AutoWarnAPI {
    private final ZoneManager zoneManager;
    private final List<Consumer<ZoneActionEvent>> actionListeners = new CopyOnWriteArrayList<>();
    private final Map<String, ZoneCustomAction> customActions = new HashMap<>();
    private final List<Consumer<ZoneEnterEvent>> enterListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ZoneLeaveEvent>> leaveListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ZonePriorityChangeEvent>> priorityChangeListeners = new CopyOnWriteArrayList<>();

    public AutoWarnAPIImpl(ZoneManager zoneManager) {
        this.zoneManager = zoneManager;
    }

    @Override
    public Zone getZoneAt(Location loc) {
        return zoneManager.getZoneAt(loc);
    }

    @Override
    public Collection<Zone> getZonesAt(Location loc) {
        return zoneManager.getZonesAt(loc);
    }

    @Override
    public Collection<Zone> getAllZones() {
        return zoneManager.getAllZones();
    }

    @Override
    public void registerZoneActionListener(Consumer<ZoneActionEvent> listener) {
        actionListeners.add(listener);
    }

    @Override
    public void registerCustomAction(String actionName, ZoneCustomAction action) {
        customActions.put(actionName.toUpperCase(Locale.ROOT), action);
    }

    @Override
    public boolean isActionAllowed(Player player, Location loc, Material mat, String action) {
        Zone zone = getZoneAt(loc);
        if (zone == null) return true;
        ZoneActionEvent event = new ZoneActionEvent(player, zone, mat, action);
        for (Consumer<ZoneActionEvent> listener : actionListeners) {
            listener.accept(event);
        }
        ZoneCustomAction custom = customActions.get(action.toUpperCase(Locale.ROOT));
        if (custom != null) {
            custom.execute(player, zone, mat, action);
        }
        return !event.isCancelled();
    }

    @Override
    public Zone getHighestPriorityZoneAt(Location loc) {
        return zoneManager.getHighestPriorityZoneAt(loc);
    }

    @Override
    public void registerZoneEnterListener(Consumer<ZoneEnterEvent> listener) {
        enterListeners.add(listener);
    }

    @Override
    public void registerZoneLeaveListener(Consumer<ZoneLeaveEvent> listener) {
        leaveListeners.add(listener);
    }

    @Override
    public void registerZonePriorityChangeListener(Consumer<ZonePriorityChangeEvent> listener) {
        priorityChangeListeners.add(listener);
    }

    public void fireZoneEnter(Player player, Zone zone) {
        ZoneEnterEvent event = new ZoneEnterEvent(player, zone);
        for (var l : enterListeners) l.accept(event);
    }

    public void fireZoneLeave(Player player, Zone zone) {
        ZoneLeaveEvent event = new ZoneLeaveEvent(player, zone);
        for (var l : leaveListeners) l.accept(event);
    }

    public void fireZonePriorityChange(Player player, Zone from, Zone to) {
        ZonePriorityChangeEvent event = new ZonePriorityChangeEvent(player, from, to);
        for (var l : priorityChangeListeners) l.accept(event);
    }
}