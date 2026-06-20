# Live Dispatch Map Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a polished real-time dispatch map to the supervisor web console.

**Architecture:** `SimulationEngine` exposes a read-only `SimulationMapSnapshot`; `SimulationRuntime` forwards the current engine snapshot; `SupervisorChatServer` serves `GET /api/map`; the browser renders a responsive SVG/HTML map from that JSON.

**Tech Stack:** Java 17, JDK `HttpServer`, Gson, JUnit 5, HTML/CSS/SVG/JavaScript.

---

## Tasks

### Task 1: Engine Snapshot

**Files:**
- Create: `FinalProject/src/main/java/finalProject/SimulationMapSnapshot.java`
- Modify: `FinalProject/src/main/java/finalProject/SimulationEngine.java`
- Modify: `FinalProject/src/test/java/finalProject/SimulationEngineTest.java`

- [ ] Write failing tests for waiting queue counts and active trip progress.
- [ ] Implement immutable snapshot DTOs.
- [ ] Implement `SimulationEngine.getMapSnapshot(boolean started)`.
- [ ] Run `gradle test --tests finalProject.SimulationEngineTest`.

### Task 2: Runtime and HTTP API

**Files:**
- Modify: `FinalProject/src/main/java/finalProject/SimulationRuntime.java`
- Modify: `FinalProject/src/main/java/finalProject/SupervisorChatServer.java`
- Modify: `FinalProject/src/test/java/finalProject/SupervisorChatServerTest.java`

- [ ] Write failing test for `GET /api/map`.
- [ ] Add `SimulationRuntime.mapSnapshot()`.
- [ ] Add `/api/map` handler.
- [ ] Run `gradle test --tests finalProject.SupervisorChatServerTest`.

### Task 3: Frontend Map UI

**Files:**
- Modify: `FinalProject/src/main/resources/supervisor/index.html`
- Modify: `FinalProject/src/main/resources/supervisor/styles.css`
- Modify: `FinalProject/src/main/resources/supervisor/app.js`

- [ ] Add map shell above the existing workspace.
- [ ] Render locations, route lines, active trip markers, queue heat, active-trip list, and queue list.
- [ ] Poll `/api/map` every two seconds and after simulation start.
- [ ] Keep layout responsive and preserve chat usability.

### Task 4: Verification

**Files:**
- No new files unless a defect is found.

- [ ] Run `gradle test --rerun-tasks`.
- [ ] Start local app.
- [ ] Verify `/api/map` before start, during simulation, and after restart.
- [ ] Verify `http://localhost:8080/` loads the map assets.
