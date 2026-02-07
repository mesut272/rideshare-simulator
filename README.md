# Ride-Sharing Dispatch Simulator

A multi-threaded ride-sharing dispatch system simulation built in Java, demonstrating concurrent request processing, priority-based scheduling, and real-time performance metrics.

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)
[![Gradle](https://img.shields.io/badge/Gradle-8.0+-green.svg)](https://gradle.org/)
[![JUnit](https://img.shields.io/badge/JUnit-5.10-blue.svg)](https://junit.org/junit5/)

---

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [System Architecture](#system-architecture)
- [Technology Stack](#technology-stack)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Design Decisions](#design-decisions)
- [Performance Metrics](#performance-metrics)
- [Testing](#testing)
- [Future Enhancements](#future-enhancements)

---

## 🎯 Overview

This project simulates a ride-sharing dispatch system (similar to Uber/Lyft) that handles:
- **Dynamic ride request generation** with realistic customer demand patterns
- **Multi-threaded concurrent processing** for requests, dispatch, and completion
- **Priority-based scheduling** supporting 4 ride types (Express, Standard, Wait & Save, Environmental)
- **Real-time performance tracking** including wait times and completion rates

### Key Highlights
- **Concurrency:** 3 independent threads handling generation, dispatch, and completion
- **Thread Safety:** Uses `AtomicInteger`, `PriorityBlockingQueue`, and `synchronized` blocks
- **Smart Scheduling:** Composite ordering strategy (Priority → Time → Distance)
- **Production-Ready:** Comprehensive unit tests with 25+ test cases

---

## ✨ Features

### Core Functionality
- ✅ **Random Ride Generation:** Simulates realistic customer requests across 6 Seattle locations
- ✅ **Priority Queue Dispatch:** Express pickups get served first, followed by Standard, then Wait & Save
- ✅ **Multi-Driver Management:** Concurrent driver pool with automatic availability tracking
- ✅ **Real-Time Monitoring:** Live console output showing every request, dispatch, and completion
- ✅ **Performance Analytics:** Automatic calculation of average/max/min wait times

### Ride Types (Priority-Based)
1. **Express Pickup** (Priority 1) - Fastest response, premium service
2. **Standard Pickup** (Priority 2) - Default ride type
3. **Wait & Save Pickup** (Priority 3) - Budget-friendly, longer wait acceptable
4. **Environmental Pickup** (Priority 4) - Eco-friendly option

### City Map
Simulates 6 key Seattle locations:
- University of Washington (UW)
- Northeastern University (NEU)
- Space Needle
- South Lake Union (SLU)
- Bellevue
- SeaTac Airport

---

## 🏗️ System Architecture

### Overview

The system consists of three concurrent threads that communicate through thread-safe blocking queues:

```
┌─────────────────────────────────────────────────────────────┐
│                    SimulationEngine                          │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │  Generator   │    │  Dispatcher  │    │  Completion  │  │
│  │   Thread     │    │    Thread    │    │   Thread     │  │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘  │
│         │                   │                    │           │
│         ↓                   ↓                    ↓           │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │   Waiting    │───→│    Active    │───→│  Available   │  │
│  │    Queue     │    │    Rides     │    │   Drivers    │  │
│  │ (Priority)   │    │ (Completion) │    │    Pool      │  │
│  └──────────────┘    └──────────────┘    └──────────────┘  │
│                                                               │
└─────────────────────────────────────────────────────────────┘
``` Rides<br/>PriorityBlockingQueue]
        DP[Available Drivers<br/>BlockingQueue]
    end
    
    Gen -->|puts| WQ
    WQ -->|polls| Disp
    DP -->|polls| Disp
    Disp -->|creates| AR
    AR -->|polls| Comp
    Comp -->|returns| DP
    
    style Gen fill:#e1f5ff,stroke:#0066cc,stroke-width:2px
    style Disp fill:#fff3cd,stroke:#ff9900,stroke-width:2px
    style Comp fill:#d4edda,stroke:#28a745,stroke-width:2px
    style WQ fill:#f8d7da,stroke:#dc3545,stroke-width:2px
    style AR fill:#fff3cd,stroke:#ffc107,stroke-width:2px
    style DP fill:#d1ecf1,stroke:#17a2b8,stroke-width:2px
```

### Component Interaction Flow

```mermaid
sequenceDiagram
    participant C as Customer
    participant G as Generator
    participant WQ as Waiting Queue
    participant D as Dispatcher
    participant DR as Driver Pool
    participant AR as Active Rides
    participant CO as Completion

    C->>G: Request Ride
    G->>WQ: Add Request (with priority)
    
    loop Every 50ms
        D->>WQ: Poll Request
        D->>DR: Check Available Driver
        alt Driver Available
            DR-->>D: Return Driver
            D->>AR: Create Active Ride
            D->>C: Notify Dispatched
        else No Driver
            D->>WQ: Re-queue Request
            D->>C: Notify Waiting
        end
    end
    
    loop Every 50ms
        CO->>AR: Check Completion Time
        alt Ride Completed
            AR-->>CO: Return Completed Ride
            CO->>DR: Return Driver to Pool
            CO->>C: Notify Completed
        end
    end
```

### Data Flow Diagram

```mermaid
flowchart LR
    A[RideRequest<br/>Generated] --> B{Priority<br/>Queue}
    B -->|Priority 1| C[Express]
    B -->|Priority 2| D[Standard]
    B -->|Priority 3| E[Wait & Save]
    B -->|Priority 4| F[Environmental]
    
    C --> G[Dispatcher]
    D --> G
    E --> G
    F --> G
    
    G --> H{Driver<br/>Available?}
    H -->|Yes| I[Match & Dispatch]
    H -->|No| J[Wait in Queue]
    J --> B
    
    I --> K[Active Ride]
    K --> L[Completion Check]
    L --> M[Return Driver]
    M --> N[Available Pool]
    
    style A fill:#e3f2fd
    style B fill:#fff3e0
    style G fill:#f3e5f5
    style I fill:#e8f5e9
    style K fill:#fce4ec
```

### Thread Architecture

#### 1. Generator Thread
- Generates ride requests at configured intervals
- Assigns random start/destination from city map
- Calculates anticipated distance
- Pushes requests to priority queue

#### 2. Dispatcher Thread
- Continuously polls waiting queue (respects priority)
- Matches available drivers to requests
- Sets actual start time and expected completion time
- Moves matched rides to active queue
- Provides waiting queue feedback to customers

#### 3. Completion Thread
- Monitors active rides by completion time
- Returns completed drivers to available pool
- Calculates and accumulates performance metrics
- Triggers shutdown when all work is done

---

## 🛠️ Technology Stack

| Component | Technology | Purpose |
|-----------|------------|---------|
| **Language** | Java 17+ | Core development |
| **Build Tool** | Gradle 8.0+ | Dependency management |
| **Testing** | JUnit 5.10 | Unit testing framework |
| **Concurrency** | `java.util.concurrent` | Thread-safe data structures |
| **Collections** | `PriorityBlockingQueue` | Priority-based scheduling |
| **Synchronization** | `AtomicInteger`, `synchronized` | Thread safety |

---

## 🚀 Getting Started

### Prerequisites
- Java 17 or higher
- Gradle 8.0 or higher (or use included Gradle wrapper)

### Installation

1. **Clone the repository**
```bash
git clone https://github.com/yourusername/rideshare-simulator.git
cd rideshare-simulator
```

2. **Build the project**
```bash
./gradlew build
```

3. **Run the simulation**
```bash
./gradlew run
```

Or run directly in IntelliJ IDEA:
- Open `RideSharingApp.java`
- Click the green play button ▶️

### Running Tests
```bash
./gradlew test
```

View test report:
```bash
open build/reports/tests/test/index.html
```

---

## ⚙️ Configuration

Edit `RideSharingApp.java` to customize simulation parameters:

```java
SimulationConfig config = new SimulationConfig(
    3,      // Number of drivers
    2000,   // Request interval (ms)
    60,     // Runtime (seconds)
    10      // Max requests to generate
);
```

### Configuration Options

| Parameter | Description | Default | Recommended Range |
|-----------|-------------|---------|-------------------|
| `driverCount` | Number of concurrent drivers | 3 | 1-10 |
| `requestIntervalMs` | Time between requests (ms) | 2000 | 500-5000 |
| `runtimeSeconds` | Simulation duration | 60 | 30-300 |
| `maxRequests` | Total requests to generate | 10 | 5-100 |

---

## 🧠 Design Decisions

### 1. **Time Semantics (Critical Fix)**
**Problem:** Original design had conflicting time fields causing incorrect wait time calculations.

**Solution:**
```java
private final LocalDateTime requestTimestamp;  // Immutable, set at creation
private LocalDateTime actualStartTime;         // Set once at dispatch
private LocalDateTime expectedCompletionTime;  // Calculated from start time
```

**Rationale:** Single Source of Truth principle - each time has one authoritative setter.

### 2. **Priority Ordering Strategy**
Uses a **composite comparator** with 3 levels:
1. **Priority Level** (Express > Standard > Wait & Save > Environmental)
2. **Request Time** (earlier requests first)
3. **Distance** (longer trips first, to maximize driver utilization)

**Trade-off:** Balances customer satisfaction (priority/time) with operational efficiency (distance).

### 3. **Dispatch Strategies Comparison**

The system supports multiple dispatch strategies that can be switched via configuration:

#### **Strategy 1: COMPOSITE (Priority-Based)**
- **Algorithm:** Priority → Time → Distance
- **Implementation:** Uses PriorityBlockingQueue with composite comparator
- **Pros:** 
  - Respects service tiers (Express customers get priority)
  - Fair to customers within same tier (FIFO)
  - Predictable behavior
- **Cons:**
  - May assign far-away drivers
  - Higher fuel costs
  - Longer pickup times

**Use Case:** When customer satisfaction and service tier differentiation are critical.

#### **Strategy 2: NEAREST_DRIVER**
- **Algorithm:** Find closest available driver to pickup location
- **Implementation:** Iterates through available drivers, calculates distances
- **Pros:**
  - Minimizes pickup time (driver reaches customer faster)
  - Better fuel efficiency
  - Lower operational costs
  - Improved driver experience (less idle driving)
- **Cons:**
  - Ignores ride priority (Express customers may wait)
  - May leave distant customers waiting
  - Less predictable for premium tiers

**Use Case:** When operational efficiency and quick response time matter most.

#### **Performance Comparison**

**Test Environment:**
- 10 ride requests
- 3 drivers
- Random locations across Seattle
- 2-second request interval

| Metric | COMPOSITE | NEAREST_DRIVER | Winner | % Improvement |
|--------|-----------|----------------|--------|---------------|
| Average Wait Time | 0.30s | [Run test] | TBD | TBD |
| Max Wait Time | 1s | [Run test] | TBD | TBD |
| Min Wait Time | 0s | [Run test] | TBD | TBD |
| Average Ride Duration | 4.90s | [Run test] | TBD | TBD |

**Analysis:**
- **COMPOSITE Strategy:** Achieved 0.30s average wait time with all customers served in priority order. Maximum wait was only 1 second, indicating good driver availability.
- **NEAREST_DRIVER Strategy:** [Add your findings after testing]

**Recommendation:** 
- Use **COMPOSITE** for premium service focus
- Use **NEAREST_DRIVER** for cost optimization
- Future work: Hybrid strategy combining both approaches

### 4. **Thread Safety**
- `AtomicInteger` for counters (lock-free, high performance)
- `PriorityBlockingQueue` for request/ride queues (built-in synchronization)
- `synchronized` blocks only for statistics accumulation (low contention)

**Rationale:** Minimize lock contention while ensuring correctness.

### 5. **Configuration-Driven Design**
All simulation parameters are externalized to `config.properties`:
```properties
simulation.driver.count=3
simulation.request.interval.ms=2000
simulation.max.requests=10
simulation.dispatch.strategy=COMPOSITE  # Switch strategies here
```

**Benefits:**
- No code recompilation needed for parameter changes
- Easy A/B testing of different strategies
- Production-ready configuration management

### 6. **Waiting Queue Feedback**
**Challenge:** Same request was being repeatedly notified in the queue.

**Solution:** Use `ConcurrentHashMap.newKeySet()` to track already-notified requests.

```java
private final Set<String> waitingNotified = ConcurrentHashMap.newKeySet();
```

**Trade-off:** Small memory overhead for better user experience.

---

## 📊 Performance Metrics

The simulator automatically tracks:

```
=== Simulation Summary ===
Created:    10
Dispatched: 10
Completed:  10
✓ OK: All requests processed

=== Performance Metrics ===
Average wait time:      1.20 seconds
Max wait time:          5 seconds
Min wait time:          0 seconds
Average ride duration:  6.80 seconds
```

### Metrics Explained
- **Average Wait Time:** Time from request to driver assignment
- **Max/Min Wait Time:** Identifies best/worst case scenarios
- **Average Ride Duration:** Actual service time per ride

---

## 🧪 Testing

### Test Coverage
- **RideRequestTest** (10 tests): Time setting logic, priority mapping, immutability
- **StrategyCompositeOrderingTest** (7 tests): Multi-level sorting, queue behavior
- **CityMapTest** (8 tests): Distance calculations, symmetry, boundary cases

### Running Specific Tests
```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests RideRequestTest

# Run with verbose output
./gradlew test --info
```

### Test Quality Standards
- ✅ Unit tests for all core logic
- ✅ Edge case coverage (null checks, duplicate operations)
- ✅ Integration tests (PriorityQueue behavior)
- ✅ Property-based tests (symmetry, positivity constraints)

---

## 🔮 Future Enhancements

### Planned Features (Days 3-20)
- [ ] **Multiple Dispatch Strategies:** Implement nearest-driver, FIFO, and load-balancing algorithms
- [ ] **Algorithm Comparison:** A/B testing framework with CSV reports
- [ ] **Data Visualization:** Charts for wait time distribution, driver utilization
- [ ] **Performance Testing:** Stress test with 1000+ concurrent requests
- [ ] **Configuration Files:** YAML/properties-based config (no hardcoding)
- [ ] **Logging Framework:** Replace System.out with Log4j2
- [ ] **Monitoring Dashboard:** Simple web UI for real-time metrics

### Stretch Goals
- [ ] Dynamic pricing (surge pricing during high demand)
- [ ] Ride pooling/carpool support
- [ ] Driver rating system
- [ ] Geospatial optimization (replace distance with actual routes)

---

## 📄 License

This project is created for educational purposes as part of a graduate-level software engineering portfolio.

---

## 👤 Author

**Lexin Yi**
- GitHub: https://github.khoury.northeastern.edu/ylx1187304321/rideshare-simulator.git
- LinkedIn: www.linkedin.com/in/lexinyi
- Email: yi.l@northeastern.edu

---

## 🙏 Acknowledgments

- Inspired by real-world ride-sharing platforms (Uber, Lyft, Didi)
- Built as a demonstration project for MSCS internship applications
- Special thanks to the Java concurrency community for best practices

---

**⭐ If you find this project helpful, please star it on GitHub!**