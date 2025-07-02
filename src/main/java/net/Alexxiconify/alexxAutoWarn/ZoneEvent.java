package net.Alexxiconify.alexxAutoWarn;

import org.bukkit.Material;
import org.bukkit.entity.Player;

public interface ZoneEvent {
 Player getPlayer ( );

 Zone getZone ( );
}

class ZoneEnterEvent implements ZoneEvent {
 private final Player player;
 private final Zone zone;

 public ZoneEnterEvent ( Player player , Zone zone ) {
  this.player = player;
  this.zone = zone;
 }

 public Player getPlayer ( ) { return player; }

 public Zone getZone ( ) { return zone; }
}

class ZoneLeaveEvent implements ZoneEvent {
 private final Player player;
 private final Zone zone;

 public ZoneLeaveEvent ( Player player , Zone zone ) {
  this.player = player;
  this.zone = zone;
 }

 public Player getPlayer ( ) { return player; }

 public Zone getZone ( ) { return zone; }
}

class ZonePriorityChangeEvent implements ZoneEvent {
 private final Player player;
 private final Zone from;
 private final Zone to;

 public ZonePriorityChangeEvent ( Player player , Zone from , Zone to ) {
  this.player = player;
  this.from = from;
  this.to = to;
 }

 public Player getPlayer ( ) { return player; }

 public Zone getZone ( ) { return to; }

 public Zone getFromZone ( ) { return from; }

 public Zone getToZone ( ) { return to; }
}

interface ZoneCustomAction {
    void execute ( Player player , Zone zone , Material mat , String context );
}

class ZoneActionEvent {
    private final Player player;
    private final Zone zone;
    private final Material material;
    private final String action;
    private boolean cancelled = false;

    public ZoneActionEvent ( Player player , Zone zone , Material material , String action ) {
        this.player = player;
        this.zone = zone;
        this.material = material;
        this.action = action;
    }

    public Player getPlayer ( ) { return player; }

    public Zone getZone ( ) { return zone; }

    public Material getMaterial ( ) { return material; }

    public String getAction ( ) { return action; }

    public boolean isCancelled ( ) { return cancelled; }

    public void setCancelled ( boolean cancelled ) { this.cancelled = cancelled; }
}