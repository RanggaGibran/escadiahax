# EscadiaHax - Professional Minecraft Anti-Cheat

A professional anti-cheat plugin for Minecraft Paper 1.21.1+ focused on anti-xray detection and staff monitoring tools.

## Logging Configuration

EscadiaHax has a robust logging system that can be configured to your needs. You can control how much information is logged to the console to avoid unnecessary spam while still keeping important notifications.

### Configuration Options

#### Xray Detection Logging

```yaml
xray-detection:
  debug:
    # Enable detailed logging for detection decisions
    detailed-logging: false
    # Debug level (0=off, 1=important only, 2=detailed)
    debug-level: 0
    # Only log high suspicion scores
    log-only-high-suspicion: true
    # Minimum suspicion score to log (if log-only-high-suspicion is true)
    min-score-to-log: 40
```

#### Replay System Logging

```yaml
replay:
  # Enable debug logging for replay functionality
  debug-mode: false
  # Debug level (0=off, 1=important only, 2=detailed)
  debug-level: 0
  # Only log important replay events (session start/end)
  log-minimal: true
```

### Recommended Settings

For production servers, we recommend:

- Set `debug-level: 0` to minimize logging
- Keep `log-only-high-suspicion: true` to only log potentially real issues
- Set `log-minimal: true` for replay logging

For debugging purposes, you can increase the debug levels temporarily:

- `debug-level: 1` for important information
- `debug-level: 2` for detailed debugging
- Set `detailed-logging: true` for Xray detection

## Performance

These logging settings help ensure EscadiaHax remains light on your server resources while still effectively detecting cheaters.

## Replay System

The replay system allows staff to review suspicious mining patterns of players.

### Commands

- `/replay <playername>` - Start a replay of the player's mining activity
- `/replay stop` - Stop the current replay

### Replay Controls

The replay system now includes an interactive hotbar interface for better control:

- **Clock** (Slot 1): Pause/Resume the replay
- **Arrow** (Slot 2): Skip backward (5s or 15s with Shift)
- **Spectral Arrow** (Slot 3): Skip forward (5s or 15s with Shift)
- **Ender Eye** (Slot 7): Toggle between POV and Observer mode
- **Barrier** (Slot 8): Exit the replay

### Observer Mode Improvements

- Armor stands now animate properly with mining actions
- Mining animations are more realistic with proper block state sequence

### Configuration

```yaml
replay:
  # Enable replay functionality
  enabled: true
  # How many minutes of data to keep per player
  retention-minutes: 30
  # Maximum number of mining events to store per player
  max-events-per-player: 1000
  # Clear player data on logout to save memory
  clear-on-logout: true
```
