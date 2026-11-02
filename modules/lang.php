<?php
$translations = [
    'ru' => [
        'welcome' => "Задайте ваш вопрос людям!",
        'description' => "Коротко, по делу. Обычно отвечают в течение 15 минут.",
        'hi' => "Без ответа вопрос удалится через 24 часа",
        'place' => "Задайте ваш вопрос…",
        'send' => "Отправить", 
        'sprosit' => "Спросить"
    ],
    'en' => [
        'welcome' => "Ask people your question!",
        'description' => "Brief and to the point. Responses usually arrive within 15 minutes.", 
        'hi' => "If unanswered, the question will be deleted in 24 hours",
        'place' => "Ask your question…",
        'send' => "Send", 
        'sprosit' => "Ask"
    ]
];

function getPreferredLang(array $translations, string $defaultLang = 'en'): string
{
    $acceptLang = $_SERVER['HTTP_ACCEPT_LANGUAGE'] ?? $defaultLang;
    $langCode = strtolower(substr($acceptLang, 0, 2));
    return isset($translations[$langCode]) ? $langCode : $defaultLang;
}

$lang = getPreferredLang($translations);
$text = $translations[$lang];
?>