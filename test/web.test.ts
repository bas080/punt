import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import * as webModule from '../src/web/main';

const main: any = (webModule as any).default || webModule;

test('Web PWA templates and assets', async (t) => {
  await t.test('Manifest contains required PWA properties', () => {
    const manifestPath = path.resolve(__dirname, '../src/web/manifest.json');
    assert.ok(fs.existsSync(manifestPath));
    const content = JSON.parse(fs.readFileSync(manifestPath, 'utf-8'));
    assert.equal(content.name, 'Punt');
    assert.equal(content.short_name, 'Punt');
    assert.equal(content.display, 'standalone');
    assert.equal(content.theme_color, '#121212');
  });

  await t.test('Service worker script exists', () => {
    const swPath = path.resolve(__dirname, '../src/web/sw.js');
    assert.ok(fs.existsSync(swPath));
    const content = fs.readFileSync(swPath, 'utf-8');
    assert.ok(content.includes('addEventListener'));
  });

  await t.test('appView renders active and punted sections', () => {
    const sampleState = {
      tasks: [
        { text: 'Buy milk', isDone: false, snoozeUntil: null },
        { text: 'Call dentist', isDone: false, snoozeUntil: Date.now() + 3600000 },
        { text: 'Old task', isDone: true }
      ],
      phoneConnected: true,
      filterQuery: '',
      activeCollapsed: false,
      puntedCollapsed: false,
      notification: null,
      isError: false
    };

    const templateResult = main.appView(sampleState);
    assert.ok(templateResult != null);
    assert.ok(templateResult.strings != null);
    const joined = templateResult.strings.join(' ');
    assert.ok(joined.includes('ACTIVE'));
    assert.ok(joined.includes('PUNTED'));
  });

  await t.test('taskCardView renders task content', () => {
    const card = main.taskCardView({ text: 'Test Task', isDone: false });
    assert.ok(card != null);
    const joined = card.strings.join(' ');
    assert.ok(joined.includes('task-card'));
  });
});
