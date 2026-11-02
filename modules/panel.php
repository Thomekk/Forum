<?php
// modules/panel.php
$active_role    = $active_role ?? 'ask';
$__langs        = LANGS;
$__current_lang = lang_init();
?>
<style>
.overlay{position:fixed;inset:0;background:rgba(0,0,0,.45);z-index:90;opacity:0;pointer-events:none;transition:opacity .25s ease}
.overlay.open{opacity:1;pointer-events:auto}
.sidebar{position:fixed;top:0;left:0;bottom:0;width:300px;max-width:84vw;background:var(--surface);border-right:1px solid var(--border);z-index:100;transform:translateX(-100%);transition:transform .25s cubic-bezier(.4,0,.2,1);display:flex;flex-direction:column;padding:14px;overflow-y:auto}
.sidebar.open{transform:translateX(0)}
.sidebar-header{display:flex;align-items:center;justify-content:space-between;padding:4px 4px 14px}
.sidebar-label{font-size:11px;text-transform:uppercase;letter-spacing:.7px;color:var(--muted);font-weight:600;margin:16px 6px 8px}
.sidebar-item{display:flex;align-items:center;gap:10px;width:100%;background:transparent;border:none;color:var(--text);padding:11px 12px;border-radius:10px;cursor:pointer;font-family:inherit;font-size:14px;text-align:left;transition:background .15s ease,color .15s ease;margin-bottom:2px;text-decoration:none}
.sidebar-item:hover{background:var(--surface-2)}
.sidebar-item.active{background:var(--accent-soft);color:var(--accent);font-weight:600}
.sidebar-item .ico{width:22px;text-align:center;font-size:15px}
.sidebar-footer{margin-top:auto;padding:14px 6px 4px;font-size:11px;color:var(--muted);border-top:1px solid var(--border)}
/* Переключатель языка */
.lang-switch{position:relative}
.lang-arrow{margin-left:auto;font-size:21px;color:var(--muted);transition:transform .2s ease}
.lang-switch.open .lang-arrow{transform:rotate(180deg)}
.lang-list{display:none;margin-top:4px;padding:4px;border:1px solid var(--border);border-radius:10px;background:var(--surface-2);}
.lang-switch.open .lang-list{display:block}
</style>
<aside class="sidebar" id="sidebar" aria-label="<?= htmlspecialchars(t('menu.title')) ?>">
  <div class="sidebar-header">
    <div class="logo"><span class="logo-dot"></span><span><?= htmlspecialchars(t('app.name')) ?></span></div>
    <button class="icon-btn" id="closeSidebar" title="<?= htmlspecialchars(t('menu.close')) ?>" aria-label="<?= htmlspecialchars(t('menu.close')) ?>">✕</button>
  </div>

  <div class="sidebar-label">Q&amp;A</div>
  <a class="sidebar-item<?= $active_role==='ask' ? ' active' : '' ?>" href="/q">
    <span class="ico">❓</span> <?= htmlspecialchars(t('menu.ask')) ?>
  </a>
  <a class="sidebar-item<?= $active_role==='answer' ? ' active' : '' ?>" href="/l">
    <span class="ico">❗</span> <?= htmlspecialchars(t('menu.answer')) ?>
  </a>
<!-- Это пока что не нужно отображать
  <div class="sidebar-label"><?= htmlspecialchars(t('menu.tasks')) ?></div>
  <button class="sidebar-item" type="button"><span class="ico">📃</span> <?= htmlspecialchars(t('menu.customer')) ?></button>
  <button class="sidebar-item" type="button"><span class="ico">💵</span> <?= htmlspecialchars(t('menu.executor')) ?></button> -->

<div class="sidebar-label"><?= htmlspecialchars(t('menu.language')) ?></div>
<div class="lang-switch" id="langSwitch">
  <button type="button" class="sidebar-item" id="langToggle"
          aria-haspopup="listbox" aria-expanded="false">
    <span class="ico">🌐</span>
    <span id="langCurrent">
      <?= htmlspecialchars($__langs[$__current_lang] ?? $__current_lang) ?>
    </span>
    <span class="lang-arrow" aria-hidden="true">▾</span>
  </button>

  <div class="lang-list" id="langList" role="listbox">
    <?php foreach ($__langs as $code => $label): ?>
      <button type="button"
              class="sidebar-item<?= $code === $__current_lang ? ' active' : '' ?>"
              data-lang="<?= htmlspecialchars($code) ?>"
              role="option"
              aria-selected="<?= $code === $__current_lang ? 'true' : 'false' ?>">
        <span class="ico">🌐</span> <?= htmlspecialchars($label) ?>
      </button>
    <?php endforeach; ?>
  </div>
</div>  

  <div class="sidebar-label"><?= htmlspecialchars(t('menu.appearance')) ?></div>
  <button class="sidebar-item" id="themeItem" type="button">
    <span class="ico" id="themeIcon">🌙</span>
    <span id="themeLabel"><?= htmlspecialchars(t('theme.dark')) ?></span>
  </button>

  <div class="sidebar-footer">
    <?= htmlspecialchars(t('footer.line1')) ?><br>
    <?= htmlspecialchars(t('footer.line2')) ?>
    <!--LiveInternet counter--><a href="https://www.liveinternet.ru/click" target="_blank" style="position: absolute; left: -9999px; top: -9999px;">
  <img id="licnt8087" width="31" height="31" style="border:0" title="LiveInternet" src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAEALAAAAAABAAEAAAIBTAA7" alt=""/>
</a>
<script>
(function(d,s){d.getElementById("licnt8087").src=
"https://counter.yadro.ru/hit?t44.1;r"+escape(d.referrer)+
((typeof(s)=="undefined")?"":";s"+s.width+"*"+s.height+"*"+
(s.colorDepth?s.colorDepth:s.pixelDepth))+";u"+escape(d.URL)+
";h"+escape(d.title.substring(0,150))+";"+Math.random()})
(document,screen)
</script><!--/LiveInternet-->
  </div>
</aside>
<div class="overlay" id="overlay"></div>

<script>
/* ============================================================
 * Панель: открытие/закрытие, смена языка, тема.
 * ============================================================ */
(function(){
  const sidebar    = document.getElementById('sidebar');
  const overlay    = document.getElementById('overlay');
  const menuBtn    = document.getElementById('menuBtn');
  const closeBtn   = document.getElementById('closeSidebar');
  const themeItem  = document.getElementById('themeItem');
  const themeIcon  = document.getElementById('themeIcon');
  const themeLabel = document.getElementById('themeLabel');

  const open  = () => { sidebar.classList.add('open');  overlay.classList.add('open');  };
  const close = () => { sidebar.classList.remove('open'); overlay.classList.remove('open'); };

  menuBtn.addEventListener('click', open);
  closeBtn.addEventListener('click', close);
  overlay.addEventListener('click', close);
  document.addEventListener('keydown', e => {
  if (e.key === 'Escape') {
    close();
    closeLang();
  }
});

/* Переключатель языка: показываем только выбранный, по клику — раскрываем список */
const langSwitch = document.getElementById('langSwitch');
const langToggle = document.getElementById('langToggle');
const langList   = document.getElementById('langList');

function closeLang(){ langSwitch.classList.remove('open'); langToggle.setAttribute('aria-expanded','false'); }

langToggle.addEventListener('click', (e) => {
  e.stopPropagation();
  const willOpen = !langSwitch.classList.contains('open');
  langSwitch.classList.toggle('open', willOpen);
  langToggle.setAttribute('aria-expanded', willOpen ? 'true' : 'false');
});

// клик мимо — закрываем
document.addEventListener('click', (e) => {
  if (!langSwitch.contains(e.target)) closeLang();
});

// выбор языка: пишем cookie и перезагружаем
langList.querySelectorAll('[data-lang]').forEach(btn => {
  btn.addEventListener('click', () => {
    const code = btn.dataset.lang;
    document.cookie = 'lang=' + encodeURIComponent(code) + '; path=/; max-age=31536000; samesite=Lax';
    closeLang();
    location.reload();
  });
});

  /* Тема */
  const L = {
    dark:  <?= json_encode(t('theme.dark')) ?>,
    light: <?= json_encode(t('theme.light')) ?>
  };
  function syncThemeUI(){
    const isDark = Theme.get() === 'dark';
    themeIcon.textContent  = isDark ? '☀️' : '🌙';
    themeLabel.textContent = isDark ? L.light : L.dark;
  }
  function toggleTheme(){ Theme.toggle(); syncThemeUI(); }

  themeItem.addEventListener('click', toggleTheme);
  document.addEventListener('theme:change', syncThemeUI);
  syncThemeUI();
})();
</script>