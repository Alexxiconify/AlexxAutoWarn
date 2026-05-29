MASTER COMMENTS (concise)
Date: 2026-05-29

Entries (file — line:col — original comment)

- src/main/java/net/alexxiconify/alexxautowarn/AutoWarnCommand.java — 37:5
  // Cache commonly used name lists to avoid repeated allocations during tab completion.

- src/main/java/net/alexxiconify/alexxautowarn/AutoWarnCommand.java — 103:17
  // Unknown subcommand: show help but report not handled so Bukkit may show usage if desired.

- src/main/java/net/alexxiconify/alexxautowarn/AutoWarnCommand.java — 135:9
  // Use requireNonNull: player#getLocation is expected to be non-null in Bukkit, assert to satisfy static analysis.

- src/main/java/net/alexxiconify/alexxautowarn/AutoWarnCommand.java — ~441:1
  // getWandKey() removed; plugin-level wand key should be accessed via the main plugin class.

Notes
- The file lists removed/shortened comments with file and location. Source now contains concise comments; domain-explanatory comments were kept.

Signed-off: code-maintenance-script (2026-05-29)