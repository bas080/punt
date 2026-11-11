import test from 'node:test';
import assert from 'node:assert/strict';
import { PuntServer } from '../src/server/server';
import { io as ClientIO } from 'socket.io-client';

test('Server Use Cases - All Endpoints, Handshake, Static Files, and Disconnects', async (t) => {
  const port = 7892;
  const server = new PuntServer({ port, code: '4321' });
  await server.start();

  await t.test('Use Case 1: GET /api/ping discovery endpoint', async () => {
    const res = await fetch(`http://localhost:${port}/api/ping`);
    assert.equal(res.status, 200);
    const data = await res.json();
    assert.equal(data.app, 'punt-server');
    assert.equal(data.version, '0.0.7');
  });

  await t.test('Use Case 2: POST /api/pair pairing handshake', async () => {
    // Invalid code
    const invalidRes = await fetch(`http://localhost:${port}/api/pair`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: '9999' })
    });
    assert.equal(invalidRes.status, 401);

    // Valid code
    const validRes = await fetch(`http://localhost:${port}/api/pair`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: '4321' })
    });
    assert.equal(validRes.status, 200);
    const validData = await validRes.json();
    assert.equal(validData.success, true);
    assert.ok(validData.token.startsWith('token_'));

    // Code is burned after single use
    const reuseRes = await fetch(`http://localhost:${port}/api/pair`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: '4321' })
    });
    assert.equal(reuseRes.status, 401);
  });

  await t.test('Use Case 3: Static web asset serving and 302 redirects', async () => {
    const rootRes = await fetch(`http://127.0.0.1:${port}/`);
    assert.equal(rootRes.status, 200);
    const html = await rootRes.text();
    assert.ok(html.includes('id="app"'));

    const redirectRes = await fetch(`http://127.0.0.1:${port}/unknown/route`, { redirect: 'manual' });
    assert.equal(redirectRes.status, 302);
    assert.equal(redirectRes.headers.get('location'), '/');
  });

  await t.test('Use Case 4: Phone connection, task dumps, and disconnect broadcast', async () => {
    const token = server.getSessionToken();
    const phoneSocket = ClientIO(`http://127.0.0.1:${port}`, {
      query: { client: 'phone', token: token || '', version: '0.0.7' },
      transports: ['websocket'],
      reconnection: false
    });

    const webSocket = ClientIO(`http://127.0.0.1:${port}`, {
      query: { client: 'web' },
      transports: ['websocket'],
      reconnection: false
    });

    let phoneConnectedState = false;
    webSocket.on('phone_status', (data: any) => {
      phoneConnectedState = data.connected;
    });

    await new Promise<void>((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('Phone connect timeout')), 3000);
      phoneSocket.on('connect', () => {
        clearTimeout(timer);
        phoneSocket.emit('tasks_dump', [
          { id: 1, text: 'Server use case task', isDone: false, snoozeUntil: null }
        ]);
        setTimeout(resolve, 50);
      });
    });

    assert.equal(server.isPhoneConnected(), true);
    assert.equal(server.getTasks().length, 1);
    assert.equal(server.getTasks()[0].text, 'Server use case task');

    // Phone disconnects
    phoneSocket.disconnect();
    await new Promise((r) => setTimeout(r, 60));
    assert.equal(server.isPhoneConnected(), false);

    webSocket.disconnect();
  });

  await server.stop();
});
