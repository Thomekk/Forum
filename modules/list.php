<?php
// modules/list.php
$__ans = t('l.answers'); // массив [one, few, many] — нужен на клиенте
?>
<style>
.mod-list{display:flex;flex-direction:column}
.topics-wrap{
  position:sticky;top:0;z-index:25;
  background: rgba(20,20,23,.32);
  background: color-mix(in srgb, var(--surface) 75%, transparent);
  -webkit-backdrop-filter: blur(4px) saturate(160%);
  backdrop-filter:         blur(4px) saturate(160%);
  border-bottom:1px solid var(--border);
}
.topics-bar{
  display:flex;gap:8px;padding:10px 16px;
  overflow-x:auto;overflow-y:hidden;
  scroll-behavior:smooth;-webkit-overflow-scrolling:touch;
  scrollbar-width:none;
  background: transparent;   /* ← ГЛАВНОЕ: убрали непрозрачный фон */
  mask-image:linear-gradient(to right,transparent 0,#000 16px,#000 calc(100% - 16px),transparent 100%);
  -webkit-mask-image:linear-gradient(to right,transparent 0,#000 16px,#000 calc(100% - 16px),transparent 100%);
}lid var(--border);
}
.topics-bar::-webkit-scrollbar{display:none}
.topics-bar[hidden]{display:none}
.subtopics-bar{padding:8px 16px;gap:6px;border-top:1px solid var(--border)}
.topic{flex:0 0 auto;display:inline-flex;align-items:center;gap:6px;padding:8px 14px;border-radius:20px;border:1px solid var(--border);background:var(--surface-2);color:var(--text);font-size:13px;font-family:inherit;white-space:nowrap;cursor:pointer;user-select:none;transition:background .2s,border-color .2s,color .2s,transform .25s cubic-bezier(.4,0,.2,1)}
.topic:hover{border-color:var(--muted)}
.topic.active{background:var(--accent);border-color:var(--accent);color:#fff}
.topic .ico{font-size:14px;line-height:1}
.topic .short{display:none}
@media(max-width:480px){.topic .full{display:none}.topic .short{display:inline}}
@keyframes topic-pin{0%{transform:translateX(0) scale(1)}40%{transform:translateX(-6px) scale(.96)}100%{transform:translateX(0) scale(1)}}
.topic.pinning{animation:topic-pin .28s ease}
.subtopic{flex:0 0 auto;display:inline-flex;align-items:center;gap:4px;padding:5px 12px;border-radius:16px;border:1px solid var(--border);background:var(--surface);color:var(--muted);font-size:12px;font-family:inherit;white-space:nowrap;cursor:pointer;user-select:none;transition:background .15s,border-color .15s,color .15s,transform .25s cubic-bezier(.4,0,.2,1)}
.subtopic:hover{border-color:var(--muted);color:var(--text)}
.subtopic.active{background:var(--accent-soft);border-color:transparent;color:var(--accent);font-weight:500}
.subtopic.pinning{animation:topic-pin .28s ease}
@media(prefers-reduced-motion:reduce){.topics-bar{scroll-behavior:auto}.topic,.subtopic{transition:none}.topic.pinning,.subtopic.pinning{animation:none}}
/* Карточки вопросов: отдельные блоки, скруглённые, с маленькими отступами */
.q-items{padding:6px 8px}
.q-item{display:block;padding:12px 14px;background:var(--surface-2);border:1px solid var(--border);border-radius:14px;margin-bottom:6px;cursor:pointer;transition:border-color .15s ease,background .15s ease;color:inherit;text-decoration:none}
.q-item:last-child{margin-bottom:0}
.q-item:hover{border-color:var(--muted);background:var(--surface)}
.q-title{font-size:15px;line-height:1.45;margin-bottom:8px}
.q-meta{display:flex;gap:10px;font-size:12px;color:var(--muted);align-items:center;flex-wrap:wrap}
.q-meta .pill{background:var(--accent-soft);color:var(--accent);padding:2px 9px;border-radius:20px;font-weight:500;font-size:11px}
.q-meta .dot{opacity:.4}
</style>
<div class="mod-list">
  <div class="topics-wrap">
    <nav class="topics-bar" id="topicsBar" aria-label="Topics"></nav>
    <nav class="topics-bar subtopics-bar" id="subtopicsBar" hidden></nav>
  </div>
  <div class="q-items" id="qItems"></div>
  <div class="hint"><?= htmlspecialchars(t('l.hint')) ?></div>
</div>

<script>
/* ============================================================
 * Лента: темы + подтемы + список вопросов.
 * Время — relative (timeAgo). Блоки — отдельные карточки.
 * ============================================================ */
(function(){
  const KEY_TOPIC    = 'selected_topic';
  const KEY_SUBTOPIC = 'selected_subtopic';

  const topicsBar    = document.getElementById('topicsBar');
  const subtopicsBar = document.getElementById('subtopicsBar');
  const qItems       = document.getElementById('qItems');

  /* Разделители времени для текущего языка + массив форм слова "ответ" */
  const TS = {
    s: <?= json_encode(t('time.s')) ?>,
    m: <?= json_encode(t('time.m')) ?>,
    h: <?= json_encode(t('time.h')) ?>,
    d: <?= json_encode(t('time.d')) ?>
  };
  const ANS = <?= json_encode($__ans, JSON_UNESCAPED_UNICODE) ?>;
  const L_EMPTY_T = <?= json_encode(t('l.empty.title')) ?>;
  const L_EMPTY_S = <?= json_encode(t('l.empty.sub')) ?>;

  /** Относительное время: 1 с / 5 м / 3 ч / 2 д */
  function timeAgo(ts){
    const s = Math.max(0, Math.floor((Date.now() - ts) / 1000));
    if (s < 60)  return s + ' ' + TS.s;
    const m = Math.floor(s / 60);
    if (m < 60)  return m + ' ' + TS.m;
    const h = Math.floor(m / 60);
    if (h < 24)  return h + ' ' + TS.h;
    return Math.floor(h / 24) + ' ' + TS.d;
  }

  /* Мок-данные: ts — timestamp публикации */
  const now = Date.now();
  const QUESTIONS = [
    { id:1, text:'Почему функция в JavaScript не возвращает значение?',       topic:'programming', subtopic:'bugs',           ts: now - 30*1000,         views:15, answers:2 },
    { id:2, text:'Как правильно обработать ошибку в Python?',                 topic:'programming', subtopic:'bugs',           ts: now - 5*60*1000,       views:24, answers:3 },
    { id:3, text:'Как починить таймер в React, который срабатывает дважды?',  topic:'programming', subtopic:'review',         ts: now - 2*3600*1000,     views:19, answers:1 },
    { id:4, text:'Как ускорить медленный SQL-запрос?',                        topic:'programming', subtopic:'architecture',   ts: now - 25*3600*1000,    views:31, answers:4 },
    { id:5, text:'Как выбрать алгоритм сортировки для большого массива?',     topic:'programming', subtopic:'architecture',   ts: now - 3*86400*1000,    views:27, answers:2 },
    { id:6, text:'Как перестать прокрастинировать перед важными делами?',     topic:'psychology',  subtopic:'self-acceptance',ts: now - 12*60*1000,      views:28, answers:1 },
    { id:7, text:'Стоит ли менять профессию в 30 лет?',                       topic:'psychology',  subtopic:'triggers',       ts: now - 90*60*1000,      views:42, answers:0 },
    { id:8, text:'Что подарить коллеге на день рождения?',                    topic:'misc',                                   ts: now - 8*86400*1000,    views:11, answers:0 },
  ];

  let currentTopic = 'all';
  let currentSubtopic = null;

  try {
    const t = localStorage.getItem(KEY_TOPIC);
    if (t && topicsLoad().some(x => x.slug === t)) currentTopic = t;
    const s = localStorage.getItem(KEY_SUBTOPIC);
    if (s && currentTopic !== 'all' && subtopicsOf(currentTopic).some(x => x.slug === s)) currentSubtopic = s;
  } catch(e){}

  /** Активные подтемы темы с переводом на текущий язык */
  function subtopicsOf(slug){
    const t = TOPICS_CONFIG.topics[slug];
    if (!t || !t.subtopics) return [];
    return Object.entries(t.subtopics)
      .filter(([, s]) => s.active)
      .map(([sslug, s]) => {
        const x = tr(s);
        return { slug:sslug, name:x.name, short:x.short||x.name, order:s.order??100 };
      })
      .sort((a,b) => a.order - b.order);
  }

  /** Метка карточки: имя подтемы (если есть), иначе — имя темы */
  function tagLabel(q){
    if (q.subtopic) {
      const t = TOPICS_CONFIG.topics[q.topic];
      const s = t && t.subtopics ? t.subtopics[q.subtopic] : null;
      if (s) return tr(s).name;
    }
    return topicName(q.topic);
  }

  function renderTopics(){
    topicsBar.innerHTML = topicsLoad().map(t => `
      <button type="button" class="topic ${t.slug===currentTopic?'active':''}" data-topic="${t.slug}" aria-current="${t.slug===currentTopic?'true':'false'}">
        ${t.icon ? `<span class="ico" aria-hidden="true">${t.icon}</span>` : ''}
        <span class="full">${escapeHtml(t.name)}</span>
        <span class="short">${escapeHtml(t.short)}</span>
      </button>
    `).join('');
  }

  function renderSubtopics({ animate = false } = {}){
    const subs = currentTopic === 'all' ? [] : subtopicsOf(currentTopic);
    if (!subs.length) { subtopicsBar.hidden = true; subtopicsBar.innerHTML = ''; return; }
    subtopicsBar.hidden = false;
    subtopicsBar.innerHTML = subs.map(s => `
      <button type="button" class="subtopic ${s.slug===currentSubtopic?'active':''}" data-subtopic="${s.slug}" aria-current="${s.slug===currentSubtopic?'true':'false'}">
        ${escapeHtml(s.name)}
      </button>
    `).join('');

    if (currentSubtopic) {
      const active = subtopicsBar.querySelector('.subtopic.active');
      if (active && subtopicsBar.firstElementChild !== active) {
        if (animate) {
          active.classList.add('pinning');
          setTimeout(() => active.classList.remove('pinning'), 300);
        }
        subtopicsBar.insertBefore(active, subtopicsBar.firstElementChild);
        if (animate) subtopicsBar.scrollTo({ left: 0, behavior: 'smooth' });
      }
    }
  }

  function renderList(){
    let list = currentTopic === 'all' ? QUESTIONS : QUESTIONS.filter(q => q.topic === currentTopic);
    if (currentSubtopic) list = list.filter(q => q.subtopic === currentSubtopic);

    qItems.innerHTML = list.length ? list.map(q => `
      <a class="q-item" href="/a?id=${q.id}">
        <div class="q-title">${escapeHtml(q.text)}</div>
        <div class="q-meta">
          <span class="pill">${escapeHtml(tagLabel(q))}</span>
          <span>${q.answers} ${plural(q.answers, ANS[0], ANS[1], ANS[2])}</span>
          <span class="dot">·</span>
          <span>👁 ${q.views}</span>
          <span class="dot">·</span>
          <span>${timeAgo(q.ts)}</span>
        </div>
      </a>
    `).join('') : `
      <div class="empty"><div class="empty-title">${escapeHtml(L_EMPTY_T)}</div><div class="empty-sub">${escapeHtml(L_EMPTY_S)}</div></div>
    `;
  }

  function activateTopic(slug, { scroll = true, animate = true } = {}){
    const buttons = Array.from(topicsBar.querySelectorAll('.topic'));
    const btn = buttons.find(b => b.dataset.topic === slug);
    if (!btn) return;
    buttons.forEach(b => { b.classList.remove('active'); b.setAttribute('aria-current','false'); });
    btn.classList.add('active');
    btn.setAttribute('aria-current','true');
    if (topicsBar.firstElementChild !== btn) {
      if (animate) { btn.classList.add('pinning'); setTimeout(() => btn.classList.remove('pinning'), 300); }
      topicsBar.insertBefore(btn, topicsBar.firstElementChild);
    }
    if (scroll) topicsBar.scrollTo({ left: 0, behavior: 'smooth' });
  }

  topicsBar.addEventListener('click', e => {
    const btn = e.target.closest('.topic');
    if (!btn) return;
    const slug = btn.dataset.topic;
    if (slug === currentTopic) return;
    currentTopic = slug;
    currentSubtopic = null;
    try {
      localStorage.setItem(KEY_TOPIC, slug);
      localStorage.removeItem(KEY_SUBTOPIC);
    } catch(e){}
    activateTopic(slug);
    renderSubtopics();
    renderList();
  });

  subtopicsBar.addEventListener('click', e => {
    const btn = e.target.closest('.subtopic');
    if (!btn) return;
    const slug = btn.dataset.subtopic;
    currentSubtopic = (currentSubtopic === slug) ? null : slug;
    try {
      if (currentSubtopic) localStorage.setItem(KEY_SUBTOPIC, currentSubtopic);
      else localStorage.removeItem(KEY_SUBTOPIC);
    } catch(e){}
    renderSubtopics({ animate: true });
    renderList();
  });

  window.addEventListener('storage', e => {
    if (e.key === KEY_TOPIC && e.newValue) {
      if (e.newValue === currentTopic) return;
      if (!topicsLoad().some(t => t.slug === e.newValue)) return;
      currentTopic = e.newValue;
      currentSubtopic = null;
      activateTopic(e.newValue, { scroll: true, animate: false });
      renderSubtopics();
      renderList();
      return;
    }
    if (e.key === KEY_SUBTOPIC) {
      const v = e.newValue;
      if (v === currentSubtopic) return;
      currentSubtopic = v || null;
      renderSubtopics({ animate: false });
      renderList();
    }
  });

  renderTopics();
  if (currentTopic !== 'all') {
    const active = topicsBar.querySelector('.topic.active');
    if (active && topicsBar.firstElementChild !== active) {
      topicsBar.insertBefore(active, topicsBar.firstElementChild);
    }
  }
  renderSubtopics();
  renderList();
})();
</script>