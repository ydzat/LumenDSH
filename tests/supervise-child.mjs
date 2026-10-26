// Mock launcher used by tests/supervise-check.mjs.
// mode: crash (exits at once) | ok (serves HTTP) | hang (never listens)
import http from 'node:http';

const [mode, portText] = process.argv.slice(2);
const port = Number(portText);
if (mode === 'crash') process.exit(3);
if (mode === 'ok') {
  const server = http.createServer((request, response) => { response.writeHead(200); response.end('ok'); });
  server.listen(port, '127.0.0.1');
} else {
  setInterval(() => {}, 1000);
}
