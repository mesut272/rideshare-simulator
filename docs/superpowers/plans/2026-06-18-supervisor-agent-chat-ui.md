# Supervisor Agent Chat UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a browser-based supervisor console that talks to the existing ride-sharing AI agent through real local HTTP APIs.

**Architecture:** Add a small JDK `HttpServer` wrapper that owns HTTP routing, JSON responses, and static asset serving. Keep AI construction in `RideSharingApp`; inject `TaxiAgentInterface` and `DriverCacheService` into the server so tests can use fakes.

**Tech Stack:** Java 17, Gradle, JUnit 5, JDK `HttpServer`, browser-native HTML/CSS/JavaScript.

---

## File Structure

- Create `FinalProject/src/main/java/finalProject/SupervisorChatServer.java`: embedded HTTP server, static routes, `/api/chat`, `/api/drivers`.
- Modify `FinalProject/src/main/java/finalProject/RideSharingApp.java`: start `SupervisorChatServer` after building the real agent.
- Create `FinalProject/src/main/resources/supervisor/index.html`: supervisor chat UI shell.
- Create `FinalProject/src/main/resources/supervisor/styles.css`: polished operations-console styling.
- Create `FinalProject/src/main/resources/supervisor/app.js`: browser chat behavior, driver refresh, quick prompts.
- Create `FinalProject/src/test/java/finalProject/SupervisorChatServerTest.java`: deterministic server tests with fake agent and fake driver cache data.
- Modify `FinalProject/build.gradle`: add dependencies already required by the existing LangChain4j source if Gradle compilation needs them.

## Tasks

### Task 1: Chat API Red-Green

**Files:**
- Create: `FinalProject/src/test/java/finalProject/SupervisorChatServerTest.java`
- Create: `FinalProject/src/main/java/finalProject/SupervisorChatServer.java`

- [ ] Write a failing JUnit test that starts `SupervisorChatServer` on port `0`, posts `{"message":"查看所有司机"}` to `/api/chat`, and asserts the response contains `"reply":"收到: 查看所有司机"`.
- [ ] Run `cd FinalProject && ./gradlew test --tests finalProject.SupervisorChatServerTest` and verify it fails because `SupervisorChatServer` does not exist.
- [ ] Implement the minimal server constructor, `start()`, `stop()`, `getPort()`, `/api/chat` handler, and JSON escaping.
- [ ] Re-run the same Gradle test and verify it passes.

### Task 2: Driver Snapshot API Red-Green

**Files:**
- Modify: `FinalProject/src/test/java/finalProject/SupervisorChatServerTest.java`
- Modify: `FinalProject/src/main/java/finalProject/SupervisorChatServer.java`

- [ ] Add a failing test that inserts two drivers into `DriverCacheService`, calls `/api/drivers`, and asserts driver IDs, locations, availability booleans, and counts are present.
- [ ] Run the focused Gradle test and verify it fails because `/api/drivers` is not implemented.
- [ ] Implement `/api/drivers` with a JSON object containing `total`, `available`, and `drivers`.
- [ ] Re-run the focused Gradle test and verify it passes.

### Task 3: Static Asset Routes Red-Green

**Files:**
- Modify: `FinalProject/src/test/java/finalProject/SupervisorChatServerTest.java`
- Modify: `FinalProject/src/main/java/finalProject/SupervisorChatServer.java`
- Create: `FinalProject/src/main/resources/supervisor/index.html`
- Create: `FinalProject/src/main/resources/supervisor/styles.css`
- Create: `FinalProject/src/main/resources/supervisor/app.js`

- [ ] Add a failing test that calls `/` and asserts HTML returns HTTP 200 and includes `监管 Agent`.
- [ ] Run the focused Gradle test and verify it fails because static assets are not implemented.
- [ ] Add the static route support and create the UI resource files.
- [ ] Re-run the focused Gradle test and verify it passes.

### Task 4: Application Integration

**Files:**
- Modify: `FinalProject/src/main/java/finalProject/RideSharingApp.java`
- Modify: `FinalProject/build.gradle` if needed

- [ ] Update `RideSharingApp` so it constructs the real agent with `new TaxiAgentAdapter(engine.getDriverCache(), engine.getOrderIndex(), engine::submitManualOrder)`.
- [ ] Start `SupervisorChatServer` on port `8080` and print `http://localhost:8080/`.
- [ ] Remove the blocking console scanner loop from the main browser path.
- [ ] Run `cd FinalProject && ./gradlew test` and fix compile or test failures.

### Task 5: Manual Verification

**Files:**
- No new code unless verification exposes a defect.

- [ ] Start the app with `cd FinalProject && ./gradlew run`.
- [ ] Open `http://localhost:8080/`.
- [ ] Verify the page loads, driver data appears, sending a message shows a loading state, and the server responds through the real agent path.
- [ ] Stop the server process before finishing.

## Self-Review

- Spec coverage: chat API, driver API, static UI, error handling, browser-first app flow, and deterministic tests are covered.
- Placeholder scan: no TBD/TODO placeholders are intentionally left in the plan.
- Type consistency: the server depends on existing `TaxiAgentInterface`, `DriverCacheService`, and `Driver` APIs verified in the codebase.
