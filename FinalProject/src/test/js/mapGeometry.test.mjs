import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import vm from "node:vm";
import test from "node:test";

const source = await readFile(new URL("../../main/resources/supervisor/mapGeometry.js", import.meta.url), "utf8");
const sandbox = { window: {}, globalThis: {} };
vm.runInNewContext(source, sandbox);
const geometry = sandbox.window.DispatchMapGeometry;

test("pointAlongRoute follows the same quadratic curve used by routePath", () => {
    const locations = {
        UW: { x: 426, y: 82 },
        Bellevue: { x: 612, y: 190 }
    };

    const midpoint = geometry.pointAlongRoute(locations, "UW", "Bellevue", 50);

    assert.equal(midpoint.x, 519);
    assert.equal(midpoint.y, 122);
});

test("routePath and pointAlongRoute share the same control point", () => {
    const locations = {
        Airport: { x: 300, y: 382 },
        NEU: { x: 352, y: 184 }
    };

    assert.equal(geometry.routePath(locations, "Airport", "NEU"), "M 300 382 Q 326 255 352 184");
    const start = geometry.pointAlongRoute(locations, "Airport", "NEU", 0);
    const end = geometry.pointAlongRoute(locations, "Airport", "NEU", 100);

    assert.equal(start.x, 300);
    assert.equal(start.y, 382);
    assert.equal(end.x, 352);
    assert.equal(end.y, 184);
});
