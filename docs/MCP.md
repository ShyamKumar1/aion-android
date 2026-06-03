# AION MCP Server

> **Status:** Phase 5 (not yet available at runtime). The Ktor WebSocket server,
> protocol handler, tool mapper, and auth manager are implemented; the server
> start/stop wiring and UI controls land in Phase 5.

## What is MCP?

The **Model Context Protocol (MCP)** is an open standard that lets AI applications
discover and call tools on remote servers. AION implements an MCP server that
runs **on your Android device**, exposing your phone's capabilities as tools
that external AI tools can invoke.

This flips the usual MCP pattern: instead of a PC controlling your phone via ADB,
any MCP-compatible client (Claude Desktop, Cursor, Cline) connects directly to
AION's on-device WebSocket server.

---

## How It Works

```
External AI Tool          AION (Android Device)
(Claude Desktop,          ┌──────────────────────┐
 Cursor, Cline)           │  MCP Server           │
     │                    │  Ktor WebSocket       │
     │  WebSocket connect │  127.0.0.1:8765       │
     │──────────────────► │  ┌──────────────────┐ │
     │                    │  │ McpAuthManager   │ │
     │  Auth (Bearer JWT) │  │  • Token validate│ │
     │──────────────────► │  │  • Rate limit    │ │
     │                    │  │  • Client ID gen │ │
     │                    │  └──────┬───────────┘ │
     │  tools/list        │         │             │
     │◄───────────────────│  ┌──────▼───────────┐ │
     │  [tool definitions]│  │ McpToolMapper    │ │
     │                    │  │  Skill → Tool    │ │
     │  tools/call        │  └──────┬───────────┘ │
     │  {name, arguments} │         │             │
     │──────────────────► │  ┌──────▼───────────┐ │
     │                    │  │ SkillRegistry    │ │
     │  {result}          │  │  Execute skill   │ │
     │◄───────────────────│  └──────────────────┘ │
     └                    └─────────────────────────┘
```

---

## Connecting from External AI Tools

### Prerequisites

1. AION installed and running (foreground service active).
2. MCP server enabled in AION Settings (default port: 8765).
3. An authentication token (shown in AION Settings → MCP Server).

### Authentication Token

- **32 cryptographically random bytes**, base64url-encoded (44 characters).
- Generated on first MCP server start.
- Stored in `SharedPreferences` (`mcp_auth`).
- Displayed as text and QR code in Settings.
- Rotatable at any time from Settings.
- **Rate limited:** 5 failed attempts per minute per IP address.
- Constant-time comparison prevents timing attacks.

### On the Same Device (localhost)

Connect to `ws://127.0.0.1:8765/mcp` with the token in the `Authorization` header:

```
Authorization: Bearer <your-token>
```

### Over LAN (same WiFi network)

> **⚠️ Security warning:** LAN mode exposes your phone's MCP server to every
> device on the same network. Only enable on trusted networks.

1. Enable **LAN mode** in AION Settings → MCP Server.
2. AION binds to the device's local network IP.
3. Connect to `ws://<device-ip>:8765/mcp` with the token.
4. mDNS advertisement (`_aion._tcp`) broadcasts the server on the LAN.

---

## Claude Desktop Configuration

Add to your `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "aion": {
      "command": "npx",
      "args": [
        "-y",
        "@modelcontextprotocol/client"
      ],
      "env": {
        "MCP_SERVER_URL": "ws://127.0.0.1:8765/mcp",
        "MCP_AUTH_TOKEN": "<your-token-here>"
      }
    }
  }
}
```

> **Note:** If connecting from a phone with a desktop client over LAN, replace
> `127.0.0.1` with the phone's LAN IP address.

---

## Cursor Configuration

1. Open Cursor Settings → Features → MCP Servers.
2. Click **Add new MCP server**.
3. Set **Name**: `AION`
4. Set **Type**: `ws` (WebSocket)
5. Set **URL**: `ws://127.0.0.1:8765/mcp`
6. Set **Authorization**: `Bearer <your-token>`
7. Click **Save**.

---

## Cline Configuration

In Cline's MCP settings:

```json
{
  "mcpServers": {
    "aion": {
      "url": "ws://127.0.0.1:8765/mcp",
      "headers": {
        "Authorization": "Bearer <your-token>"
      }
    }
  }
}
```

---

## Available Tools

Every registered AION skill is automatically exposed as an MCP tool. The
`McpToolMapper` converts skill definitions to MCP tool schemas at server start.

| Tool Name | Description | Parameters |
|---|---|---|
| `sms.send` | Sends an SMS text message. Requires user confirmation. | `to` (string, required), `body` (string, required) |
| `call.make` | Places a phone call. Requires user confirmation. | `to` (string, required) |
| `notification.read` | Reads recent device notifications. | `limit` (integer, optional, default 5) |
| `calendar.read` | Reads upcoming calendar events. | `date` (string, optional), `limit` (integer, optional, default 5) |
| `contacts.find` | Looks up a contact by name. | `query` (string, required), `limit` (integer, optional, default 5) |
| `timer.set` | Sets a timer for N minutes. | `duration_minutes` (number, required), `label` (string, optional) |
| `clipboard.manage` | Reads/writes the system clipboard. | `action` (enum: read/write, required), `text` (string, optional) |
| `web.search` | Opens a web search in the device browser. | `query` (string, required) |
| `screen.read` | Reads visible screen content. Requires FULL capability. | None |

### Capability Gating

If a skill's `requiredCapability` exceeds the current device tier, the tool is
still listed but returns an error when called:

```json
{
  "error": "insufficient_capability",
  "required": "FULL",
  "current": "PARTIAL"
}
```

---

## Protocol Details

AION implements **MCP protocol version 2025-03-26** over JSON-RPC 2.0.

### Supported Methods

| Method | Description |
|---|---|
| `initialize` | Capability exchange. Returns server info and supported features. |
| `tools/list` | Returns all registered skills as MCP tool definitions. |
| `tools/call` | Invokes a skill by name with validated parameters. |
| `resources/list` | Lists available device resources. |
| `ping` | Keepalive. Must be sent every 30 seconds. |

### Example: tools/list

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "tools/list"
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "result": {
    "tools": [
      {
        "name": "sms.send",
        "description": "Sends an SMS text message to a phone number.",
        "inputSchema": {
          "type": "object",
          "properties": {
            "to": { "type": "string", "description": "Destination phone number" },
            "body": { "type": "string", "description": "The text content of the message" }
          },
          "required": { "to": true, "body": true }
        }
      }
    ]
  }
}
```

### Example: tools/call

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": "2",
  "method": "tools/call",
  "params": {
    "name": "sms.send",
    "arguments": {
      "to": "+1234567890",
      "body": "Hello from Claude Desktop via AION!"
    }
  }
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": "2",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Awaiting confirmation"
      }
    ],
    "isError": false
  }
}
```

> Mutating skills return `ConfirmationRequired` — the user must approve the
> action on their phone before it executes. The response text indicates the
> action is pending user confirmation.

---

## Security

### Default: Localhost Only

The MCP server binds to `127.0.0.1:8765` by default. Only processes on the same
device can connect. The server cannot be reached from the local network without
explicit user opt-in.

### LAN Mode (Explicit Opt-In)

When LAN mode is enabled in Settings, the server binds to the device's network
interface. AION provides:

- **Token auth:** Every WebSocket connection must provide a valid token in the
  initial handshake. Connections without a valid token are dropped immediately
  with no response data.
- **Rate limiting:** 5 failed authentication attempts per minute per IP.
- **Constant-time comparison:** Token validation uses a constant-time string
  comparison to prevent timing attacks.
- **QR code setup:** The token is displayed as a QR code in Settings for easy
  scanning from desktop tools.
- **Audit log:** Every MCP action is logged locally (client ID, tool name,
  timestamp, result status). Logs do not include full input/output content.

### Port Configuration

| Setting | Default | Range |
|---|---|---|
| Port | 8765 | 1024–65535 |
| Bind address | 127.0.0.1 | 0.0.0.0 (LAN mode) |

If port 8765 is unavailable, AION tries 8766, 8767, then 8768. If all fail,
the server reports "Port unavailable — configure a custom port" and does not
start.

### Lifecycle

The MCP server starts when AION's foreground service starts and stops when the
service stops. It does not run independently.

---

## Resources

AION exposes the following MCP resources:

| URI | Description | MIME Type |
|---|---|---|
| `aion://notifications/recent` | Recently captured notifications | `text/plain` |

Additional resources (screen content, contacts, device state) are added in
Phase 5+.

---

## Example: Send SMS from Claude Desktop

1. Ensure AION is running on your phone with MCP server enabled.
2. Copy the auth token from AION Settings → MCP Server.
3. Configure Claude Desktop with the MCP server (see config above).
4. In Claude Desktop, ask: *"Send an SMS to +1234567890 saying 'Running low on milk, pick some up on the way home.'"*
5. Claude calls AION's `sms.send` tool.
6. AION returns `ConfirmationRequired`.
7. **On your phone:** A notification appears asking you to confirm the SMS.
8. Tap **Confirm** on your phone.
9. The SMS is sent. Claude Desktop receives a success response.

---

## Troubleshooting

| Symptom | Likely Cause | Fix |
|---|---|---|
| `Connection refused` | MCP server not running | Enable MCP Server in AION Settings |
| `Unauthorized` (1008) | Wrong token | Regenerate token in Settings, update client config |
| `Rate limit exceeded` | Too many failed auth attempts | Wait 1 minute, try again with correct token |
| Cannot connect over LAN | LAN mode disabled | Enable LAN mode in AION Settings |
| Tool returns `insufficient_capability` | Device tier too low | Enable required permissions in Settings |
| Port conflict | Another service on port 8765 | Change port in AION Settings |

---

## See Also

- [MCP Protocol Specification](https://spec.modelcontextprotocol.io/)
- AION Skills Reference: `docs/SKILLS.md`
- AION Permissions: `docs/PERMISSIONS.md`
- AION Privacy: `docs/PRIVACY.md`
