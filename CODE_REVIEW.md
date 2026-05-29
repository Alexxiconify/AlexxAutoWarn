Code review summary — AlexxAutoWarn-Paper
Date: 2026-05-29

Quick checklist used
- Reviewed sources in src/main/java/net/alexxiconify/alexxautowarn
- Noted risks, API issues, and actionable improvements

Top actionable recommendations
- Add concise Javadoc on public APIs (AutoWarnAPI, Zone, Settings) for nullability and threading.
- Normalize material names (trim + toUpperCase) when parsing config or command input.
- Cache tab-completion lists to avoid repeated allocations.
- Log listener exceptions visibly (WARN or rate-limited) instead of silently at FINE.

Per-file highlights
- AlexxAutoWarn.java: static `instance`/`api` may need `volatile` if used off main thread; document CoreProtect API behavior.
- AutoWarnAPIImpl.java: improve listener-exception handling/log level.
- AutoWarnCommand.java: normalize inputs and cache name lists; verify help/message keys.
- Settings.java: avoid storing null messages; guard formatted logs with isLoggable checks.
- Zone/ZoneManager: document containment rules (block-aligned vs precise) and normalize materials on load.

Next steps (pick one)
- I can apply any of the above changes and run a build (`mvn -DskipTests package`).
- I can generate focused patches (e.g., normalize materials, add Javadoc stubs, adjust logging).
Tell me which to apply and I will proceed.