(() => {
  "use strict";

  const SVG_NS = "http://www.w3.org/2000/svg";

  function svgElement(name, attributes = {}) {
    const element = document.createElementNS(SVG_NS, name);
    Object.entries(attributes).forEach(([key, value]) => element.setAttribute(key, value));
    return element;
  }

  function relativeRect(element, stageRect) {
    const rect = element.getBoundingClientRect();
    return {
      left: rect.left - stageRect.left,
      right: rect.right - stageRect.left,
      top: rect.top - stageRect.top,
      bottom: rect.bottom - stageRect.top,
      width: rect.width,
      height: rect.height,
      centerX: rect.left - stageRect.left + rect.width / 2,
      centerY: rect.top - stageRect.top + rect.height / 2,
    };
  }

  function anchorPoints(source, target, laneOffset) {
    const verticalGap = target.top - source.bottom;
    const reverseVerticalGap = source.top - target.bottom;
    const horizontalGap = target.left - source.right;
    const reverseHorizontalGap = source.left - target.right;

    if (verticalGap >= 0) {
      return {
        start: [source.centerX + laneOffset, source.bottom],
        end: [target.centerX + laneOffset, target.top],
        orientation: "vertical",
      };
    }
    if (reverseVerticalGap >= 0) {
      return {
        start: [source.centerX - laneOffset, source.top],
        end: [target.centerX - laneOffset, target.bottom],
        orientation: "vertical",
      };
    }
    if (horizontalGap >= 0) {
      return {
        start: [source.right, source.centerY + laneOffset],
        end: [target.left, target.centerY + laneOffset],
        orientation: "horizontal",
      };
    }
    if (reverseHorizontalGap >= 0) {
      return {
        start: [source.left, source.centerY - laneOffset],
        end: [target.right, target.centerY - laneOffset],
        orientation: "horizontal",
      };
    }
    return {
      start: [source.centerX, source.bottom],
      end: [target.centerX, target.top],
      orientation: "vertical",
    };
  }

  function orthogonalPath(points, route, stageWidth, stageHeight) {
    const [sx, sy] = points.start;
    const [tx, ty] = points.end;
    const safe = 18;

    if (route === "left") {
      const channel = safe;
      return { d: `M ${sx} ${sy} H ${channel} V ${ty} H ${tx}`, label: [channel + 44, (sy + ty) / 2] };
    }
    if (route === "right") {
      const channel = Math.max(safe, stageWidth - safe);
      return { d: `M ${sx} ${sy} H ${channel} V ${ty} H ${tx}`, label: [channel - 44, (sy + ty) / 2] };
    }
    if (route === "top") {
      const channel = safe;
      return { d: `M ${sx} ${sy} V ${channel} H ${tx} V ${ty}`, label: [(sx + tx) / 2, channel + 15] };
    }
    if (route === "bottom") {
      const channel = Math.max(safe, stageHeight - safe);
      return { d: `M ${sx} ${sy} V ${channel} H ${tx} V ${ty}`, label: [(sx + tx) / 2, channel - 15] };
    }

    if (points.orientation === "vertical") {
      const midY = (sy + ty) / 2;
      return { d: `M ${sx} ${sy} V ${midY} H ${tx} V ${ty}`, label: [(sx + tx) / 2, midY] };
    }
    const midX = (sx + tx) / 2;
    return { d: `M ${sx} ${sy} H ${midX} V ${ty} H ${tx}`, label: [midX, (sy + ty) / 2] };
  }

  function appendDefinitions(svg, markerId) {
    const defs = svgElement("defs");
    const marker = svgElement("marker", {
      id: markerId,
      viewBox: "0 0 10 10",
      refX: "9",
      refY: "5",
      markerWidth: "7",
      markerHeight: "7",
      orient: "auto-start-reverse",
    });
    marker.appendChild(svgElement("path", { d: "M 0 0 L 10 5 L 0 10 z", fill: "context-stroke" }));
    defs.appendChild(marker);
    svg.appendChild(defs);
  }

  function edgeItems(figure) {
    return [...figure.querySelectorAll(".edge-model > li[data-from][data-to]")];
  }

  function applyFocus(stage, selectedId) {
    const figure = stage.closest("figure");
    const edges = edgeItems(figure);
    const neighbors = new Set([selectedId]);
    const litKeys = new Set();

    edges.forEach((edge, index) => {
      const from = edge.dataset.from;
      const to = edge.dataset.to;
      if (from === selectedId || to === selectedId) {
        neighbors.add(from);
        neighbors.add(to);
        litKeys.add(String(index));
      }
    });

    stage.classList.toggle("has-focus", Boolean(selectedId));
    stage.querySelectorAll(".flow-node").forEach((node) => {
      node.classList.toggle("is-selected", node.id === selectedId);
      node.classList.toggle("is-lit", neighbors.has(node.id));
    });
    stage.querySelectorAll("[data-edge-index]").forEach((item) => {
      item.classList.toggle("is-lit", litKeys.has(item.dataset.edgeIndex));
    });
    stage.dataset.selectedNode = selectedId || "";
  }

  function drawFigure(figure) {
    const stage = figure.querySelector(".diagram-stage");
    const svg = stage?.querySelector(".connector-layer");
    if (!stage || !svg || window.matchMedia("(max-width: 820px)").matches) return;

    const selectedId = stage.dataset.selectedNode || "";
    svg.replaceChildren();
    const markerId = `arrow-${figure.id}`;
    appendDefinitions(svg, markerId);
    const stageRect = stage.getBoundingClientRect();
    svg.setAttribute("viewBox", `0 0 ${stageRect.width} ${stageRect.height}`);

    const sourceCounts = new Map();
    edgeItems(figure).forEach((edge) => {
      sourceCounts.set(edge.dataset.from, (sourceCounts.get(edge.dataset.from) || 0) + 1);
    });
    const sourceSeen = new Map();

    edgeItems(figure).forEach((edge, index) => {
      const source = document.getElementById(edge.dataset.from);
      const target = document.getElementById(edge.dataset.to);
      if (!source || !target) return;

      const seen = sourceSeen.get(edge.dataset.from) || 0;
      const total = sourceCounts.get(edge.dataset.from) || 1;
      sourceSeen.set(edge.dataset.from, seen + 1);
      const laneOffset = (seen - (total - 1) / 2) * 10;
      const points = anchorPoints(relativeRect(source, stageRect), relativeRect(target, stageRect), laneOffset);
      const routed = orthogonalPath(
        points,
        edge.dataset.route || "",
        stageRect.width,
        stageRect.height,
      );

      const path = svgElement("path", {
        d: routed.d,
        class: "connector-path",
        "marker-end": `url(#${markerId})`,
        "data-kind": edge.dataset.kind || "default",
        "data-edge-index": String(index),
      });
      svg.appendChild(path);

      const labelGroup = svgElement("g", {
        class: "edge-label",
        "data-edge-index": String(index),
      });
      const text = svgElement("text", { x: String(routed.label[0]), y: String(routed.label[1]) });
      text.textContent = edge.dataset.label;
      labelGroup.appendChild(text);
      svg.appendChild(labelGroup);
      const box = text.getBBox();
      const rect = svgElement("rect", {
        x: String(box.x - 6),
        y: String(box.y - 3),
        width: String(box.width + 12),
        height: String(box.height + 6),
      });
      labelGroup.insertBefore(rect, text);
    });

    if (selectedId) applyFocus(stage, selectedId);
  }

  function prepareFigure(figure) {
    const stage = figure.querySelector(".diagram-stage");
    if (!stage) return;
    stage.querySelectorAll(".flow-node").forEach((node) => {
      node.tabIndex = 0;
      const select = () => {
        const next = stage.dataset.selectedNode === node.id ? "" : node.id;
        applyFocus(stage, next);
      };
      node.addEventListener("click", (event) => {
        event.stopPropagation();
        select();
      });
      node.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          select();
        }
      });
    });
    stage.addEventListener("click", () => applyFocus(stage, ""));
  }

  function drawAll() {
    document.querySelectorAll("figure.diagram").forEach(drawFigure);
  }

  document.querySelectorAll("figure.diagram").forEach(prepareFigure);
  window.addEventListener("resize", drawAll);
  window.addEventListener("load", drawAll);
  if (document.fonts?.ready) document.fonts.ready.then(drawAll);
})();
