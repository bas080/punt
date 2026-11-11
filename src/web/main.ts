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
  phoneVersion: string | null;
  serverVersion: string | null;
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
  phoneVersion: null,
  serverVersion: '0.0.7',
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
    if (data.phoneVersion) state.phoneVersion = data.phoneVersion;
    if (data.serverVersion) state.serverVersion = data.serverVersion;
    state.tasks = data.tasks || [];
    state.pairingPin = data.pairingPin || null;
    updateView();
  });

  socket.on('phone_status', (data: any) => {
    state.phoneConnected = data.connected;
    if (data.phoneVersion) state.phoneVersion = data.phoneVersion;
    if (data.serverVersion) state.serverVersion = data.serverVersion;
    updateView();
  });

  socket.on('tasks_updated', (data: any) => {
    if (data.phoneVersion) state.phoneVersion = data.phoneVersion;
    if (data.serverVersion) state.serverVersion = data.serverVersion;
    state.tasks = data.tasks || [];
    updateView();
  });

  socket.on('disconnect', () => {
    state.phoneConnected = false;
    updateView();
  });
}

const iconDone = html`<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>`;
const iconPunt = html`<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>`;
const iconShare = html`<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="18" cy="5" r="3"></circle><circle cx="6" cy="12" r="3"></circle><circle cx="18" cy="19" r="3"></circle><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"></line><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"></line></svg>`;
const iconDelete = html`<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>`;

export const taskCardView = (task: Task): TemplateResult => {
  const isPunted = !task.isDone && task.snoozeUntil != null && task.snoozeUntil > Date.now();
  const puntLabel = isPunted ? ` • until ${new Date(task.snoozeUntil!).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}` : '';
  const identifier = task.id != null ? task.id : task.text;

  const handleShare = () => {
    if (navigator.share) {
      navigator.share({ text: task.text }).catch(() => {});
    } else if (navigator.clipboard) {
      navigator.clipboard.writeText(task.text).then(() => {
        showNotification('Task copied to clipboard');
      }).catch(() => {});
    }
  };

  return html`
    <div class="task-card ${task.isDone ? 'done' : ''}">
      <span class="task-text">${task.text}${puntLabel}</span>
      <div class="task-actions">
        ${!task.isDone ? html`
          <button class="btn-icon" title="Mark Done" aria-label="Mark Done" @click=${() => sendMutation('done', { target: identifier })}>
            ${iconDone}
          </button>
          <button class="btn-icon" title="Punt 1h" aria-label="Punt 1h" @click=${() => sendMutation('punt', { target: identifier, duration: '1h' })}>
            ${iconPunt}
          </button>
          <button class="btn-icon" title="Share" aria-label="Share" @click=${handleShare}>
            ${iconShare}
          </button>
        ` : ''}
        <button class="btn-icon delete" title="Delete" aria-label="Delete" @click=${() => sendMutation('delete', { target: identifier })}>
          ${iconDelete}
        </button>
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

  const isVersionMismatch = s.phoneConnected && s.phoneVersion && s.serverVersion && s.phoneVersion !== s.serverVersion;

  return html`
    <header>
      <span class="title">Punt</span>
      <div style="display: flex; align-items: center; gap: 8px;">
        ${isVersionMismatch ? html`
          <span class="version-mismatch" title="App version (${s.phoneVersion}) differs from server (${s.serverVersion})">
            v${s.phoneVersion} ≠ v${s.serverVersion}
          </span>
        ` : ''}
        <span class="status-badge ${s.phoneConnected ? 'connected' : ''}">
          ${s.phoneConnected ? 'Phone Connected' : 'Phone Offline'}
        </span>
      </div>
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
