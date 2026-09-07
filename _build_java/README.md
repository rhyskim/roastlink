# RoastLink Site Builder (Java)

The static-site generator actually used to build [RoastLink](https://roastlink.rhyskim.workers.dev)
(2026-09-08 onward). Replaced the original Python generator
(`../_build/build.py`, kept in the repo as historical/dead code) with a
from-scratch Java port — verified to produce identical output before the
switch.

Reads `../_build/templates/base.html` and, per page, a content fragment
from `../_build/content/<lang>/<slug>.html` (the same source files the
Python version used — not duplicated here, so there's one source of
truth), substitutes placeholders, and writes plain static HTML directly
into the repo root (`../<lang>/<slug>.html`), same as the Python version
did. `assets/` is untouched by the generator either way (hand-maintained,
referenced via `$base_path`, never templated).

Zero external dependencies on purpose (JDK only), matching the original's
"don't add a dependency you don't need" approach.

## Build & run

Requires JDK 17+ and Maven.

```
mvn -f _build_java/pom.xml package
java -jar _build_java/target/roastlink-site-builder.jar
```

Regenerates `ko/`, `en/`, `zh/` in the repo root in place. Review the diff,
then commit and push as usual.

## Design notes

- `SafeTemplate` reimplements Python's `string.Template.safe_substitute`:
  `$name` / `${name}` substitution where an unknown placeholder is left as
  literal text instead of throwing — content fragments are substituted
  once for `$base_path` and then embedded, verbatim, into a second
  substitution pass over the outer page template, so any placeholder not
  meant to resolve yet must survive untouched.
- `SiteBuilder` resolves `_build/templates` and `_build/content` (and the
  repo root it writes into) relative to its own code location on disk —
  mirrors Python's `Path(__file__).resolve().parent` — so it runs
  correctly regardless of the working directory it's invoked from.
- `SiteBuilder` mirrors `_build/build.py`'s page list, per-language UI
  strings, and nav/lang-switch markup generation exactly.
