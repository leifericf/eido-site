(ns eido-site.scenes
  "Scene-side machinery for the site build.

  - Discovery: walk the gallery namespaces, pull out vars that
    carry :example metadata.
  - Example rendering: turn each :example var into a PNG/GIF on
    disk under _site/images/.
  - Source extraction: pull a clean source string for the
    gallery's \"View source\" reveal.
  - Docs previews: a curated set of scenes that illustrate code
    blocks across the manual / design / workflow pages."
  (:require
    [clojure.java.io :as io]
    [clojure.repl :as repl]
    [clojure.string :as str]
    [eido.animate :as anim]
    [eido.color :as color]
    [eido.color.palette :as palette]
    [eido.core :as eido]
    [eido.gen.boids :as boids]
    [eido.gen.ca :as ca]
    [eido.gen.circle :as circle]
    [eido.gen.flow :as flow]
    [eido.gen.noise :as noise]
    [eido.gen.particle :as particle]
    [eido.gen.prob :as prob]
    [eido.gen.series :as series]
    [eido.gen.subdivide :as subdivide]
    [eido.path.aesthetic :as aesthetic]
    [eido.scene :as scene]
    [eido.scene3d :as s3d]))

;; --- Configuration ---

(def example-namespaces
  "Namespaces to scan for example functions."
  '[eido-site.gallery.generative
    eido-site.gallery.showcase
    eido-site.gallery.artistic-expression
    eido-site.gallery.artisan
    eido-site.gallery.scenes-2d
    eido-site.gallery.scenes-3d
    eido-site.gallery.mixed-2d-3d
    eido-site.gallery.particles
    eido-site.gallery.typography
    eido-site.gallery.paint-engine])

;; --- Discovery ---

(defn find-examples
  "Finds all vars with :example metadata in the given namespace."
  [ns-sym]
  (require ns-sym)
  (->> (ns-publics (find-ns ns-sym))
       vals
       (filter #(:example (meta %)))
       (sort-by #(:title (:example (meta %))))
       (mapv (fn [v]
               (let [ex (:example (meta v))]
                 (merge ex {:var v :ns ns-sym}))))))

(defn all-examples
  "Returns all examples grouped by category."
  []
  (doseq [ns-sym example-namespaces]
    (require ns-sym))
  (->> example-namespaces
       (mapv (fn [ns-sym]
               {:ns       ns-sym
                :category (or (:category (meta (find-ns ns-sym)))
                              (-> (name ns-sym)
                                  (str/replace #"examples\.gallery\." "")
                                  (str/replace "-" " ")
                                  str/capitalize))
                :examples (find-examples ns-sym)}))
       (remove #(empty? (:examples %)))))


;; --- Rendering examples ---

(def ^:private phane-font
  "Absolute path to the vendored font Phane text nodes resolve against.
  The native backend resolves `:text/font` relative to the render base
  directory; binding the translator's font to this path keeps text
  scenes working without a Phane source checkout."
  (delay (some-> (io/resource "fonts/Lato-Regular.ttf") io/file .getAbsolutePath)))

(defn- emit!
  "Render `scene` (a single scene or a :frames animation) to `path` through
  the native Phane backend, binding the translator font so text scenes
  resolve. `fps` is this call site's animation default."
  [scene path fps]
  (io/make-parents path)
  (with-bindings {(requiring-resolve 'eido.phane.translate/*font*) @phane-font}
    (if (:frames scene)
      (eido/render (:frames scene) {:output path :fps (:fps scene fps)})
      (eido/render scene {:output path})))
  path)

(defn render-example!
  "Renders a single example to the given output directory."
  [{:keys [var output]} out-dir]
  (let [scene ((deref var))
        path  (str out-dir "/images/" output)]
    (emit! scene path 30)))

(defn render-all-examples!
  "Renders all discovered examples. Returns a seq of rendered paths."
  [out-dir]
  (let [groups (all-examples)]
    (doall
      (for [{:keys [examples]} groups
            example examples]
        (do
          (println "  Rendering" (:output example) "...")
          (render-example! example out-dir))))))

;; --- Source code extraction ---

(defn- strip-example-metadata
  "Strips `^{:example {...}}` reader metadata from a source-fn string so
  the gallery's View source displays code a user can paste into a REPL
  as-is. The `:example` map is internal bookkeeping for the site build;
  readers don't need to see it. Uses brace balancing rather than regex
  because the metadata map can contain nested collections."
  [src]
  (if-let [start (str/index-of src "^{:example")]
    (let [open (str/index-of src "{" start)
          n (count src)
          end (loop [i (inc open) depth 1]
                (cond
                  (zero? depth)    (dec i)
                  (>= i n)         n
                  (= \{ (.charAt src i)) (recur (inc i) (inc depth))
                  (= \} (.charAt src i)) (recur (inc i) (dec depth))
                  :else            (recur (inc i) depth)))
          after (loop [i (inc end)]
                  (if (and (< i n) (Character/isWhitespace (.charAt src i)))
                    (recur (inc i)) i))]
      (str (subs src 0 start) (subs src after)))
    src))

(defn example-source
  "Returns the source code string for an example var, with the internal
  `^{:example ...}` metadata stripped so users can copy/paste into a REPL."
  [{:keys [var]}]
  (some-> (repl/source-fn (symbol (str (namespace (symbol var)))
                                  (str (name (symbol var)))))
          strip-example-metadata))

;; --- Docs preview rendering ---

(defn docs-scenes
  "Returns a map of {filename -> scene} for docs code example previews.
  Each scene matches or illustrates its corresponding code block."
  []
  (let [bg [:color/rgb 245 243 238]
        helper-nodes [(assoc (scene/regular-polygon [100 125] 70 6)
                             :style/fill [:color/rgb 100 150 255])
                      (assoc (scene/star [280 125] 70 30 5)
                             :style/fill [:color/rgb 255 100 100])
                      (merge (scene/triangle [410 210] [510 40] [610 210])
                             {:style/fill [:color/rgb 100 200 100]})
                      (merge (scene/smooth-path [[660 200] [740 50] [820 200] [900 50]])
                             {:style/stroke {:color [:color/rgb 200 100 50] :width 3}})]
        grid-nodes (vec (scene/grid 10 10
                          (fn [col row]
                            {:node/type :shape/circle
                             :circle/center [(+ 30 (* col 40)) (+ 30 (* row 40))]
                             :circle/radius 15
                             :style/fill [:color/rgb (* col 25) (* row 25) 128]})))
        dist-nodes (vec (scene/distribute 8 [50 200] [750 200]
                          (fn [x y t]
                            {:node/type :shape/circle
                             :circle/center [x y]
                             :circle/radius (+ 5 (* 20 t))
                             :style/fill [:color/rgb 0 0 0]})))
        radial-nodes (vec (scene/radial 12 [200 200] 120
                            (fn [x y _angle]
                              {:node/type :shape/circle
                               :circle/center [x y]
                               :circle/radius 15
                               :style/fill [:color/rgb 200 0 0]})))
        ]
    {"docs-rect.png"
     {:image/size [300 200] :image/background bg
      :image/nodes [{:node/type :shape/rect
                     :rect/xy [50 50] :rect/size [200 100]
                     :style/fill [:color/rgb 0 128 255]}]}

     "docs-rect-rounded.png"
     {:image/size [300 200] :image/background bg
      :image/nodes [{:node/type :shape/rect
                     :rect/xy [50 50] :rect/size [200 100]
                     :rect/corner-radius 16
                     :style/fill [:color/rgb 0 128 255]}]}

     "docs-circle.png"
     {:image/size [400 400] :image/background bg
      :image/nodes [{:node/type :shape/circle
                     :circle/center [200 200] :circle/radius 80
                     :style/stroke {:color [:color/rgb 0 0 0] :width 2}}]}

     "docs-ellipse.png"
     {:image/size [400 400] :image/background bg
      :image/nodes [{:node/type :shape/ellipse
                     :ellipse/center [200 200]
                     :ellipse/rx 120 :ellipse/ry 60
                     :style/fill [:color/rgb 200 50 50]}]}

     "docs-arc.png"
     {:image/size [400 400] :image/background bg
      :image/nodes [{:node/type :shape/arc
                     :arc/center [200 200]
                     :arc/rx 80 :arc/ry 80
                     :arc/start 0 :arc/extent 270
                     :arc/mode :pie
                     :style/fill [:color/rgb 255 200 50]}]}

     "docs-line.png"
     {:image/size [400 300] :image/background bg
      :image/nodes [{:node/type :shape/line
                     :line/from [50 50] :line/to [350 250]
                     :style/stroke {:color [:color/rgb 0 0 0] :width 2}}]}

     "docs-path.png"
     {:image/size [400 300] :image/background bg
      :image/nodes [{:node/type :shape/path
                     :path/commands [[:move-to [100 200]]
                                     [:line-to [200 50]]
                                     [:curve-to [250 0] [300 100] [300 200]]
                                     [:quad-to [250 250] [200 200]]
                                     [:close]]
                     :style/fill [:color/rgb 255 200 50]}]}

     "docs-helpers.png"
     {:image/size [950 250] :image/background bg
      :image/nodes helper-nodes}

     "docs-text.png"
     {:image/size [250 120] :image/background bg
      :image/nodes [{:node/type :shape/text
                     :text/content "Hello"
                     :text/font {:font/family "Serif" :font/size 48
                                 :font/weight :bold}
                     :text/origin [125 80]
                     :text/align :center
                     :style/fill [:color/rgb 0 0 0]}]}

     "docs-text-glyphs.png"
     {:image/size [400 150] :image/background bg
      :image/nodes [{:node/type :shape/text-glyphs
                     :text/content "COLOR"
                     :text/font {:font/family "SansSerif" :font/size 64}
                     :text/origin [50 100]
                     :text/glyphs [{:glyph/index 0
                                    :style/fill [:color/rgb 255 0 0]}
                                   {:glyph/index 1
                                    :style/fill [:color/rgb 0 255 0]}]
                     :style/fill [:color/rgb 100 100 100]}]}

     "docs-text-on-path.png"
     {:image/size [400 200] :image/background bg
      :image/nodes [{:node/type :shape/text-on-path
                     :text/content "ALONG A CURVE"
                     :text/font {:font/family "SansSerif" :font/size 24}
                     :text/path [[:move-to [30 170]]
                                 [:curve-to [120 30] [280 30] [370 170]]]
                     :text/offset 10
                     :text/spacing 1
                     :style/fill [:color/rgb 0 0 0]}]}

     "docs-gradient-linear.png"
     {:image/size [300 200] :image/background bg
      :image/nodes [{:node/type :shape/rect
                     :rect/xy [50 50] :rect/size [200 100]
                     :rect/corner-radius 8
                     :style/fill {:gradient/type :linear
                                  :gradient/from [0 0]
                                  :gradient/to [200 0]
                                  :gradient/stops
                                  [[0.0 [:color/rgb 255 0 0]]
                                   [1.0 [:color/rgb 0 0 255]]]}}]}

     "docs-gradient-radial.png"
     {:image/size [300 250] :image/background bg
      :image/nodes [{:node/type :shape/circle
                     :circle/center [150 125] :circle/radius 100
                     :style/fill {:gradient/type :radial
                                  :gradient/center [100 100]
                                  :gradient/radius 100
                                  :gradient/stops
                                  [[0.0 [:color/name "white"]]
                                   [1.0 [:color/name "black"]]]}}]}

     "docs-hatch.png"
     {:image/size [300 250] :image/background bg
      :image/nodes [{:node/type :shape/circle
                     :circle/center [150 125] :circle/radius 100
                     :style/fill {:fill/type :hatch
                                  :hatch/angle 45
                                  :hatch/spacing 4
                                  :hatch/stroke-width 1
                                  :hatch/color [:color/rgb 0 0 0]}}]}

     "docs-stipple.png"
     {:image/size [300 250] :image/background bg
      :image/nodes [{:node/type :shape/circle
                     :circle/center [150 125] :circle/radius 100
                     :style/fill {:fill/type :stipple
                                  :stipple/density 0.6
                                  :stipple/radius 1.0
                                  :stipple/seed 42
                                  :stipple/color [:color/rgb 0 0 0]}}]}

     "docs-group.png"
     {:image/size [400 400] :image/background bg
      :image/nodes [{:node/type :group
                     :node/transform [[:transform/translate 200 200]]
                     :style/fill [:color/rgb 255 0 0]
                     :node/opacity 0.8
                     :group/children
                     [{:node/type :shape/circle
                       :circle/center [0 0] :circle/radius 80}
                      {:node/type :shape/rect
                       :rect/xy [-30 -30] :rect/size [60 60]
                       :style/fill [:color/rgb 0 0 255]
                       :node/opacity 0.5}]}]}

     "docs-grid.png"
     {:image/size [420 420] :image/background bg
      :image/nodes grid-nodes}

     "docs-distribute.png"
     {:image/size [800 400] :image/background bg
      :image/nodes dist-nodes}

     "docs-radial.png"
     {:image/size [400 400] :image/background bg
      :image/nodes radial-nodes}

     "docs-contour.png"
     {:image/size [500 400] :image/background bg
      :image/nodes [{:node/type :contour
                     :contour/bounds [0 0 500 400]
                     :contour/opts {:thresholds [0.0 0.2 0.4]
                                    :resolution 3
                                    :noise-scale 0.012
                                    :seed 42}
                     :style/stroke {:color [:color/rgb 100 150 100]
                                    :width 1}}]}

     ;; --- Generative docs scenes ---

     "docs-uniform-vs-gaussian.png"
     (let [uniform-pts (prob/uniform 80 20.0 380.0 42)
           gaussian-pts (prob/gaussian 80 200.0 50.0 99)]
       {:image/size [400 300] :image/background bg
        :image/nodes
        (into
          (mapv (fn [x] {:node/type :shape/circle
                         :circle/center [x 80] :circle/radius 3
                         :style/fill [:color/rgb 80 120 200]})
                uniform-pts)
          (mapv (fn [x] {:node/type :shape/circle
                         :circle/center [(max 20 (min 380 x)) 200] :circle/radius 3
                         :style/fill [:color/rgb 200 80 80]})
                gaussian-pts))})

     "docs-weighted-shapes.png"
     (let [shapes (mapv #(prob/pick-weighted [:circle :square :triangle] [6 3 1] %)
                        (range 60))]
       {:image/size [500 120] :image/background bg
        :image/nodes
        (vec (map-indexed
               (fn [i shape]
                 (let [x (+ 15 (* i 8)) y 60
                       color (case shape
                               :circle   [:color/rgb 80 140 220]
                               :square   [:color/rgb 220 160 40]
                               :triangle [:color/rgb 200 60 80])]
                   (case shape
                     :circle   {:node/type :shape/circle
                                :circle/center [x y] :circle/radius 3.5
                                :style/fill color}
                     :square   {:node/type :shape/rect
                                :rect/xy [(- x 3) (- y 3)] :rect/size [6 6]
                                :style/fill color}
                     :triangle {:node/type :shape/path
                                :path/commands [[:move-to [x (- y 4)]]
                                                [:line-to [(+ x 4) (+ y 3)]]
                                                [:line-to [(- x 4) (+ y 3)]]
                                                [:close]]
                                :style/fill color})))
               shapes))})

     "docs-circle-pack.png"
     (let [circles (circle/circle-pack [20 20 360 360]
                     {:min-radius 3 :max-radius 35 :padding 2
                      :max-circles 200 :seed 42})
           pal (:sunset palette/palettes)
           colors (palette/weighted-sample pal [3 2 2 1 5] (count circles) 42)]
       {:image/size [400 400] :image/background bg
        :image/nodes
        (mapv (fn [{[x y] :center r :radius} c]
                {:node/type :shape/circle
                 :circle/center [x y] :circle/radius r
                 :style/fill c
                 :style/stroke {:color [:color/rgba 40 30 20 0.25] :width 0.5}})
              circles colors)})

     "docs-circle-pack-star.png"
     (let [star-cmds (:path/commands (scene/star [200 200] 180 70 5))
           circles (circle/circle-pack-in-path star-cmds
                     {:min-radius 2 :max-radius 15 :padding 1 :seed 42})]
       {:image/size [400 400] :image/background bg
        :image/nodes
        (mapv (fn [{[x y] :center r :radius} i]
                {:node/type :shape/circle
                 :circle/center [x y] :circle/radius r
                 :style/fill [:color/hsl (mod (* i 17) 360) 0.6 0.55]})
              circles (range))})

     "docs-subdivide.png"
     (let [rects (subdivide/subdivide [15 15 370 370]
                   {:depth 4 :min-size 35 :padding 5 :seed 77})
           colors [[:color/rgb 245 245 240] [:color/rgb 245 245 240]
                   [:color/rgb 245 245 240] [:color/rgb 220 30 30]
                   [:color/rgb 30 60 180] [:color/rgb 245 220 40]]]
       {:image/size [400 400] :image/background [:color/rgb 20 20 20]
        :image/nodes
        (mapv (fn [{[x y w h] :rect :as cell}]
                {:node/type :shape/rect :rect/xy [x y] :rect/size [w h]
                 :style/fill (prob/pick colors (+ (hash cell) 42))
                 :style/stroke {:color [:color/rgb 20 20 20] :width 3}})
              rects)})

     "docs-weighted-palette.png"
     (let [pal [[:color/rgb 240 235 225] [:color/rgb 200 50 50]
                [:color/rgb 50 120 200] [:color/rgb 255 200 0]]
           weights [5 2 2 1]
           colors (palette/weighted-sample pal weights 100 42)]
       {:image/size [500 80] :image/background bg
        :image/nodes
        (mapv (fn [color i]
                {:node/type :shape/rect
                 :rect/xy [(+ 5 (* i 4.9)) 10] :rect/size [4 60]
                 :style/fill color})
              colors (range))})

     "docs-smooth-vs-raw.png"
     (let [pts [[30 180] [100 40] [180 160] [260 30] [340 150] [400 60] [470 170]]
           raw-cmds (into [[:move-to (first pts)]]
                          (mapv (fn [p] [:line-to p]) (rest pts)))
           smooth (aesthetic/smooth-commands raw-cmds {:samples 60})]
       {:image/size [500 220] :image/background bg
        :image/nodes
        [{:node/type :shape/path :path/commands raw-cmds
          :style/stroke {:color [:color/rgba 180 180 180 0.7] :width 2}}
         {:node/type :shape/path :path/commands smooth
          :style/stroke {:color [:color/rgb 200 60 60] :width 2.5}}]})

     "docs-jitter.png"
     (let [cmds [[:move-to [30 100]] [:line-to [470 100]]]
           j1 (aesthetic/jittered-commands cmds {:amount 4.0 :seed 42})
           j2 (aesthetic/jittered-commands cmds {:amount 12.0 :seed 42})]
       {:image/size [500 200] :image/background bg
        :image/nodes
        [{:node/type :shape/path :path/commands cmds
          :style/stroke {:color [:color/rgba 180 180 180 0.7] :width 1.5}}
         {:node/type :shape/path :path/commands j1
          :node/transform [[:transform/translate 0 -30]]
          :style/stroke {:color [:color/rgb 60 120 200] :width 1.5}}
         {:node/type :shape/path :path/commands j2
          :node/transform [[:transform/translate 0 30]]
          :style/stroke {:color [:color/rgb 200 60 80] :width 1.5}}]})

     "docs-dashes.png"
     (let [cmds [[:move-to [30 40]] [:line-to [470 40]]]
           d1 (aesthetic/dash-commands cmds {:dash [15.0 8.0]})
           d2 (aesthetic/dash-commands cmds {:dash [30.0 5.0]})
           d3 (aesthetic/dash-commands cmds {:dash [5.0 15.0]})]
       {:image/size [500 180] :image/background bg
        :image/nodes
        (vec (concat
               (mapv (fn [d] {:node/type :shape/path :path/commands d
                              :style/stroke {:color [:color/rgb 40 40 40] :width 2}})
                     (or d1 []))
               (mapv (fn [d] {:node/type :shape/path :path/commands d
                              :node/transform [[:transform/translate 0 50]]
                              :style/stroke {:color [:color/rgb 60 120 200] :width 2.5}})
                     (or d2 []))
               (mapv (fn [d] {:node/type :shape/path :path/commands d
                              :node/transform [[:transform/translate 0 100]]
                              :style/stroke {:color [:color/rgb 200 60 80] :width 2}})
                     (or d3 []))))})

     "docs-dashed-flow.png"
     (let [paths (flow/flow-field [20 20 460 360]
                   {:density 30 :steps 35 :step-size 3 :seed 42})
           pal (:ocean palette/palettes)]
       {:image/size [500 400] :image/background bg
        :image/nodes
        (vec (mapcat
               (fn [path-node i]
                 (let [cmds (:path/commands path-node)
                       smoothed (aesthetic/smooth-commands cmds {:samples 30})
                       dashes (aesthetic/dash-commands smoothed {:dash [10.0 6.0]})]
                   (mapv (fn [d] {:node/type :shape/path :path/commands d
                                  :style/stroke {:color (nth pal (mod i (count pal)))
                                                 :width 1.5}})
                         (or dashes []))))
               paths (range)))})

     "docs-ca-life.png"
     (let [g (ca/ca-run (ca/ca-grid 40 40 :random 42) :life 50)]
       {:image/size [400 400] :image/background bg
        :image/nodes (ca/ca->nodes g 10
                       {:style/fill [:color/rgb 30 30 30]})})

     "docs-rd-coral.png"
     (let [g (ca/rd-run (ca/rd-grid 80 80 :center 42)
               (:coral ca/rd-presets) 400)]
       {:image/size [400 400] :image/background [:color/rgb 10 20 40]
        :image/nodes (ca/rd->nodes g 5
                       (fn [a b]
                         (let [v (min 1.0 (* b 4))]
                           [:color/rgb (int (+ 10 (* 80 v)))
                            (int (+ 20 (* 120 v)))
                            (int (+ 40 (* 180 (- 1.0 (* a 0.3)))))])))})

     "docs-boids.gif"
     (let [config (assoc boids/classic :count 60 :bounds [0 0 500 350] :seed 42)
           frames (boids/simulate-flock config 80 {})]
       {:frames
        (anim/frames (count frames)
          (fn [t]
            (let [i (min (int (* t (dec (count frames)))) (dec (count frames)))]
              {:image/size [500 350]
               :image/background [:color/rgb 230 235 240]
               :image/nodes
               (boids/flock->nodes (nth frames i)
                 {:shape :triangle :size 7
                  :style {:style/fill [:color/rgb 40 45 55]}})})))
        :fps 24})

     "docs-series-grid.png"
     (let [spec {:hue {:type :uniform :lo 0.0 :hi 360.0}
                 :r   {:type :gaussian :mean 20.0 :sd 8.0}}
           editions (mapv #(assoc (series/series-params spec 42 %)
                                  :edition %)
                          (range 9))]
       {:image/size [400 400] :image/background [:color/rgb 30 30 35]
        :image/nodes
        (vec (mapcat
               (fn [{:keys [hue r edition]}]
                 (let [col (mod edition 3) row (quot edition 3)
                       cx (+ 70 (* col 130)) cy (+ 70 (* row 130))]
                   [{:node/type :shape/circle
                     :circle/center [cx cy]
                     :circle/radius (max 8 (min 55 r))
                     :style/fill [:color/hsl hue 0.7 0.55]}
                    {:node/type :shape/text
                     :text/content (str "#" edition)
                     :text/origin [(- cx 8) (+ cy (max 8 (min 55 r)) 14)]
                     :text/font {:font/family "SansSerif" :font/size 10}
                     :style/fill [:color/rgb 140 140 150]}]))
               editions))})

     ;; --- Color docs scenes ---

     "docs-color-formats.png"
     {:image/size [400 100] :image/background bg
      :image/nodes
      (vec (map-indexed
             (fn [i [label color]]
               {:node/type :group
                :group/children
                [{:node/type :shape/rect
                  :rect/xy [(+ 10 (* i 78)) 10] :rect/size [70 50]
                  :rect/corner-radius 6
                  :style/fill color}
                 {:node/type :shape/text
                  :text/content label
                  :text/origin [(+ 45 (* i 78)) 80]
                  :text/font {:font/family "SansSerif" :font/size 9}
                  :text/align :center
                  :style/fill [:color/rgb 100 100 100]}]})
             [["name" [:color/name "coral"]]
              ["rgb" [:color/rgb 255 127 80]]
              ["rgba" [:color/rgba 255 127 80 0.5]]
              ["hsl" [:color/hsl 16 1.0 0.66]]
              ["hex" [:color/hex "#FF7F50"]]]))}

     "docs-oklab-lerp.png"
     (let [steps 10
           sw    (/ 380.0 steps)]
       {:image/size [400 120] :image/background bg
        :image/nodes
        (vec
          (concat
            ;; RGB row (top)
            [{:node/type :shape/text
              :text/content "RGB"
              :text/origin [200 15]
              :text/font {:font/family "SansSerif" :font/size 9}
              :text/align :center
              :style/fill [:color/rgb 100 100 100]}]
            (for [i (range steps)]
              (let [t (/ (double i) (dec steps))]
                {:node/type :shape/rect
                 :rect/xy [(+ 10 (* i sw)) 20] :rect/size [sw 35]
                 :style/fill (color/lerp [:color/name "red"]
                                         [:color/name "cyan"] t)}))
            ;; OKLAB row (bottom)
            [{:node/type :shape/text
              :text/content "OKLAB"
              :text/origin [200 75]
              :text/font {:font/family "SansSerif" :font/size 9}
              :text/align :center
              :style/fill [:color/rgb 100 100 100]}]
            (for [i (range steps)]
              (let [t (/ (double i) (dec steps))]
                {:node/type :shape/rect
                 :rect/xy [(+ 10 (* i sw)) 80] :rect/size [sw 35]
                 :style/fill (color/lerp-oklab [:color/name "red"]
                                               [:color/name "cyan"] t)}))))})

     "docs-color-manip.png"
     (let [base [:color/name "red"]]
       {:image/size [400 80] :image/background bg
        :image/nodes
        (vec (map-indexed
               (fn [i [label color]]
                 {:node/type :group
                  :group/children
                  [{:node/type :shape/rect
                    :rect/xy [(+ 10 (* i 65)) 10] :rect/size [55 40]
                    :rect/corner-radius 5
                    :style/fill color}
                   {:node/type :shape/text
                    :text/content label
                    :text/origin [(+ 37 (* i 65)) 68]
                    :text/font {:font/family "SansSerif" :font/size 8}
                    :text/align :center
                    :style/fill [:color/rgb 100 100 100]}]})
               [["original" base]
                ["lighten" (color/lighten base 0.2)]
                ["darken" (color/darken base 0.2)]
                ["saturate" (color/saturate base 0.3)]
                ["hue+120" (color/rotate-hue base 120)]
                ["blend" (color/lerp base [:color/name "blue"] 0.5)]]))})

     "docs-particles.gif"
     (let [fire-frames (vec (particle/simulate
                              (particle/with-position
                                particle/fire [200 330])
                              50 {:fps 25}))]
       {:frames
        (anim/frames (count fire-frames)
          (fn [t]
            (let [i (min (int (* t (dec (count fire-frames))))
                         (dec (count fire-frames)))]
              {:image/size [400 380]
               :image/background [:color/rgb 20 15 10]
               :image/nodes (nth fire-frames i)})))
        :fps 25})

     ;; --- Architecture page scenes ---

     "docs-arch-input.png"
     {:image/size [400 300] :image/background [:color/name "linen"]
      :image/nodes
      [{:node/type :shape/circle
        :circle/center [200 150] :circle/radius 80
        :style/fill [:color/name "coral"]}
       {:node/type :shape/rect
        :rect/xy [50 50] :rect/size [100 60]
        :style/fill [:color/name "steelblue"]}]}

     "docs-arch-flowfield.png"
     (let [paths (flow/flow-field [20 20 360 260]
                   {:density 25 :steps 30 :step-size 3 :seed 42})]
       {:image/size [400 300] :image/background bg
        :image/nodes
        (mapv (fn [path-node i]
                (assoc path-node
                  :style/stroke {:color [:color/hsl (mod (* i 15) 360) 0.5 0.45]
                                 :width 1.2}))
              paths (range))})

     "docs-arch-hatch.png"
     {:image/size [300 250] :image/background bg
      :image/nodes [{:node/type :shape/circle
                     :circle/center [150 125] :circle/radius 100
                     :style/fill {:fill/type :hatch
                                  :hatch/angle 45
                                  :hatch/spacing 5
                                  :hatch/stroke-width 1
                                  :hatch/color [:color/rgb 60 50 40]}}]}

     ;; --- Additional docs scenes ---

     "docs-strokes.png"
     {:image/size [400 200] :image/background bg
      :image/nodes
      [{:node/type :shape/path
        :path/commands [[:move-to [30 50]] [:line-to [180 50]]]
        :style/stroke {:color [:color/rgb 40 40 40] :width 6 :cap :round}}
       {:node/type :shape/path
        :path/commands [[:move-to [220 50]] [:line-to [370 50]]]
        :style/stroke {:color [:color/rgb 40 40 40] :width 6 :cap :butt}}
       {:node/type :shape/path
        :path/commands [[:move-to [30 120]] [:line-to [370 120]]]
        :style/stroke {:color [:color/rgb 80 120 200] :width 3 :dash [15 8]}}
       {:node/type :shape/path
        :path/commands [[:move-to [30 160]] [:line-to [370 160]]]
        :style/stroke {:color [:color/rgb 200 80 80] :width 3 :dash [5 12]}}]}

     "docs-clipping.png"
     {:image/size [300 300] :image/background bg
      :image/nodes
      [{:node/type :group
        :group/clip {:node/type :shape/circle
                     :circle/center [150 150]
                     :circle/radius 100}
        :group/children
        [{:node/type :shape/rect
          :rect/xy [50 50] :rect/size [100 200]
          :style/fill [:color/rgb 220 50 50]}
         {:node/type :shape/rect
          :rect/xy [150 50] :rect/size [100 200]
          :style/fill [:color/rgb 50 100 220]}
         {:node/type :shape/rect
          :rect/xy [50 50] :rect/size [200 100]
          :style/fill [:color/rgba 255 220 0 0.5]}]}]}

     "docs-compositing.png"
     {:image/size [300 200] :image/background bg
      :image/nodes
      [{:node/type :shape/circle
        :circle/center [110 100] :circle/radius 70
        :style/fill [:color/rgb 220 50 50]}
       {:node/type :shape/circle
        :circle/center [190 100] :circle/radius 70
        :style/fill [:color/rgb 50 100 220]
        :node/opacity 0.6}]}

     "docs-transforms.png"
     {:image/size [400 200] :image/background bg
      :image/nodes
      (vec (for [i (range 5)]
             {:node/type :shape/rect
              :rect/xy [0 0] :rect/size [40 40]
              :node/transform [[:transform/translate (+ 50 (* i 75)) 80]
                               [:transform/rotate (* i 0.3)]]
              :style/fill [:color/hsl (* i 60) 0.6 0.5]
              :style/stroke {:color [:color/rgb 40 40 40] :width 1}}))}

     "docs-noise-field.png"
     {:image/size [400 300] :image/background [:color/rgb 20 20 30]
      :image/nodes
      (vec (for [x (range 0 400 6)
                 y (range 0 300 6)]
             (let [v (noise/perlin2d (* x 0.015) (* y 0.015) {:seed 42})
                   brightness (+ 0.5 (* 0.5 v))]
               {:node/type :shape/rect
                :rect/xy [x y] :rect/size [5 5]
                :style/fill [:color/rgb
                             (int (* 255 brightness 0.3))
                             (int (* 255 brightness 0.7))
                             (int (* 255 brightness))]})))}

     "docs-animation.gif"
     {:frames
      (anim/frames 40
        (fn [t]
          {:image/size [250 250]
           :image/background [:color/rgb 30 30 40]
           :image/nodes
           [{:node/type :shape/circle
             :circle/center [125 125]
             :circle/radius (max 1 (* 90 t))
             :style/fill [:color/hsl (* 360 t) 0.8 0.5]}]}))
      :fps 20}

     "docs-easing.png"
     {:image/size [400 200] :image/background bg
      :image/nodes
      (vec (concat
             ;; Linear (gray dots)
             (for [i (range 20)]
               (let [t (/ i 19.0)]
                 {:node/type :shape/circle
                  :circle/center [(+ 30 (* 340 t)) (- 180 (* 150 t))]
                  :circle/radius 3
                  :style/fill [:color/rgb 180 180 180]}))
             ;; Ease-in-out (colored dots)
             (for [i (range 20)]
               (let [t (/ i 19.0)
                     et (anim/ease-in-out t)]
                 {:node/type :shape/circle
                  :circle/center [(+ 30 (* 340 t)) (- 180 (* 150 et))]
                  :circle/radius 4
                  :style/fill [:color/rgb 80 140 220]}))))}

     "docs-3d-sphere.png"
     (let [proj (s3d/perspective
                  {:scale 120 :origin [200 200]
                   :yaw 0.5 :pitch -0.3 :distance 5})
           light {:light/direction [1 1 0.5]
                  :light/ambient 0.25
                  :light/intensity 0.8}
           result (s3d/sphere proj [0 0 0]
                    {:radius 1.5
                     :style {:style/fill [:color/rgb 100 150 255]}
                     :light light
                     :subdivisions 4
                     :smooth true})]
       {:image/size [400 400] :image/background bg
        :image/nodes (if (sequential? result) (vec result) [result])})

     ;; --- Workflow previews ---

     "docs-wf-sketch-circle.png"
     {:image/size [400 400]
      :image/background [:color/name "linen"]
      :image/nodes
      [{:node/type     :shape/circle
        :circle/center [200 200]
        :circle/radius 120
        :style/fill    [:color/name "crimson"]}]}

     "docs-wf-plotter-strokes.png"
     (let [paths (flow/flow-field [0 0 400 300]
                   {:density 15 :steps 40 :noise-scale 0.005 :seed 7})]
       {:image/size [400 300]
        :image/background [:color/rgb 245 243 238]
        :image/nodes
        (mapv #(assoc % :style/stroke {:color [:color/rgb 30 30 30] :width 0.8})
              paths)})

     "docs-wf-motion-streams.png"
     {:image/size [400 300]
      :image/background [:color/rgb 245 243 238]
      :image/nodes
      (into []
        (concat
          ;; Pen 1 — red concentric rings
          (for [r (range 25 95 12)]
            {:node/type     :shape/circle
             :circle/center [100 150]
             :circle/radius r
             :style/stroke  {:color [:color/rgb 200 60 40] :width 1.0}})
          ;; Pen 2 — blue horizontal hatching
          (for [y (range 70 230 8)]
            {:node/type    :shape/line
             :line/from    [190 y]
             :line/to      [290 y]
             :style/stroke {:color [:color/rgb 40 60 160] :width 1.0}})
          ;; Pen 3 — green radial spokes
          (for [i (range 24)]
            (let [a  (* i (/ (* 2.0 Math/PI) 24.0))
                  x1 (+ 345 (* 20 (Math/cos a)))
                  y1 (+ 150 (* 20 (Math/sin a)))
                  x2 (+ 345 (* 48 (Math/cos a)))
                  y2 (+ 150 (* 48 (Math/sin a)))]
              {:node/type    :shape/line
               :line/from    [x1 y1]
               :line/to      [x2 y2]
               :style/stroke {:color [:color/rgb 40 140 70] :width 1.0}}))))}

     "docs-wf-clip-export.png"
     (let [paths (flow/flow-field [0 0 300 300]
                   {:density 10 :steps 30 :noise-scale 0.008 :seed 13})
           stroked (mapv #(assoc % :style/stroke
                                 {:color [:color/rgb 30 30 30] :width 0.8})
                         paths)]
       {:image/size       [300 300]
        :image/background [:color/rgb 245 243 238]
        :image/nodes
        [{:node/type  :group
          :group/clip {:node/type     :shape/circle
                       :circle/center [150 150]
                       :circle/radius 110}
          :group/children stroked}]})

     "docs-wf-print-paper.png"
     (let [paper (scene/paper :a4)]
       (-> paper
           (assoc :image/background :white
                  :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [1.0 1.0]
                    :rect/size [19.0 27.7]
                    :style/stroke {:color [:color/rgb 200 200 200] :width 0.02}}
                   {:node/type     :shape/circle
                    :circle/center [10.5 14.85]
                    :circle/radius 5.0
                    :style/fill    [:color/rgb 200 50 50]
                    :style/stroke  {:color [:color/rgb 30 30 30] :width 0.05}}
                   {:node/type     :shape/circle
                    :circle/center [10.5 14.85]
                    :circle/radius 3.0
                    :style/fill    [:color/rgb 50 100 200]
                    :style/stroke  {:color [:color/rgb 30 30 30] :width 0.05}}])
           scene/with-units))

     "docs-wf-color-swatch.png"
     {:image/size [400 80]
      :image/background [:color/rgb 245 243 238]
      :image/nodes
      (vec (map-indexed
             (fn [i color]
               {:node/type :shape/rect
                :rect/xy [(+ 10 (* i 78)) 10]
                :rect/size [68 60]
                :rect/corner-radius 6
                :style/fill color})
             [[:color/rgb 42 38 35]
              [:color/rgb 180 140 90]
              [:color/rgb 220 200 170]
              [:color/rgb 80 100 60]
              [:color/rgb 150 50 40]]))}

     "docs-wf-3d-sphere.png"
     (let [proj (s3d/perspective {:scale 120 :origin [200 200]
                                  :yaw 0.6 :pitch -0.3 :distance 5})
           light {:light/direction [1 1 0.5]
                  :light/ambient 0.2
                  :light/intensity 0.8}
           result (s3d/sphere proj [0 0 0]
                    {:radius 1.5
                     :style {:style/fill [:color/rgb 100 150 255]
                             :style/stroke {:color [:color/rgb 40 60 120]
                                            :width 0.5}}
                     :light light
                     :subdivisions 3
                     :smooth true})]
       {:image/size [400 400]
        :image/background [:color/rgb 30 30 40]
        :image/nodes (if (sequential? result) (vec result) [result])})

     ;; --- Guide /paint/ section previews ---

     "paint-chalk-sketch.png"
     {:image/size [600 400]
      :image/background [:color/rgb 252 250 242]
      :image/nodes
      [{:node/type :paint/surface
        :paint/size [600 400]
        :paint/strokes
        [{:paint/brush  :chalk
          :paint/color  [:color/rgb 80 60 40]
          :paint/radius 12.0
          :paint/points [[50 100 0.8 0 0 0]
                         [300 60 1.0 1.0 0 0]
                         [550 100 0.3 0.5 0 0]]}]}]}

     "paint-layered-strokes.png"
     {:image/size [600 200]
      :image/background [:color/rgb 252 250 242]
      :image/nodes
      [{:node/type :shape/path
        :path/commands [[:move-to [50 100]]
                        [:curve-to [150 30] [350 170] [550 100]]]
        :paint/brush :chalk
        :paint/color [:color/rgb 80 60 40]
        :paint/radius 12.0
        :paint/pressure [[0.0 0.3] [0.5 1.0] [1.0 0.1]]}]}

     "paint-ink-flow.png"
     {:image/size [600 600]
      :image/background [:color/rgb 252 250 242]
      :image/nodes
      [{:node/type :group
        :paint/surface {:paint/size [600 600]}
        :group/children
        [{:node/type :flow-field
          :flow/bounds [30 30 540 540]
          :flow/opts {:density 20 :steps 40 :seed 77}
          :paint/brush :ink
          :paint/color [:color/rgb 15 12 8]
          :paint/radius 2.0}]}]}

     ;; --- Paint workflow previews ---

     "paint-02-ink-calligraphy.png"
     {:image/size [800 400]
      :image/background [:color/rgb 252 250 242]
      :image/nodes
      [{:node/type :shape/path
        :path/commands [[:move-to [60 200]]
                        [:curve-to [200 80] [350 320] [740 160]]]
        :paint/brush :ink
        :paint/color [:color/rgb 15 10 5]
        :paint/radius 9.0
        :paint/pressure [[0.0 0.1] [0.3 0.9] [0.7 0.6] [1.0 0.05]]}]}

     "paint-03-watercolor-wash.png"
     {:image/size [800 400]
      :image/background [:color/rgb 252 250 242]
      :image/nodes
      [{:node/type :group
        :paint/surface {:paint/size [800 400] :substrate/tooth 0.3}
        :group/children
        [{:node/type :shape/path
          :path/commands [[:move-to [20 250]]
                          [:curve-to [200 100] [400 350] [780 200]]]
          :paint/brush :watercolor
          :paint/color [:color/hsl 210 0.45 0.65]
          :paint/radius 45.0}
         {:node/type :shape/path
          :path/commands [[:move-to [120 220]]
                          [:curve-to [300 150] [500 280] [680 180]]]
          :paint/brush :ink
          :paint/color [:color/rgb 25 30 50]
          :paint/radius 2.5}]}]}

     "paint-04-flow-ink.png"
     {:image/size [700 700]
      :image/background [:color/rgb 252 250 242]
      :image/nodes
      [{:node/type :group
        :paint/surface {:paint/size [700 700]}
        :group/children
        [{:node/type :flow-field
          :flow/bounds [40 40 620 620]
          :flow/opts {:density 18 :steps 50
                      :noise-scale 0.005 :seed 33}
          :paint/brush :ink
          :paint/color [:color/rgb 20 15 10]
          :paint/radius 1.8}]}]}

     "paint-05-pastel-landscape.png"
     ;; Showcase grain + substrate: horizontal pastel sweeps of warm
     ;; colors on a canvas with tooth so the strokes skip into valleys.
     {:image/size [700 400]
      :image/background [:color/rgb 238 230 215]
      :image/nodes
      [{:node/type :group
        :paint/surface {:paint/size [700 400]
                        :substrate/tooth 0.45
                        :substrate/scale 0.08}
        :group/children
        (mapv (fn [i]
                (let [y (+ 80 (* i 52))
                      colors [[200 110 60] [220 155 80] [230 190 120]
                              [150 130 170] [90 100 140]]]
                  {:node/type :shape/path
                   :path/commands [[:move-to [40 y]]
                                   [:curve-to [200 (- y 12)]
                                              [500 (+ y 18)]
                                              [660 (- y 6)]]]
                   :paint/brush :pastel
                   :paint/color (into [:color/rgb] (nth colors i))
                   :paint/radius 26.0
                   :paint/pressure [[0.0 0.3] [0.5 1.0] [1.0 0.3]]}))
              (range 5))}]}

     "paint-06-bristle-flat.png"
     ;; Showcase bristle multi-tip: a single broad arc with a flat
     ;; bristle brush so individual hair marks are legible.
     {:image/size [700 280]
      :image/background [:color/rgb 244 238 226]
      :image/nodes
      [{:node/type :shape/path
        :path/commands [[:move-to [40 180]]
                        [:curve-to [200 60] [500 260] [660 120]]]
        :paint/brush {:brush/type :brush/dab
                      :brush/tip {:tip/shape :ellipse
                                  :tip/hardness 0.6
                                  :tip/aspect 1.3}
                      :brush/paint {:paint/opacity 0.55
                                    :paint/spacing 0.04
                                    :paint/flow 0.9}
                      :brush/bristles {:bristle/count 9
                                       :bristle/spread 1.0
                                       :bristle/shear 0.15}}
        :paint/color [:color/rgb 140 60 55]
        :paint/radius 22.0
        :paint/pressure [[0.0 0.4] [0.5 1.0] [1.0 0.4]]}]}

     ;; --- Workflow animation ---

     "docs-wf-animation-basics.gif"
     {:frames
      (anim/frames 30
        (fn [t]
          {:image/size [300 300]
           :image/background [:color/rgb 30 30 40]
           :image/nodes
           [{:node/type     :shape/circle
             :circle/center [150 150]
             :circle/radius (+ 20 (* 110 t))
             :style/fill    [:color/hsl (* 360 t) 0.8 0.5]}]}))
      :fps 20}

     ;; --- Workflow 3D ---

     "docs-wf-3d-primitives.png"
     ;; Four primitive meshes side by side — sphere, cube, cylinder,
     ;; torus — with the same lighting and material so readers can see
     ;; the shape variety rather than stylistic differences.
     (let [light {:light/direction [1 1 0.5] :light/ambient 0.25
                  :light/intensity 0.8}
           style {:style/fill [:color/rgb 180 190 220]
                  :style/stroke {:color [:color/rgb 60 70 100]
                                 :width 0.4}}
           proj-at (fn [cx]
                     (s3d/perspective {:scale 65 :origin [cx 150]
                                       :yaw 0.5 :pitch -0.25 :distance 6}))]
       {:image/size [900 300]
        :image/background [:color/rgb 240 238 232]
        :image/nodes
        [(s3d/sphere (proj-at 110) [0 0 0]
                     {:radius 1.3 :style style :light light
                      :segments 24 :rings 16})
         (s3d/cube (proj-at 340) [0 0 0]
                   {:size 2.2 :style style :light light})
         (s3d/cylinder (proj-at 570) [0 0 0]
                       {:radius 0.8 :height 2.2 :style style
                        :light light :segments 24})
         (s3d/torus (proj-at 800) [0 0 0]
                    {:major-radius 1.1 :minor-radius 0.4
                     :style style :light light
                     :ring-segments 24 :tube-segments 12})]})

     "docs-wf-3d-transforms.png"
     ;; Show chained transforms — rotate, non-uniform scale, and a
     ;; twist deformation — producing a stretched, twisted shape.
     (let [proj (s3d/perspective {:scale 150 :origin [200 200]
                                  :yaw 0.4 :pitch -0.2 :distance 6})
           light {:light/direction [1 1 0.5] :light/ambient 0.25
                  :light/intensity 0.8}
           m (-> (s3d/sphere-mesh 1.2 {:segments 24 :rings 16})
                 (s3d/rotate-mesh :y 0.3)
                 (s3d/scale-mesh [1.0 1.5 1.0])
                 (s3d/deform-mesh {:deform/type :twist
                                   :deform/axis :y
                                   :deform/amount 0.9}))]
       {:image/size [400 400]
        :image/background [:color/rgb 240 238 232]
        :image/nodes
        [(s3d/render-mesh proj m
           {:style {:style/fill [:color/rgb 120 180 150]
                    :style/stroke {:color [:color/rgb 40 80 60]
                                   :width 0.4}}
            :light light})]})

     "docs-wf-3d-texture.png"
     ;; Noise-painted sphere via paint-mesh + render-mesh. Demonstrates
     ;; UV-projected procedural color against a neutral backdrop.
     (let [proj (s3d/perspective {:scale 150 :origin [200 200]
                                  :yaw 0.4 :pitch -0.25 :distance 6})
           light {:light/direction [1 1 0.5] :light/ambient 0.3
                  :light/intensity 0.7}
           noise-field (requiring-resolve 'eido.ir.field/noise-field)
           m (-> (s3d/sphere-mesh 1.4 {:segments 32 :rings 20})
                 (s3d/subdivide {:iterations 1})
                 (s3d/paint-mesh
                   {:color/type :field
                    :color/field (noise-field :scale 2.5 :variant :fbm
                                              :seed 19)
                    :color/palette [[:color/rgb 200 80 60]
                                    [:color/rgb 220 180 100]
                                    [:color/rgb 90 140 180]
                                    [:color/rgb 60 90 150]]}))]
       {:image/size [400 400]
        :image/background [:color/rgb 240 238 232]
        :image/nodes
        [(s3d/render-mesh proj m
           {:light light :shading :smooth})]})

     "docs-wf-3d-npr.png"
     ;; Hatch-stroked sphere — lit faces get sparse hatching, shadowed
     ;; faces get dense hatching. Produces a plotter-friendly drawing.
     (let [proj (s3d/perspective {:scale 140 :origin [200 200]
                                  :yaw 0.4 :pitch -0.25 :distance 6})
           m (s3d/sphere-mesh 1.4 {:segments 16 :rings 10})]
       {:image/size [400 400]
        :image/background [:color/rgb 248 246 240]
        :image/nodes
        [(s3d/render-mesh proj m
           {:style {:render/mode :hatch
                    :style/fill [:color/rgb 250 247 238]
                    :hatch/angle 45 :hatch/spacing 3
                    :hatch/color [:color/rgb 40 30 20]
                    :hatch/stroke-width 0.5}
            :light {:light/direction [1 2 1]
                    :light/ambient 0.2
                    :light/intensity 0.8}
            :cull-back false})]})

     ;; --- Workflow color ---

     "docs-wf-palette-generation.png"
     ;; Show three color-theory palettes derived from a common base
     ;; hue: complementary pair, analogous (5), triadic (3).
     {:image/size [480 220]
      :image/background [:color/rgb 245 243 238]
      :image/nodes
      (let [base [:color/oklch 0.65 0.15 30]
            comp-pal [base (palette/complementary base)]
            ana-pal  (palette/analogous base 5)
            tri-pal  (palette/triadic base)
            swatch   (fn [pal row]
                       (vec (map-indexed
                              (fn [i c]
                                {:node/type :shape/rect
                                 :rect/xy [(+ 20 (* i 60)) (+ 20 (* row 60))]
                                 :rect/size [52 52]
                                 :rect/corner-radius 4
                                 :style/fill c})
                              pal)))]
        (vec (concat (swatch comp-pal 0)
                     (swatch ana-pal 1)
                     (swatch tri-pal 2))))}

     "docs-wf-palette-manipulation.png"
     ;; Original palette vs warmer / cooler / muted variants stacked.
     {:image/size [480 220]
      :image/background [:color/rgb 245 243 238]
      :image/nodes
      (let [base (:sunset palette/palettes)
            rows [base
                  (palette/warmer base 12)
                  (palette/cooler base 12)
                  (palette/muted base 0.4)]
            swatch (fn [pal row]
                     (vec (map-indexed
                            (fn [i c]
                              {:node/type :shape/rect
                               :rect/xy [(+ 20 (* i 72)) (+ 10 (* row 50))]
                               :rect/size [64 40]
                               :rect/corner-radius 4
                               :style/fill c})
                            (take 6 pal))))]
        (vec (mapcat (fn [row pal] (swatch pal row))
                     (range) rows)))}

     ;; --- Workflow editions ---

     "docs-wf-editions-contact.png"
     ;; Miniature contact sheet — 10 seeded variations of one algorithm
     ;; so readers see what "export-edition-package" produces.
     (let [tile-w 90 tile-h 90 cols 5 rows 2 pad 8
           w (+ pad (* cols (+ tile-w pad)))
           h (+ pad (* rows (+ tile-h pad)))
           tile (fn [seed ox oy]
                  (let [rng (prob/make-rng seed)
                        n   (+ 6 (long (* 6 (.nextDouble rng))))
                        hue (* 360 (.nextDouble rng))
                        tile-bg [:color/hsl hue 0.25 0.92]
                        fg      [:color/hsl (mod (+ hue 20) 360) 0.55 0.35]]
                    (into [{:node/type :shape/rect
                            :rect/xy [ox oy]
                            :rect/size [tile-w tile-h]
                            :rect/corner-radius 3
                            :style/fill tile-bg}]
                          (for [_ (range n)]
                            {:node/type :shape/circle
                             :circle/center [(+ ox 6 (* (- tile-w 12) (.nextDouble rng)))
                                             (+ oy 6 (* (- tile-h 12) (.nextDouble rng)))]
                             :circle/radius (+ 2 (* 10 (.nextDouble rng)))
                             :style/fill fg
                             :node/opacity 0.7}))))]
       {:image/size [w h]
        :image/background [:color/rgb 250 248 242]
        :image/nodes
        (vec (apply concat
               (for [r (range rows) c (range cols)
                     :let [seed (+ 42 (* r cols) c)
                           ox (+ pad (* c (+ tile-w pad)))
                           oy (+ pad (* r (+ tile-h pad)))]]
                 (tile seed ox oy))))})

     }))

(defn render-docs-examples!
  "Renders preview images for docs code examples through the Phane backend.
  Supports both static scenes and animated scenes with :frames."
  [out-dir]
  (doseq [[filename scene-data] (docs-scenes)]
    (let [path (str out-dir "/images/" filename)]
      (println "  Rendering" filename "...")
      (emit! scene-data path 24))))

