import http from 'http';
import fs from 'fs';
import path from 'path';
import { Server as SocketIOServer, Socket } from 'socket.io';

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
  private io: SocketIOServer | null = null;
  private pairingCode: string | null = null;
  private pairingCodeExpiresAt: number = 0;
  private sessionToken: string | null = null;
  private tasks: TaskItem[] = [];
  private phoneSocket: Socket | null = null;
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
    return this.phoneSocket !== null && this.phoneSocket.connected;
  }

  public getTasks(): TaskItem[] {
    return this.tasks;
  }

  public setTasks(tasks: TaskItem[]) {
    this.tasks = tasks;
    this.io?.emit('tasks_updated', { tasks: this.tasks });
  }

  public start(): Promise<number> {
    return new Promise((resolve, reject) => {
      const port = this.options.port || parseInt(process.env.PUNT_PORT || '7868', 10);
      const host = this.options.host || '0.0.0.0';
      const webDist = this.options.webDistDir || path.resolve(__dirname, '../web');

      this.server = http.createServer((req, res) => {
        this.handleHttpRequest(req, res, webDist);
      });

      this.io = new SocketIOServer(this.server, {
        cors: {
          origin: '*',
          methods: ['GET', 'POST']
        }
      });

      this.io.use((socket, next) => {
        const clientType = socket.handshake.query.client as string;
        const token = socket.handshake.query.token as string;
        if (this.sessionToken && clientType === 'phone' && token !== this.sessionToken) {
          return next(new Error('unauthorized'));
        }
        next();
      });

      this.io.on('connection', (socket) => {
        const clientType = socket.handshake.query.client as string;

        if (clientType === 'phone') {
          if (this.phoneSocket && this.phoneSocket !== socket) {
            try { this.phoneSocket.disconnect(true); } catch (_) {}
          }
          this.phoneSocket = socket;
          this.io?.emit('phone_status', { connected: true });

          socket.on('tasks_dump', (data) => {
            const list = Array.isArray(data) ? data : (data?.tasks || []);
            this.tasks = list;
            this.io?.emit('tasks_updated', { tasks: this.tasks });
          });

          socket.on('mutation_response', (msg) => {
            if (!msg || !msg.mutationId) return;
            const pending = this.pendingMutations.get(msg.mutationId);
            if (pending) {
              clearTimeout(pending.timeout);
              this.pendingMutations.delete(msg.mutationId);
              if (msg.success) {
                if (msg.tasks) {
                  this.tasks = msg.tasks;
                  this.io?.emit('tasks_updated', { tasks: this.tasks });
                }
                pending.resolve(msg);
              } else {
                pending.reject(new Error(msg.error || 'Mutation failed on phone'));
              }
            }
          });

          socket.on('disconnect', () => {
            if (this.phoneSocket === socket) {
              this.phoneSocket = null;
              this.io?.emit('phone_status', { connected: false });
            }
          });

          socket.conn.on('close', () => {
            if (this.phoneSocket === socket) {
              this.phoneSocket = null;
              this.io?.emit('phone_status', { connected: false });
            }
          });
        } else {
          // Web client or CLI socket client
          socket.emit('init', {
            phoneConnected: this.isPhoneConnected(),
            tasks: this.tasks
          });

          socket.on('get_status', (callback) => {
            if (typeof callback === 'function') {
              callback({
                phoneConnected: this.isPhoneConnected(),
                tasks: this.tasks
              });
            }
          });

          socket.on('mutate', async (msg, callback) => {
            try {
              const result = await this.dispatchMutationToPhone(msg.action, msg.payload);
              if (typeof callback === 'function') {
                callback({ success: true, result });
              }
              socket.emit('mutation_ack', { mutationId: msg.mutationId, success: true, result });
            } catch (err: any) {
              if (typeof callback === 'function') {
                callback({ success: false, error: err.message });
              }
              socket.emit('mutation_ack', { mutationId: msg.mutationId, success: false, error: err.message });
            }
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

      this.phoneSocket?.emit('mutation_request', {
        mutationId,
        action,
        payload
      });
    });
  }

  private handleHttpRequest(req: http.IncomingMessage, res: http.ServerResponse, webDist: string) {
    const parsedUrl = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
    const pathname = parsedUrl.pathname;

    // CORS headers
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') {
      res.writeHead(204);
      res.end();
      return;
    }

    if (pathname.startsWith('/socket.io/')) {
      // Let Socket.IO engine handle it
      return;
    }

    // Ping endpoint for fast network discovery
    if (pathname === '/api/ping' && req.method === 'GET') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ app: 'punt-server', version: '0.0.7' }));
      return;
    }

    // Handshake/Pairing endpoint
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

    // Serve static files from root
    const knownStaticFiles = ['/style.css', '/main.js', '/manifest.json', '/sw.js', '/favicon.ico', '/icon.png'];
    if (pathname === '/' || pathname === '/index.html') {
      this.serveFile(path.join(webDist, 'index.html'), 'text/html', res);
      return;
    }

    if (knownStaticFiles.includes(pathname)) {
      const filePath = path.join(webDist, pathname);
      if (fs.existsSync(filePath)) {
        const ext = path.extname(filePath).toLowerCase();
        const mimeTypes: Record<string, string> = {
          '.css': 'text/css',
          '.js': 'application/javascript',
          '.json': 'application/json',
          '.png': 'image/png'
        };
        this.serveFile(filePath, mimeTypes[ext] || 'application/octet-stream', res);
        return;
      }
    }

    // Temporary redirect (302) any other visits back to root /
    res.writeHead(302, { Location: '/' });
    res.end();
  }

  private serveFile(filePath: string, contentType: string, res: http.ServerResponse) {
    if (!fs.existsSync(filePath)) {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('Not Found');
      return;
    }
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

      if (this.io) {
        this.io.close();
      }
      if (this.server) {
        this.server.close(() => resolve());
      } else {
        resolve();
      }
    });
  }
}
