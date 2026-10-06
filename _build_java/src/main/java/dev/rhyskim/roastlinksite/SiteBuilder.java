package dev.rhyskim.roastlinksite;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The RoastLink site's static-site generator -- this is the one actually
 * used to build the live site (2026-09-08 onward). It replaced the original
 * Python generator ({@code ../_build/build.py}, kept in the repo as
 * historical/dead code, not deleted, not run anymore): reads
 * {@code ../_build/templates/base.html} and, per page, a content fragment
 * from {@code ../_build/content/<lang>/<slug>.html}, substitutes
 * placeholders, and writes plain static HTML directly into the repo root
 * ({@code ../<lang>/<slug>.html}) -- same target paths the Python version
 * used to write, same output. Content/templates were deliberately left in
 * place under {@code _build/} rather than duplicated here, so there's a
 * single source of truth regardless of which generator reads them.
 *
 * <p>Zero external dependencies on purpose (JDK only), matching the
 * original's "don't add a dependency you don't need" approach.
 *
 * <p>Usage (from anywhere): {@code mvn -f _build_java/pom.xml package &&
 * java -jar _build_java/target/roastlink-site-builder.jar}
 */
public final class SiteBuilder {

    private static final List<String> LANGUAGES = List.of("ko", "en", "zh");

    // Mirrors build.py's BASE_PATH: "" because the real site deploys at its
    // domain root. Kept here so every internal link still routes through one
    // constant if that ever changes.
    private static final String BASE_PATH = "";

    // Absolute origin, needed for anything that can't be a relative link:
    // sitemap.xml <loc>, canonical/hreflang <link> tags, and Open
    // Graph/Twitter card URLs (both are read by crawlers/scrapers with no
    // notion of "the current page" to resolve a relative URL against).
    private static final String SITE_URL = "https://roastlink.rhyskim.workers.dev";

    // "x-default" is the hreflang value crawlers fall back to for a
    // visitor whose locale matches none of the declared alternates --
    // English, as the most broadly understood of the three.
    private static final String DEFAULT_LANG = "en";

    // Same public Formspree endpoint the live site ships in its HTML source
    // (form IDs are meant to be client-visible -- this isn't a secret).
    private static final String FORMSPREE_ENDPOINT = "https://formspree.io/f/xjyvyglv";

    private record PageDef(String slug, Map<String, String> titles, Map<String, String> descriptions) {
    }

    // Descriptions are written for readers, not keyword-stuffed -- but
    // each one naturally includes the actual terms a searcher would type
    // (product name, "Sandbox Smart R1", "Artisan", "Bluetooth"/블루투스,
    // "bridge"/브릿지, "download"/다운로드) rather than a generic sentence,
    // since this is what Google actually reads for the search-result
    // snippet (a separate <meta name="keywords"> tag is not -- Google has
    // ignored it for ranking since 2009, so this repo doesn't add one).
    private static final List<PageDef> PAGES = List.of(
            new PageDef(
                    "index",
                    Map.of("ko", "Sandbox Smart R1 아티산(Artisan) 연동 브릿지", "en", "Sandbox Smart R1 to Artisan Bridge", "zh", "Sandbox Smart R1 与 Artisan 连接桥接程序"),
                    Map.of(
                            "ko", "RoastLink는 Sandbox Smart R1 커피 로스터기를 블루투스로 Artisan 로스팅 소프트웨어와 연결하는 무료 브릿지 프로그램입니다. 복잡한 설정 없이 원클릭으로 연결하세요.",
                            "en", "RoastLink connects your Sandbox Smart R1 coffee roaster to Artisan roasting software over Bluetooth -- a free, one-click bridge with no complicated setup.",
                            "zh", "RoastLink 通过蓝牙将 Sandbox Smart R1 咖啡烘焙机连接到 Artisan 烘焙软件——免费、一键连接，无需复杂设置。"
                    )
            ),
            new PageDef(
                    "download/index",
                    Map.of("ko", "Sandbox Smart R1 Artisan 브릿지 무료 다운로드", "en", "Free Download: Sandbox Smart R1 Artisan Bridge", "zh", "免费下载 Sandbox Smart R1 Artisan 桥接程序"),
                    Map.of(
                            "ko", "RoastLink Windows용 다운로드. Sandbox Smart R1과 Artisan을 연결하는 브릿지 프로그램을 무료로 받아보세요 -- 설치 없이 실행 파일 하나로 바로 사용 가능합니다.",
                            "en", "Download RoastLink for Windows -- a free bridge connecting the Sandbox Smart R1 roaster to Artisan. No installer, just a single executable.",
                            "zh", "下载 RoastLink Windows 版——免费连接 Sandbox Smart R1 烘焙机与 Artisan 的桥接程序，无需安装，单文件即可运行。"
                    )
            ),
            new PageDef(
                    "guide/index",
                    Map.of("ko", "Sandbox Smart R1 Artisan 연결 설정 가이드", "en", "Sandbox Smart R1 Artisan Setup Guide", "zh", "Sandbox Smart R1 连接 Artisan 设置指南"),
                    Map.of(
                            "ko", "Sandbox Smart R1 로스터기를 Artisan에 연결하는 설정 가이드. 블루투스 페어링부터 Artisan 웹소켓 설정까지 단계별로 안내합니다.",
                            "en", "Step-by-step setup guide for connecting the Sandbox Smart R1 roaster to Artisan via RoastLink, from Bluetooth pairing to Artisan's WebSocket configuration.",
                            "zh", "Sandbox Smart R1 烘焙机连接 Artisan 的设置指南，从蓝牙配对到 Artisan WebSocket 设置，逐步说明。"
                    )
            ),
            new PageDef(
                    "faq/index",
                    Map.of("ko", "Sandbox Smart R1 Artisan 연동 자주 묻는 질문 (FAQ)", "en", "Sandbox Smart R1 Artisan FAQ", "zh", "Sandbox Smart R1 Artisan 常见问题"),
                    Map.of(
                            "ko", "RoastLink와 Sandbox Smart R1, Artisan 연동에 대해 자주 묻는 질문과 답변을 모았습니다.",
                            "en", "Frequently asked questions about RoastLink, the Sandbox Smart R1 roaster, and connecting to Artisan roasting software.",
                            "zh", "关于 RoastLink、Sandbox Smart R1 烘焙机与 Artisan 连接的常见问题解答。"
                    )
            )
    );

    private static final Map<String, Map<String, String>> UI = buildUi();
    private static final Map<String, String> PLACEHOLDER_BODY = Map.of(
            "ko", "<p class=\"placeholder\">이 페이지는 아직 준비 중입니다.</p>",
            "en", "<p class=\"placeholder\">This page is still being written.</p>",
            "zh", "<p class=\"placeholder\">此页面正在编写中。</p>"
    );

    private final Path templatesDir;
    private final Path contentDir;
    private final Path repoRoot;

    private SiteBuilder(Path templatesDir, Path contentDir, Path repoRoot) {
        this.templatesDir = templatesDir;
        this.contentDir = contentDir;
        this.repoRoot = repoRoot;
    }

    public static void main(String[] args) throws IOException, URISyntaxException {
        // Resolve everything relative to where this code lives on disk
        // (this jar's/classes' own location under _build_java/target/),
        // not the process's working directory -- mirrors build.py's
        // Path(__file__).resolve().parent so this runs correctly no
        // matter which directory it's invoked from.
        Path codeLocation = Path.of(
                SiteBuilder.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path buildJavaDir = codeLocation.getParent().getParent(); // target/<jar|classes> -> _build_java/
        Path repoRoot = buildJavaDir.getParent();                 // _build_java/ -> repo root
        Path templatesDir = repoRoot.resolve("_build/templates");
        Path contentDir = repoRoot.resolve("_build/content");

        int written = new SiteBuilder(templatesDir, contentDir, repoRoot).build();
        System.out.printf("Built %d pages across %d languages.%n", written, LANGUAGES.size());
    }

    private int build() throws IOException {
        String baseTemplate = normalizeNewlines(
                Files.readString(templatesDir.resolve("base.html"), StandardCharsets.UTF_8));
        int written = 0;
        for (String lang : LANGUAGES) {
            Map<String, String> u = UI.get(lang);
            for (PageDef page : PAGES) {
                String body = new SafeTemplate(loadFragment(lang, page.slug()))
                        .safeSubstitute(Map.of("base_path", BASE_PATH));

                Map<String, String> values = new LinkedHashMap<>();
                values.put("lang", lang);
                values.put("title", page.titles().get(lang) + " — " + u.get("site_name"));
                values.put("site_name", u.get("site_name"));
                values.put("tagline", u.get("tagline"));
                values.put("nav", navHtml(lang, page.slug().equals("index") ? "index" : page.slug().split("/")[0]));
                values.put("lang_switch", langSwitchHtml(lang, page.slug()));
                values.put("content", body);
                values.put("footer", u.get("footer"));
                values.put("base_path", BASE_PATH);
                values.put("formspree_endpoint", FORMSPREE_ENDPOINT);
                values.put("description", page.descriptions().get(lang));
                values.put("canonical_url", canonicalUrl(lang, page.slug()));
                values.put("hreflang_links", hreflangLinksHtml(page.slug()));
                values.put("og_locale", ogLocale(lang));
                values.put("og_image", SITE_URL + "/assets/img/use.png");
                values.put("feedback_label", u.get("feedback_label"));
                values.put("feedback_placeholder", u.get("feedback_placeholder"));
                values.put("feedback_submit", u.get("feedback_submit"));
                values.put("feedback_thanks", u.get("feedback_thanks"));
                values.put("feedback_error", u.get("feedback_error"));

                String html = new SafeTemplate(baseTemplate).safeSubstitute(values);

                // Same target build.py used: the repo root itself, e.g.
                // <repoRoot>/ko/download/index.html -- assets/ is untouched
                // by the generator either way (hand-maintained, referenced
                // via $base_path, never templated).
                Path outPath = repoRoot.resolve(lang).resolve(page.slug() + ".html");
                Files.createDirectories(outPath.getParent());
                Files.writeString(outPath, html, StandardCharsets.UTF_8);
                written++;
            }
        }
        writeSitemap();
        writeRobotsTxt();
        return written;
    }

    /**
     * One &lt;url&gt; entry per (language x page) -- 12 total -- each
     * cross-linked to its translations via xhtml:link, same information
     * the per-page hreflang &lt;link&gt; tags carry, just in the format
     * search engines expect a sitemap to repeat it in.
     */
    private void writeSitemap() throws IOException {
        String today = java.time.LocalDate.now().toString();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\" ")
                .append("xmlns:xhtml=\"http://www.w3.org/1999/xhtml\">\n");
        for (String lang : LANGUAGES) {
            for (PageDef page : PAGES) {
                xml.append("  <url>\n");
                xml.append("    <loc>").append(canonicalUrl(lang, page.slug())).append("</loc>\n");
                xml.append("    <lastmod>").append(today).append("</lastmod>\n");
                for (String altLang : LANGUAGES) {
                    xml.append("    <xhtml:link rel=\"alternate\" hreflang=\"").append(altLang)
                            .append("\" href=\"").append(canonicalUrl(altLang, page.slug())).append("\"/>\n");
                }
                xml.append("    <xhtml:link rel=\"alternate\" hreflang=\"x-default\" href=\"")
                        .append(canonicalUrl(DEFAULT_LANG, page.slug())).append("\"/>\n");
                xml.append("  </url>\n");
            }
        }
        xml.append("</urlset>\n");
        Files.writeString(repoRoot.resolve("sitemap.xml"), xml.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Overrides Cloudflare's own auto-injected default robots.txt (a
     * generic "content signals" policy notice, not tailored to this
     * site) by simply providing our own -- any file actually present in
     * the deployed assets is served as-is instead. Points crawlers at
     * the sitemap so they don't have to discover pages purely by
     * following links.
     */
    private void writeRobotsTxt() throws IOException {
        String robots = "User-agent: *\nAllow: /\n\nSitemap: " + SITE_URL + "/sitemap.xml\n";
        Files.writeString(repoRoot.resolve("robots.txt"), robots, StandardCharsets.UTF_8);
    }

    private static String canonicalUrl(String lang, String slug) {
        return SITE_URL + "/" + lang + "/" + slugUrl(slug);
    }

    /**
     * One {@code <link rel="alternate" hreflang="...">} per language plus
     * one {@code x-default} -- tells a search engine these pages are
     * translations of each other rather than duplicate content, and
     * which one to show a visitor whose locale matches none of them.
     */
    private static String hreflangLinksHtml(String slug) {
        StringBuilder sb = new StringBuilder();
        for (String lang : LANGUAGES) {
            sb.append("<link rel=\"alternate\" hreflang=\"").append(lang).append("\" href=\"")
                    .append(canonicalUrl(lang, slug)).append("\">\n");
        }
        sb.append("<link rel=\"alternate\" hreflang=\"x-default\" href=\"")
                .append(canonicalUrl(DEFAULT_LANG, slug)).append("\">");
        return sb.toString();
    }

    private static String ogLocale(String lang) {
        return switch (lang) {
            case "ko" -> "ko_KR";
            case "zh" -> "zh_CN";
            default -> "en_US";
        };
    }

    /** "index" -> "", "download/index" -> "download/". */
    private static String slugUrl(String slug) {
        if (slug.equals("index")) {
            return "";
        }
        if (slug.endsWith("/index")) {
            return slug.substring(0, slug.length() - "/index".length()) + "/";
        }
        return slug + "/";
    }

    private static String navHtml(String lang, String activeSlug) {
        Map<String, String> u = UI.get(lang);
        record NavEntry(String key, String label) {
        }
        List<NavEntry> entries = List.of(
                new NavEntry("index", u.get("nav_home")),
                new NavEntry("download", u.get("nav_download")),
                new NavEntry("guide", u.get("nav_guide")),
                new NavEntry("faq", u.get("nav_faq"))
        );
        StringBuilder sb = new StringBuilder();
        for (NavEntry entry : entries) {
            boolean isActive = activeSlug.equals(entry.key()) || activeSlug.startsWith(entry.key() + "/");
            String cls = isActive ? " class=\"active\"" : "";
            String href = entry.key().equals("index")
                    ? BASE_PATH + "/" + lang + "/"
                    : BASE_PATH + "/" + lang + "/" + entry.key() + "/";
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("      <a href=\"").append(href).append('"').append(cls).append('>')
                    .append(entry.label()).append("</a>");
        }
        return sb.toString();
    }

    private static String langSwitchHtml(String lang, String slug) {
        String path = slugUrl(slug);
        StringBuilder sb = new StringBuilder();
        for (String candidate : LANGUAGES) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            String cls = candidate.equals(lang) ? " class=\"active\"" : "";
            sb.append("<a href=\"").append(BASE_PATH).append('/').append(candidate).append('/').append(path)
                    .append('"').append(cls).append('>').append(UI.get(candidate).get("lang_name")).append("</a>");
        }
        return sb.toString();
    }

    private String loadFragment(String lang, String slug) {
        Path path = contentDir.resolve(lang).resolve(slug + ".html");
        if (Files.isRegularFile(path)) {
            try {
                return normalizeNewlines(Files.readString(path, StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return PLACEHOLDER_BODY.get(lang);
    }

    /**
     * Source files under templates/ and content/ carry CRLF line endings
     * (an artifact of how they were copied on Windows); the generator's own
     * hardcoded strings (nav/lang-switch markup) use plain LF. Normalizing
     * on read keeps every output file consistently LF-only instead of a
     * mix of the two within one file.
     */
    private static String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    private static Map<String, Map<String, String>> buildUi() {
        Map<String, Map<String, String>> ui = new LinkedHashMap<>();

        Map<String, String> ko = new LinkedHashMap<>();
        ko.put("site_name", "RoastLink");
        ko.put("tagline", "Sandbox Smart R1 로스터기를 Artisan과 연결합니다");
        ko.put("nav_home", "홈");
        ko.put("nav_download", "다운로드");
        ko.put("nav_guide", "설정 가이드");
        ko.put("nav_faq", "FAQ");
        ko.put("footer", "© 2026 RoastLink. All rights reserved.");
        ko.put("lang_name", "한국어");
        ko.put("feedback_label", "개발자에게 말하고 싶습니다!");
        ko.put("feedback_placeholder",
                "수정해야 할 사항이나 있으면 좋은 기능 등, 뭐든 개발자에게 말하고 싶은 내용을 이곳에 적어주세요! 응원의 한 마디도 모두 읽겠습니다!");
        ko.put("feedback_submit", "보내기");
        ko.put("feedback_thanks", "감사합니다! 잘 전달됐습니다.");
        ko.put("feedback_error", "전송에 실패했습니다. 잠시 후 다시 시도해주세요.");
        ui.put("ko", ko);

        Map<String, String> en = new LinkedHashMap<>();
        en.put("site_name", "RoastLink");
        en.put("tagline", "Connects the Sandbox Smart R1 roaster to Artisan");
        en.put("nav_home", "Home");
        en.put("nav_download", "Download");
        en.put("nav_guide", "Setup Guide");
        en.put("nav_faq", "FAQ");
        en.put("footer", "© 2026 RoastLink. All rights reserved.");
        en.put("lang_name", "English");
        en.put("feedback_label", "Talk to the developer!");
        en.put("feedback_placeholder",
                "Anything you'd like the developer to hear -- bugs, feature ideas, or just a word of encouragement. It'll all be read!");
        en.put("feedback_submit", "Send");
        en.put("feedback_thanks", "Thanks! Your message was sent.");
        en.put("feedback_error", "Something went wrong. Please try again in a moment.");
        ui.put("en", en);

        Map<String, String> zh = new LinkedHashMap<>();
        zh.put("site_name", "RoastLink");
        zh.put("tagline", "将 Sandbox Smart R1 烘焙机连接到 Artisan");
        zh.put("nav_home", "首页");
        zh.put("nav_download", "下载");
        zh.put("nav_guide", "设置指南");
        zh.put("nav_faq", "常见问题");
        zh.put("footer", "© 2026 RoastLink. All rights reserved.");
        zh.put("lang_name", "中文");
        zh.put("feedback_label", "想对开发者说点什么！");
        zh.put("feedback_placeholder",
                "无论是需要修正的地方、希望增加的功能，还是一句鼓励的话，都请写在这里！我会认真阅读每一条留言！");
        zh.put("feedback_submit", "发送");
        zh.put("feedback_thanks", "谢谢！留言已成功送出。");
        zh.put("feedback_error", "发送失败，请稍后再试。");
        ui.put("zh", zh);

        return ui;
    }
}
