(defproject grub "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src-clj"]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [http-kit "2.1.8"]
                 [compojure "1.1.5"]
                 [ring/ring-devel "1.2.0"]
                 [ring/ring-core "1.2.0"]
                 [hiccup "1.0.4"]
                 [prismatic/dommy "0.1.1"]
                 [core.async "0.1.0-SNAPSHOT"]]
  :plugins [[lein-cljsbuild "0.3.2"]
            [lein-ring "0.8.6"]]
  :cljsbuild {
    :builds [{
        ; The path to the top-level ClojureScript source directory:
        :source-paths ["src-cljs"]
        ; The standard ClojureScript compiler options:
        ; (See the ClojureScript compiler documentation for details.)
        :compiler {
          :output-to "public/js/main.js"  ; default: target/cljsbuild-main.js
          :optimizations :whitespace
          :pretty-print true}}]}
  :ring {:handler grub.core/app}
  :main grub.core)
