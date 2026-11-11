import test from 'node:test';
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import path from 'node:path';
import { PuntServer, TaskItem } from '../src/server/server';
import { io as ClientIO } from 'socket.io-client';

const execFileAsync = promisify(execFile);
const cliPath = path.resolve(__dirname, '../src/server/cli.ts');

test('End-to-end sync between Server, Android app, Web client, and CLI', async (t) => {
  const port = 7890;
  const server = new PuntServer({ port });
  await server.start();

  const pairingResponse = await fetch(`http://localhost:${port}/api/pair`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code: server.getPairingCode() })
  });
  const pairingData = await pairingResponse.json();
  assert.equal(pairingData.success, true);

  // Simulated Android phone client
  let phoneTasks: TaskItem[] = [
    { id: 1, text: 'Initial Android task', createdAt: Date.now(), isDone: false, snoozeUntil: null }
  ];

  const phoneSocket = ClientIO(`http://localhost:${port}`, {
    query: { client: 'phone', token: pairingData.token, version: '0.0.7' },
    transports: ['websocket']
  });

  await new Promise<void>((resolve) => {
    phoneSocket.on('connect', () => {
      phoneSocket.emit('tasks_dump', { version: '0.0.7', tasks: phoneTasks });
      resolve();
    });
  });

  // Handle phone mutations sent from server
  phoneSocket.on('mutation_request', (msg: any) => {
    const { mutationId, action, payload } = msg;
    if (action === 'create') {
      const newTask: TaskItem = {
        id: phoneTasks.length + 1,
        text: payload.text,
        createdAt: Date.now(),
        isDone: false,
        snoozeUntil: null
      };
      phoneTasks.push(newTask);
    } else if (action === 'done') {
      const t = phoneTasks.find((item) => String(item.id) === String(payload.target) || item.text === payload.target);
      if (t) t.isDone = true;
    } else if (action === 'punt') {
      const t = phoneTasks.find((item) => String(item.id) === String(payload.target) || item.text === payload.target);
      if (t) t.snoozeUntil = Date.now() + 3600000;
    }
    phoneSocket.emit('mutation_response', {
      mutationId,
      success: true,
      tasks: phoneTasks
    });
  });

  // Web client connected
  const webSocket = ClientIO(`http://localhost:${port}`, {
    query: { client: 'web' },
    transports: ['websocket']
  });

  let webReceivedTasks: TaskItem[] = [];
  webSocket.on('init', (data: any) => {
    webReceivedTasks = data.tasks || [];
  });
  webSocket.on('tasks_updated', (data: any) => {
    webReceivedTasks = data.tasks || [];
  });

  // Give initial sync a moment
  await new Promise((r) => setTimeout(r, 200));

  await t.test('1. Initial Android tasks reflect in Web and CLI', async () => {
    assert.equal(webReceivedTasks.length, 1);
    assert.equal(webReceivedTasks[0].text, 'Initial Android task');

    const { stdout } = await execFileAsync('npx', ['tsx', cliPath, 'tasks', 'list', '--port', String(port)]);
    assert.ok(stdout.includes('Initial Android task'));
  });

  await t.test('2. CLI adds task, which updates Android phone and Web UI', async () => {
    await execFileAsync('npx', ['tsx', cliPath, 'tasks', 'add', 'Added from CLI', '--port', String(port)]);

    await new Promise((r) => setTimeout(r, 200));
    assert.equal(phoneTasks.length, 2);
    assert.ok(phoneTasks.some((t) => t.text === 'Added from CLI'));
    assert.ok(webReceivedTasks.some((t) => t.text === 'Added from CLI'));
  });

  await t.test('3. Web client triggers Done mutation, which syncs to Android phone and CLI', async () => {
    await new Promise<void>((resolve, reject) => {
      webSocket.emit('mutate', { action: 'done', payload: { target: 1 } }, (res: any) => {
        if (res?.success) resolve();
        else reject(new Error('Web mutation failed'));
      });
    });

    await new Promise((r) => setTimeout(r, 200));
    const target = phoneTasks.find((t) => t.id === 1);
    assert.equal(target?.isDone, true);

    const { stdout } = await execFileAsync('npx', ['tsx', cliPath, 'tasks', 'list', '--port', String(port)]);
    assert.ok(stdout.includes('[x] 1: Initial Android task'));
  });

  await t.test('4. CLI punts task, which syncs to Android phone and Web UI', async () => {
    await execFileAsync('npx', ['tsx', cliPath, 'tasks', 'punt', '2', '1h', '--port', String(port)]);

    await new Promise((r) => setTimeout(r, 200));
    const target = phoneTasks.find((t) => t.id === 2);
    assert.ok(target?.snoozeUntil && target.snoozeUntil > Date.now());

    const webTarget = webReceivedTasks.find((t) => t.id === 2);
    assert.ok(webTarget?.snoozeUntil && webTarget.snoozeUntil > Date.now());
  });

  phoneSocket.disconnect();
  webSocket.disconnect();
  await server.stop();
});
