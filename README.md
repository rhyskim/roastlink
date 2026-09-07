# RoastLink

Website and release distribution for **RoastLink** — a bridge that connects
the Sandbox Smart R1 roaster to Artisan roasting software.

This repository contains **only the static website and packaged releases**.
The application source code is maintained in a separate, private repository
and is not published here.

## Structure

- `ko/`, `en/`, `zh/` — the live site (plain static HTML, served as-is)
- `assets/` — shared CSS and images
- `_build/` — page content fragments and templates (`_build/content/`,
  `_build/templates/`) used to produce the pages above. Not served
  directly. `_build/build.py`, the original Python generator, is retired
  (kept for history, not run anymore).
- `_build_java/` — the generator actually in use. After editing anything
  under `_build/content/` or `_build/templates/`, rebuild and commit the
  regenerated output:
  ```
  mvn -f _build_java/pom.xml package
  java -jar _build_java/target/roastlink-site-builder.jar
  ```
  (requires a JDK 17+ and Maven on PATH)

## Releases

Downloadable builds are published under this repository's
[Releases](../../releases) page.
