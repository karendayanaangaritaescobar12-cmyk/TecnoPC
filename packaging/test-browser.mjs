import { spawn } from "node:child_process";
import { once } from "node:events";
import { fileURLToPath } from "node:url";
import { setTimeout as delay } from "node:timers/promises";

const root = fileURLToPath(new URL("../", import.meta.url));
const server = spawn(process.execPath, ["packaging/preview-web.cjs"], {
  cwd: root,
  env: { ...process.env, TECNOPC_PREVIEW_PORT: "0", TECNOPC_PREVIEW_OFFLINE: "1" },
  stdio: ["ignore", "pipe", "inherit"],
});

async function waitForServer() {
  let output = "";
  const ready = new Promise((resolve, reject) => {
    server.on("error", reject);
    server.on("exit", (code) => reject(new Error(`Preview exited early: ${code}`)));
    server.stdout.on("data", (data) => {
      output += data.toString();
      const match = output.match(/http:\/\/127\.0\.0\.1:\d+/);
      if (match) resolve(match[0]);
    });
  });
  return Promise.race([
    ready,
    delay(10_000, null, { ref: false }).then(() => {
      throw new Error("Preview did not start within ten seconds");
    }),
  ]);
}

try {
  const baseUrl = await waitForServer();
  for (const suite of ["store", "order-receipt", "idempotency", "resilience"]) {
    console.log(`\nPruebas de navegador: ${suite}`);
    const child = spawn(process.execPath, [`packaging/test-${suite}.cjs`], {
      cwd: root,
      env: { ...process.env, TECNOPC_BASE_URL: baseUrl },
      stdio: "inherit",
    });
    const [code] = await once(child, "exit");
    if (code !== 0) throw new Error(`Browser suite ${suite} failed: ${code}`);
  }
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
} finally {
  if (server.exitCode === null) {
    const stopped = once(server, "exit");
    server.kill();
    await stopped.catch(() => {});
  }
}
