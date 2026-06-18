# Supervisor Agent Chat UI Design

## Goal

Build a browser-based supervisor console for the ride-sharing simulator so a regulator/supervisor agent can chat with the existing dispatch AI through a polished front-end interface. The first version must be a real conversation surface, not a static mockup.

## Chosen Approach

Use an embedded Java HTTP server inside the existing application. This keeps the project lightweight and avoids adding a separate Node/React build system. The server will expose a small API for the browser and serve static HTML/CSS/JavaScript assets from the Java resources directory.

## Backend Design

Add a `SupervisorChatServer` class that uses the JDK built-in `com.sun.net.httpserver.HttpServer`.

The server will provide:

- `GET /` to return the supervisor console HTML.
- `GET /styles.css` and `GET /app.js` to return static assets.
- `GET /api/drivers` to return a JSON snapshot of all known drivers with `driverId`, `currentLocation`, and `available`.
- `POST /api/chat` to accept `{"message":"..."}` and return `{"reply":"..."}`.

`RideSharingApp` will initialize the existing `SimulationEngine`, `OpenAiChatModel`, `TaxiAgentInterface`, and `TaxiAgentAdapter`, then start `SupervisorChatServer` on a local port. The existing console scanner can be removed from the browser-focused path or left out of the main app flow so the browser becomes the primary interaction point.

The chat server will receive a `TaxiAgentInterface` instance and a `DriverCacheService` instance through its constructor. This keeps HTTP handling separate from AI/tool construction and makes the server testable with fake implementations.

## Frontend Design

The first screen is the usable supervisor console, not a landing page.

Layout:

- Left/main area: conversation timeline with supervisor messages, AI replies, timestamps, loading state, and error state.
- Bottom of main area: fixed composer with multiline input, send button, and Enter-to-send behavior.
- Right rail: live driver status list, online/available counts, and quick action buttons such as "查看所有司机", "查询空闲司机", "录入紧急订单示例", and "查看监控名单".
- Header: concise system identity, connection status, and local API state.

Visual direction:

- Professional operations console rather than marketing page.
- High-contrast but restrained palette, with dark neutral surfaces, cyan/green operational accents, and clear state colors.
- Compact cards only for repeated entities such as messages and driver rows.
- Responsive behavior: on narrow screens, the driver rail stacks below the chat panel and the composer remains easy to use.

## Data Flow

1. User opens `http://localhost:<port>/`.
2. Browser loads the console assets from the embedded server.
3. Browser calls `GET /api/drivers` on load and periodically refreshes the driver rail.
4. User sends a message.
5. Browser disables the send control, appends a pending assistant message, and posts to `/api/chat`.
6. Server calls `agent.chat(message)` asynchronously enough to avoid blocking the UI request forever.
7. Browser replaces the pending message with the returned reply or shows a visible error if the call fails.

## Error Handling

- Empty chat messages are rejected in the browser and by the server.
- Invalid JSON returns HTTP 400 with a JSON error response.
- Agent errors return HTTP 500 with a user-readable message.
- Slow model calls show an in-progress state in the UI.
- Driver snapshot failures do not break chat; the right rail shows a refresh error.

## Testing

Add focused tests for `SupervisorChatServer`:

- Static HTML endpoint returns a successful response.
- `POST /api/chat` forwards the user message to an injected fake agent and returns the fake reply as JSON.
- `GET /api/drivers` serializes injected/fake driver cache data.
- Empty or malformed chat requests return a client error.

Do not call the real model in tests. Use a fake `TaxiAgentInterface` implementation so tests are deterministic and do not require network or API keys.

## Out of Scope

- User authentication.
- Multi-user chat history persistence.
- WebSocket streaming.
- Separate React/Vite frontend build.
- Deployment beyond local simulator use.
