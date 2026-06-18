(ns eido-site.build
  "Site build orchestrator — wires scene rendering, page generation,
  redirect emission, and CNAME output into a single build-site! entry
  point. Generators live in their own focused namespaces:
  `eido-site.render` for page templates and most generators,
  `eido-site.api` for the reflection-driven API reference, and
  `eido-site.scenes` for example/scene rendering."
  (:require
    [clojure.java.io :as io]
    [eido-site.api :as api]
    [eido-site.content.workflows :as workflows]
    [eido-site.render :as render]
    [eido-site.scenes :as scenes]))

;; --- Site builder ---

(defn write-page! [out-dir path html]
  (let [file (io/file out-dir path)]
    (io/make-parents file)
    (spit file html)))

(defn redirect-html
  "HTML body that meta-refreshes a browser to `target` immediately,
  with a canonical link and a JS fallback. Used to keep old URLs
  working after the IA restructure moved pages under /reference/."
  [target]
  (str
    "<!DOCTYPE html>\n"
    "<html lang=\"en\">\n"
    "<head>\n"
    "<meta charset=\"utf-8\">\n"
    "<title>Redirecting…</title>\n"
    "<meta http-equiv=\"refresh\" content=\"0; url=" target "\">\n"
    "<link rel=\"canonical\" href=\"https://eido.leifericf.com" target "\">\n"
    "<script>location.replace(" (pr-str target) ");</script>\n"
    "</head>\n"
    "<body>\n"
    "<p>Redirecting to <a href=\"" target "\">" target "</a>…</p>\n"
    "</body>\n"
    "</html>\n"))

(def legacy-redirects
  "Old URL → new URL. Emitted as static HTML files at the old paths
  so external links and bookmarks don't 404 after the IA restructure."
  {"guide/index.html"        "/reference/manual/"
   "api/index.html"          "/reference/api/"
   "architecture/index.html" "/reference/design/"
   "limitations/index.html"  "/reference/scope/"})

(defn build-site!
  "Builds the complete eido website into the output directory.
  Run via: clj -X:build

  Scenes render through the native Phane backend (eido.core/render)."
  [& {:keys [out-dir] :or {out-dir "_site"}}]
  (println "Building eido site into" out-dir "...")

  ;; Render all example images
  (println "Rendering examples...")
  (scenes/render-all-examples! out-dir)

  ;; Render docs example previews
  (println "Rendering docs examples...")
  (scenes/render-docs-examples! out-dir)

  ;; Discover examples for gallery
  (let [examples-by-category (scenes/all-examples)]

    ;; Generate pages
    (println "Generating landing page...")
    (write-page! out-dir "index.html"
      (render/generate-landing-html examples-by-category))

    (println "Generating gallery...")
    (write-page! out-dir "gallery/index.html"
      (render/generate-gallery-html examples-by-category))

    (println "Generating reference index...")
    (write-page! out-dir "reference/index.html"
      (render/generate-reference-html))

    (println "Generating reference/manual...")
    (write-page! out-dir "reference/manual/index.html"
      (render/generate-docs-html))

    (println "Generating reference/api...")
    (write-page! out-dir "reference/api/index.html"
      (api/generate-api-html))

    (println "Generating reference/design...")
    (write-page! out-dir "reference/design/index.html"
      (render/generate-architecture-html))

    (println "Generating reference/scope...")
    (write-page! out-dir "reference/scope/index.html"
      (render/generate-limitations-html))

    (println "Generating workflows...")
    (write-page! out-dir "workflows/index.html"
      (render/generate-workflows-index-html))
    (doseq [{:keys [slug] :as page} workflows/workflow-pages]
      (println "  Generating workflow:" slug "...")
      (write-page! out-dir (str "workflows/" slug "/index.html")
        (render/generate-workflow-page-html page)))

    ;; Legacy redirects so external links to pre-restructure URLs
    ;; don't 404. See `legacy-redirects`.
    (println "Emitting legacy redirects...")
    (doseq [[old-path new-url] legacy-redirects]
      (write-page! out-dir old-path (redirect-html new-url)))

    ;; CNAME file for custom domain
    (spit (io/file out-dir "CNAME") "eido.leifericf.com")

    (println "Site built successfully!")
    (println (str "  " (count (mapcat :examples examples-by-category)) " examples rendered"))
    (println (str "  Open " out-dir "/index.html to preview"))))
