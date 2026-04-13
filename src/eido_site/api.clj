(ns eido-site.api
  "API reference page — reflects over the configured Eido namespaces
  to extract var metadata, then renders the result as a search-able
  HTML page. Stays separate from `eido-site.render` because it's
  the only generator that touches Eido's source at build time."
  (:require
    [clojure.string :as str]
    [eido-site.render :as render]))

;; --- Namespace groups ---

(def api-namespace-groups
  "API namespaces organized by category for sidebar display."
  [{:category "Core"
    :namespaces '[eido.core]}
   {:category "Drawing"
    :namespaces '[eido.path eido.scene eido.text]}
   {:category "Path Operations"
    :namespaces '[eido.path.stroke eido.path.distort eido.path.warp
                  eido.path.morph eido.path.decorate eido.path.aesthetic]}
   {:category "Color"
    :namespaces '[eido.color eido.color.palette]}
   {:category "Generative"
    :namespaces '[eido.gen.noise eido.gen.flow eido.gen.contour
                  eido.gen.scatter eido.gen.voronoi eido.gen.lsystem
                  eido.gen.particle eido.gen.stipple eido.gen.hatch
                  eido.gen.vary eido.gen.prob eido.gen.circle
                  eido.gen.subdivide eido.gen.series eido.gen.ca
                  eido.gen.boids eido.gen.coloring]}
   {:category "Texture"
    :namespaces '[eido.texture]}
   {:category "Paint"
    :namespaces '[eido.paint]}
   {:category "Animation"
    :namespaces '[eido.animate]}
   {:category "3D"
    :namespaces '[eido.scene3d eido.scene3d.camera eido.scene3d.mesh
                  eido.scene3d.transform eido.scene3d.topology
                  eido.scene3d.surface eido.scene3d.modeling
                  eido.scene3d.render]}
   {:category "I/O"
    :namespaces '[eido.io.obj eido.io.polyline]}
   {:category "Math"
    :namespaces '[eido.math]}
   {:category "Visual Computation"
    :namespaces '[eido.ir eido.ir.domain eido.ir.resource
                  eido.ir.fill eido.ir.effect eido.ir.field
                  eido.ir.program eido.ir.transform eido.ir.generator
                  eido.ir.vary eido.ir.material eido.ir.lower]}])

(def eido-namespaces
  "Eido source namespaces for API doc generation."
  (vec (mapcat :namespaces api-namespace-groups)))

;; --- API page ---

(defn api-var-info
  "Extracts API info from a var."
  [v]
  (let [m (meta v)]
    {:name          (str (:name m))
     :arglists      (:arglists m)
     :doc           (:doc m)
     :added         (:added m)
     :convenience?  (:convenience m)
     :wraps         (when-let [s (:convenience-for m)]
                      (name s))
     :stability     (or (:stability m)
                        (:stability (meta (:ns m))))}))

(defn generate-api-html
  "Generates the API reference page from eido namespace metadata."
  []
  (doseq [ns-sym eido-namespaces]
    (require ns-sym))
  (let [ns-data (->> eido-namespaces
                     (mapv (fn [ns-sym]
                             (let [ns-obj  (find-ns ns-sym)
                                   publics (->> (ns-publics ns-obj)
                                                vals
                                                (remove #(:private (meta %)))
                                                (sort-by #(str (:name (meta %))))
                                                (mapv api-var-info))]
                               {:ns-sym  ns-sym
                                :ns-name (str ns-sym)
                                :ns-doc  (:doc (meta ns-obj))
                                :vars    publics})))
                     (remove #(empty? (:vars %))))
        ns-by-name (into {} (map (juxt :ns-name identity) ns-data))]
    (render/html-page {:title "API Reference" :active-page :reference :depth 2}
      [:h1.page-title "API Reference"]
      [:p.page-subtitle "Auto-generated from source metadata. Functions marked "
       [:span.api-var-badge.api-var-badge--provisional "Provisional"]
       " may have API changes in future releases."]
      [:div.api-search
       [:input#api-search {:type "text"
                           :placeholder "Search functions, namespaces, or keywords..."
                           :autocomplete "off"
                           :oninput "filterAPI(this.value)"}]]
      [:div.api-layout
       [:nav.api-sidebar
        (for [{:keys [category namespaces]} api-namespace-groups]
          (let [group-ns (keep #(ns-by-name (str %)) namespaces)]
            (when (seq group-ns)
              [:div.api-sidebar-category
               [:div.api-sidebar-category-title category]
               [:ul
                (for [{:keys [ns-name]} group-ns]
                  [:li [:a {:href (str "#" ns-name)} ns-name]])]])))]
       [:div
        (for [{:keys [namespaces]} api-namespace-groups
              :let [group-ns (keep #(ns-by-name (str %)) namespaces)]
              :when (seq group-ns)
              {:keys [ns-name ns-doc vars]} group-ns]
          [:section.api-ns {:id ns-name}
           [:h2.api-ns-title ns-name]
           (when ns-doc
             [:p.api-ns-doc ns-doc])
           (for [{:keys [name arglists doc convenience? wraps stability]} vars]
             [:div.api-var
              ;; Badges in top-right corner
              (when (or convenience? (= :provisional stability))
                [:div.api-var-meta
                 (when convenience?
                   [:span.api-var-badge "Helper"])
                 (when (= :provisional stability)
                   [:span.api-var-badge.api-var-badge--provisional
                    "Provisional"])
                 (when wraps
                   [:div.api-var-wraps "Wraps " [:code wraps]])])
              ;; Signature block — one line per arity
              [:div.api-var-sig
               (if (seq arglists)
                 (for [arglist arglists]
                   [:div.api-var-arity
                    [:code "(" [:span.api-var-name name]
                     (when (seq arglist)
                       [:span.api-var-args
                        " " (str/join " " (map str arglist))])
                     ")"]])
                 [:div.api-var-arity
                  [:code [:span.api-var-name name]]])]
              ;; Docstring with code formatting
              (when doc
                [:div.api-var-doc
                 {:innerHTML
                  (-> (str/replace doc #"`([^`]+)`" "<code>$1</code>")
                      (str/replace #":[\w/\-\.]+" "<code>$0</code>")
                      (str/replace #"\n" "<br>"))}])])])]]
      [:script {:innerHTML (str render/highlight-clj-js "
document.querySelectorAll('.api-var-sig code').forEach(function(el) {
  el.innerHTML = highlightClj(el.textContent);
});

function filterAPI(query) {
  var q = query.toLowerCase().trim();
  document.querySelectorAll('.api-var').forEach(function(card) {
    if (!q) { card.style.display = ''; return; }
    var text = card.textContent.toLowerCase();
    card.style.display = text.indexOf(q) >= 0 ? '' : 'none';
  });
  document.querySelectorAll('.api-ns').forEach(function(sec) {
    if (!q) { sec.style.display = ''; return; }
    var visible = sec.querySelectorAll('.api-var:not([style*=\"display: none\"])').length;
    var nsText = sec.querySelector('.api-ns-title').textContent.toLowerCase();
    sec.style.display = (visible > 0 || nsText.indexOf(q) >= 0) ? '' : 'none';
  });
}
")}])))

