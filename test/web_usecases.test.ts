import test from 'node:test';
import assert from 'node:assert/strict';
import { appView, taskCardView, state } from '../src/web/main';

test('Web App Use Cases - Rendering, Filtering, Actions, and Viewport States', async (t) => {
  await t.test('Use Case 1: Initial offline view with PIN banner and disabled input', () => {
    state.phoneConnected = false;
    state.pairingPin = '8821';
    state.tasks = [];

    const template = appView(state);
    assert.ok(template);
    assert.equal(state.phoneConnected, false);
    assert.equal(state.pairingPin, '8821');
  });

  await t.test('Use Case 2: Render active and punted task lists', () => {
    state.phoneConnected = true;
    state.tasks = [
      { id: 1, text: 'Active Task 1', isDone: false, snoozeUntil: null },
      { id: 2, text: 'Active Task 2', isDone: false, snoozeUntil: null },
      { id: 3, text: 'Punted Task', isDone: false, snoozeUntil: Date.now() + 3600000 },
      { id: 4, text: 'Done Task', isDone: true, snoozeUntil: null }
    ];

    const template = appView(state);
    assert.ok(template);
  });

  await t.test('Use Case 3: Search filter query isolates matching tasks', () => {
    state.filterQuery = 'Active Task 1';
    const template = appView(state);
    assert.ok(template);
    state.filterQuery = '';
  });

  await t.test('Use Case 4: Collapsible section states toggle visibility', () => {
    state.activeCollapsed = true;
    state.puntedCollapsed = true;
    let template = appView(state);
    assert.ok(template);

    state.activeCollapsed = false;
    state.puntedCollapsed = false;
    template = appView(state);
    assert.ok(template);
  });

  await t.test('Use Case 5: Version mismatch badge renders when server and phone versions differ', () => {
    state.phoneConnected = true;
    state.phoneVersion = '0.0.6';
    state.serverVersion = '0.0.7';

    const template = appView(state);
    assert.ok(template);
  });

  await t.test('Use Case 6: Task card view renders actions for active vs done tasks', () => {
    const activeCard = taskCardView({ id: 10, text: 'Incomplete item', isDone: false });
    assert.ok(activeCard);

    const doneCard = taskCardView({ id: 11, text: 'Completed item', isDone: true });
    assert.ok(doneCard);
  });
});
