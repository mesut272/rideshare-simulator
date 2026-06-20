const messages = document.querySelector("#messages");
const form = document.querySelector("#chatForm");
const input = document.querySelector("#messageInput");
const sendButton = document.querySelector("#sendButton");
const clearChatButton = document.querySelector("#clearChatButton");
const totalDrivers = document.querySelector("#totalDrivers");
const availableDrivers = document.querySelector("#availableDrivers");
const connectionStatus = document.querySelector("#connectionStatus");
const startSimulationButton = document.querySelector("#startSimulationButton");
const simulationState = document.querySelector("#simulationState");
const waitingQueueSize = document.querySelector("#waitingQueueSize");
const completedCount = document.querySelector("#completedCount");
const baseRoutes = document.querySelector("#baseRoutes");
const activeRoutes = document.querySelector("#activeRoutes");
const locationLayer = document.querySelector("#locationLayer");
const tripMarkerLayer = document.querySelector("#tripMarkerLayer");
const mapEmptyState = document.querySelector("#mapEmptyState");
const activeTripCount = document.querySelector("#activeTripCount");
const activeTripList = document.querySelector("#activeTripList");
const queueList = document.querySelector("#queueList");
const toggleQueuesButton = document.querySelector("#toggleQueuesButton");
const toggleRoutesButton = document.querySelector("#toggleRoutesButton");
const selectedLocationTitle = document.querySelector("#selectedLocationTitle");
const selectedLocationCount = document.querySelector("#selectedLocationCount");
const locationQueueDetail = document.querySelector("#locationQueueDetail");
const manualOrderForm = document.querySelector("#manualOrderForm");
const passengerNameInput = document.querySelector("#passengerNameInput");
const orderStartSelect = document.querySelector("#orderStartSelect");
const orderDestinationSelect = document.querySelector("#orderDestinationSelect");
const orderRideTypeSelect = document.querySelector("#orderRideTypeSelect");
const submitOrderButton = document.querySelector("#submitOrderButton");
const orderFormStatus = document.querySelector("#orderFormStatus");

const mapLocations = {
    SpaceNeedle: { x: 132, y: 180, label: "Space Needle" },
    SLU: { x: 252, y: 136, label: "SLU" },
    NEU: { x: 352, y: 184, label: "NEU" },
    UW: { x: 426, y: 82, label: "UW" },
    Bellevue: { x: 612, y: 190, label: "Bellevue" },
    Airport: { x: 300, y: 382, label: "Airport" }
};

const baseRoutePairs = [
    ["SpaceNeedle", "SLU"], ["SLU", "NEU"], ["NEU", "UW"],
    ["SLU", "Bellevue"], ["UW", "Bellevue"], ["Airport", "NEU"],
    ["Airport", "Bellevue"], ["Airport", "SLU"], ["SpaceNeedle", "Bellevue"]
];
const queuePreviewSlots = 3;

let showQueues = true;
let showRoutes = true;
let selectedLocation = null;
let selectedActiveTripKey = null;
let queueDetailRequestId = 0;
const animatedTrips = new Map();
const markerElements = new Map();
let activeTripPopover = null;

function timeLabel() {
    return new Intl.DateTimeFormat("zh-CN", {
        hour: "2-digit",
        minute: "2-digit"
    }).format(new Date());
}

function appendMessage(role, text, pending = false) {
    const article = document.createElement("article");
    article.className = `message ${role}`;
    if (pending) {
        article.dataset.pending = "true";
    }

    const avatar = document.createElement("div");
    avatar.className = "avatar";
    avatar.textContent = role === "user" ? "你" : "AI";

    const bubble = document.createElement("div");
    bubble.className = "bubble";

    const meta = document.createElement("div");
    meta.className = "message-meta";
    meta.innerHTML = `<span>${role === "user" ? "监管员" : "监管 Agent"}</span><time>${timeLabel()}</time>`;

    const paragraph = document.createElement("p");
    paragraph.textContent = text;

    bubble.append(meta, paragraph);
    article.append(avatar, bubble);
    messages.append(article);
    messages.scrollTop = messages.scrollHeight;
    return article;
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function locationLabel(location) {
    return mapLocations[location]?.label || location;
}

function populateOrderLocationSelects() {
    const options = Object.entries(mapLocations)
        .map(([value, location]) => `<option value="${value}">${location.label}</option>`)
        .join("");
    orderStartSelect.innerHTML = options;
    orderDestinationSelect.innerHTML = options;
    orderStartSelect.value = "UW";
    orderDestinationSelect.value = "Bellevue";
}

function routePath(from, to) {
    return window.DispatchMapGeometry.routePath(mapLocations, from, to);
}

function pointAlongRoute(from, to, progressPercent) {
    return window.DispatchMapGeometry.pointAlongRoute(mapLocations, from, to, progressPercent);
}

function tripKey(trip) {
    return `${trip.customerId || trip.driverId}|${trip.driverId}|${trip.startLocation}|${trip.destination}`;
}

function currentTripProgress(tripState) {
    const baseProgress = Number(tripState.baseProgress) || 0;
    const remainingSeconds = Number(tripState.remainingSeconds) || 0;
    if (remainingSeconds <= 0 || baseProgress >= 100) {
        return Math.min(100, Math.max(0, baseProgress));
    }

    const elapsedSeconds = (performance.now() - tripState.receivedAt) / 1000;
    const progressPerSecond = (100 - baseProgress) / remainingSeconds;
    return Math.min(100, Math.max(0, baseProgress + elapsedSeconds * progressPerSecond));
}

function syncAnimatedTrips(activeTrips) {
    const liveKeys = new Set();

    activeTrips.forEach((trip) => {
        if (!mapLocations[trip.startLocation] || !mapLocations[trip.destination]) {
            return;
        }

        const key = tripKey(trip);
        const existing = animatedTrips.get(key);
        const backendProgress = Number(trip.progressPercent) || 0;
        const baseProgress = existing
            ? Math.max(backendProgress, currentTripProgress(existing))
            : backendProgress;

        liveKeys.add(key);
        animatedTrips.set(key, {
            ...trip,
            baseProgress,
            remainingSeconds: Number(trip.remainingSeconds) || 0,
            receivedAt: performance.now()
        });
    });

    [...animatedTrips.keys()].forEach((key) => {
        if (!liveKeys.has(key)) {
            animatedTrips.delete(key);
            markerElements.get(key)?.remove();
            markerElements.delete(key);
            if (selectedActiveTripKey === key) {
                selectedActiveTripKey = null;
            }
        }
    });
}

function renderActiveTripPopover(tripState, point) {
    if (!selectedActiveTripKey || !tripState || !point) {
        activeTripPopover?.remove();
        activeTripPopover = null;
        return;
    }

    if (!activeTripPopover) {
        activeTripPopover = document.createElement("div");
        activeTripPopover.className = "trip-popover";
        tripMarkerLayer.append(activeTripPopover);
    }

    const customerId = tripState.customerId || "Unknown passenger";
    activeTripPopover.style.left = `${point.x / 7.2}%`;
    activeTripPopover.style.top = `${point.y / 4.2}%`;
    activeTripPopover.innerHTML = `
        <strong>${escapeHtml(customerId)}</strong>
        <dl>
            <dt>司机</dt>
            <dd>${escapeHtml(tripState.driverId)}</dd>
            <dt>路线</dt>
            <dd>${escapeHtml(locationLabel(tripState.startLocation))} → ${escapeHtml(locationLabel(tripState.destination))}</dd>
            <dt>类型</dt>
            <dd>${escapeHtml(tripState.rideType)}</dd>
            <dt>距离</dt>
            <dd>${Number(tripState.anticipatedDistance || 0).toFixed(1)} mi</dd>
            <dt>进度</dt>
            <dd>${Math.round(currentTripProgress(tripState))}% · ${tripState.remainingSeconds}s</dd>
        </dl>
    `;
}

function renderTripMarkersFrame() {
    let selectedTripState = null;
    let selectedPoint = null;

    animatedTrips.forEach((tripState, key) => {
        const progress = currentTripProgress(tripState);
        const point = pointAlongRoute(tripState.startLocation, tripState.destination, progress);
        if (!point) {
            return;
        }

        let marker = markerElements.get(key);
        if (!marker) {
            marker = document.createElement("div");
            marker.className = "trip-marker";
            marker.innerHTML = `
                <span class="marker-dot"></span>
                <span class="marker-label"></span>
            `;
            marker.setAttribute("role", "button");
            marker.setAttribute("tabindex", "0");
            marker.addEventListener("click", () => {
                selectedActiveTripKey = key;
            });
            marker.addEventListener("keydown", (event) => {
                if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    selectedActiveTripKey = key;
                }
            });
            tripMarkerLayer.append(marker);
            markerElements.set(key, marker);
        }

        marker.style.left = `${point.x / 7.2}%`;
        marker.style.top = `${point.y / 4.2}%`;
        marker.classList.toggle("selected", selectedActiveTripKey === key);
        marker.setAttribute("aria-label", `查看 ${tripState.customerId || "乘客"} 的订单详情`);
        marker.querySelector(".marker-label").textContent = `${tripState.customerId || "乘客"} · ${Math.round(progress)}%`;

        if (selectedActiveTripKey === key) {
            selectedTripState = tripState;
            selectedPoint = point;
        }
    });

    renderActiveTripPopover(selectedTripState, selectedPoint);
    requestAnimationFrame(renderTripMarkersFrame);
}

function renderBaseRoutes() {
    baseRoutes.innerHTML = "";
    baseRoutePairs.forEach(([from, to]) => {
        const path = document.createElementNS("http://www.w3.org/2000/svg", "path");
        path.setAttribute("d", routePath(from, to));
        path.setAttribute("class", "base-route");
        baseRoutes.append(path);
    });
}

async function sendMessage(message) {
    appendMessage("user", message);
    const pending = appendMessage("assistant", "正在调用调度 Agent...", true);
    sendButton.disabled = true;

    try {
        const response = await fetch("/api/chat", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ message })
        });
        const payload = await response.json();
        if (!response.ok) {
            throw new Error(payload.error || "调度链路返回错误");
        }
        pending.querySelector("p").textContent = payload.reply;
        pending.removeAttribute("data-pending");
        await loadDrivers();
    } catch (error) {
        pending.querySelector("p").textContent = `调度链路异常：${error.message}`;
        pending.classList.add("error");
    } finally {
        sendButton.disabled = false;
        input.focus();
    }
}

async function loadDrivers() {
    try {
        const response = await fetch("/api/drivers");
        const payload = await response.json();
        if (!response.ok) {
            throw new Error(payload.error || "无法读取司机状态");
        }

        totalDrivers.textContent = payload.total;
        availableDrivers.textContent = payload.available;

        connectionStatus.innerHTML = `<span class="status-dot"></span>本地 API 已连接`;
    } catch (error) {
        totalDrivers.textContent = "--";
        availableDrivers.textContent = "--";
        connectionStatus.innerHTML = `<span class="status-dot"></span>API 连接异常`;
    }
}

function renderMap(snapshot) {
    const queueByLocation = snapshot.queueByLocation || {};
    const activeTrips = snapshot.activeTrips || [];
    const maxQueue = Math.max(1, ...Object.values(queueByLocation));

    const hasVisibleMapActivity = snapshot.running || activeTrips.length > 0 || snapshot.waitingQueueSize > 0;
    mapEmptyState.hidden = snapshot.started && hasVisibleMapActivity;
    if (!snapshot.started) {
        mapEmptyState.textContent = "启动派单模拟后，地图会显示司机路线和地点排队热度。";
    } else if (!hasVisibleMapActivity) {
        mapEmptyState.textContent = "本轮模拟已结束。点击“重新开始模拟”可以开启新一轮地图追踪。";
    }

    locationLayer.innerHTML = "";
    Object.entries(mapLocations).forEach(([name, location]) => {
        const waiting = queueByLocation[name] || 0;
        const heat = showQueues ? Math.min(1, waiting / maxQueue) : 0;
        const node = document.createElement("div");
        node.className = "map-location";
        node.classList.toggle("selected", selectedLocation === name);
        node.setAttribute("role", "button");
        node.setAttribute("tabindex", "0");
        node.setAttribute("aria-label", `查看 ${location.label} 排队乘客`);
        node.style.left = `${location.x / 7.2}%`;
        node.style.top = `${location.y / 4.2}%`;
        node.style.setProperty("--heat", heat.toFixed(2));
        node.innerHTML = `
            <span class="location-name">${location.label}</span>
            <span class="queue-badge">${waiting}</span>
        `;
        node.addEventListener("click", () => selectLocation(name));
        node.addEventListener("keydown", (event) => {
            if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                selectLocation(name);
            }
        });
        locationLayer.append(node);
    });

    activeRoutes.innerHTML = "";
    syncAnimatedTrips(activeTrips);
    activeTrips.forEach((trip) => {
        if (!mapLocations[trip.startLocation] || !mapLocations[trip.destination]) {
            return;
        }

        if (showRoutes) {
            const path = document.createElementNS("http://www.w3.org/2000/svg", "path");
            path.setAttribute("d", routePath(trip.startLocation, trip.destination));
            path.setAttribute("class", "active-route");
            activeRoutes.append(path);
        }
    });

    activeTripCount.textContent = activeTrips.length;
    activeTripList.innerHTML = activeTrips.length === 0
        ? `<p class="empty-state">暂无路上订单。</p>`
        : activeTrips.map((trip) => `
            <article class="active-trip-row">
                <div>
                    <strong>${trip.driverId}</strong>
                    <span>${trip.startLocation} → ${trip.destination}</span>
                </div>
                <div class="trip-progress">
                    <span>${Math.round(trip.progressPercent)}%</span>
                    <small>${trip.remainingSeconds}s</small>
                </div>
            </article>
        `).join("");

    const topQueueRows = Object.keys(mapLocations)
        .map((location) => [location, queueByLocation[location] || 0])
        .sort((a, b) => (b[1] - a[1]) || locationLabel(a[0]).localeCompare(locationLabel(b[0])))
        .slice(0, queuePreviewSlots);
    queueList.innerHTML = topQueueRows.map(([location, count]) => {
        const hasQueue = count > 0;
        return `
            <button class="queue-row ${selectedLocation === location ? "selected" : ""} ${hasQueue ? "" : "empty"}" type="button" data-location="${location}">
                <strong>${locationLabel(location)}</strong>
                <span>${hasQueue ? `${count} waiting` : "0 waiting"}</span>
            </button>
        `;
    }).join("");
    queueList.querySelectorAll("[data-location]").forEach((button) => {
        button.addEventListener("click", () => selectLocation(button.dataset.location));
    });
}

function renderLocationQueueDetail(payload) {
    selectedLocationTitle.textContent = locationLabel(payload.location || selectedLocation);
    selectedLocationCount.textContent = payload.total;

    if (!payload.passengers || payload.passengers.length === 0) {
        locationQueueDetail.innerHTML = `<p class="empty-state">当前地点没有乘客排队。</p>`;
        return;
    }

    locationQueueDetail.innerHTML = payload.passengers.map((passenger) => `
        <article class="passenger-row">
            <div>
                <strong>${escapeHtml(passenger.customerId)}</strong>
                <span>${escapeHtml(locationLabel(passenger.startLocation))} → ${escapeHtml(locationLabel(passenger.destination))}</span>
            </div>
            <div class="passenger-meta">
                <span>${escapeHtml(passenger.rideType)}</span>
                <small>${Number(passenger.anticipatedDistance).toFixed(1)} mi · 等待 ${passenger.waitingSeconds}s</small>
            </div>
        </article>
    `).join("");
}

async function loadQueueDetail(location) {
    if (!location) {
        return;
    }
    const requestId = ++queueDetailRequestId;
    try {
        const response = await fetch(`/api/queue?location=${encodeURIComponent(location)}`);
        const payload = await response.json();
        if (!response.ok) {
            throw new Error(payload.error || "无法读取地点队列");
        }
        if (requestId !== queueDetailRequestId || selectedLocation !== location) {
            return;
        }
        renderLocationQueueDetail(payload);
    } catch (error) {
        if (requestId !== queueDetailRequestId || selectedLocation !== location) {
            return;
        }
        selectedLocationTitle.textContent = locationLabel(location);
        selectedLocationCount.textContent = "--";
        locationQueueDetail.innerHTML = `<p class="error-text">地点队列刷新失败：${error.message}</p>`;
    }
}

function selectLocation(location) {
    selectedLocation = location;
    selectedLocationTitle.textContent = locationLabel(location);
    selectedLocationCount.textContent = "--";
    orderStartSelect.value = location;
    if (orderDestinationSelect.value === location) {
        orderDestinationSelect.value = Object.keys(mapLocations).find((name) => name !== location) || location;
    }
    loadQueueDetail(location);
    loadMapSnapshot();
}

async function submitManualOrder(event) {
    event.preventDefault();
    const customerId = passengerNameInput.value.trim();
    const startLocation = orderStartSelect.value;
    const destination = orderDestinationSelect.value;
    const rideType = orderRideTypeSelect.value;

    if (!customerId) {
        passengerNameInput.focus();
        orderFormStatus.textContent = "请输入乘客名字。";
        orderFormStatus.className = "form-status error";
        return;
    }

    if (startLocation === destination) {
        orderFormStatus.textContent = "出发地和目的地不能相同。";
        orderFormStatus.className = "form-status error";
        return;
    }

    submitOrderButton.disabled = true;
    orderFormStatus.textContent = "正在提交订单...";
    orderFormStatus.className = "form-status";

    try {
        const response = await fetch("/api/orders", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ customerId, startLocation, destination, rideType })
        });
        const payload = await response.json();
        if (!response.ok) {
            throw new Error(payload.error || "下单失败");
        }

        passengerNameInput.value = "";
        selectedLocation = startLocation;
        orderFormStatus.textContent = `${payload.customerId} 已加入 ${locationLabel(startLocation)} 队列。`;
        orderFormStatus.className = "form-status success";
        appendMessage("assistant", `已创建乘客订单：${payload.customerId}，${locationLabel(startLocation)} → ${locationLabel(destination)}。`);
        await loadMapSnapshot();
        await loadQueueDetail(startLocation);
    } catch (error) {
        orderFormStatus.textContent = `下单失败：${error.message}`;
        orderFormStatus.className = "form-status error";
    } finally {
        submitOrderButton.disabled = false;
    }
}

async function loadMapSnapshot() {
    try {
        const response = await fetch("/api/map");
        const payload = await response.json();
        if (!response.ok) {
            throw new Error(payload.error || "无法读取地图状态");
        }
        renderMap(payload);
    } catch (error) {
        mapEmptyState.hidden = false;
        mapEmptyState.textContent = `地图状态刷新失败：${error.message}`;
    }
}

function renderSimulationStatus(payload) {
    waitingQueueSize.textContent = payload.waitingQueueSize;
    completedCount.textContent = payload.completedCount;

    simulationState.classList.remove("running", "finished");
    if (!payload.started) {
        simulationState.textContent = "未启动";
        startSimulationButton.textContent = "启动派单模拟";
        startSimulationButton.disabled = false;
        return;
    }

    if (payload.running) {
        startSimulationButton.textContent = "模拟运行中";
        startSimulationButton.disabled = true;
        simulationState.textContent = "运行中";
        simulationState.classList.add("running");
    } else {
        startSimulationButton.textContent = "重新开始模拟";
        startSimulationButton.disabled = false;
        simulationState.textContent = "已结束";
        simulationState.classList.add("finished");
    }
}

async function loadSimulationStatus() {
    try {
        const response = await fetch("/api/simulation/status");
        const payload = await response.json();
        if (!response.ok) {
            throw new Error(payload.error || "无法读取模拟状态");
        }
        renderSimulationStatus(payload);
    } catch (error) {
        simulationState.textContent = "状态异常";
        simulationState.classList.remove("running", "finished");
    }
}

async function startSimulation() {
    startSimulationButton.disabled = true;
    startSimulationButton.textContent = "启动中...";

    try {
        const response = await fetch("/api/simulation/start", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: "{}"
        });
        const payload = await response.json();
        if (!response.ok) {
            throw new Error(payload.error || "启动失败");
        }

        renderSimulationStatus(payload);
        appendMessage("assistant", payload.alreadyStarted
            ? "派单模拟已经启动，无需重复启动。"
            : "后端派单模拟已启动，司机状态会自动刷新。");
        await loadDrivers();
        await loadMapSnapshot();
    } catch (error) {
        startSimulationButton.disabled = false;
        appendMessage("assistant", `派单模拟启动失败：${error.message}`);
    } finally {
        await loadSimulationStatus();
    }
}

form.addEventListener("submit", (event) => {
    event.preventDefault();
    const message = input.value.trim();
    if (!message) {
        input.focus();
        return;
    }
    input.value = "";
    sendMessage(message);
});

input.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        form.requestSubmit();
    }
});

document.querySelectorAll("[data-prompt]").forEach((button) => {
    button.addEventListener("click", () => {
        input.value = button.dataset.prompt;
        input.focus();
    });
});

clearChatButton.addEventListener("click", () => {
    messages.innerHTML = "";
    appendMessage("assistant", "对话已清空。你可以继续发送监管指令。");
});

startSimulationButton.addEventListener("click", startSimulation);
manualOrderForm.addEventListener("submit", submitManualOrder);
toggleQueuesButton.addEventListener("click", () => {
    showQueues = !showQueues;
    toggleQueuesButton.classList.toggle("active", showQueues);
    loadMapSnapshot();
});
toggleRoutesButton.addEventListener("click", () => {
    showRoutes = !showRoutes;
    toggleRoutesButton.classList.toggle("active", showRoutes);
    loadMapSnapshot();
});

populateOrderLocationSelects();
renderBaseRoutes();
loadDrivers();
loadSimulationStatus();
loadMapSnapshot();
setInterval(loadDrivers, 8000);
setInterval(loadSimulationStatus, 3000);
setInterval(loadMapSnapshot, 500);
setInterval(() => {
    if (selectedLocation) {
        loadQueueDetail(selectedLocation);
    }
}, 2000);
requestAnimationFrame(renderTripMarkersFrame);
