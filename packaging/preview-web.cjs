const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");
const root = path.resolve(__dirname, "../frontend");
const types = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".ico": "image/x-icon",
};
const server = http
  .createServer(async (req, res) => {
    res.setHeader(
      "Content-Security-Policy",
      "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'",
    );
    res.setHeader("Referrer-Policy", "no-referrer");
    res.setHeader("X-Content-Type-Options", "nosniff");
    try {
      const url = new URL(req.url, "http://localhost");
      if (url.pathname.startsWith("/api/")) {
        if (process.env.TECNOPC_PREVIEW_OFFLINE === "1") {
          res.writeHead(503, { "Content-Type": "application/json" });
          res.end(JSON.stringify({ mensaje: "API simulada: configura una ruta de prueba" }));
          return;
        }
        const chunks = [];
        for await (const chunk of req) chunks.push(chunk);
        const upstream = await fetch("http://127.0.0.1:8081" + url.pathname, {
          method: req.method,
          headers: { "Content-Type": "application/json" },
          body: req.method === "GET" ? undefined : Buffer.concat(chunks),
          signal: AbortSignal.timeout(25000),
        });
        res.writeHead(upstream.status, {
          "Content-Type": upstream.headers.get("content-type") || "application/json",
        });
        res.end(Buffer.from(await upstream.arrayBuffer()));
        return;
      }
      const relative = decodeURIComponent(url.pathname === "/" ? "/index.html" : url.pathname);
      const file = path.resolve(root, "." + relative);
      if (
        !file.startsWith(root + path.sep) ||
        !fs.existsSync(file) ||
        !fs.statSync(file).isFile()
      ) {
        res.writeHead(404);
        res.end("Not found");
        return;
      }
      res.writeHead(200, {
        "Content-Type": types[path.extname(file)] || "application/octet-stream",
        "Cache-Control": "no-store",
      });
      fs.createReadStream(file).pipe(res);
    } catch {
      res.writeHead(503, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ mensaje: "Servidor no disponible" }));
    }
  })
  .listen(Number(process.env.TECNOPC_PREVIEW_PORT || 8082), "127.0.0.1", () =>
    process.stdout.write(`Vista previa en http://127.0.0.1:${server.address().port}\n`),
  );
