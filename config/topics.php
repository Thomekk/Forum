<?php
// config/topics.php
// Ключ = slug (стабильный ID). Все тексты — в 'translations' по кодам языков.

return [
    'all' => [
        'slug' => 'all',
        'icon' => '•',
        'order' => 0,
        'is_system' => true,
        'translations' => [
            'ru' => ['name' => 'Все',  'short' => 'Все'],
            'en' => ['name' => 'All',  'short' => 'All'],
        ],
    ],

    'topics' => [

        // ---------- Программирование ----------
        'programming' => [
            'icon' => '>_',
            'color' => '#10b981',
            'order' => 10,
            'active' => true,
            'translations' => [
                'ru' => ['name' => 'Программирование', 'short' => 'Код'],
                'en' => ['name' => 'Programming',      'short' => 'Code'],
            ],
            'subtopics' => [
                'bugs' => [
                    'icon' => '🐞', 'order' => 10, 'active' => true,
                    'translations' => [
                        'ru' => ['name' => 'Поиск ошибок', 'short' => 'Ошибки'],
                        'en' => ['name' => 'Bug fixing',   'short' => 'Bugs'],
                    ],
                ],
                'review' => [
                    'icon' => '👀', 'order' => 20, 'active' => true,
                    'translations' => [
                        'ru' => ['name' => 'Код-ревью',   'short' => 'Ревью'],
                        'en' => ['name' => 'Code review', 'short' => 'Review'],
                    ],
                ],
                'architecture' => [
                    'icon' => '🏗️', 'order' => 30, 'active' => true,
                    'translations' => [
                        'ru' => ['name' => 'Архитектура', 'short' => 'Архитектура'],
                        'en' => ['name' => 'Architecture','short' => 'Architecture'],
                    ],
                ],
                'ithumor' => [
                    'icon' => ':-)', 'order' => 40, 'active' => true,
                    'translations' => [
                        'ru' => ['name' => 'IT-Юмор', 'short' => 'IT-Юмор'],
                        'en' => ['name' => 'IT-Humor','short' => 'IT-Humor'],
                    ],
                ],
                'itinside' => [
                    'icon' => 'O_o', 'order' => 50, 'active' => true,
                    'translations' => [
                        'ru' => ['name' => 'IT-Инсайд', 'short' => 'IT-Инсайд'],
                        'en' => ['name' => 'IT-Inside','short' => 'IT-Inside'],
                    ],
                ],
                // Другие подтемы
            ],
        ],

        // ---------- Психология ----------
        'psychology' => [
            'icon' => '🧠',
            'order' => 20,
            'active' => true,
            'translations' => [
                'ru' => ['name' => 'Психология', 'short' => 'Психология'],
                'en' => ['name' => 'Psychology', 'short' => 'Psychology'],
            ],
            'subtopics' => [
                'self-acceptance' => [
                    'icon' => '🌱', 'order' => 10, 'active' => true,
                    'translations' => [
                        'ru' => ['name' => 'Принятие себя', 'short' => 'Принятие'],
                        'en' => ['name' => 'Self-acceptance','short' => 'Acceptance'],
                    ],
                ],
                'triggers' => [
                    'icon' => '⚡', 'order' => 20, 'active' => true,
                    'translations' => [
                        'ru' => ['name' => 'Триггеры',  'short' => 'Триггеры'],
                        'en' => ['name' => 'Triggers',  'short' => 'Triggers'],
                    ],
                ],
                'relationships' => [
                    'icon' => '💞', 'order' => 30, 'active' => true,
                    'translations' => [
                        'ru' => ['name' => 'Отношения',    'short' => 'Отношения'],
                        'en' => ['name' => 'Relationships','short' => 'Relationships'],
                    ],
                ],
            ],
        ],

        // ---------- Работа с текстом ----------
        'text' => [
            'icon' => '📝',
            'order' => 30,
            'active' => true,
            'translations' => [
                'ru' => ['name' => 'Работа с текстом', 'short' => 'Текст'],
                'en' => ['name' => 'Text work',        'short' => 'Text'],
            ],
            'subtopics' => [
                'editing' => [
                    'icon' => '✏️', 'order' => 10, 'active' => true,
                    'translations' => [
                        'ru' => ['name' => 'Редактирование', 'short' => 'Редактура'],
                        'en' => ['name' => 'Editing',        'short' => 'Editing'],
                    ],
                ],
                'translation' => [
                    'icon' => '🌐', 'order' => 20, 'active' => true,
                    'translations' => [
                        'ru' => ['name' => 'Перевод',      'short' => 'Перевод'],
                        'en' => ['name' => 'Translation',  'short' => 'Translation'],
                    ],
                ],
                'copywriting' => [
                    'icon' => '📣', 'order' => 30, 'active' => true,
                    'translations' => [
                        'ru' => ['name' => 'Копирайтинг',  'short' => 'Копирайт'],
                        'en' => ['name' => 'Copywriting',  'short' => 'Copy'],
                    ],
                ],
            ],
        ],

        // ---------- Разное (без подтем) ----------
        'misc' => [
            'slug' => 'other',
            'icon' => '💬',
            'order' => 999,
            'active' => true,
            'translations' => [
                'ru' => ['name' => 'Разное', 'short' => 'Разное'],
                'en' => ['name' => 'Other',  'short' => 'Other'],
            ],
        ],
    ],
];
