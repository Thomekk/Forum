<?php
// modules/header.php
require_once __DIR__ . '/../functions/lang.php';
$lang = lang_init();

$active_role  = $active_role  ?? 'ask';
$is_logged_in = $is_logged_in ?? false; // STUB: позже — реальная сессия
$mode_key     = $active_role === 'ask' ? 'menu.ask' : 'menu.answer';
$page_title   = $page_title   ?? $mode_key;

$__topics = require __DIR__ . '/../config/topics.php';
?>
<!doctype html>
<html lang="<?= $lang ?>">
<head>
<meta charset="utf-8">
<?php if (!empty($noindex)): ?>
<meta name="robots" content="noindex,nofollow,noarchive,nosnippet,noimageindex">
<?php endif; ?>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
<title><?= htmlspecialchars(t($page_title)) ?> · <?= htmlspecialchars(t('app.name')) ?></title>
<script>
(function(){
  var t = null;
  try { t = localStorage.getItem('theme'); } catch(e){}
  if (t !== 'light' && t !== 'dark') {
    t = (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) ? 'dark' : 'light';
    try { localStorage.setItem('theme', t); } catch(e){}
  }
  document.documentElement.dataset.theme = t;
})();
</script>
<style>
:root{--bg:#f4f4f6;--surface:#fff;--surface-2:#f7f7f9;--border:#e6e6ea;--text:#111114;--muted:#71717a;--accent:#10b981;--accent-soft:rgba(16,185,129,.12);--shadow:0 1px 2px rgba(0,0,0,.04)}
@media (prefers-color-scheme: dark){
  :root:not([data-theme]){--bg:#0a0a0c;--surface:#141417;--surface-2:#1c1c21;--border:#26262c;--text:#f2f2f4;--muted:#8b8b93;--accent:#10b981;--accent-soft:rgba(16,185,129,.15);--shadow:0 1px 2px rgba(0,0,0,.3)}
}
:root[data-theme="dark"]{--bg:#0a0a0c;--surface:#141417;--surface-2:#1c1c21;--border:#26262c;--text:#f2f2f4;--muted:#8b8b93;--accent:#10b981;--accent-soft:rgba(16,185,129,.15);--shadow:0 1px 2px rgba(0,0,0,.3)}
*{box-sizing:border-box;margin:0;padding:0}
html,body{height:100%}
body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif;background:var(--bg);color:var(--text);font-size:15px;line-height:1.5;-webkit-font-smoothing:antialiased;transition:background .2s ease,color .2s ease;overflow-x:hidden}
.app{max-width:720px;margin:0 auto;height:100dvh;background:var(--surface);display:flex;flex-direction:column;border-left:1px solid var(--border);border-right:1px solid var(--border);position:relative}
@media(max-width:760px){.app{border:none}}
.header{display:flex;align-items:center;justify-content:space-between;padding:12px;border-bottom:1px solid var(--border);position:sticky;top:0;background:var(--surface);z-index:30}
.header-left,.header-right{display:flex;align-items:center;gap:4px}
.logo{display:flex;align-items:center;gap:8px;font-weight:600;font-size:15px;letter-spacing:-.01em}
.logo-dot{width:8px;height:8px;border-radius:50%;background:var(--accent);box-shadow:0 0 0 4px var(--accent-soft)}
.icon-btn{background:transparent;border:1px solid transparent;color:var(--text);width:40px;height:40px;border-radius:10px;cursor:pointer;font-size:16px;display:inline-flex;align-items:center;justify-content:center;transition:background .15s ease,transform .1s ease;text-decoration:none;font-family:inherit}
.icon-btn:hover{background:var(--surface-2)}
.icon-btn:active{transform:scale(.94)}
.login-btn{padding:8px 14px;border-radius:10px;background:var(--accent);color:#fff;text-decoration:none;font-size:13px;font-weight:500;transition:opacity .15s ease}
.login-btn:hover{opacity:.9}
.content{flex:1;display:flex;flex-direction:column;padding-bottom:120px;overflow-y:auto;overflow-x:hidden;overscroll-behavior:contain;-webkit-overflow-scrolling:touch}
.hint{text-align:center;font-size:12px;color:var(--muted);padding:20px 16px;line-height:1.6}
.hint .soft{background:var(--surface-2);border:1px dashed var(--border);border-radius:12px;padding:12px 14px;display:inline-block}
.empty{text-align:center;padding:60px 20px;color:var(--muted)}
.empty-title{font-size:15px;margin-bottom:6px;color:var(--text)}
.empty-sub{font-size:13px}
/* стало — сдвигаем вверх на --kb-offset (высота всплывшей клавиатуры) */
.composer{position:fixed;bottom:0;left:50%;transform:translateX(-50%) translateY(calc(-1 * var(--kb-offset, 0px)));width:100%;max-width:720px;padding:10px 16px 18px;background:var(--surface);border-top:1px solid var(--border);z-index:20;transition:transform .15s ease-out;will-change:transform}
@media(prefers-reduced-motion:reduce){.composer{transition:none}}
@media(max-width:760px){.composer{max-width:none}}
.composer-row{display:flex;gap:8px;align-items:flex-end}
.composer textarea{flex:1;resize:none;background:var(--surface-2);border:1px solid var(--border);color:var(--text);font-family:inherit;font-size:14px;padding:11px 14px;border-radius:14px;outline:none;min-height:44px;max-height:120px;line-height:1.4;transition:border-color .15s ease}
.composer textarea::placeholder{color:var(--muted)}
.composer textarea:focus{border-color:var(--accent)}
.send-btn{background:var(--accent);color:#fff;border:none;width:44px;height:44px;border-radius:14px;cursor:pointer;display:inline-flex;align-items:center;justify-content:center;flex-shrink:0;transition:transform .1s ease,opacity .15s ease}
.send-btn:active{transform:scale(.95)}
.send-btn:disabled{opacity:.35;cursor:default}
.send-btn svg{width:18px;height:18px}
</style>
</head>
<body>
<div class="app">
<header class="header">
  <div class="header-left">
    <button class="icon-btn" id="menuBtn" title="<?= htmlspecialchars(t('menu.title')) ?>" aria-label="<?= htmlspecialchars(t('menu.title')) ?>">☰</button>
    <div class="logo">
      <span class="logo-dot"></span>
      <span id="modeLabel"><?= htmlspecialchars(t($mode_key)) ?></span>
    </div>
  </div>
  <div class="header-right">
    <?php if (!$is_logged_in): ?>
      <a class="login-btn" href="/e"><?= htmlspecialchars(t('menu.login')) ?></a>
    <?php endif; ?>
  </div>
</header>

<script>
/* ============================================================
 * Общие утилиты и конфиг тем.
 * ============================================================ */
window.CURRENT_LANG  = <?= json_encode($lang) ?>;
window.TOPICS_CONFIG = <?= json_encode($__topics, JSON_UNESCAPED_UNICODE) ?>;

/** Экранирование HTML */
window.escapeHtml = s => String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));

/** Склонение */
window.plural = (n,one,few,many) => {
  const m10=n%10, m100=n%100;
  if (m10===1 && m100!==11) return one;
  if (m10>=2 && m10<=4 && (m100<10||m100>=20)) return few;
  return many;
};

/** Инициалы */
window.initials = s => String(s).replace('user_','').slice(0,2);

/** Перевод темы/подтемы для текущего языка */
window.tr = (obj) => {
  if (!obj) return {name:'', short:''};
  const x = obj.translations || {};
  return x[CURRENT_LANG] || x.ru || x.en || {name:'', short:''};
};

/** Активные темы, отсортированные по order, с переводом на текущий язык */
window.topicsLoad = function(){
  const all = TOPICS_CONFIG.all;
  const at  = tr(all);
  const items = [{slug:'all', name:at.name, short:at.short||at.name, icon:all.icon||'', order:all.order??0}];
  for (const [slug, t] of Object.entries(TOPICS_CONFIG.topics)) {
    if (!t.active) continue;
    const x = tr(t);
    items.push({slug, name:x.name, short:x.short||x.name, icon:t.icon||'', order:t.order??100});
  }
  return items.sort((a,b)=>a.order-b.order);
};

/** Имя темы для текущего языка */
window.topicName = function(slug){
  if (slug === 'all') return tr(TOPICS_CONFIG.all).name;
  const t = TOPICS_CONFIG.topics[slug];
  return t ? tr(t).name : '—';
};

/** Тема: чтение/запись localStorage + событие */
window.Theme = {
  get(){ return document.documentElement.dataset.theme || 'light'; },
  set(t){
    if (t !== 'light' && t !== 'dark') return;
    document.documentElement.dataset.theme = t;
    try { localStorage.setItem('theme', t); } catch(e){}
    document.dispatchEvent(new CustomEvent('theme:change', { detail: t }));
  },
  toggle(){ this.set(this.get() === 'light' ? 'dark' : 'light'); }
};

/** Композер. onSend(text) вызывается при отправке. */
window.initComposer = function(onSend){
  const ta  = document.getElementById('composer-input');
  const btn = document.getElementById('composer-send');
  if (!ta || !btn) return;
  ta.addEventListener('input', () => {
    ta.style.height = 'auto';
    ta.style.height = Math.min(ta.scrollHeight, 120) + 'px';
    btn.disabled = ta.value.trim().length === 0;
  });
  ta.addEventListener('keydown', e => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); btn.click(); }
  });
  /* Прилипание нижних fixed-элементов к верхней грани мобильной клавиатуры.
   Считаем разницу между layout viewport и visual viewport,
   пишем в CSS-переменную --kb-offset. На ПК offset = 0. */
(function(){
  if (!window.visualViewport) return;
  const vv = window.visualViewport, root = document.documentElement;
  let raf = 0;
  const apply = () => {
    raf = 0;
    const off = Math.max(0, window.innerHeight - vv.height - vv.offsetTop);
    root.style.setProperty('--kb-offset', off + 'px');
  };
  const onAny = () => { if (!raf) raf = requestAnimationFrame(apply); };
  vv.addEventListener('resize', onAny);
  vv.addEventListener('scroll', onAny);
  apply();
})();
  btn.addEventListener('click', () => {
    const text = ta.value.trim();
    if (!text) return;
    onSend(text);
    ta.value = ''; ta.style.height = 'auto'; btn.disabled = true;
    ta.dispatchEvent(new Event('input'));  // [+] чтобы панель тем скрылась
  });
};
</script>