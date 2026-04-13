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
      [:li "Render a " [:strong "batch"] " with metadata and optional manifests"]
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
     [:p "The scene function takes sampled parameters and an edition number, and returns a scene map:"]
     [:pre [:code
            "(defn make-scene [params edition]
  {:image/size [800 800]
   :image/background [:color/hsl (:hue params) 0.1 0.95]
   :image/nodes
   [(build-artwork params)]})"]]
     [:p "This is where your creative algorithm lives. The parameters control variation; the algorithm defines the visual language."]]}

   {:id "batch-render"
    :title "Batch Rendering"
    :content
    [:div
     [:pre [:code
            "(require '[eido.gen.series :as series])

(series/render-editions
  {:spec        spec
   :master-seed 42
   :start       0
   :end         50
   :scene-fn    make-scene
   :output-dir  \"editions/\"
   :format      :png
   :render-opts {:scale 2}
   :traits      {:density [[15 \"sparse\"] [25 \"medium\"] [100 \"dense\"]]}
   :emit-manifest? true})"]]
     [:p "This renders 50 editions, writing:"]
     [:ul
      [:li [:code "editions/edition-0.png"] " through " [:code "edition-49.png"]]
      [:li [:code "editions/metadata.edn"] " — parameters and traits for every edition"]
      [:li [:code "editions/edition-0.edn"] " through " [:code "edition-49.edn"] " — per-edition manifests (with " [:code ":emit-manifest? true"] ")"]]
     [:p "Each manifest contains the full scene map, seed, parameters, and Eido version — everything needed to reproduce that exact output."]]}

   {:id "complete-package"
    :title "Complete Package"
    :content
    [:div
     [:p "For a full archival package — images, manifests, and a contact sheet in one call — use " [:code "export-edition-package"] ":"]
     [:pre {:data-img "docs-wf-editions-contact.png"} [:code
            "(series/export-edition-package
  {:spec        spec
   :master-seed 42
   :start       0
   :end         50
   :scene-fn    make-scene
   :output-dir  \"editions/\"
   :contact-cols 10
   :thumb-size  [160 160]})"]]
     [:p "This produces everything " [:code "render-editions"] " does (with manifests enabled), plus a " [:code "contact-sheet.png"] " — a grid of thumbnails useful for proofing, exhibition planning, and social media."]]}

   {:id "traits"
    :title "Trait Analysis"
    :content
    [:div
     [:p "Traits categorize continuous parameters into named labels for metadata and marketplace display:"]
     [:pre [:code
            "(series/trait-summary
  {:spec spec :master-seed 42
   :start 0 :end 100
   :traits {:density [[15 \"sparse\"] [25 \"medium\"] [100 \"dense\"]]
            :palette identity}})"]]
     [:p "Returns frequency counts — how many editions fall into each trait bucket. Useful for verifying that your parameter spec produces a good distribution."]]}

   {:id "reproducibility"
    :title "Reproducibility"
    :content
    [:div
     [:p "Every render can produce a manifest — a machine-readable EDN sidecar file:"]
     [:pre [:code
            ";; Re-render from a manifest
(eido/render-from-manifest \"editions/edition-42.edn\")

;; Or re-render to a different format
(eido/render-from-manifest \"editions/edition-42.edn\"
  {:output \"edition-42-print.tiff\" :dpi 300})"]]
     [:p "The manifest stores the complete scene map — the same data you'd pass to " [:code "render"] ". This is the primary reproduction mechanism. The Eido version is recorded for diagnostics, but the scene map alone is usually sufficient."]]}])

(defn- workflow-plotter-sections []
  [{:id "stroke-only"
    :title "Stroke-Only SVG"
    :content
    [:div
     [:p "Plotters draw lines, not fills. Use " [:code ":stroke-only true"] " to strip fills and backgrounds:"]
     [:pre [:code
            "(eido/render scene {:output \"plotter.svg\"
                        :stroke-only true})"]]
     [:p "This removes all " [:code ":style/fill"] " values and the " [:code ":image/background"] ", leaving only stroked paths."]]}

   {:id "pen-grouping"
    :title "Grouping by Pen"
    :content
    [:div
     [:p "Multi-pen plotters need paths grouped by stroke color. Use " [:code ":group-by-stroke"] ":"]
     [:pre [:code
            "(eido/render scene {:output \"plotter.svg\"
                        :stroke-only     true
                        :group-by-stroke true})"]]
     [:p "Each stroke color gets its own " [:code "<g>"] " element with an id like " [:code "pen-rgb-0-0-0"] ". Load the SVG in your plotter software and assign each group to a physical pen."]]}

   {:id "travel-optimization"
    :title "Travel Optimization"
    :content
    [:div
     [:p "Minimize pen-up travel distance with " [:code ":optimize-travel"] ":"]
     [:pre {:data-img "docs-wf-plotter-strokes.png"} [:code
            "(eido/render scene {:output \"plotter.svg\"
                        :stroke-only     true
                        :group-by-stroke true
                        :deduplicate     true
                        :optimize-travel true})"]]
     [:p [:code ":deduplicate"] " removes identical overlapping paths. " [:code ":optimize-travel"] " reorders drawing operations using greedy nearest-neighbor, which can significantly reduce total plot time."]]}

   {:id "path-aesthetics"
    :title "Path Aesthetics"
    :content
    [:div
     [:p "Plotter output benefits from path-level treatment — smoothing, jitter, and dashing:"]
     [:pre [:code
            "(require '[eido.path.aesthetic :as aes])

;; Smooth jagged paths
(aes/smooth-commands cmds {:tension 0.4})

;; Add organic wobble
(aes/jittered-commands cmds {:amount 1.5 :seed 42})

;; Break into dashes
(aes/dash-commands cmds {:dash [10 5]})

;; Chain transforms with stylize
(aes/stylize cmds
  [[:smooth {:tension 0.3}]
   [:jitter {:amount 1.0 :seed 42}]])"]]
     [:p "These transforms work on path commands, not scene nodes — apply them before building the final scene."]]}

   {:id "per-layer"
    :title "Per-Layer Export"
    :content
    [:div
     [:p "For multi-pen plotters, export one SVG file per stroke color with " [:code "export-layers"] ":"]
     [:pre [:code
            "(require '[eido.io.plotter :as plotter])

(plotter/export-layers scene \"output/plotter/\"
  {:optimize-travel true})
;; => [{:pen \"pen-rgb-255-0-0-\" :file \"output/plotter/pen-rgb-255-0-0-.svg\"}
;;     {:pen \"pen-rgb-0-0-255-\" :file \"output/plotter/pen-rgb-0-0-255-.svg\"}]
;; Also writes output/plotter/preview.svg with all layers"]]
     [:p "Each layer SVG is stroke-only with deduplicated, travel-optimized paths. Load each file in your plotter software and assign to a physical pen. Disable the preview with " [:code "{:preview false}"] "."]]}

   {:id "polylines"
    :title "Beyond Plotters: Polyline Export"
    :content
    [:div
     [:p "For CNC mills, laser cutters, and custom plotter software, export raw coordinate data:"]
     [:pre [:code
            "(eido/render scene {:format :polylines})
;=> {:polylines [[[x1 y1] [x2 y2] ...] ...]
;    :bounds [800 600]}

;; Or write to file
(eido/render scene {:format :polylines
                    :output \"paths.edn\"
                    :flatness 0.5
                    :segments 64})"]]
     [:p "All geometry is converted to polylines: curves are flattened via de Casteljau subdivision, circles and ellipses are approximated as polygons."]]}])

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
    (eido/render {:output \"print.tiff\"}))"]]
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
    (eido/render {:output \"print.tiff\"}))"]]
     [:p "The margin clips artwork to the inset rectangle, giving you a clean border. Apply it before " [:code "with-units"] "."]]}

   {:id "export-formats"
    :title "Print-Ready Export"
    :content
    [:div
     [:p "For archival output, use TIFF with DPI metadata:"]
     [:pre [:code
            ";; TIFF with embedded DPI — the standard for print shops
(eido/render scene {:output \"print.tiff\" :dpi 300})

;; High-DPI PNG
(eido/render scene {:output \"print.png\" :dpi 300})

;; Or set DPI on the scene and it propagates automatically
(eido/render (assoc scene :image/dpi 300)
  {:output \"print.tiff\"})"]]
     [:p "TIFF supports LZW compression (default), deflate, or none. DPI metadata is embedded in the file header — print software reads it automatically."]]}

   {:id "manifest"
    :title "Archival Manifests"
    :content
    [:div
     [:p "For print editions, attach a manifest to every render:"]
     [:pre [:code
            "(eido/render scene {:output \"edition-01.tiff\"
                        :dpi 300
                        :emit-manifest? true
                        :seed 42
                        :params params})"]]
     [:p "The manifest records the full scene, parameters, Eido version, and timestamp — everything needed to reproduce the exact print years later."]]}])

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
            ";; Animated GIF — loops by default
(eido/render frames {:output \"anim.gif\" :fps 30})

;; Non-looping GIF
(eido/render frames {:output \"anim.gif\" :fps 30 :loop false})

;; Higher resolution
(eido/render frames {:output \"anim.gif\" :fps 30 :scale 2})"]]]}

   {:id "svg-animation"
    :title "Animated SVG"
    :content
    [:div
     [:pre [:code
            ";; Animated SVG using SMIL
(eido/render frames {:output \"anim.svg\" :fps 30})

;; Or get the SVG string directly
(eido/render frames {:format :svg :fps 30})"]]
     [:p "Animated SVGs are resolution-independent and work in modern browsers."]]}

   {:id "png-sequence"
    :title "PNG Sequence"
    :content
    [:div
     [:p "For video production, render a numbered PNG sequence and encode with ffmpeg:"]
     [:pre [:code
            ";; Write frame-0000.png through frame-0059.png
(eido/render frames {:output \"frames/\" :fps 30})

;; Then encode:
;; ffmpeg -framerate 30 -i frames/frame-%04d.png -c:v libx264 -pix_fmt yuv420p out.mp4"]]]}

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
     [:p "The output is standard Eido scene nodes — polygons with fills and strokes. Everything downstream (export, plotter output, animation) works normally."]]}

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
     [:p "Lit faces get sparse hatching; shadowed faces get dense hatching. This produces a hand-drawn look that works especially well for plotter output."]]}])

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
     [:p "The paint engine renders brushstrokes as dab sequences onto a tiled raster surface. Everything is procedural — no bitmap textures."]
     [:pre {:data-img "paint-02-ink-calligraphy.png"} [:code
            "(require '[eido.core :as eido])

;; A single painted path with pressure
{:image/size [800 400]
 :image/background [:color/rgb 252 250 242]
 :image/nodes
 [{:node/type :shape/path
   :path/commands [[:move-to [60 200]]
                   [:curve-to [200 80] [350 320] [740 160]]]
   :paint/brush :ink
   :paint/color [:color/rgb 15 10 5]
   :paint/radius 9.0
   :paint/pressure [[0.0 0.1] [0.3 0.9] [0.7 0.6] [1.0 0.05]]}]}"]]
     [:p "Add " [:code ":paint/brush"] " to any path and it becomes a painted stroke. "
      "The " [:code ":paint/pressure"] " curve maps stroke parameter t (0 = start, 1 = end) to pressure, which scales radius and opacity."]]}

   {:id "paint-presets"
    :title "Brush Presets"
    :content
    [:div
     [:p "Built-in presets cover common media. Each is a full brush spec you can override:"]
     [:pre [:code
            ";; Use a preset directly
:paint/brush :chalk

;; Override specific parameters
:paint/brush {:brush/type :brush/dab
              :brush/tip {:tip/shape :ellipse
                          :tip/hardness 0.5
                          :tip/aspect 2.0}
              :brush/grain {:grain/type :fiber
                            :grain/scale 0.06
                            :grain/contrast 0.5}
              :brush/paint {:paint/opacity 0.12
                            :paint/spacing 0.04}}"]]
     [:p "Available presets: " [:code ":pencil"] ", " [:code ":marker"] ", " [:code ":airbrush"]
      ", " [:code ":chalk"] ", " [:code ":ink"] ", " [:code ":oil"] ", "
      [:code ":watercolor"] ", " [:code ":pastel"] "."]]}

   {:id "paint-surfaces"
    :title "Shared Surfaces"
    :content
    [:div
     [:p "For multiple strokes on one canvas (e.g. layered watercolor), use a group with "
      [:code ":paint/surface"] ":"]
     [:pre {:data-img "paint-03-watercolor-wash.png"} [:code
            "{:node/type :group
 :paint/surface {:substrate/tooth 0.3}
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
     [:p "Or use the standalone " [:code ":paint/surface"] " node with explicit point data for full control over pressure, speed, and tilt per point."]]}

   {:id "paint-generators"
    :title "Composing with Generators"
    :content
    [:div
     [:p "Paint parameters flow through generators. Put a flow field inside a paint group and every streamline becomes a painted stroke:"]
     [:pre {:data-img "paint-04-flow-ink.png"} [:code
            "{:node/type :group
 :paint/surface {:paint/size [700 700]}
 :group/children
 [{:node/type :flow-field
   :flow/bounds [40 40 620 620]
   :flow/opts {:density 18 :steps 50
               :noise-scale 0.005 :seed 33}
   :paint/brush :ink
   :paint/color [:color/rgb 20 15 10]
   :paint/radius 1.8}]}"]]
     [:p "This works with any generator: " [:code ":scatter"] ", " [:code ":symmetry"] ", " [:code ":flow-field"] ", " [:code ":path/decorated"] ". "
      "The paint parameters propagate from the generator node to all its generated paths."]]}

   {:id "paint-texture"
    :title "Grain and Substrate"
    :content
    [:div
     [:p "Grain textures modulate deposition inside the brush tip. Substrate describes the paper/canvas surface. Both are procedural:"]
     [:pre {:data-img "paint-05-pastel-landscape.png"} [:code
            ";; Grain: breaks up the stroke with texture
:brush/grain {:grain/type :fiber    ;; :fbm :ridge :weave :canvas
              :grain/scale 0.06
              :grain/contrast 0.5
              :grain/stretch 4.0}

;; Substrate: paper tooth blocks paint in valleys
:paint/surface {:substrate/tooth 0.4
                :substrate/scale 0.1}"]]
     [:p "Available grain types: " [:code ":fbm"] " (general), " [:code ":fiber"]
      " (directional), " [:code ":weave"] " (canvas), " [:code ":ridge"]
      " (sharp), " [:code ":turbulence"] " (billowy), " [:code ":canvas"]
      " (weave + fine noise)."]]}

   {:id "paint-bristles"
    :title "Bristle Brushes"
    :content
    [:div
     [:p "Add " [:code ":brush/bristles"] " to create multi-tip brushes that show individual hair marks:"]
     [:pre {:data-img "paint-06-bristle-flat.png"} [:code
            ":brush/bristles {:bristle/count 9
                 :bristle/spread 1.0
                 :bristle/shear 0.15}"]]
     [:p "Bristles are arranged perpendicular to the stroke direction. "
      [:code ":bristle/spread"] " controls width, "
      [:code ":bristle/shear"] " adds a fan effect. "
      "Each bristle gets subtle opacity and size variation for a natural look."]]}])

(def workflow-pages
  "Registry of all workflow guide pages."
  [{:slug "sketching"  :title "Sketching & Iteration"
   :desc "REPL-driven exploration, watch workflows, seed grids, and rapid prototyping."
   :sections-fn workflow-sketching-sections}
  {:slug "editions"   :title "Long-Form Editions"
   :desc "Deterministic series, seed management, trait distribution, and batch rendering."
   :sections-fn workflow-editions-sections}
  {:slug "plotter"    :title "Plotter Art"
   :desc "Stroke-only SVG, pen grouping, travel optimization, and polyline export."
   :sections-fn workflow-plotter-sections}
  {:slug "print"      :title "Print Production"
   :desc "Paper presets, real-world units, margins, DPI, and archival TIFF output."
   :sections-fn workflow-print-sections}
  {:slug "animation"  :title "Animation & Screen"
   :desc "Frame sequences, GIF export, animated SVG, easing, and seamless loops."
   :sections-fn workflow-animation-sections}
  {:slug "3d"         :title "3D Generative Art"
   :desc "Mesh construction, transforms, camera, textures, and non-photorealistic rendering."
   :sections-fn workflow-3d-sections}
  {:slug "color"      :title "Color & Palette Development"
   :desc "Perceptual color, palette generation, analysis, image extraction, and application."
   :sections-fn workflow-color-sections}
  {:slug "paint"      :title "Paint Engine"
   :desc "Procedural brushwork, dab rendering, grain textures, bristle brushes, and generator composition."
   :sections-fn workflow-paint-sections}])
