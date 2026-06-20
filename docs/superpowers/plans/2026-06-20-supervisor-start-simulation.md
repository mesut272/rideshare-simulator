# Supervisor Start Simulation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the supervisor web console start the backend ride dispatch simulation on demand.

**Architecture:** Add a small `SimulationControl` boundary that wraps `SimulationEngine` start/status operations and prevents duplicate starts. `SupervisorChatServer` exposes `/api/simulation/start` and `/api/simulation/status`; the browser UI shows a start button and live status.

**Tech Stack:** Java 17, JDK `HttpServer`, Gson, JUnit 5, browser-native HTML/CSS/JavaScript.

---

## Tasks

### Task 1: Server Simulation API

**Files:**
- Create: `FinalProject/src/main/java/finalProject/SimulationControl.java`
- Modify: `FinalProject/src/main/java/finalProject/SupervisorChatServer.java`
- Modify: `FinalProject/src/test/java/finalProject/SupervisorChatServerTest.java`

- [ ] Add a failing test that calls `POST /api/simulation/start` and asserts the fake control's `start()` method is called once and returns `{"started":true}`.
- [ ] Add a failing test that calls `GET /api/simulation/status` and asserts `started`, `running`, `waitingQueueSize`, and `completedCount` are returned.
- [ ] Implement `SimulationControl` and its `forEngine(SimulationEngine)` factory.
- [ ] Implement simulation API handlers in `SupervisorChatServer`.
- [ ] Run `gradle test --tests finalProject.SupervisorChatServerTest`.

### Task 2: RideSharingApp Delayed Start

**Files:**
- Modify: `FinalProject/src/main/java/finalProject/RideSharingApp.java`

- [ ] Remove automatic `engine.start()` from app startup.
- [ ] Pass `SimulationControl.forEngine(engine)` into `SupervisorChatServer`.
- [ ] Keep shutdown behavior unchanged.
- [ ] Run the full Gradle test suite.

### Task 3: Frontend Control

**Files:**
- Modify: `FinalProject/src/main/resources/supervisor/index.html`
- Modify: `FinalProject/src/main/resources/supervisor/styles.css`
- Modify: `FinalProject/src/main/resources/supervisor/app.js`

- [ ] Add a prominent "启动派单模拟" button and simulation status metrics.
- [ ] Wire the button to `POST /api/simulation/start`.
- [ ] Poll `GET /api/simulation/status` alongside driver refresh.
- [ ] Disable the button after simulation has started.
- [ ] Manually verify `http://localhost:8080/` can start the backend simulation.
