package net.Alexxiconify.alexxAutoWarn;

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