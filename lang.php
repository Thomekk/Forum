<?php
// functions/lang.php

const LANGS = ['en' => 'English', 'ru'  => 'Русский', 'kk' => 'Қазақша', 'uz' => 'Oʻzbekcha', 'de' => 'Deutsch', 'fr' => 'Français', 'es' => 'Español', 'pt' => 'Português', 'tr' => 'Türkçe', 'zh' => '简体中文', 'ja' => '日本語', 'ko' => '한국어', 'hi' => 'हिन्दी'];
const DEFAULT_LANG = 'en';

function lang_init(): string {
    static $lang = null;
    if ($lang !== null) return $lang;

    $c = $_COOKIE['lang'] ?? '';
    if ($c !== '' && isset(LANGS[$c])) return $lang = $c;

    $accept = $_SERVER['HTTP_ACCEPT_LANGUAGE'] ?? '';
    if ($accept && preg_match_all('/([a-z]{2})(?:-[A-Z]{2})?(?:;q=([0-9.]+))?/i', $accept, $m, PREG_SET_ORDER)) {
        $cands = [];
        foreach ($m as $x) {
            $code = strtolower($x[1]);
            if (!isset(LANGS[$code])) continue;
            $q = isset($x[2]) ? (float)$x[2] : 1.0;
            if (!isset($cands[$code]) || $q > $cands[$code]) $cands[$code] = $q;
        }
        if ($cands) {
            arsort($cands);
            return $lang = lang_set(array_key_first($cands));
        }
    }
    return $lang = lang_set(DEFAULT_LANG);
}
function lang_set(string $code): string {
    if (!isset(LANGS[$code])) $code = DEFAULT_LANG;
    if (($_COOKIE['lang'] ?? '') !== $code) {
        setcookie('lang', $code, [
            'expires'  => time() + 31536000,
            'path'     => '/',
            'samesite' => 'Lax',
        ]);
    }
    return $code;
}
function lang_load(string $page, string $lang): array {
    $dir = __DIR__ . '/../lang/';
    $out = [];
    $f1 = $dir . "_common/{$lang}.php";
    if (is_file($f1)) $out = require $f1;
    if ($page !== '') {
        $f2 = $dir . "{$page}/{$lang}.php";
        if (is_file($f2)) $out += require $f2;
    }
    return $out;
}
function lang_current_page(): string {
    static $p = null;
    if ($p !== null) return $p;
    $name = basename($_SERVER['SCRIPT_NAME'] ?? '', '.php');
    return $p = preg_match('/^[a-z]$/', $name) ? $name : 'q';
}
function t(string $key) {
    static $text = null;
    if ($text === null) $text = lang_load(lang_current_page(), lang_init());
    return $text[$key] ?? $key;
}