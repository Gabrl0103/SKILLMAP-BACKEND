// Recordatorios de escritorio: configuración guardada (desktop-settings.json) y cuándo toca avisar. No usa Electron:
// main.js lo conecta con la ventana, la bandeja y Notification.
//
// Reparto: aquí se decide CUÁNDO (día y hora, horas de silencio, máximo uno por día, sin repetir, y solo el mismo día si
// el equipo estaba apagado a la hora del aviso). El QUÉ (cuántas faltan, racha, meta cumplida, nombre del objetivo) lo
// calcula la app (js/recordatorios.js con la lógica de js/rachas.js) y llega como "snapshot".
//
// Tipos:
//   weekly  "Te faltan N para tu meta de esta semana": el día y la hora elegidos (por defecto sábado 6:00 pm, el día
//           antes del cierre de la semana).
//   streak  Racha en riesgo: el último día de la semana (domingo) a la misma hora, solo con racha >= 1 y la meta sin
//           cumplir. Si el aviso semanal también cae en domingo, gana este (dice lo mismo y algo más).
//   met     Meta cumplida (desactivado por defecto): una vez por semana, en cuanto se cumple y fuera del silencio.
// Ninguno sale si ya hubo otro ese día, y weekly/streak nunca con la meta cumplida.
//
// Semanas de lunes a domingo en la hora local del equipo, como js/plan-semanal.js.
'use strict';

const fs = require('node:fs');
const path = require('node:path');

const TYPES = ['weekly', 'streak', 'met'];
const CLOSING_DAY = 7; // domingo
const TIME_STEP = 30; // la hora del aviso va de media en media hora
const MAX_ACCOUNTS = 20;

const DEFAULT_REMINDERS = Object.freeze({
  enabled: true,
  day: 6, // 1 = lunes … 7 = domingo
  time: 18 * 60, // minutos desde las 00:00
  quietStart: 21, // horas de silencio: de 9:00 pm…
  quietEnd: 8, // …a 8:00 am
  types: Object.freeze({ weekly: true, streak: true, met: false }),
});
const DEFAULT_SYSTEM = Object.freeze({ background: false, openAtLogin: false });

// ---------- Fechas ----------

const pad = (n) => String(n).padStart(2, '0');
const dayKey = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const isoWeekday = (d) => ((d.getDay() + 6) % 7) + 1; // getDay: 0 = domingo
const weekKey = (d) => dayKey(new Date(d.getFullYear(), d.getMonth(), d.getDate() - (isoWeekday(d) - 1)));

function inQuiet(d, start, end) {
  if (start === end) return false; // sin horas de silencio
  const h = d.getHours();
  return start < end ? h >= start && h < end : h >= start || h < end;
}

// ---------- Validación de lo leído o recibido ----------

const isObject = (v) => Boolean(v) && typeof v === 'object' && !Array.isArray(v);
const intIn = (v, min, max, fallback) => (Number.isInteger(v) && v >= min && v <= max ? v : fallback);
const bool = (v, fallback) => (typeof v === 'boolean' ? v : fallback);
const isDayKey = (v) => typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v);
const isAccountId = (v) => typeof v === 'string' && /^\d{1,18}$/.test(v);

function cleanReminders(raw) {
  const r = isObject(raw) ? raw : {};
  const types = isObject(r.types) ? r.types : {};
  const time = intIn(r.time, 0, 24 * 60 - 1, DEFAULT_REMINDERS.time);
  return {
    enabled: bool(r.enabled, DEFAULT_REMINDERS.enabled),
    day: intIn(r.day, 1, 7, DEFAULT_REMINDERS.day),
    time: time - (time % TIME_STEP),
    quietStart: intIn(r.quietStart, 0, 23, DEFAULT_REMINDERS.quietStart),
    quietEnd: intIn(r.quietEnd, 0, 23, DEFAULT_REMINDERS.quietEnd),
    types: Object.fromEntries(TYPES.map((t) => [t, bool(types[t], DEFAULT_REMINDERS.types[t])])),
  };
}

function cleanSent(raw) {
  const s = isObject(raw) ? raw : {};
  const out = {};
  for (const key of ['lastDay', ...TYPES]) if (isDayKey(s[key])) out[key] = s[key];
  return out;
}

function cleanSystem(raw) {
  const s = isObject(raw) ? raw : {};
  return {
    background: bool(s.background, DEFAULT_SYSTEM.background),
    openAtLogin: bool(s.openAtLogin, DEFAULT_SYSTEM.openAtLogin),
  };
}

function cleanSettings(raw) {
  const data = isObject(raw) ? raw : {};
  const accounts = {};
  if (isObject(data.accounts)) {
    for (const [id, account] of Object.entries(data.accounts).slice(-MAX_ACCOUNTS)) {
      if (!isAccountId(id) || !isObject(account)) continue;
      accounts[id] = { reminders: cleanReminders(account.reminders), sent: cleanSent(account.sent) };
    }
  }
  return { version: 1, system: cleanSystem(data.system), accounts };
}

// Lo que pide la app desde Ajustes: solo los campos conocidos, cada uno validado; el resto queda como estaba
function patchReminders(current, patch) {
  if (!isObject(patch)) return current;
  const next = { ...current, types: { ...current.types } };
  if ('enabled' in patch) next.enabled = bool(patch.enabled, next.enabled);
  if ('day' in patch) next.day = intIn(patch.day, 1, 7, next.day);
  if ('time' in patch) {
    const time = intIn(patch.time, 0, 24 * 60 - 1, next.time);
    next.time = time - (time % TIME_STEP);
  }
  if ('quietStart' in patch) next.quietStart = intIn(patch.quietStart, 0, 23, next.quietStart);
  if ('quietEnd' in patch) next.quietEnd = intIn(patch.quietEnd, 0, 23, next.quietEnd);
  if (isObject(patch.types)) for (const t of TYPES) if (t in patch.types) next.types[t] = bool(patch.types[t], next.types[t]);
  return next;
}

// Lo que manda la app al pedirle el estado. null si no sirve (sin sesión, sin objetivo o datos raros)
function cleanSnapshot(raw) {
  if (!isObject(raw) || !isAccountId(String(raw.userId))) return null;
  const goal = intIn(raw.goal, 1, 100, null);
  const count = intIn(raw.count, 0, 10_000, null);
  const streak = intIn(raw.streak, 0, 10_000, null);
  if (goal === null || count === null || streak === null || !isObject(raw.texts)) return null;
  const text = (v, max) => (typeof v === 'string' ? v.replace(/[\u0000-\u001f]/g, ' ').trim().slice(0, max) : '');
  const texts = {};
  for (const t of TYPES) {
    const entry = isObject(raw.texts[t]) ? raw.texts[t] : {};
    texts[t] = { title: text(entry.title, 120), body: text(entry.body, 240) };
    if (!texts[t].title || !texts[t].body) return null;
  }
  return { userId: String(raw.userId), goal, count, streak, met: count >= goal, left: Math.max(0, goal - count), texts };
}

// ---------- Archivo ----------

function readSettings(file) {
  try {
    return cleanSettings(JSON.parse(fs.readFileSync(file, 'utf8')));
  } catch {
    return cleanSettings(null); // no existe o está roto: valores por defecto
  }
}

// Escritura atómica: un archivo temporal y rename (un corte a mitad no deja el JSON roto)
function writeSettings(file, settings) {
  const tmp = `${file}.tmp`;
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(tmp, JSON.stringify(settings, null, 2), 'utf8');
  fs.renameSync(tmp, file);
}

function accountOf(settings, id) {
  if (!isAccountId(id)) return null;
  if (!settings.accounts[id]) {
    const ids = Object.keys(settings.accounts);
    if (ids.length >= MAX_ACCOUNTS) delete settings.accounts[ids[0]];
    settings.accounts[id] = { reminders: cleanReminders(null), sent: {} };
  }
  return settings.accounts[id];
}

// ---------- Cuándo ----------

// Tipos que pueden salir ahora mismo según la hora y lo ya enviado, en orden de preferencia. Vacío = nada que hacer
// (y no hace falta pedirle nada a la app).
function dueTypes(now, reminders, sent) {
  if (!reminders.enabled || inQuiet(now, reminders.quietStart, reminders.quietEnd)) return [];
  if (sent.lastDay === dayKey(now)) return []; // máximo uno por día
  const week = weekKey(now);
  const weekday = isoWeekday(now);
  const atTime = now.getHours() * 60 + now.getMinutes() >= reminders.time;
  const out = [];
  if (reminders.types.streak && weekday === CLOSING_DAY && atTime && sent.streak !== week) out.push('streak');
  if (reminders.types.weekly && weekday === reminders.day && atTime && sent.weekly !== week) out.push('weekly');
  if (reminders.types.met && sent.met !== week) out.push('met');
  return out;
}

// El primero de los candidatos que tiene sentido con el estado real
function pickReminder(types, snap) {
  return types.find((type) => {
    if (type === 'met') return snap.met;
    if (snap.met || snap.left <= 0) return false; // nunca con la meta cumplida
    return type !== 'streak' || snap.streak >= 1;
  }) || null;
}

function markSent(sent, type, now) {
  return { ...sent, lastDay: dayKey(now), [type]: weekKey(now) };
}

module.exports = {
  TYPES, TIME_STEP, DEFAULT_REMINDERS, dayKey, weekKey, isoWeekday, inQuiet,
  cleanSettings, cleanReminders, cleanSystem, cleanSnapshot, patchReminders, isAccountId,
  readSettings, writeSettings, accountOf, dueTypes, pickReminder, markSent,
};
