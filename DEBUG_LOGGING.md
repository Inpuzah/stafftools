# StaffTools Debug Logging System

## Overview
StaffTools includes a **temporary debug logging system** for troubleshooting and testing. This is separate from the **permanent audit logging** system that records all punishments and staff actions.

### Debug Logging (Temporary)
- Used for **testing and troubleshooting** only
- Enable when diagnosing issues or verifying functionality
- Can be disabled in production once verified
- Stores recent logs in memory (ring buffer)
- Optional Discord integration for real-time monitoring

### Audit Logging (Permanent)
- **Always active** - records all punishment and staff actions permanently
- Stored in database with full history
- Used for accountability and record-keeping
- Cannot be disabled
- Includes: punishments issued/removed, staff mode changes, vanish toggles, freeze actions, notes, etc.

## When to Use Debug Logging

✅ **Use debug logging when:**
- Testing new features or changes
- Diagnosing performance issues
- Troubleshooting errors or unexpected behavior
- Monitoring plugin operations during development
- Investigating player reports of issues

❌ **Don't rely on debug logging for:**
- Permanent record-keeping (use audit logs instead)
- Player punishment history (stored in database)
- Staff accountability (use audit system)
- Long-term monitoring (too much data, performance impact)

## Features

### 📊 Log Levels
- **TRACE** 🔍 - Extremely detailed execution flow (rarely needed)
- **DEBUG** 🐛 - Detailed information for debugging
- **INFO** ℹ️ - General informational messages
- **WARN** ⚠️ - Warning messages for potential issues
- **ERROR** ❌ - Error messages with exception details

### 📁 Log Categories
Logs are organized by category for easy filtering:
- `Plugin` - Plugin initialization and shutdown
- `Database` - Database connections and queries
- `Punishment` - Punishment issuance and expiration
- `Vanish` - Vanish system operations
- `Freeze` - Player freeze/unfreeze actions
- `GUI` - GUI interactions and clicks
- `Command` - Command execution
- `BuildBan` - Build ban operations
- `Report` - Report system
- `Appeal` - Appeal system
- `StaffMode` - Staff mode toggling
- And more...

### 💾 Ring Buffer
- Keeps recent debug messages in memory (default: 500 entries)
- Automatically removes oldest entries when buffer is full
- View history with `/debug dump` command

### 💬 Discord Integration
- Real-time debug log streaming to Discord channel
- Configurable rate limiting to prevent spam
- Filter by log level and categories
- Formatted with emojis and code blocks

## Configuration

### Basic Setup

```yaml
debug:
  # Enable debug logging
  enabled: false

  # Size of the debug ring buffer (recent logs kept in memory)
  buffer-size: 500

  # Log debug messages to console
  log-to-console: true
```

### Discord Integration

```yaml
debug:
  discord:
    # Enable sending debug logs to Discord
    enabled: false

    # Discord channel ID for debug logs
    # 1. Create a #debug-logs channel in your Discord server
    # 2. Enable Developer Mode in Discord (User Settings > Advanced)
    # 3. Right-click the channel and "Copy ID"
    # 4. Paste the ID here
    channel-id: "000000000000000000"

    # Minimum log level to send to Discord
    # Options: TRACE, DEBUG, INFO, WARN, ERROR
    # Recommended: WARN (only warnings and errors)
    min-level: "WARN"

    # Rate limit for Discord messages (milliseconds)
    # Prevents spam by throttling same category+level messages
    rate-limit-ms: 3000

    # Categories to send to Discord
    # Use "ALL" to send everything, or list specific categories
    categories:
      - "ALL"
    
    # Example of selective categories:
    # categories:
    #   - "Database"
    #   - "Punishment"
    #   - "Error"
```

## Usage

### For Developers

#### Adding Debug Logs to Your Code

**Important:** Only add debug logs for temporary testing/troubleshooting. For permanent records, use `AuditManager.logAction()`.

```java
// Get the debug manager
DebugManager debug = plugin.getDebugManager();

// Temporary debugging (will only log if debug.enabled: true)
debug.info("MyCategory", "Something happened");
debug.debug("MyCategory", "Processing " + count + " items");
debug.warn("MyCategory", "Potential issue detected");
debug.error("MyCategory", "Failed to process", exception);

// Permanent audit logging (always recorded)
plugin.getAuditManager().logAction(
    staffUuid,
    staffName,
    "PUNISHMENT_ISSUED",
    targetUuid,
    targetName,
    "Details: " + details
);
```

#### Debug Logging vs Audit Logging

| Feature | Debug Logging | Audit Logging |
|---------|--------------|---------------|
| **Purpose** | Temporary troubleshooting | Permanent records |
| **Storage** | Memory (ring buffer) | Database |
| **Retention** | Until buffer full or restart | Forever (or config-based) |
| **When Active** | Only when enabled in config | Always active |
| **Use Case** | Testing, debugging, monitoring | Accountability, history |
| **Examples** | "Loaded 50 punishments", "Query took 250ms" | "Staff issued ban to Player", "Vanish enabled" |

### For Server Administrators

#### In-Game Commands

```
/debug on          - Enable debug logging
/debug off         - Disable debug logging
/debug dump [n]    - View last n debug messages (default: 50)
/debug category <cat> [n] - View logs for specific category
/debug level <level> [n]  - View logs of specific level or higher
/debug categories  - List all categories in buffer
```

#### Discord Setup Steps

1. **Install DiscordRelay** plugin if not already installed
2. **Create Debug Channel** in your Discord server
   - Recommended name: `#debug-logs` or `#server-debug`
   - Set appropriate permissions (staff-only recommended)
3. **Get Channel ID**
   - Enable Developer Mode: Discord Settings > Advanced > Developer Mode
   - Right-click the channel → Copy ID
4. **Configure StaffTools**
   ```yaml
   debug:
     enabled: true
     discord:
       enabled: true
       channel-id: "YOUR_CHANNEL_ID_HERE"
       min-level: "WARN"
       categories:
         - "ALL"
   ```
5. **Reload Plugin**
   ```
   /stafftools reload
   ```

#### Monitoring Production Servers

**For production:** Debug logging should typically be **disabled** unless actively troubleshooting.

```yaml
debug:
  enabled: false  # Disable in production by default
```

**When troubleshooting in production:**

```yaml
debug:
  enabled: true
  buffer-size: 1000
  log-to-console: false  # Don't spam console
  
  discord:
    enabled: true
    min-level: "WARN"  # Only warnings and errors
    rate-limit-ms: 5000
    categories:
      - "Database"
      - "Error"
```

**For development/testing:**

**For development/testing:**

```yaml
debug:
  enabled: true
  buffer-size: 500
  log-to-console: true
  
  discord:
    enabled: true
    min-level: "DEBUG"
    rate-limit-ms: 2000
    categories:
      - "ALL"
```

**Remember:** Once you've verified everything works, disable debug logging:
```yaml
debug:
  enabled: false
```

Permanent records (punishments, staff actions) are always logged via the audit system regardless of this setting.

## Example Discord Output

When errors occur, Discord messages look like:

```
❌ **ERROR** `[Database]`
Failed to load player punishment history
```
```sql
SQLException: Connection timed out
```

```
⚠️ **WARN** `[Punishment]`
Attempted to punish offline player: Steve
```

```
ℹ️ **INFO** `[Plugin]`
Loaded 45 active mutes, 12 active bans, expired 3 punishments
```

## Troubleshooting

### Debug logs not appearing in Discord

1. **Check DiscordRelay**
   - Is DiscordRelay plugin installed and enabled?
   - Run `/plugins` to verify

2. **Verify Channel ID**
   - Channel ID must be exact (no quotes in the ID itself)
   - Test with a temporary low min-level like INFO

3. **Check Permissions**
   - Bot must have permission to send messages in the channel
   - Check DiscordRelay bot permissions

4. **Rate Limiting**
   - Messages of same category+level are rate-limited
   - Try waiting a few seconds between tests

### Buffer filling up too fast

Increase buffer size or disable verbose categories:

```yaml
debug:
  buffer-size: 2000  # Increase capacity
  discord:
    categories:
      - "Error"      # Only critical categories
      - "Database"
```

### Too much console spam

```yaml
debug:
  log-to-console: false  # Disable console output
```

## Performance Impact

- **Memory**: ~1KB per log entry × buffer size (500 entries ≈ 500KB)
- **CPU**: Minimal - async Discord sending, efficient ring buffer
- **Network**: Discord messages are rate-limited and async
- **Recommendation**: Safe to run on production with WARN level

## Best Practices

1. **Use appropriate log levels**
   - ERROR: Exceptions, failures that need immediate attention
   - WARN: Potential issues, deprecated usage
   - INFO: Important state changes, milestones
   - DEBUG: Detailed operational info
   - TRACE: Extreme detail (method entry/exit)

2. **Choose descriptive categories**
   - Use consistent category names throughout codebase
   - Group related operations under same category

3. **Include context in messages**
   ```java
   // Good
   debug.error("Punishment", "Failed to ban player " + playerName + " (UUID: " + uuid + ")", ex);
   
   // Bad
   debug.error("Punishment", "Error", ex);
   ```

4. **Production settings**
   - Enable: true
   - Console: false
   - Discord min-level: WARN
   - Categories: Specific critical categories only

## Integration Examples

### Logging Database Operations
```java
debug.debug("Database", "Executing query: " + sql);
try {
    // Execute query
    debug.info("Database", "Query completed successfully, " + rows + " rows affected");
} catch (SQLException e) {
    debug.error("Database", "Query failed: " + sql, e);
}
```

### Logging Command Execution
```java
debug.trace("Command", player.getName() + " executing: /" + command);
try {
    // Execute command logic
    debug.info("Command", "Command completed: /" + command);
} catch (Exception e) {
    debug.error("Command", "Command failed: /" + command, e);
}
```

### Monitoring Performance
```java
long startTime = System.currentTimeMillis();
// Do work
long duration = System.currentTimeMillis() - startTime;
if (duration > 1000) {
    debug.warn("Performance", "Slow operation took " + duration + "ms: " + operationName);
}
```

## Support

If you encounter issues with the debug logging system:
1. Check this documentation first
2. Verify your configuration is valid YAML
3. Check console for initialization errors
4. Test with `debug.enabled: true` and `discord.enabled: false` first
5. Contact plugin developers with debug dump output

---

**Debug logging is your friend!** Use it to understand what your plugin is doing and catch issues before they become problems.
