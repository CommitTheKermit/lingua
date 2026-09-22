import { readdir, readFile, writeFile } from "node:fs/promises";
import { createHash } from "node:crypto";
const root = new URL("../dist/", import.meta.url);
async function files(url, prefix = "") {
  const entries = await readdir(url, { withFileTypes: true });
  return (
    await Promise.all(
      entries.map((entry) =>
        entry.isDirectory()
          ? files(new URL(entry.name + "/", url), prefix + entry.name + "/")
          : prefix + entry.name,
      ),
    )
  ).flat();
}
const paths = (await files(root)).filter((path) => path !== "sw.js").sort();
const hash = createHash("sha256");
for (const path of paths) hash.update(await readFile(new URL(path, root)));
const cache = "lingua-web-" + hash.digest("hex").slice(0, 16);
const urls = paths.map((path) => "/" + path);
await writeFile(
  new URL("sw.js", root),
  `
const CACHE = ${JSON.stringify(cache)};
const ASSETS = ${JSON.stringify(urls)};
self.addEventListener('install', event => event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(ASSETS.map(url => new Request(url, { cache: 'reload' }))))));
self.addEventListener('activate', event => event.waitUntil(Promise.all([
  caches.keys().then(keys => Promise.all(keys.filter(key => key.startsWith('lingua-web-') && key !== CACHE).map(key => caches.delete(key)))),
  self.clients.claim()
])));
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  if (event.request.method !== 'GET' || url.origin !== self.location.origin) return;
  if (!ASSETS.includes(url.pathname) && event.request.mode !== 'navigate') return;
  event.respondWith(caches.open(CACHE).then(async cache => {
    if (event.request.mode === 'navigate') return (await cache.match('/index.html')) || fetch(event.request);
    return (await cache.match(event.request, { ignoreVary: true })) || fetch(event.request);
  }));
});
`,
);
console.log(`오프라인 캐시 생성: ${paths.length}개 파일`);
