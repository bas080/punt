import http from 'http';
import fs from 'fs';
import path from 'path';
import { WebSocketServer, WebSocket } from 'ws';

export interface TaskItem {
  id?: number | string;
  text: string;
  createdAt?: number;
  snoozeUntil?: number | null;
  isDone?: boolean;
  recurrenceInterval?: string | null;
  updatedAt?: number;
}

export interface ServerOptions {
  port?: number;
  host?: string;
  code?: string;
  webDistDir?: string;
}

export class PuntServer {
  private server: http.Server | null = null;
  private wss: WebSocketServer | null = null;
  private pairingCode: string | null = null;
  private pairingCodeExpiresAt: number = 0;
  private sessionToken: string | null = null;
  private tasks: TaskItem[] = [];
  private phoneWs: WebSocket | null = null;
  private webClients: Set<WebSocket> = new Set();
  private pendingMutations: Map<string, { resolve: (res: any) => void; reject: (err: any) => void; timeout: NodeJS.Timeout }> = new Map();

  constructor(private options: ServerOptions = {}) {
    if (options.code) {
      this.setPairingCode(options.code);
    }
  }

  public setPairingCode(code: string, validityMs: number = 15 * 60 * 1000) {
    this.pairingCode = code;
    this.pairingCodeExpiresAt = Date.now() + validityMs;
  }

  public getSessionToken(): string | null {
    return this.sessionToken;
  }

  public isPhoneConnected(): boolean {
    return this.phoneWs !== null && this.phoneWs.readyState === WebSocket.OPEN;
  }

  public getTasks(): TaskItem[] {
    return this.tasks;
  }

  public setTasks(tasks: TaskItem[]) {
    this.tasks = tasks;
    this.broadcastToWeb({ type: 'tasks_updated', tasks: this.tasks });
  }

  private broadcastToWeb(message: any) {
    const payload = JSON.stringify(message);
    for (const ws of this.webClients) {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(payload);
      }
    }
  }

  public start(): Promise<number> {
    return new Promise((resolve, reject) => {
      const port = this.options.port || parseInt(process.env.PUNT_PORT || '7868', 10);
      const host = this.options.host || '0.0.0.0';
      const webDist = this.options.webDistDir || path.resolve(__dirname, '../web');

      this.server = http.createServer((req, res) => {
        this.handleHttpRequest(req, res, webDist);
      });

      this.wss = new WebSocketServer({ noServer: true });

      this.server.on('upgrade', (req, socket, head) => {
        const url = new URL(req.url || '', `http://${req.headers.host || 'localhost'}`);
        if (url.pathname === '/api/ws') {
          const token = url.searchParams.get('token') || req.headers['sec-websocket-protocol'];
          const isPhone = url.searchParams.get('client') === 'phone';

          // Authenticate WebSocket connection
          if (this.sessionToken && isPhone) {
            const clientToken = url.searchParams.get('token') || token;
            if (clientToken !== this.sessionToken) {
              socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n');
              socket.destroy();
              return;
            }
          }

          this.wss?.handleUpgrade(req, socket, head, (ws) => {
            this.wss?.emit('connection', ws, req);
          });
        } else {
          socket.write('HTTP/1.1 404 Not Found\r\n\r\n');
          socket.destroy();
        }
      });

      this.wss.on('connection', (ws, req) => {
        const url = new URL(req.url || '', `http://${req.headers.host || 'localhost'}`);
        const isPhone = url.searchParams.get('client') === 'phone';

        if (isPhone) {
          if (this.phoneWs && this.phoneWs !== ws) {
            try { this.phoneWs.close(); } catch (_) {}
          }
          this.phoneWs = ws;
          this.broadcastToWeb({ type: 'phone_status', connected: true });

          ws.on('message', (data) => {
            try {
              const msg = JSON.parse(data.toString());
              if (msg.type === 'tasks_dump') {
                this.tasks = msg.tasks || [];
                this.broadcastToWeb({ type: 'tasks_updated', tasks: this.tasks });
              } else if (msg.type === 'mutation_response') {
                const pending = this.pendingMutations.get(msg.mutationId);
                if (pending) {
                  clearTimeout(pending.timeout);
                  this.pendingMutations.delete(msg.mutationId);
                  if (msg.success) {
                    if (msg.tasks) {
                      this.tasks = msg.tasks;
                      this.broadcastToWeb({ type: 'tasks_updated', tasks: this.tasks });
                    }
                    pending.resolve(msg);
                  } else {
                    pending.reject(new Error(msg.error || 'Mutation failed on phone'));
                  }
                }
              }
            } catch (_) {}
          });

          ws.on('close', () => {
            if (this.phoneWs === ws) {
              this.phoneWs = null;
              this.broadcastToWeb({ type: 'phone_status', connected: false });
            }
          });
        } else {
          this.webClients.add(ws);
          ws.send(JSON.stringify({
            type: 'init',
            phoneConnected: this.isPhoneConnected(),
            tasks: this.tasks
          }));

          ws.on('message', async (data) => {
            try {
              const msg = JSON.parse(data.toString());
              if (msg.type === 'mutate') {
                try {
                  const result = await this.dispatchMutationToPhone(msg.action, msg.payload);
                  ws.send(JSON.stringify({ type: 'mutation_ack', mutationId: msg.mutationId, success: true, result }));
                } catch (err: any) {
                  ws.send(JSON.stringify({ type: 'mutation_ack', mutationId: msg.mutationId, success: false, error: err.message }));
                }
              }
            } catch (_) {}
          });

          ws.on('close', () => {
            this.webClients.delete(ws);
          });
        }
      });

      this.server.listen(port, host, () => {
        const address = this.server?.address();
        const actualPort = typeof address === 'object' && address ? address.port : port;
        resolve(actualPort);
      });

      this.server.on('error', (err) => {
        reject(err);
      });
    });
  }

  public async dispatchMutationToPhone(action: string, payload: any, timeoutMs: number = 3000): Promise<any> {
    if (!this.isPhoneConnected()) {
      throw new Error('Phone is unreachable');
    }
    const mutationId = 'mut_' + Math.random().toString(36).substring(2, 9);
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pendingMutations.delete(mutationId);
        reject(new Error('Mutation timeout: phone did not respond in time'));
      }, timeoutMs);

      this.pendingMutations.set(mutationId, { resolve, reject, timeout: timer });

      this.phoneWs?.send(JSON.stringify({
        type: 'mutation_request',
        mutationId,
        action,
        payload
      }));
    });
  }

  private handleHttpRequest(req: http.IncomingMessage, res: http.ServerResponse, webDist: string) {
    const parsedUrl = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
    const pathname = parsedUrl.pathname;

    // CORS headers for local subnet development
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') {
      res.writeHead(204);
      res.end();
      return;
    }

    if (pathname === '/api/ping' && req.method === 'GET') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ app: 'punt-server', version: '0.0.7' }));
      return;
    }

    if (pathname === '/api/pair' && req.method === 'POST') {
      let body = '';
      req.on('data', chunk => { body += chunk; });
      req.on('end', () => {
        try {
          const data = JSON.parse(body || '{}');
          const code = data.code;
          if (!this.pairingCode || !code || code !== this.pairingCode) {
            res.writeHead(401, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, error: 'Invalid pairing code' }));
            return;
          }
          if (Date.now() > this.pairingCodeExpiresAt) {
            res.writeHead(401, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, error: 'Pairing code expired' }));
            return;
          }
          this.pairingCode = null; // Burn code
          this.sessionToken = 'token_' + Math.random().toString(36).substring(2, 12);
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ success: true, token: this.sessionToken }));
        } catch (_) {
          res.writeHead(400, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ success: false, error: 'Malformed request body' }));
        }
      });
      return;
    }

    if (pathname === '/api/tasks' && req.method === 'GET') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        tasks: this.tasks,
        phoneConnected: this.isPhoneConnected()
      }));
      return;
    }

    if (pathname === '/api/tasks/mutate' && req.method === 'POST') {
      let body = '';
      req.on('data', chunk => { body += chunk; });
      req.on('end', async () => {
        try {
          const data = JSON.parse(body || '{}');
          if (!this.isPhoneConnected()) {
            res.writeHead(504, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, error: 'Phone is unreachable' }));
            return;
          }
          const result = await this.dispatchMutationToPhone(data.action, data.payload);
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ success: true, result }));
        } catch (err: any) {
          res.writeHead(500, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ success: false, error: err.message }));
        }
      });
      return;
    }

    // Static asset serving from webDist
    this.serveStaticFile(pathname, res, webDist);
  }

  private serveStaticFile(pathname: string, res: http.ServerResponse, webDist: string) {
    let filePath = pathname === '/' ? path.join(webDist, 'index.html') : path.join(webDist, pathname);

    if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
      filePath = path.join(webDist, 'index.html');
    }

    if (!fs.existsSync(filePath)) {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('Not Found');
      return;
    }

    const ext = path.extname(filePath).toLowerCase();
    const mimeTypes: Record<string, string> = {
      '.html': 'text/html',
      '.css': 'text/css',
      '.js': 'application/javascript',
      '.json': 'application/json',
      '.png': 'image/png',
      '.svg': 'image/svg+xml'
    };
    const contentType = mimeTypes[ext] || 'application/octet-stream';

    res.writeHead(200, { 'Content-Type': contentType });
    fs.createReadStream(filePath).pipe(res);
  }

  public stop(): Promise<void> {
    return new Promise((resolve) => {
      for (const [, mut] of this.pendingMutations) {
        clearTimeout(mut.timeout);
        mut.reject(new Error('Server stopped'));
      }
      this.pendingMutations.clear();

      if (this.phoneWs) {
        try { this.phoneWs.close(); } catch (_) {}
        this.phoneWs = null;
      }
      for (const ws of this.webClients) {
        try { ws.close(); } catch (_) {}
      }
      this.webClients.clear();

      if (this.wss) {
        this.wss.close(() => {
          if (this.server) {
            this.server.close(() => resolve());
          } else {
            resolve();
          }
        });
      } else if (this.server) {
        this.server.close(() => resolve());
      } else {
        resolve();
      }
    });
  }
}
