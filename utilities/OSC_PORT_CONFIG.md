# OSC Port Configuration

This document explains the OSC port configuration used in bice-box to prevent future port-related issues.

## Port Layout

- **57110**: SuperCollider Server (scsynth) - handles audio synthesis commands
- **57121**: Electron OSC Server - receives OSC messages FROM SuperCollider  
- **57122**: SuperCollider Language (sclang) - receives OSC messages FROM Electron

## Message Flow

```
Electron ──────────────────────────> SuperCollider Language (OSCdefs)
         (port 57122)                 
         
SuperCollider Language ──────────────> Electron  
                       (port 57121)
```

## Dynamic Port Discovery

The system uses dynamic port discovery to handle platform differences:

1. SuperCollider sends its port configuration via `/sc/config` message on startup
2. Electron stores this configuration in `global.scPortConfig`
3. All subsequent OSC communication uses the discovered ports
4. **Fails fast** if port config not received (no silent fallbacks)

## Platform Differences

- **Mac**: May have different SuperCollider default configurations
- **Pi**: May use different ports due to system restrictions
- **Dynamic discovery**: Ensures compatibility across platforms

## Debugging

Enable OSC debugging by checking these log messages:

- `"OSCManager: Received SC port config"` - Port discovery working
- `"Using SC port config"` - Dynamic ports in use  
- `"SuperCollider port configuration not received"` - Port discovery failed
- `"SC: Sent port configuration to Electron"` - SuperCollider side working

## Common Issues

- **"Command not found" errors**: Usually means messages sent to wrong port (server vs language)
- **No parameter faders**: Often indicates OSC messages not reaching SuperCollider language
- **Platform differences**: Use dynamic port discovery rather than hardcoded ports 