const START_DATE = '2026-11-19';
const LOCATION = { latitude: 32.9, longitude: 115.817, label: '阜阳' };
const $ = (selector) => document.querySelector(selector);

const weatherNames = {
  0: ['晴朗', '☀'], 1: ['大致晴朗', '🌤'], 2: ['局部多云', '⛅'], 3: ['阴天', '☁'],
  45: ['有雾', '〰'], 48: ['雾凇', '〰'], 51: ['毛毛雨', '🌦'], 53: ['毛毛雨', '🌦'], 55: ['较强毛毛雨', '🌧'],
  56: ['冻毛毛雨', '🌧'], 57: ['较强冻毛毛雨', '🌧'], 61: ['小雨', '🌧'], 63: ['中雨', '🌧'], 65: ['大雨', '🌧'],
  66: ['冻雨', '🌧'], 67: ['强冻雨', '🌧'], 71: ['小雪', '❄'], 73: ['中雪', '❄'], 75: ['大雪', '❄'], 77: ['雪粒', '❄'],
  80: ['阵雨', '🌦'], 81: ['较强阵雨', '🌧'], 82: ['强阵雨', '🌧'], 85: ['阵雪', '🌨'], 86: ['强阵雪', '🌨'],
  95: ['雷雨', '⛈'], 96: ['雷雨伴冰雹', '⛈'], 99: ['强雷雨伴冰雹', '⛈']
};

function todayShanghai() {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit'
  }).formatToParts(new Date());
  const get = (type) => parts.find((part) => part.type === type)?.value;
  return `${get('year')}-${get('month')}-${get('day')}`;
}

function prettyDate(isoDate, options = { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }) {
  return new Intl.DateTimeFormat('zh-CN', { ...options, timeZone: 'Asia/Shanghai' })
    .format(new Date(`${isoDate}T12:00:00+08:00`));
}

function dayNumber(date) {
  const [year, month, day] = date.split('-').map(Number);
  const [startYear, startMonth, startDay] = START_DATE.split('-').map(Number);
  return Math.floor((Date.UTC(year, month - 1, day) - Date.UTC(startYear, startMonth - 1, startDay)) / 86400000);
}

function setWeatherSummary(code, max, min) {
  const [name, symbol] = weatherNames[code] || ['天气情况', '☁'];
  $('#current-weather').textContent = name;
  $('#weather-symbol').textContent = symbol;
  if (Number.isFinite(max)) $('#max-temp').textContent = `${Math.round(max)}°`;
  if (Number.isFinite(min)) $('#min-temp').textContent = `${Math.round(min)}°`;
}

let currentWeatherLoaded = false;

async function loadCurrentWeather() {
  const query = new URLSearchParams({
    latitude: LOCATION.latitude, longitude: LOCATION.longitude,
    current: 'temperature_2m,apparent_temperature,weather_code',
    daily: 'weather_code,temperature_2m_max,temperature_2m_min',
    timezone: 'Asia/Shanghai', forecast_days: '1'
  });
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 8000);
  let response;
  try {
    response = await fetch(`https://api.open-meteo.com/v1/forecast?${query}`, { cache: 'no-store', signal: controller.signal });
    if (!response.ok) throw new Error('weather unavailable');
    const data = await response.json();
    const now = data.current;
    const today = data.daily;
    const [name] = weatherNames[now.weather_code] || ['天气情况'];
    currentWeatherLoaded = true;
    $('#current-temp').firstChild.textContent = String(Math.round(now.temperature_2m));
    $('#feels-like').textContent = `${Math.round(now.apparent_temperature)}°`;
    setWeatherSummary(now.weather_code, today.temperature_2m_max[0], today.temperature_2m_min[0]);
    $('#weather-status').textContent = '实时更新';
    const updateTime = new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date());
    $('#weather-foot').textContent = `${LOCATION.label} · ${name} · 更新于 ${updateTime}`;
  } catch {
    $('#weather-status').textContent = '暂时离线';
    $('#current-weather').textContent = '实时天气暂不可用';
    $('#weather-foot').textContent = '历史天气仍会在天气档案中保留';
  } finally {
    window.clearTimeout(timeout);
  }
}

function makeEntry(record) {
  const article = document.createElement('article');
  article.className = 'entry';
  if (record.photo) {
    const image = document.createElement('img');
    image.className = 'entry-photo';
    image.loading = 'lazy';
    image.src = record.photo;
    image.alt = record.source === 'ai-generated' ? 'AI 生成示意图，非实拍' : `${record.date} 的菜园照片`;
    image.addEventListener('error', () => {
      const placeholder = document.createElement('div');
      placeholder.className = 'entry-placeholder';
      placeholder.setAttribute('aria-label', '照片暂时无法显示');
      placeholder.textContent = '芽';
      image.replaceWith(placeholder);
    }, { once: true });
    article.append(image);
  } else {
    const placeholder = document.createElement('div');
    placeholder.className = 'entry-placeholder';
    placeholder.setAttribute('aria-label', '今天还没有照片');
    placeholder.textContent = '芽';
    article.append(placeholder);
  }

  const body = document.createElement('div');
  body.className = 'entry-body';
  const meta = document.createElement('div');
  meta.className = 'entry-meta';
  const date = document.createElement('time');
  date.dateTime = record.date;
  date.textContent = record.date;
  const badge = document.createElement('span');
  badge.className = 'entry-source';
  badge.textContent = record.source === 'ai-generated' ? 'AI 示意 · 非实拍' : '菜园实拍';
  meta.append(date, badge);

  const title = document.createElement('h3');
  title.textContent = `第 ${record.day} 天 · ${record.label || '日常观察'}`;
  const note = document.createElement('p');
  note.textContent = record.note || '今天也认真看了看它。';
  body.append(meta, title, note);
  article.append(body);
  return article;
}

async function loadJournal() {
  const container = $('#entries');
  try {
    const response = await fetch(`data/photos.json?v=${encodeURIComponent(todayShanghai())}`, { cache: 'no-store' });
    if (!response.ok) throw new Error('journal unavailable');
    const data = await response.json();
    const records = [...(Array.isArray(data.days) ? data.days : [])].sort((a, b) => b.date.localeCompare(a.date));
    container.replaceChildren();
    if (!records.length) {
      const empty = document.createElement('p');
      empty.className = 'empty-state';
      empty.textContent = '还没有生长记录。今天拍一张照片，开启这本小小的日记吧。';
      container.append(empty);
      return;
    }
    records.forEach((record) => container.append(makeEntry(record)));
  } catch {
    container.textContent = '生长记录暂时无法加载，请稍后再试。';
  }
}

function weatherText(code) {
  return (weatherNames[code] || ['天气情况'])[0];
}

function makeWeatherCard(record) {
  const card = document.createElement('article');
  card.className = 'weather-row';
  const date = document.createElement('time');
  date.dateTime = record.date;
  date.textContent = record.date;
  const title = document.createElement('strong');
  title.textContent = weatherText(record.code);
  const detail = document.createElement('p');
  const low = Number.isFinite(record.min) ? Math.round(record.min) : '—';
  const high = Number.isFinite(record.max) ? Math.round(record.max) : '—';
  const rain = Number.isFinite(record.rain) ? Number(record.rain).toFixed(1) : '—';
  detail.textContent = `${low}° / ${high}° · 降水 ${rain} mm`;
  card.append(date, title, detail);
  return card;
}

async function loadWeatherArchive(today) {
  const archive = $('#archive-list');
  try {
    const response = await fetch(`data/weather.json?v=${encodeURIComponent(today)}`, { cache: 'no-store' });
    if (!response.ok) throw new Error('archive unavailable');
    const data = await response.json();
    const records = [...(Array.isArray(data.days) ? data.days : [])].sort((a, b) => b.date.localeCompare(a.date));
    archive.replaceChildren();
    if (!records.length) {
      const empty = document.createElement('p');
      empty.className = 'empty-state';
      empty.textContent = '天气归档正在准备中，自动归档任务会在明天开始记录。';
      archive.append(empty);
      return;
    }
    records.forEach((record) => archive.append(makeWeatherCard(record)));
    const latest = records[0];
    if (!currentWeatherLoaded) {
      $('#weather-status').textContent = '历史记录可用';
      $('#weather-foot').textContent = `最近归档 ${latest.date} · Open-Meteo 历史天气`;
    }
  } catch {
    archive.textContent = '天气档案暂时无法加载。';
  }
}

const REMINDER_KEY = 'garden-journal-reminder-v1';
let reminderTimer;

function readReminder() {
  try { return JSON.parse(localStorage.getItem(REMINDER_KEY) || 'null'); } catch { return null; }
}

function setReminderStatus(text, state = '') {
  const element = $('#reminder-status');
  element.textContent = text;
  element.classList.toggle('is-success', state === 'success');
  element.classList.toggle('is-error', state === 'error');
}

function scheduleBrowserReminder(time) {
  window.clearTimeout(reminderTimer);
  const [hours, minutes] = time.split(':').map(Number);
  const now = new Date();
  const next = new Date(now);
  next.setHours(hours, minutes, 0, 0);
  if (next <= now) next.setDate(next.getDate() + 1);
  reminderTimer = window.setTimeout(async () => {
    const today = todayShanghai();
    const lastSent = localStorage.getItem(`${REMINDER_KEY}-sent`);
    if (lastSent !== today && 'Notification' in window && Notification.permission === 'granted') {
      new Notification('一畦日记 · 今天也看看它', {
        body: '给奶油白菜拍一张今天的照片，发到聊天里，留住它的生长变化。',
        tag: `garden-${today}`
      });
      localStorage.setItem(`${REMINDER_KEY}-sent`, today);
    }
    scheduleBrowserReminder(time);
  }, Math.min(next.getTime() - now.getTime() + 500, 2147483000));
}

async function enableReminder() {
  const time = $('#reminder-time').value;
  if (!time) {
    setReminderStatus('请先选择提醒时间。', 'error');
    return;
  }
  if (!('Notification' in window)) {
    setReminderStatus('这个浏览器不支持网页通知。可以用手机闹钟或日历设置每日提醒。', 'error');
    return;
  }
  try {
    const permission = Notification.permission === 'granted' ? 'granted' : await Notification.requestPermission();
    if (permission !== 'granted') {
      setReminderStatus('没有获得通知权限。你也可以用手机闹钟提醒自己。', 'error');
      return;
    }
    localStorage.setItem(REMINDER_KEY, JSON.stringify({ time }));
    scheduleBrowserReminder(time);
    setReminderStatus(`已设为每天 ${time} 提醒。请保持此网页在浏览器中打开；浏览器关闭或系统休眠时可能不会准时通知。`, 'success');
    $('#reminder-button').textContent = '更新提醒时间';
  } catch {
    setReminderStatus('无法保存提醒设置，请检查浏览器通知权限。', 'error');
  }
}

function initReminder() {
  const saved = readReminder();
  if (saved?.time) {
    $('#reminder-time').value = saved.time;
    if ('Notification' in window && Notification.permission === 'granted') {
      scheduleBrowserReminder(saved.time);
      setReminderStatus(`每日 ${saved.time} 提醒已开启。网页需保持打开，关闭网页或系统休眠时可能不会准时通知。`, 'success');
      $('#reminder-button').textContent = '更新提醒时间';
    }
  }
  $('#reminder-button').addEventListener('click', enableReminder);
}

async function init() {
  const today = todayShanghai();
  $('#hero-date').textContent = prettyDate(today).toUpperCase();
  $('#date').textContent = prettyDate(today);
  const day = dayNumber(today);
  $('#day').textContent = day < 0 ? '尚未开始' : `第 ${day} 天`;
  initReminder();
  await Promise.all([loadCurrentWeather(), loadJournal(), loadWeatherArchive(today)]);
}

init();
