import { html, render, TemplateResult } from 'lit-html';

export interface Task {
  id?: number | string;
  text: string;
  createdAt?: number;
  snoozeUntil?: number | null;
  isDone?: boolean;
  recurrenceInterval?: string | null;
}

export interface AppState {
  tasks: Task[];
  phoneConnected: boolean;
  pairingPin: string | null;
  filterQuery: string;
  activeCollapsed: boolean;
  puntedCollapsed: boolean;
  notification: string | null;
  isError: boolean;
}

export const state: AppState = {
  tasks: [],
  phoneConnected: false,
  pairingPin: null,
  filterQuery: '',
  activeCollapsed: false,
  puntedCollapsed: false,
  notification: null,
  isError: false
};

let ws: WebSocket | null = null;
let notificationTimer: any = null;

export function showNotification(msg: string, isError: boolean = false) {
  state.notification = msg;
  state.isError = isError;
  clearTimeout(notificationTimer);
  notificationTimer = setTimeout(() => {
    state.notification = null;
    updateView();
  }, 3000);
  updateView();
}

import { io, Socket as ClientSocket } from 'socket.io-client';

let socket: ClientSocket | null = null;

export function sendMutation(action: string, payload: any) {
  if (!state.phoneConnected) {
    showNotification('Phone is unreachable', true);
    return;
  }
  const mutationId = 'web_mut_' + Math.random().toString(36).substring(2, 9);
  if (socket && socket.connected) {
    socket.emit('mutate', { action, payload, mutationId }, (res: any) => {
      if (res && !res.success) {
        showNotification(res.error || 'Action failed', true);
      }
    });
  } else {
    showNotification('Server disconnected', true);
  }
}

export function initWebSocket() {
  socket = io({
    query: { client: 'web' },
    transports: ['websocket', 'polling']
  });

  socket.on('init', (data: any) => {
    state.phoneConnected = data.phoneConnected;
    state.tasks = data.tasks || [];
    state.pairingPin = data.pairingPin || null;
    updateView();
  });

  socket.on('phone_status', (data: any) => {
    state.phoneConnected = data.connected;
    updateView();
  });

  socket.on('tasks_updated', (data: any) => {
    state.tasks = data.tasks || [];
    updateView();
  });

  socket.on('disconnect', () => {
    state.phoneConnected = false;
    updateView();
  });
}

export const taskCardView = (task: Task): TemplateResult => {
  const isPunted = !task.isDone && task.snoozeUntil != null && task.snoozeUntil > Date.now();
  const puntLabel = isPunted ? ` • until ${new Date(task.snoozeUntil!).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}` : '';
  const identifier = task.id != null ? task.id : task.text;

  return html`
    <div class="task-card ${task.isDone ? 'done' : ''}">
      <span class="task-text">${task.text}${puntLabel}</span>
      <div class="task-actions">
        ${!task.isDone ? html`
          <button class="btn-action" @click=${() => sendMutation('punt', { target: identifier, duration: '1h' })}>Punt 1h</button>
          <button class="btn-action" @click=${() => sendMutation('done', { target: identifier })}>Done</button>
        ` : ''}
      </div>
    </div>
  `;
};

export const appView = (s: AppState): TemplateResult => {
  const now = Date.now();
  const query = s.filterQuery.trim().toLowerCase();

  const filtered = s.tasks.filter(t => {
    if (!query) return true;
    return t.text.toLowerCase().includes(query);
  });

  const activeTasks = filtered.filter(t => !t.isDone && (!t.snoozeUntil || t.snoozeUntil <= now));
  const puntedTasks = filtered.filter(t => !t.isDone && t.snoozeUntil && t.snoozeUntil > now);

  return html`
    <header>
      <span class="title">Punt</span>
      <span class="status-badge ${s.phoneConnected ? 'connected' : ''}">
        ${s.phoneConnected ? 'Phone Connected' : 'Phone Offline'}
      </span>
    </header>

    ${!s.phoneConnected && s.pairingPin ? html`
      <div class="card pin-banner">
        <div style="font-size: 13px; color: var(--text-secondary); margin-bottom: 6px;">
          Waiting for phone... Enter PIN in Punt Android app:
        </div>
        <div class="pin-display">${s.pairingPin}</div>
      </div>
    ` : ''}

    <div class="card">
      <input
        class="search-input"
        type="text"
        placeholder="Add reminder (press ↵) or search..."
        .value=${s.filterQuery}
        @input=${(e: Event) => {
          s.filterQuery = (e.target as HTMLInputElement).value;
          updateView();
        }}
        @keydown=${(e: KeyboardEvent) => {
          if (e.key === 'Enter' && !e.shiftKey) {
            const val = (e.target as HTMLInputElement).value.trim();
            if (val) {
              sendMutation('create', { text: val });
              s.filterQuery = '';
              (e.target as HTMLInputElement).value = '';
              updateView();
            }
          }
        }}
      />
    </div>

    <div>
      <div
        class="section-header"
        @click=${() => { s.activeCollapsed = !s.activeCollapsed; updateView(); }}
      >
        ${s.activeCollapsed ? '▶' : '▼'} ACTIVE (${activeTasks.length})
      </div>
      ${!s.activeCollapsed ? html`
        <div class="task-list">
          ${activeTasks.map(taskCardView)}
        </div>
      ` : ''}
    </div>

    <div>
      <div
        class="section-header"
        @click=${() => { s.puntedCollapsed = !s.puntedCollapsed; updateView(); }}
      >
        ${s.puntedCollapsed ? '▶' : '▼'} PUNTED (${puntedTasks.length})
      </div>
      ${!s.puntedCollapsed ? html`
        <div class="task-list">
          ${puntedTasks.map(taskCardView)}
        </div>
      ` : ''}
    </div>

    ${s.notification ? html`
      <div class="toast ${s.isError ? 'error' : ''}">
        ${s.notification}
      </div>
    ` : ''}
  `;
};

export function updateView() {
  const container = document.getElementById('app');
  if (container) {
    render(appView(state), container);
  }
}

if (typeof window !== 'undefined' && document.getElementById('app')) {
  initWebSocket();
  updateView();
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }
}
