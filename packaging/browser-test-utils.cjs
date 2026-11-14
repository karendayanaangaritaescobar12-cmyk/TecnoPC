const { chromium } = require("playwright");
const fs = require("node:fs");
const path = require("node:path");

const baseUrl = process.env.TECNOPC_BASE_URL || "http://127.0.0.1:8082";
const launchOptions = {
  headless: true,
  ...(process.env.TECNOPC_BROWSER_CHANNEL ? { channel: process.env.TECNOPC_BROWSER_CHANNEL } : {}),
};
function artifactPath(name) {
  const directory = path.resolve(__dirname, "../logs/browser-tests");
  fs.mkdirSync(directory, { recursive: true });
  return path.join(directory, name);
}
module.exports = { chromium, launchOptions, baseUrl, artifactPath };
