package net.Alexxiconify.alexxAutoWarn;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class AutoWarnAPIImpl implements AutoWarnAPI {
 private final ZoneManager zoneManager;
 private final List < Consumer < ZoneActionEvent > > actionListeners = new CopyOnWriteArrayList <> ( );
 private final Map < String, ZoneCustomAction > customActions = new HashMap <> ( );

 public AutoWarnAPIImpl ( ZoneManager zoneManager ) {
  this.zoneManager = zoneManager;
 }

 @Override
 public Zone getZoneAt ( Location loc ) {
  return zoneManager.getZoneAt ( loc );
 }

 @Override
 public Collection < Zone > getZonesAt ( Location loc ) {
  // For now, just return the first matching zone as a singleton list
  Zone z = zoneManager.getZoneAt ( loc );
  return z == null ? Collections.emptyList ( ) : Collections.singletonList ( z );
 }

 @Override
 public Collection < Zone > getAllZones ( ) {
  return zoneManager.getAllZones ( );
 }

 @Override
 public void registerZoneActionListener ( Consumer < ZoneActionEvent > listener ) {
  actionListeners.add ( listener );
 }

 @Override
 public void registerCustomAction ( String actionName , ZoneCustomAction action ) {
  customActions.put ( actionName.toUpperCase ( Locale.ROOT ) , action );
 }

 @Override
 public boolean isActionAllowed ( Player player , Location loc , Material mat , String action ) {
  Zone zone = getZoneAt ( loc );
  if ( zone == null ) return true;
  ZoneActionEvent event = new ZoneActionEvent ( player , zone , mat , action );
  for ( Consumer < ZoneActionEvent > listener : actionListeners ) {
   listener.accept ( event );
  }
  ZoneCustomAction custom = customActions.get ( action.toUpperCase ( Locale.ROOT ) );
  if ( custom != null ) {
   custom.execute ( player , zone , mat , action );
  }
  return !event.isCancelled ( );
 }
}