package net.Alexxiconify.alexxAutoWarn.objects;

/**
 * Defines the possible actions for materials within an AutoInform zone.
 */
public enum ZoneAction {
 DENY,   // Deny the action (e.g., block placement)
 ALERT,  // Allow the action, but alert staff/console
 ALLOW   // Allow the action without any special alerts
}