<?php
// public_html/q.php
$page_title  = 'menu.ask';
$active_role = 'ask';
$is_logged_in = false;
$noindex = true;
require __DIR__ . '/../modules/header.php';
?>
<div class="content">
<?php require __DIR__ . '/../modules/question.php'; ?>
</div>
<?php require __DIR__ . '/../modules/panel.php'; ?>
</div>
</body>
</html>