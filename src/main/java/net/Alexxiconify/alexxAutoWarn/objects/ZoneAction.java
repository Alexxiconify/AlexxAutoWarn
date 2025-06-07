/**
 * Defines the possible actions to take within a protected zone.
 * - DENY: Prevents the action from occurring.
 * - ALERT: Allows the action but sends an alert to staff.
 * - ALLOW: Explicitly allows the action, overriding any default denials.
 */
public enum ZoneAction {
 DENY,
 ALERT,
 ALLOW
}