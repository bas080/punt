import test from 'node:test';
import assert from 'node:assert/strict';
import { PuntServer, TaskItem } from '../src/server/server';
import { io as ClientIO } from 'socket.io-client';

test('Server endpoints, Socket.IO task sync, and root routing', async (t) => {
  const server = new PuntServer({ port: 0 });
  const port = await server.start();

  t.after(async () => {
    await server.stop();
  });

  await t.test('GET /api/ping returns app metadata', async () => {
    const res = await fetch(`http://127.0.0.1:${port}/api/ping`);
    assert.equal(res.status, 200);
    const data = await res.json();
    assert.equal(data.app, 'punt-server');
    assert.equal(data.version, '0.0.7');
  });

  await t.test('Any other endpoint temporarily redirects (302) to root /', async () => {
    const res = await fetch(`http://127.0.0.1:${port}/random/path/unknown`, {
      redirect: 'manual'
    });
    assert.equal(res.status, 302);
    assert.equal(res.headers.get('location'), '/');
  });

  await t.test('Phone sockets are rejected before PIN pairing', async () => {
    const phoneSocket = ClientIO(`http://127.0.0.1:${port}`, {
      query: { client: 'phone' },
      transports: ['websocket'],
      reconnection: false
    });

    try {
      await new Promise<void>((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error('Phone connection was not rejected')), 1000);
        phoneSocket.once('connect_error', (error) => {
          clearTimeout(timer);
          assert.match(error.message, /unauthorized/);
          resolve();
        });
        phoneSocket.once('connect', () => reject(new Error('Unpaired phone connected')));
      });
    } finally {
      phoneSocket.disconnect();
    }
  });

  await t.test('POST /api/pair accepts valid code, burns it, and rejects reuse/invalid', async () => {
    server.setPairingCode('4829');

    // Invalid code
    const resBad = await fetch(`http://127.0.0.1:${port}/api/pair`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: '0000' })
    });
    assert.equal(resBad.status, 401);

    // Valid code
    const resGood = await fetch(`http://127.0.0.1:${port}/api/pair`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: '4829' })
    });
    assert.equal(resGood.status, 200);
    const dataGood = await resGood.json();
    assert.equal(dataGood.success, true);
    assert.ok(dataGood.token.startsWith('token_'));

    // Burned code reuse
    const resReuse = await fetch(`http://127.0.0.1:${port}/api/pair`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: '4829' })
    });
    assert.equal(resReuse.status, 401);
  });

  await t.test('Expired pairing PIN is regenerated and broadcast to web clients', async () => {
    const webSocket = ClientIO(`http://127.0.0.1:${port}`, {
      query: { client: 'web' },
      transports: ['websocket']
    });

    try {
      await new Promise<void>((resolve, reject) => {
        webSocket.once('connect', resolve);
        webSocket.once('connect_error', reject);
      });

      const updatePromise = new Promise<string>((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error('Timed out waiting for pin_update')), 1000);
        webSocket.once('pin_update', (data: { pairingPin: string }) => {
          clearTimeout(timer);
          resolve(data.pairingPin);
        });
      });

      server.setPairingCode('4829', 20);
      const newPin = await updatePromise;

      assert.match(newPin, /^\d{4}$/);
      assert.notEqual(newPin, '4829');
      assert.equal(server.getPairingCode(), newPin);
    } finally {
      webSocket.disconnect();
    }
  });

  await t.test('Full task list hydration and Socket.IO sync', async () => {
    const token = server.getSessionToken();
    const phoneSocket = ClientIO(`http://127.0.0.1:${port}`, {
      query: { client: 'phone', token: token || '' },
      transports: ['websocket']
    });

    await new Promise((resolve) => phoneSocket.on('connect', resolve));

    const sampleTasks: TaskItem[] = [
      { text: 'Buy milk', isDone: false },
      { text: 'Pay taxes #finance', isDone: true }
    ];

    phoneSocket.emit('tasks_dump', sampleTasks);

    // Wait briefly for server state update
    await new Promise(r => setTimeout(r, 60));

    assert.equal(server.isPhoneConnected(), true);
    assert.equal(server.getTasks().length, 2);
    assert.equal(server.getTasks()[0].text, 'Buy milk');

    phoneSocket.disconnect();
    await new Promise(r => setTimeout(r, 60));
  });

  await t.test('Fail-fast error when phone is disconnected on mutation', async () => {
    try {
      await server.dispatchMutationToPhone('create', { text: 'New item' });
      assert.fail('Should fail fast');
    } catch (err: any) {
      assert.equal(err.message, 'Phone is unreachable');
    }
  });
});
