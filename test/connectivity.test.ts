import test from 'node:test';
import assert from 'node:assert/strict';
import { io as ClientIO } from 'socket.io-client';
import { PuntServer, TaskItem } from '../src/server/server';

test('Local server accepts pairing and syncs phone tasks over Socket.IO', async (t) => {
  const server = new PuntServer({ port: 0, host: '127.0.0.1' });
  const port = await server.start();
  const serverUrl = `http://127.0.0.1:${port}`;
  const webSocket = ClientIO(serverUrl, {
    query: { client: 'web' },
    transports: ['websocket'],
    reconnection: false
  });
  const webHandshake = new Promise<void>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Web Socket.IO handshake timed out')), 3000);
    webSocket.once('init', () => {
      clearTimeout(timer);
      resolve();
    });
    webSocket.once('connect_error', (error) => {
      clearTimeout(timer);
      reject(error);
    });
  });
  let phoneSocket: ReturnType<typeof ClientIO> | undefined;

  t.after(async () => {
    webSocket.disconnect();
    phoneSocket?.disconnect();
    await server.stop();
  });

  const pingResponse = await fetch(`${serverUrl}/api/ping`);
  assert.equal(pingResponse.status, 200);
  assert.equal((await pingResponse.json()).app, 'punt-server');

  const pairingResponse = await fetch(`${serverUrl}/api/pair`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code: server.getPairingCode() })
  });
  assert.equal(pairingResponse.status, 200, 'The displayed PIN should pair successfully');
  const pairing = await pairingResponse.json() as { token: string };

  await webHandshake;

  phoneSocket = ClientIO(serverUrl, {
    query: { client: 'phone', token: pairing.token, version: PuntServer.VERSION },
    transports: ['websocket'],
    reconnection: false
  });
  await new Promise<void>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Paired phone Socket.IO handshake timed out')), 3000);
    phoneSocket!.once('connect', () => {
      clearTimeout(timer);
      resolve();
    });
    phoneSocket!.once('connect_error', (error) => {
      clearTimeout(timer);
      reject(error);
    });
  });
  assert.equal(server.isPhoneConnected(), true);

  const tasks: TaskItem[] = [{ id: 1, text: 'Connectivity probe', isDone: false }];
  const updatedTasks = new Promise<TaskItem[]>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Task snapshot was not broadcast')), 3000);
    webSocket.once('tasks_updated', (payload: { tasks: TaskItem[] }) => {
      clearTimeout(timer);
      resolve(payload.tasks);
    });
  });
  phoneSocket.emit('tasks_dump', { version: PuntServer.VERSION, tasks });

  assert.deepEqual(await updatedTasks, tasks);
  assert.deepEqual(server.getTasks(), tasks);
});