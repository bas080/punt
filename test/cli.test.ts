import test from 'node:test';
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import path from 'node:path';
import { createCli } from '../src/server/cli';

const execFileAsync = promisify(execFile);
const cliPath = path.resolve(__dirname, '../src/server/cli.ts');

test('CLI option parsing and execution', async (t) => {
  await t.test('Default port is 7868 and environment variable override', async () => {
    const originalExit = process.exit;
    const originalError = console.error;
    const originalLog = console.log;
    (process as any).exit = () => {};
    console.error = () => {};
    console.log = () => {};

    try {
      const cli = createCli();
      const parsedDefault = await cli.parseAsync(['status']);
      assert.equal(parsedDefault.port, 7868);

      process.env.PUNT_PORT = '9000';
      const parsedEnv = await createCli().parseAsync(['status']);
      assert.equal(parsedEnv.port, 9000);

      const parsedOverride = await createCli().parseAsync(['status', '--port', '8080']);
      assert.equal(parsedOverride.port, 8080);
      delete process.env.PUNT_PORT;
    } finally {
      process.exit = originalExit;
      console.error = originalError;
      console.log = originalLog;
    }
  });

  await t.test('Tasks add fails gracefully when empty', async () => {
    try {
      await execFileAsync('npx', ['tsx', cliPath, 'tasks', 'add', '   ']);
      assert.fail('Should have failed on empty task text');
    } catch (err: any) {
      assert.equal(err.code, 1);
      assert.ok(err.stderr.includes('cannot be empty'));
    }
  });

  await t.test('Tasks list when server not running reports clear error', async () => {
    try {
      await execFileAsync('npx', ['tsx', cliPath, 'tasks', 'list', '--port', '59999']);
      assert.fail('Should have failed when server is offline');
    } catch (err: any) {
      assert.equal(err.code, 1);
      assert.ok(err.stderr.includes("Is 'punt serve' running?"));
    }
  });
});
