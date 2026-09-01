# RoastLink

Website and release distribution for **RoastLink** — a bridge that connects
the Sandbox Smart R1 roaster to Artisan roasting software.

This repository contains **only the static website and packaged releases**.
The application source code is maintained in a separate, private repository
and is not published here.

## Structure

- `ko/`, `en/`, `zh/` — the live site (plain static HTML, served as-is by
  GitHub Pages, one folder per language)
- `assets/` — shared CSS and images
- `_build/` — the site generator (`build.py`) and page content fragments
  used to produce the pages above. Not served directly; re-run
  `python _build/build.py` after editing anything under `_build/content/`
  or `_build/templates/` and commit the regenerated output.

## Releases

Downloadable builds are published under this repository's
[Releases](../../releases) page.
