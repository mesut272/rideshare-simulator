(function (root) {
    function routeControlPoint(start, end) {
        return {
            x: (start.x + end.x) / 2,
            y: (start.y + end.y) / 2 - 28
        };
    }

    function routePath(locations, from, to) {
        const start = locations[from];
        const end = locations[to];
        if (!start || !end) {
            return "";
        }
        const control = routeControlPoint(start, end);
        return `M ${start.x} ${start.y} Q ${control.x} ${control.y} ${end.x} ${end.y}`;
    }

    function pointAlongRoute(locations, from, to, progressPercent) {
        const start = locations[from];
        const end = locations[to];
        if (!start || !end) {
            return null;
        }

        const progress = Math.max(0, Math.min(1, progressPercent / 100));
        const inverse = 1 - progress;
        const control = routeControlPoint(start, end);

        return {
            x: inverse * inverse * start.x + 2 * inverse * progress * control.x + progress * progress * end.x,
            y: inverse * inverse * start.y + 2 * inverse * progress * control.y + progress * progress * end.y
        };
    }

    root.DispatchMapGeometry = {
        routePath,
        pointAlongRoute
    };
})(typeof window !== "undefined" ? window : globalThis);
