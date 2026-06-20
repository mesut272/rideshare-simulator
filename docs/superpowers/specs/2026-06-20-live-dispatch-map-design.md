# Live Dispatch Map Design

## Goal

Add a polished live map view to the supervisor console so the operator can see the ride-sharing simulation as it runs: city locations, queue pressure at each location, active drivers on routes, and trip progress.

## Visual Direction

The map becomes the primary operations surface. The interface should feel like a modern dispatch command center: dark neutral background, crisp route lines, glowing active-trip markers, readable queue badges, and compact operational panels. It should not look like a generic card dashboard.

## Map Data

The map uses the existing six locations:

- UW
- NEU
- SLU
- SpaceNeedle
- Bellevue
- Airport

The frontend will use stable hand-tuned coordinates for these locations. The backend will provide live state:

- `locations`: location name and number of waiting requests starting there.
- `routes`: directed active-trip route data.
- `activeTrips`: driver ID, start location, destination, ride type, progress percentage, and remaining seconds.
- `queueByLocation`: waiting queue counts by request start location.
- `summary`: active trip count, waiting queue size, completed count, and running state.

## Backend Design

Add a read-only snapshot method to `SimulationEngine`.

The method will:

- Count waiting requests by start location from `waitingQueue`.
- Read active rides from `activeRequests`.
- Calculate each trip's progress from `actualStartTime` and `expectedCompletionTime`.
- Return immutable snapshot objects that can be safely serialized.

Expose the snapshot through `SimulationRuntime`, then add `GET /api/map` to `SupervisorChatServer`.

The endpoint must not mutate simulation state and must continue to work before simulation start, while running, after completion, and after a new round starts.

## Frontend Design

Add a new full-width map band above the chat/control workspace.

Map elements:

- SVG route layer for city links.
- Location nodes with queue badges and heat intensity.
- Animated active trip markers interpolated between start and destination coordinates.
- Active route highlights with driver labels.
- Empty state when no simulation is running.

Side panels:

- Active trips list sorted by remaining time.
- Queue pressure list sorted by waiting count.
- Small map controls to toggle route labels and queue heat.

The existing chat and simulation controls remain available below the map. The map refreshes every two seconds and also refreshes immediately after starting or restarting a simulation.

## Error Handling

- If `/api/map` fails, the map shows a compact error state and keeps the last rendered state if available.
- Unknown locations are ignored in the visual layer rather than breaking rendering.
- Progress is clamped to `0..100`.

## Testing

Add focused tests for:

- Waiting queue counts by location.
- Active trip snapshot includes driver, route, progress, and remaining time.
- `/api/map` returns map snapshot JSON.
- Snapshot works when the simulation has not started.

Do not test visual styling with JUnit. Verify the browser UI manually through the local server.
