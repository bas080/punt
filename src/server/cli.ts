#!/usr/bin/env node
import yargs from 'yargs';
import { hideBin } from 'yargs/helpers';
import { PuntServer } from './server';
import { io, Socket } from 'socket.io-client';
import path from 'path';
import fs from 'fs';

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

function connectToCliSocket(port: number, timeoutMs: number = 2000): Promise<Socket> {
  return new Promise((resolve, reject) => {
    const socket = io(`http://localhost:${port}`, {
      query: { client: 'cli' },
      transports: ['websocket', 'polling'],
      timeout: timeoutMs,
      reconnection: false
    });

    const timer = setTimeout(() => {
      socket.disconnect();
      reject(new Error(`Unable to connect to Punt server at localhost:${port}. Is 'punt serve' running?`));
    }, timeoutMs);

    socket.on('connect', () => {
      clearTimeout(timer);
      resolve(socket);
    });

    socket.on('connect_error', () => {
      clearTimeout(timer);
      socket.disconnect();
      reject(new Error(`Unable to connect to Punt server at localhost:${port}. Is 'punt serve' running?`));
    });
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
          const pairingPin = server.getPairingCode();
          console.log(`Punt server listening on http://${host === '0.0.0.0' ? 'localhost' : host}:${actualPort}`);
          if (pairingPin) {
            console.log(`Pairing PIN: ${pairingPin} (enter this in Punt Android app)`);
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
          const socket = await connectToCliSocket(port);
          socket.emit('get_status', (data: any) => {
            console.log(`Server Status: Online (port ${port})`);
            console.log(`Phone Connected: ${data.phoneConnected ? 'Yes' : 'No'}`);
            console.log(`Active Tasks: ${data.tasks.filter((t: any) => !t.isDone && (!t.snoozeUntil || t.snoozeUntil <= Date.now())).length}`);
            console.log(`Punted Tasks: ${data.tasks.filter((t: any) => !t.isDone && t.snoozeUntil && t.snoozeUntil > Date.now()).length}`);
            console.log(`Total Tasks: ${data.tasks.length}`);
            socket.disconnect();
          });
        } catch (err: any) {
          console.error(err.message);
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
                const socket = await connectToCliSocket(port);
                socket.emit('get_status', (data: any) => {
                  if (!data.tasks || data.tasks.length === 0) {
                    console.log('No tasks found.');
                  } else {
                    for (const t of data.tasks) {
                      const check = t.isDone ? '[x]' : '[ ]';
                      const punted = t.snoozeUntil && t.snoozeUntil > Date.now() ? ` (punted until ${new Date(t.snoozeUntil).toLocaleTimeString()})` : '';
                      console.log(`${check} ${t.id != null ? `${t.id}: ` : ''}${t.text}${punted}`);
                    }
                  }
                  socket.disconnect();
                });
              } catch (err: any) {
                console.error(err.message);
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
                const socket = await connectToCliSocket(port);
                socket.emit('mutate', { action: 'create', payload: { text: text.trim() } }, (res: any) => {
                  if (!res.success) {
                    console.error(`Failed to add task: ${res.error || 'Server error'}`);
                    socket.disconnect();
                    process.exit(1);
                  }
                  console.log(`Task added: "${text.trim()}"`);
                  socket.disconnect();
                });
              } catch (err: any) {
                console.error(err.message);
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
                const socket = await connectToCliSocket(port);
                socket.emit('mutate', { action: 'done', payload: { target } }, (res: any) => {
                  if (!res.success) {
                    console.error(`Failed to mark task done: ${res.error || 'Server error'}`);
                    socket.disconnect();
                    process.exit(1);
                  }
                  console.log(`Task marked done: ${target}`);
                  socket.disconnect();
                });
              } catch (err: any) {
                console.error(err.message);
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
                const socket = await connectToCliSocket(port);
                socket.emit('mutate', { action: 'punt', payload: { target, duration } }, (res: any) => {
                  if (!res.success) {
                    console.error(`Failed to punt task: ${res.error || 'Server error'}`);
                    socket.disconnect();
                    process.exit(1);
                  }
                  console.log(`Task punted: ${target} for ${duration}`);
                  socket.disconnect();
                });
              } catch (err: any) {
                console.error(err.message);
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

function isCliEntrypoint(): boolean {
  const scriptPath = process.argv[1];
  if (!scriptPath) return false;
  try {
    const real = fs.realpathSync(scriptPath);
    const base = path.basename(real);
    if (base === 'cli.js' || base === 'cli.ts' || base === 'punt') return true;
  } catch {
    const base = path.basename(scriptPath);
    if (base === 'cli.js' || base === 'cli.ts' || base === 'punt') return true;
  }
  const base = path.basename(scriptPath);
  return base === 'cli.js' || base === 'cli.ts' || base === 'punt';
}

if (!process.env.PUNT_TEST && isCliEntrypoint()) {
  createCli().parse();
}
