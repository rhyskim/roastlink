#!/usr/bin/env python3
"""Static site generator for the RoastLink site (skeleton phase).

Reads templates/base.html and, per page, an optional content fragment
from content/<lang>/<slug>.html; writes the final static pages into the
repo root (ko/, en/, zh/) that GitHub Pages serves directly as plain
files -- nothing runs on GitHub's side, this is only ever run locally by
whoever edits the site content.

Usage (from anywhere):
    python _build/build.py

Zero external dependencies on purpose (stdlib string.Template only) --
matches the sibling bridge project's "don't add a dependency you don't
need" habit, and means editing this site never requires a package install.
"""
from __future__ import annotations

from pathlib import Path
from string import Template

HERE = Path(__file__).resolve().parent
REPO_ROOT = HERE.parent
TEMPLATES = HERE / "templates"
CONTENT = HERE / "content"

LANGUAGES = ["ko", "en", "zh"]

# Shared layout chrome (nav labels, footer, tagline) per language. Page
# BODY content lives in content/<lang>/<slug>.html instead (or falls back
# to PLACEHOLDER_BODY below when that file doesn't exist yet) -- this
# dict is only ever the bits every page's header/footer needs.
UI = {
    "ko": {
        "site_name": "RoastLink",
        "tagline": "Sandbox Smart R1 로스터기를 Artisan과 연결합니다",
        "nav_home": "홈",
        "nav_download": "다운로드",
        "nav_guide": "설정 가이드",
        "nav_faq": "FAQ",
        "footer": "© 2026 RoastLink. All rights reserved.",
        "lang_name": "한국어",
    },
    "en": {
        "site_name": "RoastLink",
        "tagline": "Connects the Sandbox Smart R1 roaster to Artisan",
        "nav_home": "Home",
        "nav_download": "Download",
        "nav_guide": "Setup Guide",
        "nav_faq": "FAQ",
        "footer": "© 2026 RoastLink. All rights reserved.",
        "lang_name": "English",
    },
    "zh": {
        "site_name": "RoastLink",
        "tagline": "将 Sandbox Smart R1 烘焙机连接到 Artisan",
        "nav_home": "首页",
        "nav_download": "下载",
        "nav_guide": "设置指南",
        "nav_faq": "常见问题",
        "footer": "© 2026 RoastLink. All rights reserved.",
        "lang_name": "中文",
    },
}

# (slug, {lang: page_title}) -- slug doubles as the content-fragment
# filename (content/<lang>/<slug>.html) and, via slug_url(), the URL path.
# Representative pattern: every guide step follows "guide/NN-name/index"
# the same way 01-device does; add more the same way rather than a
# special case per step.
PAGES: list[tuple[str, dict[str, str]]] = [
    ("index", {"ko": "홈", "en": "Home", "zh": "首页"}),
    ("download/index", {"ko": "다운로드", "en": "Download", "zh": "下载"}),
    ("guide/index", {"ko": "설정 가이드", "en": "Setup Guide", "zh": "设置指南"}),
    ("guide/01-device/index", {"ko": "1단계 — 장치", "en": "Step 1 — Device", "zh": "第1步 — 设备"}),
    ("guide/02-port/index", {"ko": "2단계 — 포트", "en": "Step 2 — Port", "zh": "第2步 — 端口"}),
    ("guide/03-sampling/index", {"ko": "3단계 — 샘플링", "en": "Step 3 — Sampling", "zh": "第3步 — 采样"}),
    ("guide/04-events/index", {"ko": "4단계 — 이벤트 버튼", "en": "Step 4 — Event Buttons", "zh": "第4步 — 事件按钮"}),
    ("guide/05-sliders/index", {"ko": "5단계 — 슬라이더", "en": "Step 5 — Sliders", "zh": "第5步 — 滑块"}),
    ("guide/06-quantifiers/index", {"ko": "6단계 — 구간표시들 끄기", "en": "Step 6 — Disable Quantifiers", "zh": "第6步 — 关闭量化器"}),
    ("guide/07-roasting/index", {"ko": "로스팅 실전 절차", "en": "Roasting Walkthrough", "zh": "烘焙实操流程"}),
    ("faq/index", {"ko": "자주 묻는 질문", "en": "Frequently Asked Questions", "zh": "常见问题"}),
]

PLACEHOLDER_BODY = {
    "ko": '<p class="placeholder">이 페이지는 아직 준비 중입니다.</p>',
    "en": '<p class="placeholder">This page is still being written.</p>',
    "zh": '<p class="placeholder">此页面正在编写中。</p>',
}


def slug_url(slug: str) -> str:
    """"index" -> "", "download/index" -> "download/", "guide/01-device/index" -> "guide/01-device/"."""
    if slug == "index":
        return ""
    if slug.endswith("/index"):
        return slug.rsplit("/", 1)[0] + "/"
    return slug + "/"


def nav_html(lang: str, active_slug: str) -> str:
    u = UI[lang]
    entries = [
        ("index", u["nav_home"]),
        ("download", u["nav_download"]),
        ("guide", u["nav_guide"]),
        ("faq", u["nav_faq"]),
    ]
    links = []
    for key, label in entries:
        is_active = active_slug == key or active_slug.startswith(key + "/")
        cls = ' class="active"' if is_active else ""
        href = f"/{lang}/" if key == "index" else f"/{lang}/{key}/"
        links.append(f'      <a href="{href}"{cls}>{label}</a>')
    return "\n".join(links)


def lang_switch_html(lang: str, slug: str) -> str:
    path = slug_url(slug)
    parts = []
    for candidate in LANGUAGES:
        cls = ' class="active"' if candidate == lang else ""
        parts.append(f'<a href="/{candidate}/{path}"{cls}>{UI[candidate]["lang_name"]}</a>')
    return " · ".join(parts)


def load_fragment(lang: str, slug: str) -> str:
    path = CONTENT / lang / f"{slug}.html"
    if path.is_file():
        return path.read_text(encoding="utf-8")
    return PLACEHOLDER_BODY[lang]


def build() -> None:
    base = Template((TEMPLATES / "base.html").read_text(encoding="utf-8"))
    written = 0
    for lang in LANGUAGES:
        u = UI[lang]
        for slug, titles in PAGES:
            body = load_fragment(lang, slug)
            html = base.safe_substitute(
                lang=lang,
                title=f"{titles[lang]} — {u['site_name']}",
                site_name=u["site_name"],
                tagline=u["tagline"],
                nav=nav_html(lang, slug.split("/")[0] if slug != "index" else "index"),
                lang_switch=lang_switch_html(lang, slug),
                content=body,
                footer=u["footer"],
            )
            out_path = REPO_ROOT / lang / f"{slug}.html"
            out_path.parent.mkdir(parents=True, exist_ok=True)
            out_path.write_text(html, encoding="utf-8")
            written += 1
    print(f"Built {written} pages across {len(LANGUAGES)} languages.")


if __name__ == "__main__":
    build()
