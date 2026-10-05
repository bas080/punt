const { test, describe, before, after } = require('node:test');
const assert = require('node:assert/strict');
const http = require('http');
const { createPuntServer } = require('../index.js');

function makeRequest(port, options = {}, postData = null) {
  return new Promise((resolve, reject) => {
    const reqOptions = {
      hostname: 'localhost',
      port,
      path: options.path || '/',
      method: options.method || 'GET',
      headers: options.headers || {}
    };

    if (postData) {
      if (typeof postData === 'object') {
        postData = JSON.stringify(postData);
        reqOptions.headers['Content-Type'] = 'application/json';
      }
      reqOptions.headers['Content-Length'] = Buffer.byteLength(postData);
    }

    const req = http.request(reqOptions, res => {
      let body = '';
      res.on('data', chunk => { body += chunk; });
      res.on('end', () => {
        let json = null;
        try {
          json = JSON.parse(body);
        } catch (_) {}
        resolve({ statusCode: res.statusCode, headers: res.headers, body, json });
      });
    });

    req.on('error', err => reject(err));
    if (postData) {
      req.write(postData);
    }
    req.end();
  });
}

describe('Punt Synchronization Server Authentication tests', () => {
  let serverInstance;
  let port;

  before(async () => {
    serverInstance = createPuntServer({ port: 0, codeTTLMs: 1000 }); // 1 sec TTL for testing
    port = await serverInstance.listen();
  });

  after(async () => {
    await serverInstance.close();
  });

  test('GET /health returns status ok', async () => {
    const res = await makeRequest(port, { path: '/health', method: 'GET' });
    assert.equal(res.statusCode, 200);
    assert.equal(res.json.status, 'ok');
  });

  test('POST /api/auth/code generates temporary 6-digit setup code', async () => {
    const res = await makeRequest(port, { path: '/api/auth/code', method: 'POST' });
    assert.equal(res.statusCode, 201);
    assert.ok(res.json.code);
    assert.equal(typeof res.json.code, 'string');
    assert.equal(res.json.code.length, 6);
    assert.ok(res.json.expiresAt);
  });

  test('POST /api/auth/pair exchanges valid code for session token once', async () => {
    // Generate code
    const codeRes = await makeRequest(port, { path: '/api/auth/code', method: 'POST' });
    const code = codeRes.json.code;

    // Pair with code
    const pairRes = await makeRequest(port, { path: '/api/auth/pair', method: 'POST' }, { code });
    assert.equal(pairRes.statusCode, 200);
    assert.ok(pairRes.json.token);
    const token = pairRes.json.token;

    // Reuse same code fails (one-time setup code)
    const reuseRes = await makeRequest(port, { path: '/api/auth/pair', method: 'POST' }, { code });
    assert.equal(reuseRes.statusCode, 401);
    assert.equal(reuseRes.json.error, 'Setup code has already been used');

    // Token allows access to authenticated endpoint /api/reminders
    const syncRes = await makeRequest(port, {
      path: '/api/reminders',
      method: 'GET',
      headers: { 'Authorization': `Bearer ${token}` }
    });
    assert.equal(syncRes.statusCode, 200);
    assert.deepEqual(syncRes.json.reminders, []);
  });

  test('POST /api/auth/pair fails with invalid setup code', async () => {
    const res = await makeRequest(port, { path: '/api/auth/pair', method: 'POST' }, { code: '000000' });
    assert.equal(res.statusCode, 401);
    assert.equal(res.json.error, 'Invalid setup code');
  });

  test('POST /api/auth/pair fails when code has expired', async () => {
    // Generate code
    const codeRes = await makeRequest(port, { path: '/api/auth/code', method: 'POST' });
    const code = codeRes.json.code;

    // Wait 1.1s for code TTL (1s) to expire
    await new Promise(resolve => setTimeout(resolve, 1100));

    const pairRes = await makeRequest(port, { path: '/api/auth/pair', method: 'POST' }, { code });
    assert.equal(pairRes.statusCode, 401);
    assert.equal(pairRes.json.error, 'Setup code has expired');
  });

  test('Unauthenticated access to /api/reminders is rejected with 401', async () => {
    const res = await makeRequest(port, { path: '/api/reminders', method: 'GET' });
    assert.equal(res.statusCode, 401);
  });

  test('Authenticated user can sync and retrieve reminders', async () => {
    // Generate code & pair
    const codeRes = await makeRequest(port, { path: '/api/auth/code', method: 'POST' });
    const pairRes = await makeRequest(port, { path: '/api/auth/pair', method: 'POST' }, { code: codeRes.json.code });
    const token = pairRes.json.token;

    // Sync reminders
    const remindersPayload = [
      { text: 'Buy groceries', done: false },
      { text: 'Submit tax return', done: true }
    ];
    const syncRes = await makeRequest(port, {
      path: '/api/reminders/sync',
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token}` }
    }, { reminders: remindersPayload });

    assert.equal(syncRes.statusCode, 200);
    assert.equal(syncRes.json.reminders.length, 2);

    // Fetch reminders
    const getRes = await makeRequest(port, {
      path: '/api/reminders',
      method: 'GET',
      headers: { 'Authorization': `Bearer ${token}` }
    });
    assert.equal(getRes.statusCode, 200);
    assert.equal(getRes.json.reminders.length, 2);
    assert.equal(getRes.json.reminders[0].text, 'Buy groceries');
  });
});
