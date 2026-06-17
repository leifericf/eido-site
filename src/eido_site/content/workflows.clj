(ns eido-site.content.workflows
  "Workflow guide pages — registry of guides + the section-builder
  fns each one renders.")

;; --- Workflow pages ---

(defn- workflow-sketching-sections []
  [{:id "repl-loop"
    :title "The REPL Loop"
    :content
    [:div
     [:p "Eido's primary workflow is REPL-driven: edit a scene, evaluate it, see the result, adjust. No compile step, no build tool, no save-and-refresh cycle."]
     [:pre {:data-img "docs-wf-sketch-circle.png"} [:code
            "(require '[eido.core :as eido])

;; Define a scene
(def scene
  {:image/size [400 400]
   :image/background [:color/name \"linen\"]
   :image/nodes
   [{:node/type     :shape/circle
     :circle/center [200 200]
     :circle/radius 120
     :style/fill    [:color/name \"crimson\"]}]})

;; Render and display in the REPL
(eido/show scene)"]]
     [:p "Change the radius, re-evaluate, see the result instantly. This tight loop is how most Eido work happens."]]}

   {:id "watch-workflow"
    :title "Watch Workflows"
    :content
    [:div
     [:p "For continuous feedback, watch a scene atom or a file. Every change triggers a re-render in a preview window:"]
     [:pre [:code
            ";; Watch an atom — updates on every swap!/reset!
(def my-scene (atom scene))
(eido/watch-scene my-scene)

;; Now just change the atom:
(swap! my-scene assoc-in [:image/nodes 0 :circle/radius] 80)
;; Preview updates automatically

;; Or watch an EDN file — updates on every save
(eido/watch-file \"sketch.edn\")"]]
     [:p "The first render validates the scene; subsequent re-renders skip validation for speed."]]}

   {:id "seed-exploration"
    :title "Exploring Seeds"
    :content
    [:div
     [:p [:code "seed-grid"] " renders a grid of variations from different seeds — a visual overview of what your algorithm produces:"]
     [:pre [:code
            "(require '[eido.gen.series :as series])

(series/seed-grid
  {:spec      {:hue {:type :uniform :lo 0.0 :hi 360.0}
               :density {:type :gaussian :mean 20.0 :sd 5.0}}
   :master-seed 42
   :scene-fn  (fn [params _edition]
                (make-scene params))
   :cols 5 :rows 3
   :thumb-size [160 160]})"]]
     [:p "Each cell is a different edition with different sampled parameters. Spot interesting ones, note the edition number, and explore further."]]}

   {:id "param-sweeps"
    :title "Parameter Sweeps"
    :content
    [:div
     [:p [:code "param-grid"] " isolates a single parameter, rendering a sweep across a range of values:"]
     [:pre [:code
            "(series/param-grid
  {:scene-fn    (fn [v] (make-scene {:density v}))
   :param-name  \"density\"
   :values      (range 5 50 5)
   :thumb-size  [120 120]})"]]
     [:p "This makes design decisions concrete — you can see exactly where a parameter transitions from \"too sparse\" to \"just right\" to \"too dense.\""]]}

   {:id "capturing-seeds"
    :title "Capturing Interesting Seeds"
    :content
    [:div
     [:p "When you find a combination you like, use " [:code "save-seed!"] " to bookmark it:"]
     [:pre [:code
            "(require '[eido.gen.series :as series])

;; Bookmark a discovery
(series/save-seed! \"keepers.edn\"
  {:seed 4217 :params params :note \"nice organic density\"})

;; Later, load all bookmarks
(series/load-seeds \"keepers.edn\")
;=> [{:seed 4217 :params {...} :note \"nice organic density\"
;     :timestamp \"2026-04-10T...\"}]"]]
     [:p "Each entry is timestamped automatically. The file accumulates entries — one per " [:code "save-seed!"] " call."]
     [:p "For maximum reliability, save the scene map directly — it's a complete, self-contained description that can be re-rendered anytime:"]
     [:pre [:code "(spit \"keeper-4217.edn\" (pr-str scene))"]]]}])

(defn- workflow-editions-sections []
  [{:id "overview"
    :title "Overview"
    :content
    [:div
     [:p "A long-form edition series is one algorithm that produces many unique outputs — each driven by a deterministic seed. The same seed always produces the same image. This is the workflow behind Art Blocks, fxhash, and numbered print editions."]
     [:p "Eido provides this pipeline:"]
     [:ol
      [:li "Define a " [:strong "parameter spec"] " — what varies across editions"]
      [:li "Write a " [:strong "scene function"] " — turns parameters into a scene"]
      [:li "Render the " [:strong "range"] " of editions — one deterministic parameter map each"]
      [:li "Analyze " [:strong "trait distribution"] " across the series"]]]}

   {:id "param-spec"
    :title "Parameter Specs"
    :content
    [:div
     [:p "A parameter spec defines what varies across editions using data-driven distributions:"]
     [:pre [:code
            "(def spec
  {:hue      {:type :uniform :lo 0.0 :hi 360.0}
   :density  {:type :gaussian :mean 20.0 :sd 5.0}
   :palette  {:type :choice :options [:sunset :ocean :forest]}
   :bold?    {:type :boolean :probability 0.3}
   :weight   {:type :weighted-choice
              :options [:light :medium :heavy]
              :weights [0.5 0.35 0.15]}})"]]
     [:p "Each edition samples from these distributions using a deterministic seed derived from the master seed + edition number. Distribution types: " [:code ":uniform"] ", " [:code ":gaussian"] ", " [:code ":choice"] ", " [:code ":weighted-choice"] ", " [:code ":boolean"] ", " [:code ":pareto"] ", " [:code ":triangular"] ", " [:code ":eased"] "."]]}

   {:id "scene-fn"
    :title "Scene Function"
    :content
    [:div
     [:p "The scene function takes sampled parameters and returns a scene map:"]
     [:pre [:code
            "(defn make-scene [params]
  {:image/size [800 800]
   :image/background [:color/hsl (:hue params) 0.1 0.95]
   :image/nodes
   [(build-artwork params)]})"]]
     [:p "This is where your creative algorithm lives. The parameters control variation; the algorithm defines the visual language."]]}

   {:id "batch-render"
    :title "Batch Rendering"
    :content
    [:div
     [:p [:code "series-range"] " expands a spec into one deterministic parameter map per edition. Render each one in a loop:"]
     [:pre [:code
            "(require '[eido.gen.series :as series])

(doseq [[edition params]
        (map-indexed vector (series/series-range spec 42 0 50))]
  (eido/render (make-scene params)
               {:output (str \"editions/edition-\" edition \".png\")}))"]]
     [:p "This writes " [:code "editions/edition-0.png"] " through " [:code "edition-49.png"]
      ". Each edition's parameters come from a seed derived from the master seed (42) and the edition number, so the same pair always reproduces the same image."]]}

   {:id "traits"
    :title "Trait Analysis"
    :content
    [:div
     [:p "Traits categorize continuous parameters into named labels. " [:code "trait-summary"]
      " counts how many editions fall into each bucket across the series:"]
     [:pre [:code
            "(series/trait-summary
  spec 42 100
  {:density [[15 \"sparse\"] [25 \"medium\"] [100 \"dense\"]]})
;=> {:density {\"sparse\" 22, \"medium\" 48, \"dense\" 30}}"]]
     [:p "Useful for verifying that rare traits are actually rare before releasing a series."]]}

   {:id "reproducibility"
    :title "Reproducibility"
    :content
    [:div
     [:p "Reproduction rests on determinism: the same scene map renders to the same bytes, every time. Store the scene as EDN — or the seed and spec that generate it — and you can always re-render it."]
     [:p "When you stumble on a seed worth keeping, bookmark it:"]
     [:pre [:code
            "(series/save-seed! \"seeds.edn\"
  {:seed 42 :params params :note \"strong diagonal\"})

;; Later, read them all back
(series/load-seeds \"seeds.edn\")
;=> [{:seed 42 :params {,,,} :note \"strong diagonal\" :timestamp \"...\"}]"]]
     [:p [:code "save-seed!"] " appends one EDN form per line; " [:code "load-seeds"] " reads them back in append order."]]}])

(defn- workflow-print-sections []
  [{:id "paper-presets"
    :title "Paper Presets"
    :content
    [:div
     [:p "Start with a standard paper size — the preset gives you dimensions, units, and DPI:"]
     [:pre [:code
            "(require '[eido.scene :as scene])

(scene/paper :a4)
;=> {:image/size [21.0 29.7] :image/units :cm :image/dpi 300}

(scene/paper :letter :landscape true)
;=> {:image/size [11.0 8.5] :image/units :in :image/dpi 300}

(scene/paper :a3 :dpi 600)
;=> {:image/size [29.7 42.0] :image/units :cm :image/dpi 600}"]]
     [:p "Available sizes: " [:code ":a3"] ", " [:code ":a4"] ", " [:code ":a5"] ", " [:code ":letter"] ", " [:code ":legal"] ", " [:code ":tabloid"] ", " [:code ":square-8"] "."]]}

   {:id "units"
    :title "Real-World Units"
    :content
    [:div
     [:p "Describe geometry in centimeters, millimeters, or inches. " [:code "with-units"] " converts to pixels before rendering:"]
     [:pre {:data-img "docs-wf-print-paper.png"} [:code
            "(-> (scene/paper :a4)
    (assoc :image/background :white
           :image/nodes
           [{:node/type     :shape/circle
             :circle/center [10.5 14.85]   ;; center of A4 in cm
             :circle/radius 5.0            ;; 5 cm radius
             :style/stroke  {:color :black :width 0.1}}])  ;; 1mm stroke
    scene/with-units
    (eido/render {:output \"print.png\"}))"]]
     [:p [:code "with-units"] " walks the entire scene tree, scaling all spatial values (coordinates, radii, stroke widths, dash patterns, font sizes) while leaving non-spatial values (opacity, angles, colors) untouched."]]}

   {:id "margins"
    :title "Margins"
    :content
    [:div
     [:p "Add margins to any scene with " [:code "with-margin"] ":"]
     [:pre [:code
            "(-> (scene/paper :a4)
    (assoc :image/nodes [,,,your artwork,,,])
    (scene/with-margin 2.0)   ;; 2cm margin on all sides
    scene/with-units
    (eido/render {:output \"print.png\"}))"]]
     [:p "The margin clips artwork to the inset rectangle, giving you a clean border. Apply it before " [:code "with-units"] "."]]}

   {:id "export-formats"
    :title "Rendering at Print Resolution"
    :content
    [:div
     [:p "Author in real-world units, convert with " [:code "with-units"] ", and render to PNG. The pixel dimensions follow from the paper size and the DPI on the scene:"]
     [:pre [:code
            ";; A3 at 600 DPI — with-units expands to ~7016 x 9933 px
(-> (scene/paper :a3 :dpi 600)
    (assoc :image/nodes [,,,your artwork,,,])
    scene/with-units
    (eido/render {:output \"print.png\"}))"]]
     [:p "Pick the DPI your printer wants on the paper preset before converting units — higher DPI means more pixels and a larger file."]]}])

(defn- workflow-animation-sections []
  [{:id "basics"
    :title "Frames as Sequences"
    :content
    [:div
     [:p "An animation is a sequence of scenes — one map per frame. No timeline, no keyframes, no mutation:"]
     [:pre {:data-img "docs-wf-animation-basics.gif"} [:code
            "(require '[eido.animate :as anim])

(def frames
  (anim/frames 60
    (fn [t]   ;; t goes from 0.0 to 1.0
      {:image/size [400 400]
       :image/background [:color/rgb 30 30 40]
       :image/nodes
       [{:node/type     :shape/circle
         :circle/center [200 200]
         :circle/radius (* 150 t)
         :style/fill    [:color/hsl (* 360 t) 0.8 0.5]}]})))"]]
     [:p "60 frames = 60 maps in a vector. Each frame is an independent scene — no shared mutable state between frames."]]}

   {:id "easing"
    :title "Easing and Helpers"
    :content
    [:div
     [:p "The animation helpers shape how values change over time:"]
     [:pre [:code
            ";; Sine pulse — 0→1→0 over the cycle
(anim/pulse t)

;; Linear fade — 1→0
(anim/fade-linear t)

;; Quadratic fade — softer tail
(anim/fade-out t)

;; Quadratic ease-in
(anim/fade-in t)"]]
     [:p "Combine these with your own math. Since " [:code "t"] " is just a number, any function works."]]}

   {:id "gif-export"
    :title "GIF Export"
    :content
    [:div
     [:pre [:code
            ";; Animated GIF (:fps default 12)
(eido/render frames {:output \"anim.gif\" :fps 30})"]]
     [:p "A sequence of frames renders to an animated GIF. Render the same frames without "
      [:code ":output"] " to get the encoded GIF bytes back as a value."]]}

   {:id "looping"
    :title "Seamless Loops"
    :content
    [:div
     [:p "Use 4D noise for seamless looping — sample a circle in the noise space:"]
     [:pre [:code
            "(require '[eido.gen.noise :as noise])

;; Seamless 1-second loop at 30fps
(anim/frames 30
  (fn [t]
    (let [angle (* 2 Math/PI t)
          nx    (Math/cos angle)
          ny    (Math/sin angle)]
      ;; perlin4d produces values that loop seamlessly
      ;; as (nx, ny) trace a circle
      (make-scene (noise/perlin4d x y nx ny)))))"]]
     [:p [:code "perlin4d"] " is specifically designed for this — the cos/sin trick traces a circle in the 3rd and 4th dimensions, producing a seamless loop in the first two."]]}])

(defn- workflow-3d-sections []
  [{:id "meshes"
    :title "Building Meshes"
    :content
    [:div
     [:p "3D scenes start with mesh primitives — pure data maps describing vertices and faces:"]
     [:pre {:data-img "docs-wf-3d-primitives.png"} [:code
            "(require '[eido.scene3d :as s3d])

;; Primitive constructors
(s3d/sphere-mesh 1.0)
(s3d/sphere-mesh 1.0 {:segments 32 :rings 20})
(s3d/cube-mesh [0 0 0] 1.0)
(s3d/cylinder-mesh 0.5 2.0)
(s3d/torus-mesh 1.5 0.5)
(s3d/plane-mesh 2.0 2.0)

;; Platonic solids
(s3d/icosahedron-mesh 1.0)
(s3d/dodecahedron-mesh 1.0)"]]
     [:p "Every mesh is a map with " [:code ":vertices"] " and " [:code ":faces"] ". You can inspect, transform, and combine them as data."]]}

   {:id "transforms"
    :title "Mesh Transforms"
    :content
    [:div
     [:p "All transforms are pure functions — mesh in, mesh out:"]
     [:pre {:data-img "docs-wf-3d-transforms.png"} [:code
            "(-> (s3d/sphere-mesh 1.0)
    (s3d/translate [2 0 0])
    (s3d/rotate-y 0.5)
    (s3d/scale [1 2 1])
    (s3d/subdivide-mesh)
    (s3d/deform (fn [v] (update v 1 + (* 0.2 (noise/perlin3d (v 0) (v 1) (v 2)))))))"]]
     [:p "Chain transforms with " [:code "->"] ". Deform takes a function from vertex to vertex — use noise for organic shapes."]]}

   {:id "camera"
    :title "Camera and Projection"
    :content
    [:div
     [:pre [:code
            ";; Perspective projection — :scale is pixels-per-unit,
;; :origin is the image center, :distance sets the camera setback.
(def proj (s3d/perspective
            {:scale 120 :origin [400 300] :distance 5}))

;; Orbit around a target — replaces proj's yaw/pitch so the camera
;; looks at the target from the given spherical angle.
(def cam (s3d/orbit proj [0 0 0]
           {:radius 5 :yaw 0.3 :pitch -0.2}))"]]
     [:p "The projection transforms 3D coordinates to 2D screen space. " [:code "orbit"] " positions the camera looking at a target point."]]}

   {:id "rendering"
    :title "Rendering to 2D"
    :content
    [:div
     [:p "3D meshes render into regular 2D scene nodes:"]
     [:pre {:data-img "docs-wf-3d-sphere.png"} [:code
            ";; Render a mesh into 2D nodes
(def nodes (s3d/render-mesh mesh cam
             {:style {:fill :white
                      :stroke {:color :black :width 0.5}}}))

;; Compose into a scene
{:image/size [800 600]
 :image/background [:color/rgb 30 30 40]
 :image/nodes nodes}"]]
     [:p "The output is standard Eido scene nodes — polygons with fills and strokes. Everything downstream (compositing, effects, animation) works normally."]]}

   {:id "textures"
    :title "Procedural Textures"
    :content
    [:div
     [:p "The 2D↔3D bridge lets you use noise, palettes, and fields as surface textures:"]
     [:pre {:data-img "docs-wf-3d-texture.png"} [:code
            ";; UV-mapped noise texture
(s3d/paint-mesh mesh
  (fn [u v face-normal]
    (let [n (noise/fbm u v {:octaves 4 :scale 3.0})]
      [:color/hsl (* 360 n) 0.6 0.5])))"]]
     [:p "The paint function receives UV coordinates and the face normal — use them to drive color, pattern, or density."]]}

   {:id "npr"
    :title "Non-Photorealistic Rendering"
    :content
    [:div
     [:p "Apply 2D hatching and stippling to 3D faces, with density driven by lighting:"]
     [:pre {:data-img "docs-wf-3d-npr.png"} [:code
            ";; Hatch lines whose density follows the light direction
(s3d/render-mesh mesh cam
  {:style :hatch
   :hatch {:angle 45 :spacing 3 :light-dir [1 1 1]}})"]]
     [:p "Lit faces get sparse hatching; shadowed faces get dense hatching. This produces a hand-drawn, woodcut-like look."]]}])

(defn- workflow-color-sections []
  [{:id "color-spaces"
    :title "Perceptual Color"
    :content
    [:div
     [:p "Eido works in OKLAB/OKLCH — perceptually uniform color spaces where mathematical distance matches visual difference:"]
     [:pre [:code
            "(require '[eido.color :as color])

;; Convert and manipulate
(color/rgb->oklch [255 100 50])
(color/oklch 0.7 0.15 30)

;; Perceptual interpolation
(color/lerp-oklab color-a color-b 0.5)

;; Contrast checking (WCAG 2.0)
(color/contrast :white :black)   ;=> 21.0
(color/contrast :white :yellow)  ;=> 1.07"]]
     [:p "OKLCH uses lightness, chroma, and hue — intuitive axes for artistic color decisions."]]}

   {:id "palettes"
    :title "Palette Generation"
    :content
    [:div
     [:pre {:data-img "docs-wf-palette-generation.png"} [:code
            "(require '[eido.color.palette :as palette])

;; Built-in palettes
(:sunset palette/palettes)
(:ocean palette/palettes)

;; Generate from color theory
(palette/complementary base-color)
(palette/analogous base-color 5)
(palette/triadic base-color)

;; Assign semantic roles
(palette/with-roles pal
  {:background 0 :primary 1 :accent 2 :text 3})"]]]}

   {:id "manipulation"
    :title "Palette Manipulation"
    :content
    [:div
     [:p "Transform entire palettes while preserving relationships:"]
     [:pre {:data-img "docs-wf-palette-manipulation.png"} [:code
            ";; Temperature and saturation — rotate hue in degrees,
;; desaturate/saturate in 0–1
(palette/warmer pal 12)
(palette/cooler pal 12)
(palette/muted pal 0.4)
(palette/vivid pal 0.4)

;; Lightness
(palette/darker pal 0.1)
(palette/lighter pal 0.1)

;; General adjustment
(palette/adjust pal {:lightness 0.1 :chroma -0.05})"]]]}

   {:id "analysis"
    :title "Palette Analysis"
    :content
    [:div
     [:p "Check that your palette works before committing to it:"]
     [:pre [:code
            ";; Minimum pairwise contrast (WCAG)
(palette/min-contrast pal)

;; Sort by perceptual lightness
(palette/sort-by-lightness pal)

;; Visual preview at the REPL
(palette/swatch pal)"]]
     [:p [:code "min-contrast"] " returns the lowest WCAG contrast ratio between any two colors in the palette — useful for ensuring text readability or visual separation."]]}

   {:id "extraction"
    :title "Extracting from Images"
    :content
    [:div
     [:p "Pull a palette from an existing image using k-means clustering in OKLAB space:"]
     [:pre {:data-img "docs-wf-color-swatch.png"} [:code
            "(palette/from-image \"reference.jpg\" 5)
;=> [[:color/rgb 42 38 35]
;    [:color/rgb 180 140 90]
;    [:color/rgb 220 200 170]
;    [:color/rgb 80 100 60]
;    [:color/rgb 150 50 40]]"]]
     [:p "The number is how many dominant colors to extract. Clustering in OKLAB produces perceptually balanced palettes."]]}

   {:id "application"
    :title "Applying Color"
    :content
    [:div
     [:p "Use palette and noise together for organic color distribution:"]
     [:pre [:code
            "(require '[eido.gen.vary :as vary])

;; Assign palette colors by index
(vary/by-palette 10 pal {:seed 42})

;; Noise-driven palette mapping
(vary/by-noise-palette positions pal
  {:noise-scale 0.01 :seed 42})

;; Weighted random palette — some colors appear more often
(palette/weighted-pick pal [0.4 0.3 0.2 0.1] {:seed 42})"]]
     [:p "The " [:code "vary"] " functions take seeds for deterministic results — same seed, same color assignment every time."]]}])

(defn- workflow-paint-sections []
  [{:id "paint-basics"
    :title "First Painted Stroke"
    :content
    [:div
     [:p "The paint engine renders brushstrokes as dab sequences onto a raster surface — brushes are data, strokes are data. The simplest form is a "
      [:code ":paint/surface"] " node listing strokes; each is a brush, a color, a radius, and a series of " [:code "[x y]"] " points the dab follows:"]
     [:pre {:data-img "paint-chalk-sketch.png"} [:code
            "{:node/type :paint/surface
 :paint/size [600 400]
 :paint/strokes
 [{:paint/brush  :chalk
   :paint/color  [:color/rgb 80 60 40]
   :paint/radius 12.0
   :paint/points [[50 100] [300 60] [550 100]]}]}"]]
     [:p "The dab is laid down along the points in order."]]}

   {:id "paint-presets"
    :title "Brush Presets"
    :content
    [:div
     [:p "Six presets cover the common media — name one as " [:code ":paint/brush"] ", or override the brush directly:"]
     [:pre [:code
            ";; Use a preset
:paint/brush :chalk   ;; :pencil :ink :marker :watercolor :oil :chalk

;; Or override the brush — tip shape, hardness, flow, blend
:paint/brush {:brush/tip   {:tip/shape :ellipse :tip/hardness 0.9}
              :brush/paint {:paint/flow 0.7 :paint/blend :multiply}}"]]
     [:p "Tip shapes: " [:code ":round"] ", " [:code ":ellipse"] ", " [:code ":chisel"] ", "
      [:code ":rect"] ", " [:code ":line"] ". Blend modes: " [:code ":normal"] ", "
      [:code ":multiply"] ", " [:code ":screen"] ", " [:code ":overlay"] ", "
      [:code ":darken"] ", " [:code ":lighten"] ", " [:code ":add"] "."]]}

   {:id "paint-surfaces"
    :title "Painting Paths"
    :content
    [:div
     [:p "To paint along curves, wrap brush-painted " [:code ":shape/path"] " nodes in a group carrying "
      [:code ":paint/surface"] ". Each path's commands become a stroke, and all of them render onto the same surface — so later strokes lay over earlier ones:"]
     [:pre {:data-img "paint-03-watercolor-wash.png"} [:code
            "{:node/type :group
 :paint/surface {:paint/size [800 400]}
 :group/children
 [{:node/type :shape/path
   :path/commands [[:move-to [20 250]]
                   [:curve-to [200 100] [400 350] [780 200]]]
   :paint/brush :watercolor
   :paint/color [:color/hsl 210 0.45 0.65]
   :paint/radius 45.0}
  ;; Ink detail on top
  {:node/type :shape/path
   :path/commands [[:move-to [120 220]]
                   [:curve-to [300 150] [500 280] [680 180]]]
   :paint/brush :ink
   :paint/color [:color/rgb 25 30 50]
   :paint/radius 2.5}]}"]]
     [:p "Give each painted path a " [:code ":paint/brush"] ", " [:code ":paint/color"] ", and "
      [:code ":paint/radius"] " — and no " [:code ":style/fill"] " or " [:code ":style/stroke"] "."]]}])

(def workflow-pages
  "Registry of all workflow guide pages."
  [{:slug "sketching"  :title "Sketching & Iteration"
   :desc "REPL-driven exploration, watch workflows, seed grids, and rapid prototyping."
   :sections-fn workflow-sketching-sections}
  {:slug "editions"   :title "Long-Form Editions"
   :desc "Deterministic series, seed management, trait distribution, and batch rendering."
   :sections-fn workflow-editions-sections}
  {:slug "print"      :title "Print Production"
   :desc "Paper presets, real-world units, margins, and rendering at print resolution."
   :sections-fn workflow-print-sections}
  {:slug "animation"  :title "Animation & Screen"
   :desc "Frame sequences, GIF export, easing, and seamless loops."
   :sections-fn workflow-animation-sections}
  {:slug "3d"         :title "3D Generative Art"
   :desc "Mesh construction, transforms, camera, textures, and non-photorealistic rendering."
   :sections-fn workflow-3d-sections}
  {:slug "color"      :title "Color & Palette Development"
   :desc "Perceptual color, palette generation, analysis, image extraction, and application."
   :sections-fn workflow-color-sections}
  {:slug "paint"      :title "Paint Engine"
   :desc "Procedural brushwork — paint surfaces, brush presets, and painting along paths."
   :sections-fn workflow-paint-sections}])
