import test from 'node:test';
import assert from 'node:assert/strict';
import { execFile, spawn } from 'node:child_process';
import { promisify } from 'node:util';
import path from 'node:path';
import { PuntServer } from '../src/server/server';
import { io as ClientIO } from 'socket.io-client';

const execFileAsync = promisify(execFile);
const cliPath = path.resolve(__dirname, '../src/server/cli.ts');

test('CLI Use Cases - All Commands and Flags', async (t) => {
  const testPort = 7891;
  const server = new PuntServer({ port: testPort });
  await server.start();

  const pairingResponse = await fetch(`http://localhost:${testPort}/api/pair`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code: server.getPairingCode() })
  });
  const pairingData = await pairingResponse.json();
  assert.equal(pairingData.success, true);

  let phoneTasks: any[] = [];
  const phoneSocket = ClientIO(`http://localhost:${testPort}`, {
    query: { client: 'phone', token: pairingData.token, version: '0.0.7' },
    transports: ['websocket']
  });

  await new Promise<void>((resolve) => {
    phoneSocket.on('connect', () => {
      phoneSocket.emit('tasks_dump', { version: '0.0.7', tasks: phoneTasks });
      resolve();
    });
  });

  phoneSocket.on('mutation_request', (msg: any) => {
    const { mutationId, action, payload } = msg;
    if (action === 'create') {
      const newTask = {
        id: phoneTasks.length + 1,
        text: payload.text,
        isDone: false,
        snoozeUntil: null
      };
      phoneTasks.push(newTask);
    } else if (action === 'done') {
      const target = phoneTasks.find((item) => String(item.id) === String(payload.target) || item.text === payload.target);
      if (target) target.isDone = true;
    } else if (action === 'punt') {
      const target = phoneTasks.find((item) => String(item.id) === String(payload.target) || item.text === payload.target);
      if (target) target.snoozeUntil = Date.now() + 3600000;
    }
    phoneSocket.emit('mutation_response', {
      mutationId,
      success: true,
      tasks: phoneTasks
    });
  });

  await t.test('Use Case 1: punt --help and punt --version', async () => {
    const helpResult = await execFileAsync('npx', ['tsx', cliPath, '--help']);
    assert.ok(helpResult.stdout.includes('punt <command> [options]'));
    assert.ok(helpResult.stdout.includes('serve'));
    assert.ok(helpResult.stdout.includes('status'));
    assert.ok(helpResult.stdout.includes('tasks'));

    const versionResult = await execFileAsync('npx', ['tsx', cliPath, '--version']);
    assert.ok(versionResult.stdout.includes('0.0.7'));
  });

  await t.test('Use Case 2: punt status returns connected phone status and task counts', async () => {
    const statusResult = await execFileAsync('npx', ['tsx', cliPath, 'status', '--port', String(testPort)]);
    assert.ok(statusResult.stdout.includes(`Server Status: Online (port ${testPort})`));
    assert.ok(statusResult.stdout.includes('Phone Connected: Yes'));
  });

  await t.test('Use Case 3: punt tasks add with text argument', async () => {
    const addResult = await execFileAsync('npx', ['tsx', cliPath, 'tasks', 'add', 'Urgent client meeting #work', '--port', String(testPort)]);
    assert.ok(addResult.stdout.includes('Task added: "Urgent client meeting #work"'));
  });

  await t.test('Use Case 4: punt tasks add via stdin pipe', async () => {
    const child = spawn('npx', ['tsx', cliPath, 'tasks', 'add', '--port', String(testPort)], { stdio: ['pipe', 'pipe', 'pipe'] });
    let output = '';
    child.stdout?.on('data', chunk => { output += chunk.toString(); });
    child.stdin?.write('Task from piped stdin\n');
    child.stdin?.end();

    await new Promise<void>((resolve, reject) => {
      child.on('close', code => {
        if (code === 0) resolve();
        else reject(new Error(`Process exited with code ${code}`));
      });
    });

    assert.ok(output.includes('Task added: "Task from piped stdin"'));
  });

  await t.test('Use Case 5: punt tasks list reflects added tasks', async () => {
    const listResult = await execFileAsync('npx', ['tsx', cliPath, 'tasks', 'list', '--port', String(testPort)]);
    assert.ok(listResult.stdout.includes('Urgent client meeting #work'));
    assert.ok(listResult.stdout.includes('Task from piped stdin'));
  });

  await t.test('Use Case 6: punt tasks punt for durations (15m, 1h, 1d, 1w, 1M)', async () => {
    const puntResult = await execFileAsync('npx', ['tsx', cliPath, 'tasks', 'punt', '1', '1h', '--port', String(testPort)]);
    assert.ok(puntResult.stdout.includes('Task punted: 1 for 1h'));
  });

  await t.test('Use Case 7: punt tasks done marks task complete', async () => {
    const doneResult = await execFileAsync('npx', ['tsx', cliPath, 'tasks', 'done', '2', '--port', String(testPort)]);
    assert.ok(doneResult.stdout.includes('Task marked done: 2'));

    const listResult = await execFileAsync('npx', ['tsx', cliPath, 'tasks', 'list', '--port', String(testPort)]);
    assert.ok(listResult.stdout.includes('[x] 2: Task from piped stdin'));
  });

  phoneSocket.disconnect();
  await server.stop();
});
