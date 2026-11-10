import test from 'node:test';
import assert from 'node:assert/strict';
import { PuntServer, TaskItem } from '../src/server/server';
import { WebSocket } from 'ws';

test('Server endpoints and lifecycle', async (t) => {
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

  await t.test('Pairing with expired code fails', async () => {
    server.setPairingCode('1234', -1000); // Expired 1 second ago
    const res = await fetch(`http://127.0.0.1:${port}/api/pair`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: '1234' })
    });
    assert.equal(res.status, 401);
  });

  await t.test('Full task list hydration and WebSocket sync', async () => {
    const token = server.getSessionToken();
    const tokenQuery = token ? `&token=${token}` : '';
    const phoneWs = new WebSocket(`ws://127.0.0.1:${port}/api/ws?client=phone${tokenQuery}`);
    await new Promise((resolve) => phoneWs.on('open', resolve));

    const sampleTasks: TaskItem[] = [
      { text: 'Buy milk', isDone: false },
      { text: 'Pay taxes #finance', isDone: true }
    ];

    phoneWs.send(JSON.stringify({
      type: 'tasks_dump',
      tasks: sampleTasks
    }));

    // Wait briefly for server state update
    await new Promise(r => setTimeout(r, 50));

    const res = await fetch(`http://127.0.0.1:${port}/api/tasks`);
    assert.equal(res.status, 200);
    const data = await res.json();
    assert.equal(data.phoneConnected, true);
    assert.equal(data.tasks.length, 2);
    assert.equal(data.tasks[0].text, 'Buy milk');

    phoneWs.close();
  });

  await t.test('Fail-fast error when phone is disconnected on mutation', async () => {
    const res = await fetch(`http://127.0.0.1:${port}/api/tasks/mutate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'create', payload: { text: 'New item' } })
    });
    assert.equal(res.status, 504);
    const data = await res.json();
    assert.equal(data.success, false);
    assert.equal(data.error, 'Phone is unreachable');
  });
});
