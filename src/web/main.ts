import { html, render, TemplateResult } from 'lit-html';

export interface Task {
  id?: number | string;
  text: string;
  createdAt?: number;
  snoozeUntil?: number | null;
  isDone?: boolean;
  recurrenceInterval?: string | null;
}

export interface FocusProfile {
  name: string;
  query: string;
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
  focusProfiles: FocusProfile[];
  activeFocusProfiles: string[];
  menuOpen: boolean;
  dialog: 'punt' | 'focus' | 'export' | 'import' | null;
  puntTargets: string[];
  puntIsPunted: boolean;
  editingId: number | string | null;
  editingText: string;
  focusDraft: string;
  importText: string;
  exportTargets: Task[];
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
  isError: false,
  focusProfiles: [],
  activeFocusProfiles: [],
  menuOpen: false,
  dialog: null,
  puntTargets: [],
  puntIsPunted: false,
  editingId: null,
  editingText: '',
  focusDraft: '',
  importText: '',
  exportTargets: []
};

interface ParsedTaskText {
  text: string;
  isDone: boolean;
  snoozeUntilMillis: number | null;
}

export function getCleanTrimmed(reminderText: string): string {
  return reminderText.replace(/\s*#done\b/gi, '').trim().toLowerCase();
}

export function formatSnoozeUntil(snoozeUntil: number, now: number = Date.now()): string {
  const snoozeDate = new Date(snoozeUntil);
  const nowDate = new Date(now);

  const timeStr = `${snoozeDate.getHours()}`.padStart(2, '0') + ':' + `${snoozeDate.getMinutes()}`.padStart(2, '0');

  const startOfSnoozeDay = new Date(snoozeDate.getFullYear(), snoozeDate.getMonth(), snoozeDate.getDate()).getTime();
  const startOfNowDay = new Date(nowDate.getFullYear(), nowDate.getMonth(), nowDate.getDate()).getTime();
  const dayDiff = Math.round((startOfSnoozeDay - startOfNowDay) / 86400000);
  const sameYear = snoozeDate.getFullYear() === nowDate.getFullYear();

  const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

  if (sameYear && dayDiff === 0) return `today at ${timeStr}`;
  if (sameYear && dayDiff === 1) return `tomorrow at ${timeStr}`;
  if (sameYear && dayDiff >= 2 && dayDiff <= 6) return `${days[snoozeDate.getDay()]} at ${timeStr}`;
  if (sameYear) return `${months[snoozeDate.getMonth()]} ${snoozeDate.getDate()} at ${timeStr}`;
  return `${months[snoozeDate.getMonth()]} ${snoozeDate.getDate()}, ${snoozeDate.getFullYear()} at ${timeStr}`;
}

function formatSnoozeUntilFull(millis: number): string {
  const d = new Date(millis);
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  const hh = `${d.getHours()}`.padStart(2, '0');
  const mm = `${d.getMinutes()}`.padStart(2, '0');
  return `${months[d.getMonth()]} ${d.getDate()}, ${d.getFullYear()} at ${hh}:${mm}`;
}

export function filterSearchQueryTiered(reminders: string[], query: string): string[] {
  const q = query.trim();
  if (!q) return reminders;

  const rawQueryWords = q.toLowerCase().split(/\s+/).filter(Boolean);
  if (rawQueryWords.length === 0) return reminders;

  const tokenize = (text: string): string[] => text.toLowerCase().split(/\s+/).filter(Boolean);

  const tier1 = reminders.filter((reminder) => {
    const words = tokenize(reminder);
    return rawQueryWords.every((qWord) => words.includes(qWord));
  });
  if (tier1.length > 0) return tier1;

  const tier2 = reminders.filter((reminder) => rawQueryWords.every((qWord) => reminder.toLowerCase().includes(qWord)));
  if (tier2.length > 0) return tier2;

  const tier3 = reminders.filter((reminder) => {
    const words = tokenize(reminder);
    return rawQueryWords.some((qWord) => words.includes(qWord));
  });
  if (tier3.length > 0) return tier3;

  const tier4 = reminders.filter((reminder) => rawQueryWords.some((qWord) => reminder.toLowerCase().includes(qWord)));
  if (tier4.length > 0) return tier4;

  return [];
}

function filterTaskTexts(tasks: Task[], query: string): Task[] {
  const matched = new Set(filterSearchQueryTiered(tasks.map((t) => t.text), query));
  return tasks.filter((t) => matched.has(t.text));
}

function unionProfileMatches(tasks: Task[], profiles: FocusProfile[]): Task[] {
  const union = new Set<string>();
  for (const profile of profiles) {
    if (profile.query.trim().length > 0) {
      for (const text of filterSearchQueryTiered(tasks.map((t) => t.text), profile.query)) {
        union.add(text);
      }
    } else {
      for (const task of tasks) {
        union.add(task.text);
      }
    }
  }
  return tasks.filter((t) => union.has(t.text));
}

export function selectVisibleTasks(
  tasks: Task[],
  focusProfiles: FocusProfile[] = [],
  activeFocusProfileNames: string[] = [],
  searchQuery: string = ''
): Task[] {
  const activeProfiles = (focusProfiles ?? []).filter((p) =>
    (activeFocusProfileNames ?? []).some((n) => n.toLowerCase() === p.name.toLowerCase())
  );

  const searchContainsDone = /#done/i.test(searchQuery) || activeProfiles.some((p) => /#done/i.test(p.query));

  const statusFiltered = tasks.filter((task) => (task.isDone ? searchContainsDone : true));

  const focusedFiltered = activeProfiles.length > 0
    ? unionProfileMatches(statusFiltered, activeProfiles)
    : statusFiltered;

  const query = searchQuery.replace(/#done/gi, ' ').trim();
  if (!query) return focusedFiltered;

  const focusMatches = filterTaskTexts(focusedFiltered, query);
  if (focusMatches.length === 0 && activeProfiles.length > 0) {
    return filterTaskTexts(statusFiltered, query);
  }
  return focusMatches;
}

export function millisToIso(millis: number): string {
  return new Date(millis).toISOString().replace(/\.\d{3}Z$/, 'Z');
}

export function parseIsoToMillis(datetimeStr: string): number | null {
  if (/^\d+$/.test(datetimeStr)) {
    const numeric = Number(datetimeStr);
    return Number.isNaN(numeric) ? null : numeric;
  }
  const parsed = Date.parse(datetimeStr);
  return Number.isNaN(parsed) ? null : parsed;
}

export function exportToMarkdown(
  tasks: Task[],
  includePuntInfo: boolean
): string {
  if (tasks.length === 0) return '';
  return tasks.map((task) => {
    const lines = task.text.split('\n');
    const firstLine = lines[0] ?? '';
    const remainingLines = lines.slice(1);

    const isDone = task.isDone === true;
    const prefix = isDone ? '- [x] ' : '- [ ] ';

    let timeTag = '';
    if (includePuntInfo && !isDone && typeof task.snoozeUntil === 'number' && task.snoozeUntil > 0) {
      const display = formatSnoozeUntilFull(task.snoozeUntil);
      timeTag = ` <time datetime="${millisToIso(task.snoozeUntil)}">punted until ${display}</time>`;
    }

    const formattedFirstLine = `${prefix}${firstLine}${timeTag}`;
    const formattedRemaining = remainingLines.map((line) => `  ${line}`);
    return [formattedFirstLine, ...formattedRemaining].join('\n');
  }).join('\n');
}

const CHECKLIST_REGEX = /^\s*[-*+]?\s*\[[ xX]?\]\s*(.+)$/;
const BULLET_REGEX = /^\s*[-*+]\s+(.+)$/;
const NUMBER_REGEX = /^\s*\d+\.\s+(.+)$/;
const CHECKLIST_CHECKED_REGEX = /^\s*[-*+]?\s*\[[xX]\]/;
const TIME_TAG_REGEX = /<time\s+datetime="([^"]+)"[^>]*>(.*?)<\/time>/i;
const TIME_TAG_SELF_CLOSING_REGEX = /<time\s+datetime="([^"]+)"\s*\/?>/i;

interface RawItem {
  lines: string[];
  isChecked: boolean;
}

export function importFromMarkdown(markdownContent: string): ParsedTaskText[] {
  if (!markdownContent || !markdownContent.trim()) return [];

  const rawItems = parseRawMarkdownItems(markdownContent.split('\n'));
  const results: ParsedTaskText[] = [];

  for (const rawItem of rawItems) {
    const processed = processRawMarkdownItem(rawItem);
    if (processed && !results.some((r) => r.text === processed.text)) {
      results.push(processed);
    }
  }

  return results;
}

function parseRawMarkdownItems(lines: string[]): RawItem[] {
  const rawItems: RawItem[] = [];
  let currentItem: RawItem | null = null;

  for (const line of lines) {
    currentItem = processMarkdownLine(line, currentItem, rawItems);
  }

  if (currentItem) rawItems.push(currentItem);
  return rawItems;
}

function processMarkdownLine(line: string, currentItem: RawItem | null, rawItems: RawItem[]): RawItem | null {
  const trimmed = line.trim();
  if (!trimmed || trimmed.startsWith('#')) {
    if (currentItem) rawItems.push(currentItem);
    return null;
  }

  const listMatch = CHECKLIST_REGEX.exec(line) || BULLET_REGEX.exec(line) || NUMBER_REGEX.exec(line);

  if (listMatch) {
    if (currentItem) rawItems.push(currentItem);
    const isChecked = CHECKLIST_CHECKED_REGEX.test(line);
    const content = (listMatch[1] ?? '').trim();
    return { lines: [content], isChecked };
  }

  const isIndented = line.startsWith('  ') || line.startsWith('\t');
  if (isIndented && currentItem) {
    const unindented = line.startsWith('  ') ? line.substring(2) : line.replace(/^\t/, '');
    currentItem.lines.push(unindented);
    return currentItem;
  }

  if (currentItem) rawItems.push(currentItem);
  return { lines: [trimmed], isChecked: false };
}

function processRawMarkdownItem(rawItem: RawItem): ParsedTaskText | null {
  const fullContent = rawItem.lines.join('\n');
  const { cleanText, snoozeUntilMillis } = extractPuntTimeTag(fullContent);
  if (!cleanText.trim()) return null;

  const hasDoneTag = /#done/i.test(cleanText);
  const isDone = rawItem.isChecked || hasDoneTag;

  const finalText = isDone && !hasDoneTag ? `${cleanText} #done` : cleanText;
  return { text: finalText, isDone, snoozeUntilMillis };
}

function extractPuntTimeTag(content: string): { cleanText: string; snoozeUntilMillis: number | null } {
  const match = TIME_TAG_REGEX.exec(content) || TIME_TAG_SELF_CLOSING_REGEX.exec(content);
  if (!match) return { cleanText: content, snoozeUntilMillis: null };

  const snoozeUntilMillis = parseIsoToMillis(match[1]);
  const cleanContent = content.replace(match[0], '');
  const normalized = cleanContent.split('\n').map((l) => l.trim()).join('\n').trim();
  return { cleanText: normalized, snoozeUntilMillis };
}

const STORAGE_KEYS = {
  focusProfiles: 'punt_web_focus_profiles',
  activeFocusProfiles: 'punt_web_active_focus_profiles',
  filterQuery: 'punt_web_search_query',
  activeCollapsed: 'punt_web_active_collapsed',
  puntedCollapsed: 'punt_web_punted_collapsed'
};

function storageGet(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch (_) {
    return null;
  }
}

function storageSet(key: string, value: string) {
  try {
    localStorage.setItem(key, value);
  } catch (_) {}
}

export function loadPersistedWebState() {
  const profilesRaw = storageGet(STORAGE_KEYS.focusProfiles);
  if (profilesRaw) {
    try {
      const parsed = JSON.parse(profilesRaw);
      if (Array.isArray(parsed)) state.focusProfiles = parsed.filter((p: any) => p && typeof p.name === 'string');
    } catch (_) {}
  }
  const activeRaw = storageGet(STORAGE_KEYS.activeFocusProfiles);
  if (activeRaw) {
    try {
      const parsed = JSON.parse(activeRaw);
      if (Array.isArray(parsed)) state.activeFocusProfiles = parsed.filter((n: any) => typeof n === 'string');
    } catch (_) {}
  }
  state.filterQuery = storageGet(STORAGE_KEYS.filterQuery) ?? '';
  state.activeCollapsed = storageGet(STORAGE_KEYS.activeCollapsed) === 'true';
  state.puntedCollapsed = storageGet(STORAGE_KEYS.puntedCollapsed) === 'true';
}

export function persistWebState() {
  storageSet(STORAGE_KEYS.focusProfiles, JSON.stringify(state.focusProfiles));
  storageSet(STORAGE_KEYS.activeFocusProfiles, JSON.stringify(state.activeFocusProfiles));
  storageSet(STORAGE_KEYS.filterQuery, state.filterQuery);
  storageSet(STORAGE_KEYS.activeCollapsed, state.activeCollapsed ? 'true' : 'false');
  storageSet(STORAGE_KEYS.puntedCollapsed, state.puntedCollapsed ? 'true' : 'false');
}

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

  socket.on('pin_update', (data: any) => {
    state.pairingPin = data?.pairingPin || null;
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

const iconMenu = html`<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><line x1="4" y1="6" x2="20" y2="6"></line><line x1="4" y1="12" x2="20" y2="12"></line><line x1="4" y1="18" x2="20" y2="18"></line></svg>`;
const iconDone = html`<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>`;
const iconUndo = html`<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3 7v6h6"></path><path d="M21 17a9 9 0 0 0-9-9 9 9 0 0 0-6 2.3L3 13"></path></svg>`;
const iconPunt = html`<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>`;
const iconUnpunt = html`<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline><line x1="4" y1="20" x2="20" y2="4"></line></svg>`;
const iconShare = html`<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="18" cy="5" r="3"></circle><circle cx="6" cy="12" r="3"></circle><circle cx="18" cy="19" r="3"></circle><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"></line><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"></line></svg>`;
const iconDelete = html`<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>`;
const iconClear = html`<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>`;

const DEFAULT_PUNT_CHOICES = ['15m', '1h', '4h', '24h', '1w', '1M'];

const RECURRENCE_TAG_REGEX = /#(?:every|repeat)(?:\(([^)]+)\)|[:/]?\s*([^#\n]+)?)/gi;
const NOTIFY_TAG_REGEX = /#notify(?:\(([^)]+)\)|[:/]?\s*([^#\n]+)?)/g;

export function renderTaskText(text: string): TemplateResult | string {
  interface Segment {
    start: number;
    end: number;
    cls: string;
  }
  const segments: Segment[] = [];

  for (const entry of [
    { regex: RECURRENCE_TAG_REGEX, cls: 'tag-recurrence' },
    { regex: NOTIFY_TAG_REGEX, cls: 'tag-notify' }
  ]) {
    entry.regex.lastIndex = 0;
    let match: RegExpExecArray | null;
    while ((match = entry.regex.exec(text)) !== null) {
      let end = match.index + match[0].length;
      while (end > match.index && /\s/.test(text[end - 1])) end--;
      if (end > match.index) {
        segments.push({ start: match.index, end, cls: entry.cls });
      }
      if (match.index === entry.regex.lastIndex) entry.regex.lastIndex++;
    }
  }

  if (segments.length === 0) return text;

  segments.sort((a, b) => a.start - b.start);
  const parts: (string | TemplateResult)[] = [];
  let cursor = 0;
  for (const seg of segments) {
    if (seg.start < cursor) continue;
    if (seg.start > cursor) parts.push(text.slice(cursor, seg.start));
    parts.push(html`<span class=${seg.cls}>${text.slice(seg.start, seg.end)}</span>`);
    cursor = seg.end;
  }
  if (cursor < text.length) parts.push(text.slice(cursor));
  return html`${parts}`;
}

let puntDurationDraft = '';

export function beginEdit(task: Task) {
  state.editingId = task.id != null ? task.id : task.text;
  state.editingText = task.text;
  updateView();
}

export function commitEdit(target: string | number) {
  if (state.editingId == null) return;
  const text = state.editingText.trim();
  state.editingId = null;
  state.editingText = '';
  if (text) {
    sendMutation('edit', { target, text });
  }
  updateView();
}

export function cancelEdit() {
  state.editingId = null;
  state.editingText = '';
  updateView();
}

export function openPuntDialog(targets: string[], isPunted: boolean = false) {
  if (targets.length === 0) {
    showNotification('No active reminders to punt', true);
    return;
  }
  state.puntTargets = targets;
  state.puntIsPunted = isPunted;
  state.dialog = 'punt';
  state.menuOpen = false;
  puntDurationDraft = '';
  updateView();
}

export function applyPuntDuration(durationChoice: string) {
  const targets = state.puntTargets;
  const choice = durationChoice.trim();
  state.dialog = null;
  state.puntTargets = [];
  state.puntIsPunted = false;
  if (!choice) {
    showNotification('Failed to punt: Invalid duration entered', true);
    updateView();
    return;
  }
  for (const target of targets) {
    sendMutation('punt', { target, duration: choice });
  }
  showNotification(`Reminder punted for ${choice}`);
  updateView();
}

export function unpuntTarget(target: string) {
  state.dialog = null;
  state.puntTargets = [];
  state.puntIsPunted = false;
  sendMutation('unpunt', { target });
  showNotification('Punt cancelled');
  updateView();
}

export function toggleFocusProfile(name: string) {
  if (state.activeFocusProfiles.some((n) => n.toLowerCase() === name.toLowerCase())) {
    state.activeFocusProfiles = state.activeFocusProfiles.filter((n) => n.toLowerCase() !== name.toLowerCase());
  } else {
    state.activeFocusProfiles = [...state.activeFocusProfiles, name];
  }
  persistWebState();
  updateView();
}

export function clearFocusProfiles() {
  state.activeFocusProfiles = [];
  persistWebState();
  updateView();
}

export function addFocusProfileFromDraft() {
  const value = state.focusDraft.trim();
  if (!value) return;
  const parts = value.split(/\s+/);
  const name = parts[0];
  const query = parts.slice(1).join(' ');
  const existing = state.focusProfiles.find((p) => p.name.toLowerCase() === name.toLowerCase());
  if (existing) {
    existing.query = query;
  } else {
    state.focusProfiles = [...state.focusProfiles, { name, query }];
  }
  if (!state.activeFocusProfiles.some((n) => n.toLowerCase() === name.toLowerCase())) {
    state.activeFocusProfiles = [...state.activeFocusProfiles, name];
  }
  state.focusDraft = '';
  persistWebState();
  updateView();
}

export function deleteFocusProfile(name: string) {
  state.focusProfiles = state.focusProfiles.filter((p) => p.name !== name);
  state.activeFocusProfiles = state.activeFocusProfiles.filter((n) => n.toLowerCase() !== name.toLowerCase());
  persistWebState();
  updateView();
}

export function exportVisibleTasks(includePuntInfo: boolean, tasks: Task[]) {
  closeDialog();
  if (tasks.length === 0) {
    showNotification('No reminders to export', true);
    return;
  }

  const markdown = exportToMarkdown(tasks, includePuntInfo);
  const blob = new Blob([markdown], { type: 'text/markdown' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = 'punt_tasks_backup.md';
  anchor.click();
  URL.revokeObjectURL(url);
  showNotification('Reminders exported');
}

export function importTasksFromDraft() {
  const items = importFromMarkdown(state.importText);
  state.dialog = null;
  state.importText = '';
  if (items.length === 0) {
    showNotification('No reminders found to import', true);
    updateView();
    return;
  }
  for (const item of items) {
    sendMutation('create', { text: item.text });
  }
  showNotification(`Imported ${items.length} new reminder(s)`);
  updateView();
}

async function prefillImportFromClipboard() {
  try {
    const clip = await navigator.clipboard.readText();
    if (clip && !state.importText) {
      state.importText = clip;
      updateView();
    }
  } catch (_) {}
}

export function toggleMenu() {
  state.menuOpen = !state.menuOpen;
  updateView();
}

function closeDialog() {
  state.dialog = null;
  state.puntTargets = [];
  state.puntIsPunted = false;
  updateView();
}

export const taskCardView = (task: Task): TemplateResult => {
  const isDone = task.isDone === true;
  const isPunted = !isDone && typeof task.snoozeUntil === 'number' && task.snoozeUntil > Date.now();
  const identifier = task.id != null ? task.id : task.text;
  const isEditing = state.editingId != null && state.editingId === identifier;

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
    <div class="task-card ${isDone ? 'done' : ''}">
      ${isEditing ? html`
        <textarea
          class="task-edit-text"
          rows="2"
          .value=${state.editingText}
          @input=${(e: Event) => { state.editingText = (e.target as HTMLTextAreaElement).value; updateView(); }}
          @keydown=${(e: KeyboardEvent) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              commitEdit(identifier);
            } else if (e.key === 'Escape') {
              cancelEdit();
            }
          }}
          @blur=${() => commitEdit(identifier)}
        ></textarea>
      ` : html`
        <span
          class="task-text"
          title="Click to edit"
          tabindex="0"
          @click=${() => beginEdit(task)}
        >${renderTaskText(task.text)}${isPunted ? html`<span class="task-punt-label"> · punted until ${formatSnoozeUntil(task.snoozeUntil!)}</span>` : ''}</span>
      `}
      <div class="task-actions">
        ${isDone ? html`
          <button class="btn-icon" title="Undo Done" aria-label="Undo Done" @click=${() => sendMutation('undone', { target: identifier })}>
            ${iconUndo}
          </button>
        ` : html`
          <button class="btn-icon" title="Mark Done" aria-label="Mark Done" @click=${() => {
            sendMutation('done', { target: identifier });
            showNotification('Reminder marked done');
          }}>
            ${iconDone}
          </button>
          <button class="btn-icon" title="Punt" aria-label="Punt" @click=${() => openPuntDialog([String(identifier)], isPunted)}>
            ${iconPunt}
          </button>
          <button class="btn-icon" title="Share" aria-label="Share" @click=${handleShare}>
            ${iconShare}
          </button>
        `}
        <button class="btn-icon delete" title="Delete" aria-label="Delete" @click=${() => {
          sendMutation('delete', { target: identifier });
          showNotification('Reminder deleted');
        }}>
          ${iconDelete}
        </button>
      </div>
    </div>
  `;
};

const puntDialogView = (s: AppState): TemplateResult => {
  const isBulk = s.puntTargets.length > 1;
  const title = isBulk ? 'Punt All Reminders' : 'Punt Reminder';
  const subtitle = isBulk ? `${s.puntTargets.length} tasks` : s.puntTargets[0];
  return html`
    <div class="dialog-backdrop" @click=${() => closeDialog()}>
      <div class="dialog" @click=${(e: Event) => e.stopPropagation()}>
        <div class="dialog-title">${title}</div>
        <span class="dialog-subtle">${subtitle}</span>
        <div class="punt-presets">
          ${DEFAULT_PUNT_CHOICES.map((choice) => html`
            <button class="punt-preset" @click=${() => applyPuntDuration(choice)}>${choice}</button>
          `)}
        </div>
        <input
          class="dialog-input"
          type="text"
          placeholder="e.g. 15m, 1h, 4h, 18:00, or Mon"
          .value=${puntDurationDraft}
          @input=${(e: Event) => { puntDurationDraft = (e.target as HTMLInputElement).value; updateView(); }}
          @keydown=${(e: KeyboardEvent) => { if (e.key === 'Enter') { applyPuntDuration(puntDurationDraft); } }}
        />
        <button class="dialog-submit" ?disabled=${!puntDurationDraft.trim()} @click=${() => applyPuntDuration(puntDurationDraft)}>Punt</button>
        ${s.puntIsPunted && !isBulk ? html`
          <button class="dialog-link" @click=${() => unpuntTarget(s.puntTargets[0])}>Unpunt</button>
        ` : ''}
        <button class="dialog-cancel" @click=${() => closeDialog()}>Cancel</button>
      </div>
    </div>
  `;
};

const focusDialogView = (s: AppState): TemplateResult => html`
  <div class="dialog-backdrop" @click=${() => closeDialog()}>
    <div class="dialog" @click=${(e: Event) => e.stopPropagation()}>
      <div class="dialog-title">Focus Profiles</div>
      <div class="focus-create-row">
        <input
          class="dialog-input focus-create-input"
          type="text"
          placeholder="Name #tag..."
          .value=${s.focusDraft}
          @input=${(e: Event) => { s.focusDraft = (e.target as HTMLInputElement).value; updateView(); }}
          @keydown=${(e: KeyboardEvent) => { if (e.key === 'Enter') { addFocusProfileFromDraft(); } }}
        />
        <button class="focus-add" title="Add Focus Profile" aria-label="Add Focus Profile" @click=${() => addFocusProfileFromDraft()}>+</button>
      </div>

      <div class="focus-list">
        ${s.focusProfiles.length === 0 ? html`
          <span class="dialog-subtle">No focus profiles saved.</span>
        ` : s.focusProfiles.map((p) => {
          const isActive = s.activeFocusProfiles.some((n) => n.toLowerCase() === p.name.toLowerCase());
          return html`
            <div class="focus-row ${isActive ? 'active' : ''}">
              <span class="focus-toggle" role="button" aria-label="Toggle focus profile ${p.name}" @click=${() => toggleFocusProfile(p.name)}>
                ${isActive ? '●' : '○'} <span class="focus-name">${p.name}</span>${p.query ? html` <span class="focus-query">${p.query}</span>` : ''}
              </span>
              <span
                class="focus-delete"
                role="button"
                aria-label="Delete focus profile ${p.name}"
                @click=${() => deleteFocusProfile(p.name)}
              >✕</span>
            </div>
          `;
        })}
      </div>

      <button class="dialog-cancel" @click=${() => { clearFocusProfiles(); }}>Clear Focus</button>
      <button class="dialog-cancel" @click=${() => closeDialog()}>Cancel</button>
    </div>
  </div>
`;

const exportDialogView = (s: AppState): TemplateResult => html`
  <div class="dialog-backdrop" @click=${() => closeDialog()}>
    <div class="dialog" @click=${(e: Event) => e.stopPropagation()}>
      <div class="dialog-title">Export Reminders</div>
      <span class="dialog-subtle">${s.exportTargets.length} task(s) matched</span>
      <button class="dialog-submit" @click=${() => exportVisibleTasks(true, s.exportTargets)}>Include Punt Info</button>
      <button class="dialog-secondary" @click=${() => exportVisibleTasks(false, s.exportTargets)}>Text Only</button>
      <button class="dialog-cancel" @click=${() => closeDialog()}>Cancel</button>
    </div>
  </div>
`;

const importDialogView = (s: AppState): TemplateResult => html`
  <div class="dialog-backdrop" @click=${() => closeDialog()}>
    <div class="dialog" @click=${(e: Event) => e.stopPropagation()}>
      <div class="dialog-title">Import Reminders from Markdown</div>
      <textarea
        class="dialog-textarea"
        rows="6"
        placeholder="Paste Markdown checklist or text here..."
        .value=${s.importText}
        @input=${(e: Event) => { s.importText = (e.target as HTMLTextAreaElement).value; updateView(); }}
      ></textarea>
      <button class="dialog-submit" @click=${() => importTasksFromDraft()}>Import</button>
      <button class="dialog-cancel" @click=${() => closeDialog()}>Cancel</button>
    </div>
  </div>
`;

export const appView = (s: AppState): TemplateResult => {
  const now = Date.now();
  s.focusProfiles = s.focusProfiles ?? [];
  s.activeFocusProfiles = s.activeFocusProfiles ?? [];
  s.puntTargets = s.puntTargets ?? [];
  s.exportTargets = s.exportTargets ?? [];

  const visibleTasks = selectVisibleTasks(s.tasks, s.focusProfiles, s.activeFocusProfiles, s.filterQuery);

  const activeTasks = visibleTasks
    .filter((t) => !t.isDone && (!t.snoozeUntil || t.snoozeUntil <= now))
    .sort((a, b) => {
      const aCreated = typeof a.createdAt === 'number' ? a.createdAt : 0;
      const bCreated = typeof b.createdAt === 'number' ? b.createdAt : 0;
      if (aCreated !== bCreated) return bCreated - aCreated;
      return a.id != null && b.id != null ? Number(b.id) - Number(a.id) : 0;
    });

  const puntedTasks = visibleTasks
    .filter((t) => !t.isDone && t.snoozeUntil != null && t.snoozeUntil > now)
    .sort((a, b) => (a.snoozeUntil as number) - (b.snoozeUntil as number));

  const doneTasks = visibleTasks.filter((t) => t.isDone === true);

  const isVersionMismatch = s.phoneConnected && s.phoneVersion && s.serverVersion && s.phoneVersion !== s.serverVersion;
  const hasActiveFocusProfiles = s.activeFocusProfiles.length > 0;
  const searchHint = hasActiveFocusProfiles ? `Focus: ${s.activeFocusProfiles.join(', ')}` : 'Add reminder (press ⏎) or search...';
  const showEmptyState = visibleTasks.length === 0;
  const hasFilterOrSearch = s.filterQuery.trim().length > 0;

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
        <button
          class="btn-icon menu-btn"
          title="Menu"
          aria-label="Menu"
          aria-expanded=${s.menuOpen ? 'true' : 'false'}
          @click=${() => toggleMenu()}
        >${iconMenu}</button>
      </div>
    </header>

    ${s.menuOpen ? html`
      <div class="menu-backdrop" @click=${() => { s.menuOpen = false; updateView(); }}></div>
      <div class="menu-container" role="menu">
        <button class="menu-item" role="menuitem" @click=${() => { s.menuOpen = false; s.dialog = 'focus'; updateView(); }}>Focus Profiles</button>
        <button class="menu-item" role="menuitem" @click=${() => openPuntDialog(activeTasks.filter((t) => !t.isDone).map((t) => String(t.id != null ? t.id : t.text)))}>Punt All</button>
        <button class="menu-item" role="menuitem" @click=${() => {
          s.menuOpen = false;
          const exportable = visibleTasks.filter((t) => !t.isDone);
          s.dialog = exportable.length > 0 ? 'export' : null;
          s.exportTargets = exportable;
          if (exportable.length === 0) showNotification('No reminders to export', true);
          updateView();
        }}>Export Matching</button>
        <button class="menu-item" role="menuitem" @click=${() => {
          s.menuOpen = false;
          s.dialog = 'import';
          s.importText = '';
          updateView();
          prefillImportFromClipboard();
        }}>Import</button>
      </div>
    ` : ''}

    ${!s.phoneConnected && s.pairingPin ? html`
      <div class="card pin-banner">
        <div style="font-size: 13px; color: var(--text-secondary); margin-bottom: 6px;">
          Waiting for phone... Enter PIN in Punt Android app:
        </div>
        <div class="pin-display">${s.pairingPin}</div>
      </div>
    ` : ''}

    <div class="main-content ${!s.phoneConnected ? 'offline-disabled' : ''}">
      <div class="card search-card">
        <input
          class="search-input"
          type="text"
          placeholder=${searchHint}
          ?disabled=${!s.phoneConnected}
          .value=${s.filterQuery}
          @input=${(e: Event) => {
            s.filterQuery = (e.target as HTMLInputElement).value;
            persistWebState();
            updateView();
          }}
          @keydown=${(e: KeyboardEvent) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              const val = (e.target as HTMLInputElement).value.trim();
              if (val) {
                sendMutation('create', { text: val });
                s.filterQuery = '';
                (e.target as HTMLInputElement).value = '';
                persistWebState();
                updateView();
              }
            }
          }}
        />
        ${s.filterQuery ? html`
          <button
            class="btn-icon clear-input-button"
            title="Clear Filter"
            aria-label="Clear Filter"
            @click=${() => {
              s.filterQuery = '';
              persistWebState();
              updateView();
            }}
          >${iconClear}</button>
        ` : ''}
      </div>

      ${showEmptyState ? html`
        <div class="empty-state">
          <div class="empty-instructions">
            <div>Press Enter to create task</div>
            <div>Use the ✓ button to mark tasks done</div>
            <div>Use the ⏱ button to punt tasks</div>
            <div>Use <span class="tag-recurrence">#every 1d</span> for recurring tasks</div>
          </div>
          ${hasFilterOrSearch ? html`
            <button
              class="empty-action"
              @click=${() => {
                s.filterQuery = '';
                s.activeFocusProfiles = [];
                persistWebState();
                updateView();
              }}
            >Clear Filter</button>
          ` : ''}
          ${hasActiveFocusProfiles ? html`
            <button
              class="empty-action"
              @click=${() => { clearFocusProfiles(); }}
            >Clear focus (${s.activeFocusProfiles.join(', ')})</button>
          ` : ''}
        </div>
      ` : html`
        <div>
          <div
            class="section-header"
            role="button"
            @click=${() => { s.activeCollapsed = !s.activeCollapsed; persistWebState(); updateView(); }}
          >
            ${s.activeCollapsed ? '▶' : '▼'} ACTIVE (${activeTasks.length})
          </div>
          ${!s.activeCollapsed ? html`
            <div class="task-list">
              ${activeTasks.map(taskCardView)}
            </div>
          ` : ''}
        </div>

        ${puntedTasks.length > 0 ? html`
          <div>
            <div
              class="section-header"
              role="button"
              @click=${() => { s.puntedCollapsed = !s.puntedCollapsed; persistWebState(); updateView(); }}
            >
              ${s.puntedCollapsed ? '▶' : '▼'} PUNTED (${puntedTasks.length})
            </div>
            ${!s.puntedCollapsed ? html`
              <div class="task-list">
                ${puntedTasks.map(taskCardView)}
              </div>
            ` : ''}
          </div>
        ` : ''}

        ${doneTasks.length > 0 ? html`
          <div class="task-list">
            ${doneTasks.map(taskCardView)}
          </div>
        ` : ''}
      `}

      <div class="footer-instructions">Actions are applied through the connected phone</div>
    </div>

    ${s.dialog === 'punt' ? puntDialogView(s) : ''}
    ${s.dialog === 'focus' ? focusDialogView(s) : ''}
    ${s.dialog === 'export' ? exportDialogView(s) : ''}
    ${s.dialog === 'import' ? importDialogView(s) : ''}

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
  loadPersistedWebState();
  initWebSocket();
  updateView();
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }
}
