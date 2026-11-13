// REA Web - 本地 APK 逆向分析交互界面后端
// 零依赖：仅使用 Node.js 内置模块
const http = require("http");
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const { execFile } = require("child_process");

const ROOT = __dirname;
const PUBLIC_DIR = path.join(ROOT, "public");
const JADX_JAVA = process.env.JADX_JAVA || path.join(ROOT, "tools", "jadx", "jre", "bin", "java.exe");
const JADX_JAR = process.env.JADX_JAR || path.join(ROOT, "tools", "jadx", "lib", "jadx-gui-1.5.6-all.jar");
const UPLOADS_DIR = path.join(ROOT, "data", "uploads");
const OUTPUTS_DIR = path.join(ROOT, "data", "outputs");
const PORT = Number(process.env.PORT) || 8787;

for (const d of [UPLOADS_DIR, OUTPUTS_DIR]) fs.mkdirSync(d, { recursive: true });

// 任务状态表: id -> { status, log, file, error, done }
const jobs = new Map();

function sendJson(res, code, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(code, { "Content-Type": "application/json; charset=utf-8" });
  res.end(body);
}

function safeJoin(base, rel) {
  const p = path.resolve(base, rel);
  if (!p.startsWith(base + path.sep)) return null;
  return p;
}

function buildTree(dir) {
  const items = [];
  for (const name of fs.readdirSync(dir).sort((a, b) => a.localeCompare(b))) {
    if (name.startsWith(".")) continue;
    const full = path.join(dir, name);
    const st = fs.statSync(full);
    if (st.isDirectory()) {
      items.push({ name, type: "dir", children: buildTree(full) });
    } else {
      items.push({ name, type: "file", size: st.size });
    }
  }
  return items;
}

function runJadx(jobId, apkPath) {
  const outDir = path.join(OUTPUTS_DIR, jobId);
  const job = jobs.get(jobId);
  job.status = "analyzing";

  const args = [
    "-cp", JADX_JAR,
    "jadx.cli.JadxCLI",
    "-d", outDir,          // 输出目录
    "--no-res",            // 只出源码，速度快很多
    "--deobf",             // 反混淆
    "-j", "4",             // 线程数
    apkPath,
  ];

  const child = execFile(JADX_JAVA, args, { windowsHide: true, timeout: 10 * 60 * 1000 }, (err, stdout, stderr) => {
    job.log = (stdout + "\n" + stderr).slice(-4000);
    // jadx 部分类反编译失败时会返回非零退出码，但大部分结果仍然有效
    const srcDir = path.join(outDir, "sources");
    const hasOutput = fs.existsSync(srcDir) && fs.readdirSync(srcDir).length > 0;
    if (err && !hasOutput) {
      job.status = "error";
      job.error = job.log || String(err);
    } else {
      job.status = "done";
      job.done = true;
      if (err) job.warn = "partial-errors";
    }
  });
  job.child = child;
}

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".svg": "image/svg+xml",
  ".json": "application/json; charset=utf-8",
};

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://127.0.0.1:${PORT}`);
  const p = url.pathname;

  // 上传 APK（raw body，文件名放 header，避免手写 multipart 解析）
  if (p === "/api/analyze" && req.method === "POST") {
    const fileName = decodeURIComponent(req.headers["x-filename"] || "app.apk");
    if (!fileName.toLowerCase().endsWith(".apk")) {
      return sendJson(res, 400, { error: "只支持 .apk 文件" });
    }
    const jobId = crypto.randomBytes(6).toString("hex");
    const apkPath = path.join(UPLOADS_DIR, `${jobId}.apk`);
    const ws = fs.createWriteStream(apkPath);
    let bytes = 0;
    req.on("data", (chunk) => {
      bytes += chunk.length;
      if (bytes > 512 * 1024 * 1024) { // 512MB 上限
        req.destroy();
        ws.destroy();
      }
      ws.write(chunk);
    });
    req.on("end", () => {
      ws.end();
      jobs.set(jobId, { status: "uploaded", file: fileName, log: "", error: null, done: false });
      runJadx(jobId, apkPath);
      sendJson(res, 200, { jobId });
    });
    req.on("error", () => sendJson(res, 500, { error: "上传失败" }));
    return;
  }

  // 任务状态轮询
  const mStatus = p.match(/^\/api\/job\/([a-f0-9]+)$/);
  if (mStatus && req.method === "GET") {
    const job = jobs.get(mStatus[1]);
    if (!job) return sendJson(res, 404, { error: "任务不存在" });
    return sendJson(res, 200, {
      status: job.status, file: job.file, error: job.error,
      logTail: job.log ? job.log.slice(-1500) : "",
    });
  }

  // 反编译输出文件树
  const mTree = p.match(/^\/api\/tree\/([a-f0-9]+)$/);
  if (mTree && req.method === "GET") {
    const job = jobs.get(mTree[1]);
    if (!job || !job.done) return sendJson(res, 404, { error: "结果尚未就绪" });
    const srcDir = path.join(OUTPUTS_DIR, mTree[1], "sources");
    const base = fs.existsSync(srcDir) ? srcDir : path.join(OUTPUTS_DIR, mTree[1]);
    return sendJson(res, 200, { root: path.basename(base), tree: buildTree(base) });
  }

  // 读取单个文件内容
  const mFile = p.match(/^\/api\/file\/([a-f0-9]+)$/);
  if (mFile && req.method === "GET") {
    const job = jobs.get(mFile[1]);
    if (!job || !job.done) return sendJson(res, 404, { error: "结果尚未就绪" });
    const srcDir = path.join(OUTPUTS_DIR, mFile[1], "sources");
    const base = fs.existsSync(srcDir) ? srcDir : path.join(OUTPUTS_DIR, mFile[1]);
    const rel = url.searchParams.get("path") || "";
    const full = safeJoin(base, rel);
    if (!full || !fs.existsSync(full) || !fs.statSync(full).isFile()) {
      return sendJson(res, 404, { error: "文件不存在" });
    }
    const size = fs.statSync(full).size;
    if (size > 2 * 1024 * 1024) {
      return sendJson(res, 413, { error: `文件过大 (${(size / 1048576).toFixed(1)}MB)，暂不支持在线查看` });
    }
    return sendJson(res, 200, { name: path.basename(full), content: fs.readFileSync(full, "utf-8") });
  }

  // 静态文件
  if (req.method === "GET") {
    let rel = p === "/" ? "index.html" : p.slice(1);
    const full = safeJoin(PUBLIC_DIR, rel);
    if (full && fs.existsSync(full) && fs.statSync(full).isFile()) {
      const ext = path.extname(full).toLowerCase();
      res.writeHead(200, { "Content-Type": MIME[ext] || "application/octet-stream" });
      return fs.createReadStream(full).pipe(res);
    }
  }

  sendJson(res, 404, { error: "Not Found" });
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(`REA Web running at http://127.0.0.1:${PORT}`);
});
