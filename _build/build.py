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

# M-feedback-widget (2026-09-01, plan.md): real submissions don't work until
# this is replaced with an actual Formspree form endpoint (formspree.io ->
# sign up -> create a form -> paste its endpoint URL here). Until then the
# widget renders and the AJAX submit handler runs fine, Formspree just
# answers with a 404 for this placeholder ID, which the widget already
# shows as a plain error message (see feedback_error below) -- nothing
# breaks, it just doesn't save anything yet.
FORMSPREE_ENDPOINT = "https://formspree.io/f/__REPLACE_ME__"

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
        "feedback_label": "개발자에게 말하고 싶습니다!",
        "feedback_placeholder": (
            "수정해야 할 사항이나 있으면 좋은 기능 등, 뭐든 개발자에게 말하고 싶은 "
            "내용을 이곳에 적어주세요! 응원의 한 마디도 모두 읽겠습니다!"
        ),
        "feedback_submit": "보내기",
        "feedback_thanks": "감사합니다! 잘 전달됐습니다.",
        "feedback_error": "전송에 실패했습니다. 잠시 후 다시 시도해주세요.",
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
        "feedback_label": "Talk to the developer!",
        "feedback_placeholder": (
            "Anything you'd like the developer to hear -- bugs, feature ideas, or just "
            "a word of encouragement. It'll all be read!"
        ),
        "feedback_submit": "Send",
        "feedback_thanks": "Thanks! Your message was sent.",
        "feedback_error": "Something went wrong. Please try again in a moment.",
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
        "feedback_label": "想对开发者说点什么！",
        "feedback_placeholder": (
            "无论是需要修正的地方、希望增加的功能，还是一句鼓励的话，都请写在这里！"
            "我会认真阅读每一条留言！"
        ),
        "feedback_submit": "发送",
        "feedback_thanks": "谢谢！留言已成功送出。",
        "feedback_error": "发送失败，请稍后再试。",
    },
}

# (slug, {lang: page_title}) -- slug doubles as the content-fragment
# filename (content/<lang>/<slug>.html) and, via slug_url(), the URL path.
#
# M-guide-single-page (2026-09-01, plan.md): the setup guide used to be one
# page per step (guide/01-device/index ... guide/07-roasting/index) -- it's
# now a single "guide/index" page whose content fragment holds all seven
# steps as <section id="..."> blocks with a sticky, scroll-spied table of
# contents (see base.html/style.css). Don't re-add per-step PAGES entries;
# add new guide steps as another <section> inside content/<lang>/guide/index.html.
PAGES: list[tuple[str, dict[str, str]]] = [
    ("index", {"ko": "홈", "en": "Home", "zh": "首页"}),
    ("download/index", {"ko": "다운로드", "en": "Download", "zh": "下载"}),
    ("guide/index", {"ko": "설정 가이드", "en": "Setup Guide", "zh": "设置指南"}),
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
                formspree_endpoint=FORMSPREE_ENDPOINT,
                feedback_label=u["feedback_label"],
                feedback_placeholder=u["feedback_placeholder"],
                feedback_submit=u["feedback_submit"],
                feedback_thanks=u["feedback_thanks"],
                feedback_error=u["feedback_error"],
            )
            out_path = REPO_ROOT / lang / f"{slug}.html"
            out_path.parent.mkdir(parents=True, exist_ok=True)
            out_path.write_text(html, encoding="utf-8")
            written += 1
    print(f"Built {written} pages across {len(LANGUAGES)} languages.")


if __name__ == "__main__":
    build()
