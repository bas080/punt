import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import * as webModule from '../src/web/main';

const main: any = (webModule as any).default || webModule;

function flatten(value: any, out: string[] = []): string[] {
  if (value == null) return out;
  if (typeof value === 'string') {
    out.push(value);
    return out;
  }
  if (Array.isArray(value)) {
    for (const item of value) flatten(item, out);
    return out;
  }
  if (value.strings != null) {
    const strings: string[] = value.strings;
    const values: any[] = value.values ?? [];
    for (let i = 0; i < strings.length; i++) {
      out.push(strings[i]);
      if (i < values.length) flatten(values[i], out);
    }
  }
  return out;
}

function rendered(template: any): string {
  return flatten(template).join('');
}

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
    const joined = rendered(templateResult);
    assert.ok(joined.includes('ACTIVE'));
    assert.ok(joined.includes('PUNTED'));
  });

  await t.test('taskCardView renders task content', () => {
    const card = main.taskCardView({ text: 'Test Task', isDone: false });
    assert.ok(card != null);
    const joined = card.strings.join(' ');
    assert.ok(joined.includes('task-card'));
  });

  await t.test('taskCardView renders undo action for done tasks', () => {
    const card = main.taskCardView({ text: 'Completed item', isDone: true });
    assert.ok(card != null);
    const joined = rendered(card);
    assert.ok(joined.includes('Undo Done'));
    assert.ok(!joined.includes('Mark Done'));
  });

  await t.test('appView opens punt dialog with presets and custom input', () => {
    const templateResult = main.appView({
      tasks: [{ text: 'Buy milk', isDone: false }],
      phoneConnected: true,
      filterQuery: '',
      activeCollapsed: false,
      puntedCollapsed: false,
      notification: null,
      isError: false,
      dialog: 'punt',
      puntTargets: ['Buy milk'],
      puntIsPunted: false,
      focusProfiles: [],
      activeFocusProfiles: []
    });
    const joined = rendered(templateResult);
    assert.ok(joined.includes('Punt Reminder'));
    assert.ok(joined.includes('15m'));
    assert.ok(joined.includes('1h'));
    assert.ok(joined.includes('24h'));
    assert.ok(joined.includes('1w'));
    assert.ok(joined.includes('1M'));
    assert.ok(joined.includes('e.g. 15m, 1h, 4h, 18:00, or Mon'));
  });

  await t.test('appView renders bulk punt dialog title for multiple targets', () => {
    const templateResult = main.appView({
      tasks: [],
      phoneConnected: true,
      filterQuery: '',
      notification: null,
      isError: false,
      dialog: 'punt',
      puntTargets: ['Task A', 'Task B'],
      puntIsPunted: false,
      focusProfiles: [],
      activeFocusProfiles: []
    });
    const joined = rendered(templateResult);
    assert.ok(joined.includes('Punt All Reminders'));
    assert.ok(joined.includes('2 tasks'));
  });

  await t.test('appView opens focus profiles dialog with create input and rows', () => {
    const templateResult = main.appView({
      tasks: [],
      phoneConnected: true,
      filterQuery: '',
      notification: null,
      isError: false,
      dialog: 'focus',
      focusProfiles: [{ name: 'Work', query: '#work' }],
      activeFocusProfiles: ['Work']
    });
    const joined = rendered(templateResult);
    assert.ok(joined.includes('Focus Profiles'));
    assert.ok(joined.includes('Name #tag...'));
    assert.ok(joined.includes('Work'));
    assert.ok(joined.includes('#work'));
    assert.ok(joined.includes('Clear Focus'));
  });

  await t.test('appView opens export dialog with punt info options', () => {
    const templateResult = main.appView({
      tasks: [{ text: 'Buy milk', isDone: false }],
      phoneConnected: true,
      filterQuery: '',
      notification: null,
      isError: false,
      dialog: 'export',
      exportTargets: [{ text: 'Buy milk', isDone: false }]
    });
    const joined = rendered(templateResult);
    assert.ok(joined.includes('Export Reminders'));
    assert.ok(joined.includes('Include Punt Info'));
    assert.ok(joined.includes('Text Only'));
  });

  await t.test('appView opens import dialog with markdown textarea', () => {
    const templateResult = main.appView({
      tasks: [],
      phoneConnected: true,
      filterQuery: '',
      notification: null,
      isError: false,
      dialog: 'import',
      importText: ''
    });
    const joined = rendered(templateResult);
    assert.ok(joined.includes('Import Reminders from Markdown'));
    assert.ok(joined.includes('Paste Markdown checklist or text here...'));
  });

  await t.test('appView renders menu with minimal menu items', () => {
    const templateResult = main.appView({
      tasks: [{ text: 'Buy milk', isDone: false }],
      phoneConnected: true,
      filterQuery: '',
      notification: null,
      isError: false,
      menuOpen: true
    });
    const joined = rendered(templateResult);
    assert.ok(joined.includes('Focus Profiles'));
    assert.ok(joined.includes('Punt All'));
    assert.ok(joined.includes('Export Matching'));
    assert.ok(joined.includes('Import'));
  });

  await t.test('appView shows focus hint in search input when profiles are active', () => {
    const templateResult = main.appView({
      tasks: [{ text: 'Buy milk #work', isDone: false }],
      phoneConnected: true,
      filterQuery: '',
      notification: null,
      isError: false,
      focusProfiles: [{ name: 'Work', query: '#work' }],
      activeFocusProfiles: ['Work']
    });
    const joined = rendered(templateResult);
    assert.ok(joined.includes('Focus: Work'));
  });

  await t.test('appView hides done tasks unless query includes #done', () => {
    const baseState = {
      tasks: [
        { id: 1, text: 'Buy milk', isDone: false, createdAt: 2 },
        { id: 2, text: 'Old task', isDone: true, createdAt: 1 }
      ],
      phoneConnected: true,
      filterQuery: '',
      notification: null,
      isError: false,
      focusProfiles: [],
      activeFocusProfiles: []
    };

    const hidden = rendered(main.appView({ ...baseState }));
    assert.ok(hidden.includes('ACTIVE'));
    assert.ok(!hidden.includes('Old task'));

    const revealed = rendered(main.appView({ ...baseState, filterQuery: '#done' }));
    assert.ok(revealed.includes('Old task'));
    assert.ok(revealed.includes('ACTIVE'));
  });

  await t.test('appView shows empty state with clear filter and clear focus actions', () => {
    const templateResult = main.appView({
      tasks: [],
      phoneConnected: true,
      filterQuery: 'nomatch',
      notification: null,
      isError: false,
      focusProfiles: [{ name: 'Work', query: '#work' }],
      activeFocusProfiles: ['Work']
    });
    const joined = rendered(templateResult);
    assert.ok(joined.includes('Press Enter to create task'));
    assert.ok(joined.includes('Clear Filter'));
    assert.ok(joined.includes('Clear focus (Work)'));
    assert.ok(joined.includes('#every 1d'));
  });

  await t.test('renderTaskText separates recurrence and notify tags', () => {
    const renderedText = main.renderTaskText('Water plants #every 1d #notify loud');
    const joined = rendered(renderedText as any);
    assert.ok(joined.includes('tag-recurrence'));
    assert.ok(joined.includes('#every 1d'));
    assert.ok(joined.includes('tag-notify'));
    assert.ok(joined.includes('#notify loud'));
  });
});

test('Web parity helpers', async (t) => {
  await t.test('formatSnoozeUntil humanizes punt timestamps', () => {
    const now = new Date(2026, 10, 19, 10, 0, 0).getTime();
    const today = new Date(2026, 10, 19, 14, 30).getTime();
    const tomorrow = new Date(2026, 10, 20, 9, 0).getTime();
    const thisWeek = new Date(2026, 10, 24, 8, 15).getTime();
    const farFuture = new Date(2027, 0, 3, 12, 0).getTime();

    assert.ok(webModule.formatSnoozeUntil(today, now).includes('today at 14:30'));
    assert.ok(webModule.formatSnoozeUntil(tomorrow, now).includes('tomorrow at 09:00'));
    assert.equal(webModule.formatSnoozeUntil(thisWeek, now), 'Tue at 08:15');
    assert.equal(webModule.formatSnoozeUntil(farFuture, now), 'Jan 3, 2027 at 12:00');
  });

  await t.test('filterSearchQueryTiered falls back through the four tiers', () => {
    const reminders = [
      'buy groceries after work',
      'work meeting notes',
      'call dentist',
      'finish quarterly review at work'
    ];

    assert.deepEqual(
      webModule.filterSearchQueryTiered(reminders, 'work'),
      ['buy groceries after work', 'work meeting notes', 'finish quarterly review at work']
    );
    assert.deepEqual(
      webModule.filterSearchQueryTiered(reminders, 'work meeting'),
      ['work meeting notes']
    );
    assert.deepEqual(
      webModule.filterSearchQueryTiered(reminders, 'dentist meeting'),
      ['work meeting notes', 'call dentist']
    );
    assert.deepEqual(
      webModule.filterSearchQueryTiered(reminders, 'dentistxy meetingz'),
      []
    );
  });

  await t.test('selectVisibleTasks applies focus then search with union matching', () => {
    const tasks = [
      { id: 1, text: 'buy groceries #home', isDone: false },
      { id: 2, text: 'send email #work', isDone: false },
      { id: 3, text: 'status report #work', isDone: false },
      { id: 4, text: 'gym session #fitness', isDone: false },
      { id: 5, text: 'archived #work', isDone: true }
    ];

    const visible = webModule.selectVisibleTasks(
      tasks,
      [
        { name: 'Work', query: '#work' },
        { name: 'Fitness', query: '#fitness' }
      ],
      ['Work', 'Fitness'],
      ''
    );
    assert.deepEqual(visible.map((task: any) => task.text), [
      'send email #work',
      'status report #work',
      'gym session #fitness'
    ]);

    const focusedThenSearched = webModule.selectVisibleTasks(
      tasks,
      [{ name: 'Work', query: '#work' }],
      ['Work'],
      'report'
    );
    assert.deepEqual(focusedThenSearched.map((task: any) => task.text), ['status report #work']);

    const searchFallback = webModule.selectVisibleTasks(
      tasks,
      [{ name: 'Work', query: '#work' }],
      ['Work'],
      'groceries'
    );
    assert.deepEqual(searchFallback.map((task: any) => task.text), ['buy groceries #home']);
  });

  await t.test('exportToMarkdown emits checklist with optional punt time tags', () => {
    const tasks = [
      { id: 1, text: 'buy groceries', isDone: false, snoozeUntil: null },
      { id: 2, text: 'first line\nsecond line', isDone: false, snoozeUntil: webModule.parseIsoToMillis('2026-11-20T08:00:00Z') }
    ];

    const textOnly = webModule.exportToMarkdown(tasks, false);
    assert.equal(textOnly, '- [ ] buy groceries\n- [ ] first line\n  second line');

    const withPuntInfo = webModule.exportToMarkdown(tasks, true);
    assert.ok(withPuntInfo.includes('- [ ] first line <time datetime="'));
    assert.ok(withPuntInfo.includes('>punted until '));
  });

  await t.test('importFromMarkdown parses checklists, bullets, and time tags', () => {
    const markdown = [
      '- [ ] buy groceries',
      '- [x] archived item',
      '* plain bullet',
      '1. numbered item',
      '- [ ] punted item <time datetime="2026-11-20T08:00:00Z">punted until Nov 20, 2026 at 08:00</time>',
      '',
      '# Heading ignored',
      'indented continuation'
    ].join('\n');

    const items = webModule.importFromMarkdown(markdown);
    assert.deepEqual(items.map((item: any) => item.text), [
      'buy groceries',
      'archived item #done',
      'plain bullet',
      'numbered item',
      'punted item',
      'indented continuation'
    ]);
    assert.equal(items[1].isDone, true);
    assert.equal(items[4].isDone, false);
    assert.equal(items[4].snoozeUntilMillis, webModule.parseIsoToMillis('2026-11-20T08:00:00Z'));
  });

  await t.test('markdown export/import roundtrip preserves tasks and punt state', () => {
    const tasks = [
      { id: 1, text: 'buy groceries', isDone: false, snoozeUntil: 1765228800000 },
      { id: 2, text: 'old chore', isDone: true, snoozeUntil: null }
    ];
    const markdown = webModule.exportToMarkdown(tasks, true);
    const imported = webModule.importFromMarkdown(markdown);
    assert.deepEqual(imported.map((item: any) => item.text), ['buy groceries', 'old chore #done']);
    assert.equal(imported[0].snoozeUntilMillis, 1765228800000);
    assert.equal(imported[1].isDone, true);
  });

  await t.test('getCleanTrimmed strips done tags', () => {
    assert.equal(webModule.getCleanTrimmed('Buy milk #DONE'), 'buy milk');
    assert.equal(webModule.getCleanTrimmed('Call dentist'), 'call dentist');
  });

  await t.test('millisToIso and parseIsoToMillis roundtrip', () => {
    const millis = 1765228800000;
    assert.equal(webModule.parseIsoToMillis(webModule.millisToIso(millis)), millis);
    assert.equal(webModule.parseIsoToMillis('1765228800000'), 1765228800000);
  });
});
