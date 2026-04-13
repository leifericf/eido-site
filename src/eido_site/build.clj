(ns eido-site.build
  "Site build entry point. Stub: the real implementation is ported
  in subsequent commits.")

(defn build-site!
  "Builds the static site to the given :out-dir (default `_site`).
  Implementation pending — currently a no-op stub."
  [{:keys [out-dir] :or {out-dir "_site"}}]
  (println "build-site! stub: would write to" out-dir))
