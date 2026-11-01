import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const docsDir = path.dirname(fileURLToPath(import.meta.url));
const htmlFiles = process.argv.slice(2).length
  ? process.argv.slice(2).map((item) => path.resolve(item))
  : [path.join(docsDir, "架构文档.html"), path.join(docsDir, "应用流程文档.html")];

const errors = [];

function attributesOf(source) {
  const result = new Map();
  const pattern = /([:\w-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')/g;
  for (const match of source.matchAll(pattern)) {
    result.set(match[1], match[2] ?? match[3] ?? "");
  }
  return result;
}

function tagsOf(html) {
  return [...html.matchAll(/<([a-zA-Z][\w:-]*)\b([^>]*)>/g)].map((match) => ({
    name: match[1].toLowerCase(),
    attributes: attributesOf(match[2]),
    raw: match[0],
  }));
}

function validateHtml(file) {
  const html = fs.readFileSync(file, "utf8");
  const tags = tagsOf(html);
  const ids = [];
  const nodes = [];
  const edges = [];

  for (const tag of tags) {
    const id = tag.attributes.get("id");
    if (id) ids.push(id);
    const classes = new Set((tag.attributes.get("class") || "").split(/\s+/).filter(Boolean));
    if (classes.has("flow-node")) {
      nodes.push({ id, allowIsolated: tag.attributes.get("data-allow-isolated") === "true" });
    }
    if (tag.name === "li" && tag.attributes.has("data-from") && tag.attributes.has("data-to")) {
      edges.push({
        from: tag.attributes.get("data-from"),
        to: tag.attributes.get("data-to"),
        label: tag.attributes.get("data-label") || "",
      });
    }
    if (tag.attributes.has("style")) errors.push(`${file}: inline style attribute`);
    if (tag.name === "style") errors.push(`${file}: inline style block`);
    if (tag.name === "script" && !tag.attributes.has("src")) errors.push(`${file}: inline script block`);

    for (const attr of ["href", "src"]) {
      const reference = tag.attributes.get(attr);
      if (!reference || reference.startsWith("#") || reference.startsWith("mailto:") || reference.startsWith("data:")) continue;
      if (/^[a-z][a-z\d+.-]*:/i.test(reference)) {
        errors.push(`${file}: remote/non-local reference ${reference}`);
        continue;
      }
      const target = path.resolve(path.dirname(file), decodeURIComponent(reference.split("#")[0]));
      if (!fs.existsSync(target)) errors.push(`${file}: broken local reference ${reference}`);
    }
  }

  const counts = new Map();
  ids.forEach((id) => counts.set(id, (counts.get(id) || 0) + 1));
  for (const [id, count] of counts) {
    if (count > 1) errors.push(`${file}: duplicate id ${id}`);
  }

  const idSet = new Set(ids);
  const usedNodes = new Set();
  edges.forEach((edge, index) => {
    usedNodes.add(edge.from);
    usedNodes.add(edge.to);
    if (!idSet.has(edge.from)) errors.push(`${file}: edge ${index + 1} missing source ${edge.from}`);
    if (!idSet.has(edge.to)) errors.push(`${file}: edge ${index + 1} missing target ${edge.to}`);
    if (!edge.label.trim()) errors.push(`${file}: edge ${index + 1} has empty label`);
  });
  nodes.forEach((node) => {
    if (!node.id) errors.push(`${file}: flow-node without id`);
    else if (!usedNodes.has(node.id) && !node.allowIsolated) errors.push(`${file}: isolated flow-node ${node.id}`);
  });

  for (const forbidden of ["diagram-tools", "requestFullscreen", "hide-labels"]) {
    if (html.includes(forbidden)) errors.push(`${file}: forbidden UI token ${forbidden}`);
  }
  console.log(`${file}: ids=${idSet.size} flow_nodes=${nodes.length} edges=${edges.length}`);
}

htmlFiles.forEach(validateHtml);

const cssFile = path.join(docsDir, "assets", "dom-diagrams.css");
const css = fs.readFileSync(cssFile, "utf8");
if ((css.match(/{/g) || []).length !== (css.match(/}/g) || []).length) {
  errors.push(`${cssFile}: unbalanced braces`);
}
if (/\.diagram-viewport\s*\{[^}]*overflow\s*:\s*auto/is.test(css)) {
  errors.push(`${cssFile}: diagram-internal scrolling`);
}
if (/\.diagram-viewport\s*\{[^}]*max-height\s*:/is.test(css)) {
  errors.push(`${cssFile}: diagram height cap`);
}
console.log(`${cssFile}: css_bytes=${Buffer.byteLength(css)}`);

if (errors.length) {
  console.error("\nValidation failed:");
  errors.forEach((error) => console.error(`- ${error}`));
  process.exitCode = 1;
} else {
  console.log("Validation passed.");
}
