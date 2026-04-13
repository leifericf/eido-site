;; Tiny nREPL client used by the assistant for REPL-driven dev.
;; Run via: bb dev/repl_eval.clj '<clojure expression>'
;; Reads .nrepl-port from cwd, evaluates the expression, prints out/err/value.
;; Babashka script — not loaded into the JVM REPL itself.

(require '[bencode.core :as b]
         '[clojure.string :as str])
(import '[java.net Socket])

(defn- ->str [x]
  (cond
    (bytes? x)        (String. ^bytes x "UTF-8")
    (sequential? x)   (mapv ->str x)
    (map? x)          (into {} (for [[k v] x] [(->str k) (->str v)]))
    :else             x))

(let [expr (first *command-line-args*)
      port (-> ".nrepl-port" slurp str/trim Integer/parseInt)]
  (with-open [sock (Socket. "127.0.0.1" port)
              os   (.getOutputStream sock)
              is   (java.io.PushbackInputStream.
                     (.getInputStream sock))]
    (b/write-bencode os {"op" "eval" "code" expr})
    (loop []
      (let [reply (->str (b/read-bencode is))]
        (when-let [out (get reply "out")]   (print out))
        (when-let [err (get reply "err")]   (binding [*out* *err*] (print err)))
        (when-let [val (get reply "value")] (println "=>" val))
        (when-let [ex (get reply "ex")]     (binding [*out* *err*]
                                              (println "EX:" ex)))
        (flush)
        (when-not (some #{"done"} (get reply "status"))
          (recur))))))
