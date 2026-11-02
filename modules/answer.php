<?php
// modules/answer.php
?>
<style>
.mod-answer{display:flex;flex-direction:column}
.detail{padding:8px 16px 16px}
.back-btn{display:inline-flex;align-items:center;gap:6px;background:transparent;border:none;color:var(--muted);font-size:13px;cursor:pointer;padding:6px 0;font-family:inherit;transition:color .15s ease;margin-bottom:6px;text-decoration:none}
.back-btn:hover{color:var(--text)}
.detail-q{background:var(--surface-2);border:1px solid var(--border);border-radius:16px;padding:16px}
.detail-q .q-text{font-size:16px;line-height:1.5;margin-bottom:10px;letter-spacing:-.005em}
.detail-q .q-meta{display:flex;gap:10px;font-size:12px;color:var(--muted);align-items:center;flex-wrap:wrap}
.detail-q .pill{background:var(--accent-soft);color:var(--accent);padding:2px 9px;border-radius:20px;font-weight:500;font-size:11px}
.detail-q .dot{opacity:.4}
</style>
<div class="mod-answer">
  <div class="detail">
    <a class="back-btn" href="/l"><?= htmlspecialchars(t('a.back')) ?></a>
    <div class="detail-q" id="detailQ"></div>
    <div class="hint" style="padding-top:24px">
      <div class="soft"><?= t('a.hidden') ?></div>
    </div>
    <div class="hint"><?= htmlspecialchars(t('a.private')) ?></div>
  </div>
</div>
<div class="composer">
  <div class="composer-row">
    <textarea id="composer-input" rows="1" placeholder="<?= htmlspecialchars(t('a.placeholder')) ?>"></textarea>
    <button class="send-btn" id="composer-send" disabled aria-label="<?= htmlspecialchars(t('a.send')) ?>">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
        <line x1="12" y1="19" x2="12" y2="5"/>
        <polyline points="5 12 12 5 19 12"/>
      </svg>
    </button>
  </div>
</div>
<script>
/* Детали вопроса по ?id=N + отправка ответа. */
(function(){
  const QUESTIONS = {
    1: { id:1, text:'Почему моя функция в JavaScript не возвращает значение?', topic:'programming', time:'10:15', views:15 },
    2: { id:2, text:'Как перестать прокрастинировать перед важными делами?',   topic:'psychology',  time:'10:12', views:28 },
    3: { id:3, text:'Стоит ли менять профессию в 30 лет?',                     topic:'psychology',  time:'09:58', views:42 },
    4: { id:4, text:'Как починить таймер в React, который срабатывает дважды?',topic:'programming', time:'09:42', views:19 },
    5: { id:5, text:'Что подарить коллеге на день рождения?',                  topic:'misc',        time:'09:30', views:11 },
  };
  const NF = <?= json_encode(t('a.not_found')) ?>;

  const id  = Number(new URLSearchParams(location.search).get('id')) || 1;
  const q   = QUESTIONS[id];
  const box = document.getElementById('detailQ');

  if (q) {
    box.innerHTML = `
      <div class="q-text">${escapeHtml(q.text)}</div>
      <div class="q-meta">
        <span class="pill">${escapeHtml(topicName(q.topic))}</span>
        <span>👁 ${q.views}</span>
        <span class="dot">·</span>
        <span>${q.time}</span>
      </div>`;
  } else {
    box.innerHTML = `<div class="q-text">${escapeHtml(NF)}</div>`;
  }

  initComposer(text => {
    console.log('Ответ на вопрос #' + id + ':', text);
    alert('Ответ отправлен: ' + text);
    location.href = '/l';
  });
})();
</script>
