<?php
// modules/question.php
?>
<style>
/* Дополнительный нижний отступ, т.к. над композером может раскрыться панель тем */
.content{padding-bottom:200px}
.mod-ask{display:flex;flex-direction:column;flex:1}
.ask-empty{display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;padding:60px 24px;flex:1}
.ask-empty-icon{font-size:44px;margin-bottom:14px;filter:saturate(.9)}
.ask-empty h2{font-size:22px;font-weight:600;margin-bottom:8px;letter-spacing:-.02em}
.ask-empty p{font-size:14px;color:var(--muted);max-width:320px}

/* ---- Всплывающая панель тем над полем ввода ---- */
.composer-topics{border-bottom:1px solid var(--border);padding:8px 8px 6px;display:flex;flex-direction:column;gap:6px;animation:ct-in .18s ease-out}
@keyframes ct-in{from{opacity:0;transform:translateY(6px)}to{opacity:1;transform:translateY(0)}}
.composer-topics[hidden]{display:none}
.ct-wrap{display:flex;align-items:center;gap:4px}
.ct-wrap[hidden]{display:none}
.ct-scroll{display:flex;gap:6px;overflow-x:auto;overflow-y:hidden;scroll-behavior:smooth;flex:1;scrollbar-width:none;-webkit-overflow-scrolling:touch;
  mask-image:linear-gradient(to right,transparent 0,#000 10px,#000 calc(100% - 10px),transparent 100%);
  -webkit-mask-image:linear-gradient(to right,transparent 0,#000 10px,#000 calc(100% - 10px),transparent 100%)}
.ct-scroll::-webkit-scrollbar{display:none}
.ct-nav{background:transparent;border:none;color:var(--muted);font-size:20px;cursor:pointer;padding:0 6px;line-height:1;display:none;flex-shrink:0;font-family:inherit;user-select:none}
.ct-nav:hover{color:var(--text)}
@media(min-width:761px){.ct-nav{display:inline-block}}  /* стрелки только на ПК */
.ct-chip{flex:0 0 auto;display:inline-flex;align-items:center;gap:4px;padding:5px 10px;border-radius:14px;border:1px solid var(--border);background:var(--surface-2);color:var(--text);font-size:12px;font-family:inherit;white-space:nowrap;cursor:pointer;user-select:none;transition:background .15s,border-color .15s,color .15s,transform .12s}
.ct-chip:hover{border-color:var(--muted)}
.ct-chip:active{transform:scale(.96)}
.ct-chip.active{background:var(--accent);border-color:var(--accent);color:#fff}
.ct-chip.sub{padding:3px 9px;font-size:11px;border-radius:12px;background:var(--surface)}
.ct-chip.sub.active{background:var(--accent-soft);border-color:transparent;color:var(--accent);font-weight:500}
</style>

<div class="mod-ask">
  <div class="ask-empty">
    <div class="ask-empty-icon">💬</div>
    <h2><?= htmlspecialchars(t('q.welcome')) ?></h2>
    <p><?= htmlspecialchars(t('q.description')) ?></p>
  </div>
</div>

<div class="composer">
  <!-- [+] Панель тем/подтем (показывается при вводе ≥1 символа) -->
  <div class="composer-topics" id="ctPanel" hidden>
    <div class="ct-wrap">
      <button type="button" class="ct-nav" data-nav="topics" data-dir="-1" aria-label="‹">‹</button>
      <div class="ct-scroll" id="ctTopics" role="tablist"></div>
      <button type="button" class="ct-nav" data-nav="topics" data-dir="1"  aria-label="›">›</button>
    </div>
    <div class="ct-wrap" id="ctSubWrap" hidden>
      <button type="button" class="ct-nav" data-nav="subs" data-dir="-1" aria-label="‹">‹</button>
      <div class="ct-scroll" id="ctSubs" role="tablist"></div>
      <button type="button" class="ct-nav" data-nav="subs" data-dir="1"  aria-label="›">›</button>
    </div>
  </div>

  <div class="composer-row">
    <textarea id="composer-input" rows="1" maxlength="2000" placeholder="<?= htmlspecialchars(t('q.placeholder')) ?>"></textarea>
    <button class="send-btn" id="composer-send" disabled aria-label="<?= htmlspecialchars(t('q.send')) ?>">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
        <line x1="12" y1="19" x2="12" y2="5"/>
        <polyline points="5 12 12 5 19 12"/>
      </svg>
    </button>
  </div>
</div>

<script>
/* ============================================================
 * Панель тем/подтем над композером (модуль question).
 * Правила:
 *  - появляется при вводе ≥1 символа;
 *  - по умолчанию активна тема «Разное» (slug = 'all');
 *  - при выборе темы с подтемами — снизу отображается их ряд;
 *  - на ПК дополнительные кнопки ‹ › для скролла рядов.
 * ============================================================ */
(function(){
  const ta      = document.getElementById('composer-input');
  const panel   = document.getElementById('ctPanel');
  const topicsEl= document.getElementById('ctTopics');
  const subWrap = document.getElementById('ctSubWrap');
  const subsEl  = document.getElementById('ctSubs');
  if (!ta || !panel) return;

  let curTopic = 'all';
  let curSub   = null;

  /** Активные подтемы темы slug с переводом текущего языка */
  function subsOf(slug){
    const t = TOPICS_CONFIG.topics[slug];
    if (!t || !t.subtopics) return [];
    return Object.entries(t.subtopics)
      .filter(([, s]) => s.active)
      .map(([sslug, s]) => {
        const x = tr(s);
        return { slug: sslug, name: x.name, order: s.order ?? 100 };
      })
      .sort((a, b) => a.order - b.order);
  }

  /** Отрисовать ряд тем */
  function renderTopics(){
    topicsEl.innerHTML = topicsLoad().map(t => `
      <button type="button" class="ct-chip${t.slug === curTopic ? ' active' : ''}" data-slug="${t.slug}" role="tab" aria-selected="${t.slug === curTopic}">
        ${t.icon ? `<span aria-hidden="true">${t.icon}</span>` : ''}${escapeHtml(t.name)}
      </button>`).join('');
  }

  /** Отрисовать ряд подтем (или скрыть) */
  function renderSubs(){
    const subs = curTopic === 'all' ? [] : subsOf(curTopic);
    if (!subs.length){ subWrap.hidden = true; subsEl.innerHTML = ''; return; }
    subWrap.hidden = false;
    subsEl.innerHTML = subs.map(s => `
      <button type="button" class="ct-chip sub${s.slug === curSub ? ' active' : ''}" data-slug="${s.slug}" role="tab" aria-selected="${s.slug === curSub}">
        ${escapeHtml(s.name)}
      </button>`).join('');
  }

  /* Клик по теме: сбрасываем подтему, перерисовываем */
  topicsEl.addEventListener('click', e => {
    const b = e.target.closest('.ct-chip'); if (!b) return;
    curTopic = b.dataset.slug;
    curSub = null;
    renderTopics(); renderSubs();
  });
  /* Клик по подтеме: toggle (повторный клик снимает выбор) */
  subsEl.addEventListener('click', e => {
    const b = e.target.closest('.ct-chip'); if (!b) return;
    const s = b.dataset.slug;
    curSub = (curSub === s) ? null : s;
    renderSubs();
  });

  /* Кнопки ‹ › — скролл соответствующих рядов на 80% ширины (только ПК) */
  panel.addEventListener('click', e => {
    const n = e.target.closest('.ct-nav'); if (!n) return;
    const dir = Number(n.dataset.dir);
    const sc = n.dataset.nav === 'topics' ? topicsEl : subsEl;
    sc.scrollBy({ left: dir * sc.clientWidth * 0.8, behavior: 'smooth' });
  });

  /* Показ/скрытие панели по наличию непустого значения textarea */
  function syncPanel(){
    const has = ta.value.trim().length > 0;
    if (has && panel.hidden){
      panel.hidden = false;
      renderTopics(); renderSubs();
    } else if (!has && !panel.hidden){
      panel.hidden = true;
    }
  }
  ta.addEventListener('input', syncPanel);
  syncPanel(); // на случай если value восстановлено браузером

  /* Значения выбранных темы/подтемы отдаём наружу —
     чтобы обработчик отправки мог их использовать (без изменения initComposer) */
  window.getComposerTopic = () => ({ topic: curTopic, subtopic: curSub });
})();

/* Отправка вопроса: сюда придёт обработка POST → сервер/Redis.
   При необходимости используйте window.getComposerTopic(). */
initComposer(text => {
  const sel = (window.getComposerTopic && window.getComposerTopic()) || { topic: 'all', subtopic: null };
  console.log('Отправка:', { text, ...sel });
  alert('...' + text);
});
</script>