const http = require('http');
const crypto = require('crypto');

class PuntServer {
  constructor(options = {}) {
    this.port = options.port || 0;
    this.codeTTLMs = options.codeTTLMs || 5 * 60 * 1000; // 5 minutes default
    this.pairingCodes = new Map(); // code -> { expiresAt, used }
    this.validTokens = new Set();  // set of valid persistent session tokens
    this.reminders = [];           // in-memory reminders list
    this.server = null;
  }

  generateCode() {
    // Generate a random 6-digit numeric string
    const num = crypto.randomInt(100000, 1000000);
    return num.toString();
  }

  generateToken() {
    return crypto.randomBytes(32).toString('hex');
  }

  parseJsonBody(req) {
    return new Promise((resolve, reject) => {
      let body = '';
      req.on('data', chunk => { body += chunk; });
      req.on('end', () => {
        if (!body) {
          return resolve({});
        }
        try {
          resolve(JSON.parse(body));
        } catch (err) {
          reject(new Error('Invalid JSON payload'));
        }
      });
      req.on('error', err => reject(err));
    });
  }

  sendJson(res, statusCode, data) {
    res.writeHead(statusCode, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(data));
  }

  extractBearerToken(req) {
    const authHeader = req.headers['authorization'];
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return null;
    }
    return authHeader.substring(7).trim();
  }

  async handleRequest(req, res) {
    const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    const pathname = url.pathname;
    const method = req.method.toUpperCase();

    // CORS headers
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (method === 'OPTIONS') {
      res.writeHead(204);
      return res.end();
    }

    if (pathname === '/health' && method === 'GET') {
      return this.sendJson(res, 200, { status: 'ok' });
    }

    // Endpoint 1: Request a temporary connection setup code
    if (pathname === '/api/auth/code' && method === 'POST') {
      const code = this.generateCode();
      const expiresAt = Date.now() + this.codeTTLMs;
      this.pairingCodes.set(code, { expiresAt, used: false });
      return this.sendJson(res, 201, {
        code,
        expiresAt: new Date(expiresAt).toISOString(),
        message: 'Temporary setup code generated. Use this code once to pair client.'
      });
    }

    // Endpoint 2: Complete one-time connection setup using temporary code
    if (pathname === '/api/auth/pair' && method === 'POST') {
      let body;
      try {
        body = await this.parseJsonBody(req);
      } catch (err) {
        return this.sendJson(res, 400, { error: 'Invalid JSON payload' });
      }

      const { code } = body;
      if (!code || typeof code !== 'string') {
        return this.sendJson(res, 400, { error: 'Temporary setup code is required' });
      }

      const pairingEntry = this.pairingCodes.get(code.trim());
      if (!pairingEntry) {
        return this.sendJson(res, 401, { error: 'Invalid setup code' });
      }

      if (pairingEntry.used) {
        return this.sendJson(res, 401, { error: 'Setup code has already been used' });
      }

      if (Date.now() > pairingEntry.expiresAt) {
        this.pairingCodes.delete(code.trim());
        return this.sendJson(res, 401, { error: 'Setup code has expired' });
      }

      // Mark setup code as used (one-time setup)
      pairingEntry.used = true;

      // Issue persistent session token
      const token = this.generateToken();
      this.validTokens.add(token);

      return this.sendJson(res, 200, {
        token,
        message: 'Connection established successfully'
      });
    }

    // Authenticated endpoints below
    const token = this.extractBearerToken(req);
    if (!token || !this.validTokens.has(token)) {
      return this.sendJson(res, 401, { error: 'Unauthorized: Invalid or missing token' });
    }

    // Endpoint 3: Fetch reminders
    if (pathname === '/api/reminders' && method === 'GET') {
      return this.sendJson(res, 200, { reminders: this.reminders });
    }

    // Endpoint 4: Sync reminders
    if (pathname === '/api/reminders/sync' && method === 'POST') {
      let body;
      try {
        body = await this.parseJsonBody(req);
      } catch (err) {
        return this.sendJson(res, 400, { error: 'Invalid JSON payload' });
      }

      if (Array.isArray(body.reminders)) {
        this.reminders = body.reminders;
      }
      return this.sendJson(res, 200, {
        reminders: this.reminders,
        message: 'Reminders synchronized successfully'
      });
    }

    return this.sendJson(res, 404, { error: 'Not found' });
  }

  listen() {
    return new Promise((resolve, reject) => {
      this.server = http.createServer((req, res) => this.handleRequest(req, res));
      this.server.listen(this.port, () => {
        this.port = this.server.address().port;
        resolve(this.port);
      });
      this.server.on('error', err => reject(err));
    });
  }

  close() {
    return new Promise((resolve, reject) => {
      if (!this.server) {
        return resolve();
      }
      this.server.close(err => {
        if (err) reject(err);
        else resolve();
      });
    });
  }
}

function createPuntServer(options) {
  return new PuntServer(options);
}

if (require.main === module) {
  const port = process.env.PORT || 3000;
  const puntServer = createPuntServer({ port });
  puntServer.listen().then(assignedPort => {
    console.log(`Punt backend server running on http://localhost:${assignedPort}`);
  }).catch(err => {
    console.error('Failed to start Punt server:', err);
    process.exit(1);
  });
}

module.exports = { PuntServer, createPuntServer };
