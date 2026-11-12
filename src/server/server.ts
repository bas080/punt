import http from 'http';
import fs from 'fs';
import path from 'path';
import createDebug from 'debug';
import { Server as SocketIOServer, Socket } from 'socket.io';

const debugServer = createDebug('punt:server');
const debugAuth = createDebug('punt:auth');
const debugSync = createDebug('punt:sync');

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
  private pairingCodeTimer: NodeJS.Timeout | null = null;
  private sessionToken: string | null = null;
  private tasks: TaskItem[] = [];
  private phoneSocket: Socket | null = null;
  private phoneVersion: string | null = null;
  public static readonly VERSION: string = '0.0.7';
  private pendingMutations: Map<string, { resolve: (res: any) => void; reject: (err: any) => void; timeout: NodeJS.Timeout }> = new Map();

  constructor(private options: ServerOptions = {}) {
    if (options.code) {
      this.setPairingCode(options.code);
    } else {
      this.generatePairingPin();
    }
  }

  public generatePairingPin(): string {
    const pin = Math.floor(1000 + Math.random() * 9000).toString();
    this.setPairingCode(pin);
    debugAuth('Generated a pairing PIN');
    return pin;
  }

  public getPairingCode(): string | null {
    if (this.pairingCode && Date.now() > this.pairingCodeExpiresAt) {
      return this.refreshExpiredPairingPin();
    }
    if (this.pairingCode && Date.now() <= this.pairingCodeExpiresAt) {
      return this.pairingCode;
    }
    return null;
  }

  public setPairingCode(code: string, validityMs: number = 15 * 60 * 1000) {
    if (this.pairingCodeTimer) clearTimeout(this.pairingCodeTimer);
    this.pairingCode = code;
    this.pairingCodeExpiresAt = Date.now() + validityMs;
    this.schedulePairingCodeExpiry(code);
  }

  private schedulePairingCodeExpiry(code: string) {
    const delay = Math.max(1, this.pairingCodeExpiresAt - Date.now() + 1);
    this.pairingCodeTimer = setTimeout(() => {
      if (this.pairingCode !== code) return;
      if (Date.now() <= this.pairingCodeExpiresAt) {
        this.schedulePairingCodeExpiry(code);
        return;
      }
      this.pairingCodeTimer = null;
      this.refreshExpiredPairingPin();
    }, delay);
    this.pairingCodeTimer.unref();
  }

  private refreshExpiredPairingPin(): string | null {
    if (!this.pairingCode || Date.now() <= this.pairingCodeExpiresAt) return this.pairingCode;
    const newPin = this.generatePairingPin();
    debugAuth('Expired pairing PIN regenerated');
    this.io?.emit('pin_update', { pairingPin: newPin });
    return newPin;
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
      const port = this.options.port ?? parseInt(process.env.PUNT_PORT || '7868', 10);
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
        if (clientType === 'phone' && (!this.sessionToken || token !== this.sessionToken)) {
          debugAuth('Rejected unauthorized phone socket id=%s', socket.id);
          return next(new Error('unauthorized'));
        }
        debugAuth('Socket authorized id=%s client=%s', socket.id, clientType || 'unknown');
        next();
      });

      this.io.on('connection', (socket) => {
        const clientType = socket.handshake.query.client as string;
        debugServer('Socket connected id=%s client=%s', socket.id, clientType || 'unknown');

        if (clientType === 'phone') {
          if (this.phoneSocket && this.phoneSocket !== socket) {
            debugServer('Replacing phone socket id=%s', this.phoneSocket.id);
            try { this.phoneSocket.disconnect(true); } catch (_) {}
          }
          this.phoneSocket = socket;
          this.phoneVersion = (socket.handshake.query.version as string) || null;
          this.io?.emit('phone_status', {
            connected: true,
            phoneVersion: this.phoneVersion,
            serverVersion: PuntServer.VERSION
          });

          socket.on('tasks_dump', (data) => {
            const list = Array.isArray(data) ? data : (data?.tasks || []);
            if (!Array.isArray(data) && data?.version) {
              this.phoneVersion = data.version;
            }
            this.tasks = list;
            debugSync('Received task snapshot count=%d', this.tasks.length);
            this.io?.emit('tasks_updated', {
              tasks: this.tasks,
              phoneVersion: this.phoneVersion,
              serverVersion: PuntServer.VERSION
            });
          });

          socket.on('mutation_response', (msg) => {
            if (!msg || !msg.mutationId) return;
            const pending = this.pendingMutations.get(msg.mutationId);
            if (pending) {
              clearTimeout(pending.timeout);
              this.pendingMutations.delete(msg.mutationId);
              if (msg.success) {
                debugSync('Mutation completed id=%s', msg.mutationId);
                if (msg.tasks) {
                  this.tasks = msg.tasks;
                  this.io?.emit('tasks_updated', { tasks: this.tasks });
                }
                pending.resolve(msg);
              } else {
                debugSync('Mutation failed id=%s reason=%s', msg.mutationId, msg.error || 'Mutation failed on phone');
                pending.reject(new Error(msg.error || 'Mutation failed on phone'));
              }
            }
          });

          socket.on('disconnect', () => {
            if (this.phoneSocket === socket) {
              debugServer('Phone socket disconnected id=%s', socket.id);
              this.phoneSocket = null;
              this.phoneVersion = null;
              this.io?.emit('phone_status', { connected: false });
            }
          });

          socket.conn.on('close', () => {
            if (this.phoneSocket === socket) {
              debugServer('Phone socket transport closed id=%s', socket.id);
              this.phoneSocket = null;
              this.phoneVersion = null;
              this.io?.emit('phone_status', { connected: false });
            }
          });
        } else {
          // Web client or CLI socket client
          socket.emit('init', {
            phoneConnected: this.isPhoneConnected(),
            phoneVersion: this.phoneVersion,
            serverVersion: PuntServer.VERSION,
            tasks: this.tasks,
            pairingPin: this.getPairingCode()
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
            debugSync('Mutation requested action=%s id=%s', msg?.action || 'unknown', socket.id);
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
        debugServer('Listening on %s:%d', host, actualPort);
        resolve(actualPort);
      });

      this.server.on('error', (err) => {
        reject(err);
      });
    });
  }

  public async dispatchMutationToPhone(action: string, payload: any, timeoutMs: number = 3000): Promise<any> {
    if (!this.isPhoneConnected()) {
      debugSync('Cannot dispatch mutation action=%s: phone is unreachable', action);
      throw new Error('Phone is unreachable');
    }
    const mutationId = 'mut_' + Math.random().toString(36).substring(2, 9);
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pendingMutations.delete(mutationId);
        reject(new Error('Mutation timeout: phone did not respond in time'));
      }, timeoutMs);

      this.pendingMutations.set(mutationId, { resolve, reject, timeout: timer });
      debugSync('Dispatching mutation action=%s id=%s', action, mutationId);

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
            debugAuth('Rejected invalid pairing attempt');
            res.writeHead(401, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, error: 'Invalid pairing code' }));
            return;
          }
          if (Date.now() > this.pairingCodeExpiresAt) {
            debugAuth('Rejected expired pairing attempt');
            this.refreshExpiredPairingPin();
            res.writeHead(401, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, error: 'Pairing code expired' }));
            return;
          }
          if (this.pairingCodeTimer) clearTimeout(this.pairingCodeTimer);
          this.pairingCodeTimer = null;
          this.pairingCode = null; // Burn code
          this.sessionToken = 'token_' + Math.random().toString(36).substring(2, 12);
          debugAuth('Pairing succeeded');
          this.io?.emit('pin_update', { pairingPin: null });
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
    if (pathname === '/' || pathname === '/index.html') {
      this.serveFile(path.join(webDist, 'index.html'), 'text/html', res);
      return;
    }

    // Serve any file existing in webDist
    const safePath = path.normalize(pathname).replace(/^(\.\.[\/\\])+/, '');
    const candidatePath = path.join(webDist, safePath);
    if (fs.existsSync(candidatePath) && fs.statSync(candidatePath).isFile()) {
      const ext = path.extname(candidatePath).toLowerCase();
      const mimeTypes: Record<string, string> = {
        '.html': 'text/html',
        '.css': 'text/css',
        '.js': 'application/javascript',
        '.json': 'application/json',
        '.png': 'image/png',
        '.svg': 'image/svg+xml'
      };
      this.serveFile(candidatePath, mimeTypes[ext] || 'application/octet-stream', res);
      return;
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
      debugServer('Stopping server');
      if (this.pairingCodeTimer) clearTimeout(this.pairingCodeTimer);
      this.pairingCodeTimer = null;
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
