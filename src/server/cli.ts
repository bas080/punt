#!/usr/bin/env node
import yargs from 'yargs';
import { hideBin } from 'yargs/helpers';
import { PuntServer } from './server';
import readline from 'readline';

async function readStdin(): Promise<string> {
  if (process.stdin.isTTY) return '';
  return new Promise((resolve) => {
    let data = '';
    process.stdin.setEncoding('utf-8');
    process.stdin.on('data', chunk => { data += chunk; });
    process.stdin.on('end', () => { resolve(data.trim()); });
    process.stdin.on('error', () => { resolve(''); });
  });
}

export function createCli() {
  return yargs(hideBin(process.argv))
    .exitProcess(false)
    .env('PUNT')
    .scriptName('punt')
    .usage('$0 <command> [options]')
    .command(
      'serve',
      'Spawn and run in-memory Punt server in the foreground',
      (y) => {
        return y
          .option('port', {
            alias: 'p',
            type: 'number',
            description: 'Port to run the Punt server on',
            default: 7868
          })
          .option('host', {
            alias: 'h',
            type: 'string',
            description: 'Host to bind to',
            default: '0.0.0.0'
          })
          .option('code', {
            alias: 'c',
            type: 'string',
            description: '4-digit pairing code from Android app'
          })
          .option('open', {
            alias: 'o',
            type: 'boolean',
            description: 'Automatically open browser to web client',
            default: false
          });
      },
      async (argv) => {
        const port = argv.port as number;
        const host = argv.host as string;
        const code = argv.code as string | undefined;
        const server = new PuntServer({ port, host, code });
        try {
          const actualPort = await server.start();
          console.log(`Punt server listening on http://${host === '0.0.0.0' ? 'localhost' : host}:${actualPort}`);
          if (code) {
            console.log(`Pairing code set: ${code}`);
          }
          if (argv.open) {
            const { exec } = await import('child_process');
            const url = `http://localhost:${actualPort}`;
            exec(`xdg-open ${url} || open ${url} 2>/dev/null`);
          }
        } catch (err: any) {
          console.error(`Failed to start server: ${err.message}`);
          process.exit(1);
        }
      }
    )
    .command(
      'status',
      'Show status of the running server and connected phone',
      (y) => {
        return y.option('port', {
          alias: 'p',
          type: 'number',
          description: 'Port of running Punt server',
          default: 7868
        });
      },
      async (argv) => {
        const port = argv.port as number;
        try {
          const res = await fetch(`http://localhost:${port}/api/tasks`);
          if (!res.ok) {
            console.error(`Unable to connect to Punt server at localhost:${port}.`);
            process.exit(1);
          }
          const data = await res.json();
          console.log(`Server Status: Online (port ${port})`);
          console.log(`Phone Connected: ${data.phoneConnected ? 'Yes' : 'No'}`);
          console.log(`Active Tasks: ${data.tasks.filter((t: any) => !t.isDone && (!t.snoozeUntil || t.snoozeUntil <= Date.now())).length}`);
          console.log(`Punted Tasks: ${data.tasks.filter((t: any) => !t.isDone && t.snoozeUntil && t.snoozeUntil > Date.now()).length}`);
          console.log(`Total Tasks: ${data.tasks.length}`);
        } catch (_) {
          console.error(`Unable to connect to Punt server at localhost:${port}. Is 'punt serve' running?`);
          process.exit(1);
        }
      }
    )
    .command(
      'tasks <subcommand>',
      'Manage tasks through the running server',
      (y) => {
        return y
          .command(
            'list',
            'List mirrored tasks from the phone',
            (subY) => subY.option('port', { alias: 'p', type: 'number', default: 7868 }),
            async (argv) => {
              const port = argv.port as number;
              try {
                const res = await fetch(`http://localhost:${port}/api/tasks`);
                if (!res.ok) throw new Error();
                const data = await res.json();
                if (!data.tasks || data.tasks.length === 0) {
                  console.log('No tasks found.');
                  return;
                }
                for (const t of data.tasks) {
                  const check = t.isDone ? '[x]' : '[ ]';
                  const punted = t.snoozeUntil && t.snoozeUntil > Date.now() ? ` (punted until ${new Date(t.snoozeUntil).toLocaleTimeString()})` : '';
                  console.log(`${check} ${t.id != null ? `${t.id}: ` : ''}${t.text}${punted}`);
                }
              } catch (_) {
                console.error(`Unable to connect to Punt server at localhost:${port}. Is 'punt serve' running?`);
                process.exit(1);
              }
            }
          )
          .command(
            'add [text]',
            'Add a new task',
            (subY) => subY
              .positional('text', { type: 'string', description: 'Task text' })
              .option('port', { alias: 'p', type: 'number', default: 7868 }),
            async (argv) => {
              const port = argv.port as number;
              let text = argv.text as string | undefined;
              if (!text) {
                text = await readStdin();
              }
              if (!text || text.trim().length === 0) {
                console.error('Error: Task text cannot be empty.');
                process.exit(1);
              }
              try {
                const res = await fetch(`http://localhost:${port}/api/tasks/mutate`, {
                  method: 'POST',
                  headers: { 'Content-Type': 'application/json' },
                  body: JSON.stringify({ action: 'create', payload: { text: text.trim() } })
                });
                const data = await res.json();
                if (!res.ok || !data.success) {
                  console.error(`Failed to add task: ${data.error || 'Server error'}`);
                  process.exit(1);
                }
                console.log(`Task added: "${text.trim()}"`);
              } catch (_) {
                console.error(`Unable to connect to Punt server at localhost:${port}. Is 'punt serve' running?`);
                process.exit(1);
              }
            }
          )
          .command(
            'done <idOrText>',
            'Mark a task done',
            (subY) => subY
              .positional('idOrText', { type: 'string', demandOption: true, description: 'Task ID or task text' })
              .option('port', { alias: 'p', type: 'number', default: 7868 }),
            async (argv) => {
              const port = argv.port as number;
              const target = argv.idOrText as string;
              try {
                const res = await fetch(`http://localhost:${port}/api/tasks/mutate`, {
                  method: 'POST',
                  headers: { 'Content-Type': 'application/json' },
                  body: JSON.stringify({ action: 'done', payload: { target } })
                });
                const data = await res.json();
                if (!res.ok || !data.success) {
                  console.error(`Failed to mark task done: ${data.error || 'Server error'}`);
                  process.exit(1);
                }
                console.log(`Task marked done: ${target}`);
              } catch (_) {
                console.error(`Unable to connect to Punt server at localhost:${port}. Is 'punt serve' running?`);
                process.exit(1);
              }
            }
          )
          .command(
            'punt <idOrText> <duration>',
            'Punt a task for a duration (e.g. 15m, 1h, 1d, 1w, 1M)',
            (subY) => subY
              .positional('idOrText', { type: 'string', demandOption: true, description: 'Task ID or task text' })
              .positional('duration', { type: 'string', demandOption: true, description: 'Duration to punt for' })
              .option('port', { alias: 'p', type: 'number', default: 7868 }),
            async (argv) => {
              const port = argv.port as number;
              const target = argv.idOrText as string;
              const duration = argv.duration as string;
              try {
                const res = await fetch(`http://localhost:${port}/api/tasks/mutate`, {
                  method: 'POST',
                  headers: { 'Content-Type': 'application/json' },
                  body: JSON.stringify({ action: 'punt', payload: { target, duration } })
                });
                const data = await res.json();
                if (!res.ok || !data.success) {
                  console.error(`Failed to punt task: ${data.error || 'Server error'}`);
                  process.exit(1);
                }
                console.log(`Task punted: ${target} for ${duration}`);
              } catch (_) {
                console.error(`Unable to connect to Punt server at localhost:${port}. Is 'punt serve' running?`);
                process.exit(1);
              }
            }
          )
          .demandCommand(1, 'Please specify a tasks subcommand (list, add, done, punt)');
      }
    )
    .demandCommand(1, 'Please specify a command (serve, status, tasks)')
    .help();
}

if (process.argv[1] && (process.argv[1].endsWith('cli.ts') || process.argv[1].endsWith('cli.js'))) {
  createCli().parse();
}
