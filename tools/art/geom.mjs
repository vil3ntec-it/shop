/**
 *  Small 2D geometry for the art tools: flatten a path to points, point-in-polygon, distance to a polygon's edge.
 *  Used by the kit (to keep a face's features inside its head) and by check.mjs (the face guard).
 */
import { normPath } from './engine.mjs';

/** flatten an SVG path (any of M L H V C S Q T Z) to subpaths of points; `steps` samples per curve */
export function flatten(d, steps = 12) {
  const ops = typeof d === 'string' ? normPath(d) : d;
  const subs = [];
  let cur = null, x = 0, y = 0;
  for (const o of ops) {
    if (o[0] === 'M') { cur = [[o[1], o[2]]]; subs.push(cur); x = o[1]; y = o[2]; } else if (o[0] === 'L') { cur.push([o[1], o[2]]); x = o[1]; y = o[2]; } else if (o[0] === 'C') {
      for (let i = 1; i <= steps; i++) {
        const t = i / steps, u = 1 - t;
        cur.push([
          u * u * u * x + 3 * u * u * t * o[1] + 3 * u * t * t * o[3] + t * t * t * o[5],
          u * u * u * y + 3 * u * u * t * o[2] + 3 * u * t * t * o[4] + t * t * t * o[6],
        ]);
      }
      x = o[5]; y = o[6];
    }
  }
  return subs;
}

/** even-odd point-in-polygon */
export function inside(poly, px, py) {
  let c = false;
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const [xi, yi] = poly[i], [xj, yj] = poly[j];
    if ((yi > py) !== (yj > py) && px < ((xj - xi) * (py - yi)) / (yj - yi) + xi) c = !c;
  }
  return c;
}

/** distance from a point to the polygon's outline */
export function edgeDistance(poly, px, py) {
  let best = Infinity;
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const [ax, ay] = poly[j], [bx, by] = poly[i];
    const dx = bx - ax, dy = by - ay;
    const l2 = dx * dx + dy * dy || 1;
    const t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / l2));
    best = Math.min(best, Math.hypot(px - (ax + t * dx), py - (ay + t * dy)));
  }
  return best;
}

/** signed margin: + inside by that much, − outside by that much */
export const margin = (poly, px, py) => (inside(poly, px, py) ? 1 : -1) * edgeDistance(poly, px, py);

export function bbox(points) {
  let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
  for (const [x, y] of points) { x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y); }
  return [x0, y0, x1, y1];
}

/** points on the outline of a box (corners + `n` per edge) */
export function boxPoints([x0, y0, x1, y1], n = 6) {
  const out = [];
  for (let i = 0; i <= n; i++) {
    const t = i / n;
    out.push([x0 + (x1 - x0) * t, y0], [x0 + (x1 - x0) * t, y1], [x0, y0 + (y1 - y0) * t], [x1, y0 + (y1 - y0) * t]);
  }
  return out;
}
