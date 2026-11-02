<?php
// public_html/a.php
$page_title  = 'menu.answer';
$active_role = 'answer';
$is_logged_in = false;
require __DIR__ . '/../modules/header.php';
?>
<div class="content">
<?php require __DIR__ . '/../modules/answer.php'; ?>
</div>
<?php require __DIR__ . '/../modules/panel.php'; ?>
</div>
</body>
</html>