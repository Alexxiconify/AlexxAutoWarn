package net.Alexxiconify.alexxAutoWarn.objects;

/**
 * Defines the possible actions for a material interaction within an AutoInform zone.
 */
public enum ZoneAction {
 /**
  * Deny the action. The event will be cancelled.
  */
 DENY,
 /**
  * Allow the action, but alert staff members about it.
  */
 ALERT,
 /**
  * Allow the action without any alerts or restrictions.
  */
 ALLOW
}