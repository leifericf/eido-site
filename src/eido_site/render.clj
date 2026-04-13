(ns eido-site.render
  "Page templates and HTML generators.

  - html-page is the shared chrome (head, nav, footer) wrapping
    every page.
  - One generate-*-html fn per page surface (landing, gallery,
    manual, design, scope, reference index, workflows index,
    individual workflow pages).
  - insert-doc-previews splices `:data-img` preview images into
    code-block hiccup on the long-form pages.

  Generate-api-html lives in `eido-site.api` since it's tied to
  reflection over the Eido namespaces."
  (:require
    [clojure.string :as str]
    [eido-site.content.landing :as landing]
    [eido-site.content.reference :as reference]
    [eido-site.content.workflows :as workflows]
    [eido-site.scenes :as scenes]
    [eido-site.styles :as styles]
    [replicant.string :as replicant]))

;; --- Configuration ---

(def site-url "https://eido.leifericf.com")

(def highlight-clj-js
  "function highlightClj(code) {
  // HTML-escape first
  code = code.replace(/&/g, '&amp;').replace(/\\x3c/g, '&lt;').replace(/>/g, '&gt;');
  // Extract comments and strings into placeholders so they don't interfere
  var tokens = [];
  code = code.replace(/(;;[^\\n]*)/g, function(m) { tokens.push('\\x3cspan class=\"clj-comment\">' + m + '\\x3c/span>'); return '\\x00T' + (tokens.length-1) + 'T\\x00'; });
  code = code.replace(/(\"(?:[^\"\\\\]|\\\\.)*\")/g, function(m) { tokens.push('\\x3cspan class=\"clj-string\">' + m + '\\x3c/span>'); return '\\x00T' + (tokens.length-1) + 'T\\x00'; });
  // Highlight remaining tokens
  code = code.replace(/(:[a-zA-Z][a-zA-Z0-9_\\-.*+!?\\/<>]*)/g, '\\x3cspan class=\"clj-keyword\">$1\\x3c/span>');
  code = code.replace(/\\b(\\d+\\.?\\d*)\\b/g, '\\x3cspan class=\"clj-number\">$1\\x3c/span>');
  code = code.replace(/(?<=\\()\\b(defn-?|def|let|fn|if|when|cond|do|loop|recur|for|doseq|mapv|map|filter|reduce|into|concat|vec|assoc|merge|require|ns)\\b/g, '\\x3cspan class=\"clj-special\">$1\\x3c/span>');
  // Restore placeholders
  code = code.replace(/\\x00T(\\d+)T\\x00/g, function(_, i) { return tokens[parseInt(i)]; });
  return code;
}")

;; --- Docs preview rendering ---

(defn- insert-doc-previews
  "Walks Hiccup content, inserting preview images after code blocks
  that have a :data-img attribute on their :pre element.

  img-prefix is the relative path from the hosting page to the
  images directory — callers pass `../../images/` from depth-2
  pages like /workflows/<slug>/ and /reference/<slug>/. No default:
  forgetting to specify used to quietly break depth-2 preview
  images, so an explicit value is required."
  [content img-prefix]
  (cond
    (not (vector? content)) content
    (not (keyword? (first content))) (mapv #(insert-doc-previews % img-prefix) content)

    ;; [:pre {:data-img "file.png"} [:code "..."]]
    (and (= :pre (first content))
         (map? (second content))
         (:data-img (second content)))
    (let [img-file (:data-img (second content))
          clean-attrs (dissoc (second content) :data-img)
          clean-pre (if (seq clean-attrs)
                      (into [:pre clean-attrs] (drop 2 content))
                      (into [:pre] (drop 2 content)))]
      [:div.docs-code-example
       clean-pre
       [:img.docs-preview {:src (str img-prefix img-file)
                           :alt "Rendered output"
                           :loading "lazy"}]])

    ;; Recurse into children of Hiccup elements
    :else
    (let [has-attrs? (and (> (count content) 1) (map? (second content)))
          tag (first content)
          attrs (when has-attrs? (second content))
          children (if has-attrs? (drop 2 content) (rest content))]
      (into (if attrs [tag attrs] [tag])
            (map #(insert-doc-previews % img-prefix) children)))))

;; --- HTML generation ---

(defn html-page
  "Wraps content in a full HTML page with nav, footer, and styles.
  :depth controls relative path prefix (0 = root, 1 = one dir deep)."
  [{:keys [title active-page depth] :or {depth 0}} & body]
  (let [prefix (if (zero? depth) "." (str/join "/" (repeat depth "..")))]
    (str
      "<!DOCTYPE html>\n"
      (replicant/render
        [:html {:lang "en"}
         [:head
          [:meta {:charset "utf-8"}]
          [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
          [:title (str title " — Eido")]
          ;; Google Analytics 4 — eido.leifericf.com data stream.
          ;; Hostname-gated so local builds and any future PR-preview
          ;; deploys don't pollute the production stream.
          [:script {:async true
                    :src   "https://www.googletagmanager.com/gtag/js?id=G-LD8F7JFYGB"}]
          [:script {:innerHTML
                    (str "if (location.hostname === 'eido.leifericf.com') {\n"
                         "  window.dataLayer = window.dataLayer || [];\n"
                         "  function gtag(){dataLayer.push(arguments);}\n"
                         "  gtag('js', new Date());\n"
                         "  gtag('config', 'G-LD8F7JFYGB', "
                         "{anonymize_ip: true});\n"
                         "}")}]
          [:style {:innerHTML (styles/site-css)}]]
         [:body
          [:div.container
           [:nav.nav
            [:a.nav-logo {:href (str prefix "/")} "Eido"]
            [:ul.nav-links
             [:li [:a {:href (str prefix "/")
                       :style (when (= active-page :home) "color: #e0ddd5")}
                   "Home"]]
             [:li [:a {:href (str prefix "/gallery/")
                       :style (when (= active-page :gallery) "color: #e0ddd5")}
                   "Gallery"]]
             [:li [:a {:href (str prefix "/workflows/")
                       :style (when (= active-page :workflows) "color: #e0ddd5")}
                   "Workflows"]]
             [:li [:a {:href (str prefix "/reference/")
                       :style (when (= active-page :reference) "color: #e0ddd5")}
                   "Reference"]]
             [:li [:a {:href "https://github.com/leifericf/eido"} "GitHub"]]]]
           [:main body]
           [:footer.footer
            [:p "Eido (from Greek " [:em "eido"] ", \"I see\") — describe what you see as plain data"]]
           ]]]))))

;; --- Landing page ---

(defn generate-landing-html
  "Generates the landing page HTML."
  [examples-by-category]
  (let [all-images (->> examples-by-category
                       (mapcat :examples)
                       (mapv :output))]
    (html-page {:title "Eido" :active-page :home :depth 0}
      [:div.beta-banner
       "Beta — The core API is stabilizing. Breaking changes may still occur between releases, but the fundamentals are in place."]
      [:section.hero
       [:h1.hero-title "Eido"]
       [:p.hero-tagline
        "An end-to-end Clojure toolkit for generative artists — "
        "from REPL sketch to screen, print, and plotter."]
       [:p {:style "color: #6a6a7a; font-size: 0.85rem; margin-top: 0.3rem; font-style: italic;"}
        "From Greek " [:em "eido"] " \u2014 \"I see.\" "
        "A tool for making art, not charts — see "
        [:a {:href "./reference/scope/" :style "color: #8a8a9a"} "Scope"] "."]
       [:div#hero-images.hero-images]
       [:div.hero-links
        [:a.hero-link.hero-link--primary {:href "./gallery/"} "Browse Gallery"]
        [:a.hero-link.hero-link--secondary {:href "./reference/manual/"} "Read the Manual"]
        [:a.hero-link.hero-link--secondary {:href "./reference/design/"} "Design Notes"]]]
      [:section.features
       (for [{:keys [title desc]} (landing/features)]
         [:div.feature
          [:div.feature-marker "\u2022"]
          [:div.feature-body
           [:div.feature-title title]
           [:div.feature-desc desc]]])]
      [:section {:style "margin-top: 3rem"}
       [:h2 {:style "font-size: 1.5rem; margin-bottom: 1rem"} "How it works"]
       (landing/quick-start-content)]
      [:section {:style "margin-top: 2rem"}
       [:h2 {:style "font-size: 1.5rem; margin-bottom: 1rem"} "Getting Started"]
       (landing/install-content)]
      [:script {:innerHTML (str highlight-clj-js "
document.querySelectorAll('pre code').forEach(function(el) {
  el.innerHTML = highlightClj(el.textContent);
});
var allImages = [" (str/join ", " (map #(str "\"" % "\"") all-images)) "];
var shuffled = allImages.sort(function() { return 0.5 - Math.random(); });
var container = document.getElementById('hero-images');
shuffled.slice(0, 6).forEach(function(img) {
  var el = document.createElement('img');
  el.src = './images/' + img;
  el.alt = '';
  el.loading = 'lazy';
  el.style.cursor = 'pointer';
  el.onclick = function() { openLightbox(this.src, this.alt); };
  container.appendChild(el);
});
function openLightbox(src, alt) {
  var lb = document.getElementById('lightbox');
  document.getElementById('lightbox-img').src = src;
  lb.classList.add('active');
  document.body.style.overflow = 'hidden';
}
function closeLightbox() {
  document.getElementById('lightbox').classList.remove('active');
  document.body.style.overflow = '';
}
document.addEventListener('keydown', function(e) { if (e.key === 'Escape') closeLightbox(); });
")}]
      [:div#lightbox {:onclick "closeLightbox()"}
       [:img#lightbox-img]])))

;; --- Gallery page ---

(defn gallery-card
  "Renders a single gallery card with image, title, desc, and collapsible source."
  [example]
  (let [src (scenes/example-source example)
        card-id (str "src-" (hash (:output example)))]
    (let [tags (or (:tags example) [])]
      [:div.gallery-card {:data-tags (str/join "," tags)}
       [:div.gallery-card-img-wrap {:onclick "openLightbox(this.querySelector('img').src, this.querySelector('img').alt)"}
        [:img {:src (str "../images/" (:output example))
               :alt (:title example)
               :loading "lazy"}]
        [:div.gallery-card-expand
         [:svg {:width "18" :height "18" :viewBox "0 0 24 24" :fill "none"
                :stroke "currentColor" :stroke-width "2" :stroke-linecap "round" :stroke-linejoin "round"}
          [:polyline {:points "15 3 21 3 21 9"}]
          [:polyline {:points "9 21 3 21 3 15"}]
          [:line {:x1 "21" :y1 "3" :x2 "14" :y2 "10"}]
          [:line {:x1 "3" :y1 "21" :x2 "10" :y2 "14"}]]]]
       [:div.gallery-card-body
        [:div.gallery-card-title (:title example)]
        [:div.gallery-card-desc (:desc example)]
        (when (seq tags)
          [:div.gallery-card-tags
           (for [tag tags]
             [:span.tag tag])])
        (when src
          [:div
           [:a.view-source {:href "#"
                            :onclick (str "openCodeLightbox('" card-id "'); return false;")}
            "View source"]
           [:pre {:id card-id :style "display:none"} [:code src]]])]])))

(defn generate-gallery-html
  "Generates the gallery page HTML."
  [examples-by-category]
  (let [all-tags (->> examples-by-category
                      (mapcat :examples)
                      (mapcat :tags)
                      (remove nil?)
                      distinct
                      sort
                      vec)]
    (html-page {:title "Gallery" :active-page :gallery :depth 1}
      [:h1.page-title "Gallery"]
      [:p.page-subtitle "Every image on this page was rendered from code."]
      ;; Tag filter bar
      (when (seq all-tags)
        [:div.gallery-filter
         [:span.gallery-filter-label "Filter by feature:"]
         [:button.tag.tag--active {:onclick "filterGallery('all')" :data-tag "all"} "All"]
         (for [tag all-tags]
           [:button.tag {:onclick (str "filterGallery('" tag "')") :data-tag tag} tag])])
      (for [{:keys [category examples]} examples-by-category]
        [:section.gallery-section
         [:h2.gallery-section-title category]
         [:div.gallery-grid
          (for [example examples]
            (gallery-card example))]])
    ;; Image lightbox
    [:div#lightbox {:onclick "closeLightbox()"}
     [:img#lightbox-img]
     [:div#lightbox-caption]]
    ;; Code lightbox
    [:div#code-lightbox {:onclick "closeCodeLightbox()"}
     [:div#code-lightbox-inner {:onclick "event.stopPropagation()"}
      [:div#code-lightbox-header
       [:span#code-lightbox-title]
       [:div {:style "display: flex; gap: 0.75rem; align-items: center;"}
        [:a#copy-btn {:href "#" :onclick "copyCode(); return false;"
                      :title "Copy to clipboard"
                      :style "color: #9090a0; font-size: 0.85rem; text-decoration: none; display: flex; align-items: center; gap: 0.3rem; line-height: 1;"}
         [:svg {:width "14" :height "14" :viewBox "0 0 24 24" :fill "none"
                :stroke "currentColor" :stroke-width "2" :stroke-linecap "round" :stroke-linejoin "round"}
          [:rect {:x "9" :y "9" :width "13" :height "13" :rx "2" :ry "2"}]
          [:path {:d "M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"}]]
         "Copy"]
        [:a {:href "#" :onclick "closeCodeLightbox(); return false;"
             :style "color: #9090a0; font-size: 1.2rem; text-decoration: none; line-height: 1; display: flex; align-items: center;"} "\u00d7"]]]
      [:pre#code-lightbox-pre [:code#code-lightbox-code]]]]
    [:script {:innerHTML (str highlight-clj-js "
function openLightbox(src, alt) {
  var lb = document.getElementById('lightbox');
  document.getElementById('lightbox-img').src = src;
  document.getElementById('lightbox-caption').textContent = alt;
  lb.classList.add('active');
  document.body.style.overflow = 'hidden';
}
function closeLightbox() {
  document.getElementById('lightbox').classList.remove('active');
  document.body.style.overflow = '';
}
function openCodeLightbox(id) {
  var src = document.getElementById(id);
  var code = src.querySelector('code').textContent;
  var title = src.closest('.gallery-card').querySelector('.gallery-card-title').textContent;
  document.getElementById('code-lightbox-code').innerHTML = highlightClj(code);
  document.getElementById('code-lightbox-code').dataset.raw = code;
  document.getElementById('code-lightbox-title').textContent = title;
  document.getElementById('copy-btn').querySelector('span') || null;
  document.getElementById('code-lightbox').classList.add('active');
  document.body.style.overflow = 'hidden';
}
function copyCode() {
  var code = document.getElementById('code-lightbox-code').dataset.raw;
  navigator.clipboard.writeText(code).then(function() {
    var btn = document.getElementById('copy-btn');
    var orig = btn.lastChild.textContent;
    btn.lastChild.textContent = 'Copied!';
    setTimeout(function() { btn.lastChild.textContent = orig; }, 1500);
  });
}
function closeCodeLightbox() {
  document.getElementById('code-lightbox').classList.remove('active');
  document.body.style.overflow = '';
}
document.addEventListener('keydown', function(e) {
  if (e.key === 'Escape') { closeLightbox(); closeCodeLightbox(); }
});
function filterGallery(tag) {
  // Update active button
  document.querySelectorAll('.gallery-filter .tag').forEach(function(btn) {
    btn.classList.toggle('tag--active', btn.dataset.tag === tag);
  });
  // Show/hide cards
  document.querySelectorAll('.gallery-card').forEach(function(card) {
    var tags = card.dataset.tags || '';
    var show = (tag === 'all') || tags.split(',').indexOf(tag) >= 0;
    card.style.display = show ? '' : 'none';
  });
  // Hide empty sections
  document.querySelectorAll('.gallery-section').forEach(function(sec) {
    var visible = sec.querySelectorAll('.gallery-card:not([style*=\"display: none\"])').length;
    sec.style.display = visible > 0 ? '' : 'none';
  });
}
")}])))

;; --- Guide page ---

(defn generate-docs-html
  "Generates the user guide page HTML."
  []
  (let [categories (reference/docs-categories)]
    (html-page {:title "Manual" :active-page :reference :depth 2}
      [:h1.page-title "Guide"]
      [:p.page-subtitle "A hands-on tour of Eido — from first shapes to generative art."]
      [:div.intent-grid
       (for [{:keys [intent links]} (reference/intent-cards)]
         [:div.intent-card
          [:div.intent-label intent]
          [:div.intent-links
           (for [{:keys [label href]} links]
             [:a {:href href} label])]])]
      [:div.docs-layout
       [:nav.docs-sidebar
        (for [{:keys [category id sections]} categories]
          [:div.docs-sidebar-category
           [:div.docs-sidebar-category-title
            [:a {:href (str "#" id)} category]]
           [:ul
            (for [{sec-id :id sec-title :title} sections]
              [:li [:a {:href (str "#" sec-id)} sec-title]])]])]
       [:div.docs-content
        (for [{:keys [category id sections intro]} categories]
          [:div.docs-category {:id id}
           [:h2.docs-category-title category]
           (when intro
             [:div.docs-category-intro
              (insert-doc-previews intro "../../images/")])
           (for [{sec-id :id sec-title :title content :content} sections]
             [:section.docs-section {:id sec-id}
              [:h3 sec-title]
              (insert-doc-previews content "../../images/")])])]
       [:script {:innerHTML (str highlight-clj-js "
document.querySelectorAll('pre code').forEach(function(el) {
  el.innerHTML = highlightClj(el.textContent);
});
")}]])))

;; --- Architecture page ---

(defn generate-architecture-html
  "Generates the 'How Eido Works' architecture page."
  []
  (let [sections (reference/architecture-sections)]
    (html-page {:title "Design notes" :active-page :reference :depth 2}
      [:h1.page-title "How Eido Works"]
      [:p.page-subtitle "From data to pixels — a tour of the rendering pipeline"]
      [:div.arch-layout
       [:nav.arch-sidebar
        (for [{:keys [id title]} sections]
          [:div [:a {:href (str "#" id)} title]])]
       [:div.arch-content
        (for [{:keys [id title content]} sections]
          [:section.arch-section {:id id}
           [:h2 title]
           (insert-doc-previews content "../../images/")])]]
      [:script {:innerHTML (str highlight-clj-js "
document.querySelectorAll('.arch-content pre code').forEach(function(el) {
  el.innerHTML = highlightClj(el.textContent);
});
")}])))

;; --- Scope & Limitations page ---

(defn generate-limitations-html
  "Generates the 'Scope & Limitations' page."
  []
  (let [sections (reference/limitations-sections)]
    (html-page {:title "Scope & Limitations" :active-page :reference :depth 2}
      [:h1.page-title "Scope & Limitations"]
      [:p.page-subtitle "What Eido does, what it doesn't, and why"]
      [:div.arch-layout
       [:nav.arch-sidebar
        (for [{:keys [id title]} sections]
          [:div [:a {:href (str "#" id)} title]])]
       [:div.arch-content
        (for [{:keys [id title content]} sections]
          [:section.arch-section {:id id}
           [:h2 title]
           content])]])))

;; --- Reference index page ---

(def reference-cards
  "Cards shown on the /reference/ landing page."
  [{:slug  "api"
    :title "API"
    :desc  "Auto-generated function reference, grouped by namespace."}
   {:slug  "manual"
    :title "Manual"
    :desc  "A hands-on tour of Eido — from first shapes to generative art."}
   {:slug  "design"
    :title "Design notes"
    :desc  "Why Eido is shaped this way — pipeline and architecture."}
   {:slug  "scope"
    :title "Scope & limitations"
    :desc  "What Eido is, and what it intentionally is not."}])

(defn generate-reference-html
  "Generates the /reference/ landing page — a card grid linking to
  api, manual, design, and scope sub-pages."
  []
  (html-page {:title "Reference" :active-page :reference :depth 1}
    [:h1.page-title "Reference"]
    [:p.page-subtitle "Look-up surface — API, manual, design notes, scope."]
    [:div.workflow-grid
     (for [{:keys [slug title desc]} reference-cards]
       [:a.workflow-card {:href (str slug "/")}
        [:div.workflow-card-title title]
        [:div.workflow-card-desc desc]])]))

;; --- Workflow pages ---

(defn generate-workflows-index-html
  "Generates the workflows landing page with cards for each workflow."
  []
  (html-page {:title "Workflows" :active-page :workflows :depth 1}
    [:h1.page-title "Workflows"]
    [:p.page-subtitle "End-to-end guides for common generative art workflows"]
    [:div.workflow-grid
     (for [{:keys [slug title desc]} workflows/workflow-pages]
       [:a.workflow-card {:href (str slug "/")}
        [:div.workflow-card-title title]
        [:div.workflow-card-desc desc]])]))

(defn generate-workflow-page-html
  "Generates a single workflow page with sidebar navigation."
  [{:keys [title sections-fn]}]
  (let [sections (sections-fn)]
    (html-page {:title title :active-page :workflows :depth 2}
      [:h1.page-title title]
      [:div.arch-layout
       [:nav.arch-sidebar
        (for [{:keys [id title]} sections]
          [:div [:a {:href (str "#" id)} title]])]
       [:div.arch-content
        (for [{:keys [id title content]} sections]
          [:section.arch-section {:id id}
           [:h2 title]
           (insert-doc-previews content "../../images/")])]]
      [:script {:innerHTML (str highlight-clj-js "
document.querySelectorAll('.arch-content pre code').forEach(function(el) {
  el.innerHTML = highlightClj(el.textContent);
});
")}])))
