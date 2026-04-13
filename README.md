# eido-site

Source for the [Eido](https://github.com/leifericf/eido) website at
[eido.leifericf.com](https://eido.leifericf.com).

The library lives in [leifericf/eido](https://github.com/leifericf/eido);
this repo is the gallery, guides, and reference documentation built
on top of a tagged Eido release.

## Stack

- Clojure on the JVM, no ClojureScript
- [Stasis](https://github.com/magnars/stasis) — static site assembly
- [Replicant](https://github.com/cjohansen/replicant) — hiccup → HTML
- [Garden](https://github.com/noprompt/garden) — CSS as Clojure data
- [Eido](https://github.com/leifericf/eido) — pinned to a release tag,
  used at build time to render gallery and documentation previews

## Build

```sh
clj -X:build
```

Renders all gallery and documentation preview scenes, generates HTML
pages, and writes everything to `_site/`. Deployed to GitHub Pages by
the workflow in `.github/workflows/`.

## Develop

```sh
clj -M:dev
```

Starts a REPL with the dev classpath. Iterate on pages and components
at the REPL; rebuild the site with `(eido-site.build/build-site!)`.
