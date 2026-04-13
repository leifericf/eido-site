(ns eido-site.content.landing
  "Static content for the landing page — hero images, feature
  bullets, the friendly Quick Start walkthrough, and the install
  walkthrough that pulls the current Eido version from the JAR."
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]))

(def ^:private eido-version
  (-> (io/resource "eido/version.edn") slurp edn/read-string))

(defn hero-images
  "Filenames of hero images to display on the landing page."
  []
  ["galaxy.gif"
   "art-ink-landscape.png"
   "3d-teapot-spin.gif"
   "spiral-grid.gif"
   "art-stained-glass.png"
   "mixed-neon-orbit.gif"])

(defn features
  "Feature bullet points for the landing page."
  []
  [{:title "Images are values"
    :desc  "A scene is a plain data structure — printable, serializable, diffable. Nothing opaque."}
   {:title "One function"
    :desc  "render takes a scene (or a sequence of scenes) and produces output. That's the entire API."}
   {:title "Description, not instruction"
    :desc  "You declare what the image contains — Eido decides how to draw it."}
   {:title "Animations are sequences"
    :desc  "60 frames = 60 scenes in a list. No timeline, no keyframes, no mutable state."}
   {:title "3D sculpting pipeline"
    :desc  "Composable mesh operations — deform, extrude, subdivide, mirror — all pure data, chainable via ->."}
   {:title "2D↔3D bridge"
    :desc  "Same fields, palettes, and programs work across both domains. UV-mapped procedural textures on 3D meshes."}
   {:title "Particle simulation"
    :desc  "Physics-based effects — fire, snow, sparks — configured as data with deterministic results."}
   {:title "Typography as paths"
    :desc  "Text converted to vector paths — compatible with gradients, transforms, and 3D extrusion."}
   {:title "Bring your own workflow"
    :desc  "Every function takes data and returns data. No framework, no state management."}
   {:title "Zero dependencies"
    :desc  "Just the language and the standard library. Nothing to install, nothing to break."}])

(defn quick-start-content
  "Friendly Quick Start walkthrough for the landing page."
  []
  [:div
   [:p "In Eido, an image is just a description — a plain data structure that says what the image contains. Here's a red circle on a light background:"]
   [:pre [:code
          "{:image/size [400 400]                       ;; 400x400 pixels
 :image/background [:color/name \"linen\"]      ;; warm off-white
 :image/nodes
 [{:node/type     :shape/circle
   :circle/center [200 200]                  ;; center of the canvas
   :circle/radius 120
   :style/fill    [:color/name \"crimson\"]}]}  ;; red fill"]]
   [:p "That's it — no drawing commands, no canvas API, no mutable state. You describe "
    [:em "what you see"] ", and Eido renders it. To produce an image file:"]
   [:pre [:code
          "(eido/render scene {:output \"circle.png\"})"]]
   [:p "Want animation? Return a different scene for each frame. Here's a circle that grows and shifts color over 60 frames:"]
   [:pre [:code
          "(def frames
  (anim/frames 60
    (fn [t]                             ;; t goes from 0.0 to 1.0
      {:image/size [400 400]
       :image/background [:color/rgb 30 30 40]
       :image/nodes
       [{:node/type     :shape/circle
         :circle/center [200 200]
         :circle/radius (* 150 t)       ;; grows over time
         :style/fill [:color/hsl        ;; hue shifts through
                      (* 360 t)         ;;   the rainbow
                      0.8 0.5]}]})))

(eido/render frames {:output \"grow.gif\" :fps 30})"]]
   [:p "Convenience helpers let you write the same thing more concisely:"]
   [:pre [:code
          ";; Shorthand — same circle, fewer keystrokes
(require '[eido.scene :as scene])
(require '[eido.color :as color])

(scene/circle-node [200 200] 120 (color/hsl 0 0.8 0.5))"]]
   [:p "Low-level control when you need it, high-level convenience when you don't. Every example in the "
    [:a {:href "./gallery/"} "gallery"]
    " works this way — pure data in, image out. See the "
    [:a {:href "./reference/manual/"} "Manual"]
    " to get started."]])

(defn install-content
  "Getting started walkthrough for the landing page."
  []
  [:div
   [:p "New to Clojure? Follow the "
    [:a {:href "https://calva.io/getting-started/" :target "_blank"} "Calva Getting Started guide"]
    " — it walks you through installing VS Code, Clojure, and the Calva extension, which lets you evaluate code directly from your editor."]
   [:h4 "1. Add Eido to your project"]
   [:p "Create a " [:code "deps.edn"] " file with Eido as a dependency:"]
   [:pre [:code
          (str ";; deps.edn\n{:deps\n {io.github.leifericf/eido\n"
               "  {:git/tag \"" (:tag eido-version)
               "\" :git/sha \"" (:sha eido-version) "\"}}}")]]
   [:h4 "2. Render your first image"]
   [:p "Start a REPL via Calva (\"Jack-in\"), then evaluate:"]
   [:pre [:code
          "(require '[eido.core :as eido])

(eido/render
  {:image/size [400 400]
   :image/background [:color/name \"linen\"]
   :image/nodes
   [{:node/type     :shape/circle
     :circle/center [200 200]
     :circle/radius 120
     :style/fill    [:color/name \"coral\"]}]}
  {:output \"my-first-image.png\"})
;; => \"my-first-image.png\""]]
   [:p "That's it — " [:code "my-first-image.png"]
    " is now on disk. Change the color, re-evaluate, see the new image. This interactive loop is how generative artists work with Eido."]])
