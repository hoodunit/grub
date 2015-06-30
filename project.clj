(defproject grub "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
                 [org.clojure/clojure "1.7.0-RC1"]
                 [org.clojure/clojurescript "0.0-3308"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [om "0.7.3"]
                 [http-kit "2.1.19"]
                 [ring/ring-core "1.3.2" :exclusions [org.clojure/tools.reader]]
                 [hiccup "1.0.5"]
                 [prismatic/dommy "1.1.0"]
                 [com.novemberain/monger "2.1.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [sablono "0.3.4"]
                 [cljs-uuid "0.0.4"]
                 [com.cognitect/transit-clj "0.8.275"]
                 [com.cognitect/transit-cljs "0.8.220"]]
  :aliases {"cljx" ["with-profile" "cljx" "cljx"]}
  :min-lein-version "2.1.2"
  :plugins [[lein-cljsbuild "1.0.3"]
            [lein-ring "0.8.6"]
            [com.keminglabs/cljx "0.4.0"]]
  :cljsbuild {:builds {:dev {:source-paths ["src/cljs" "target/generated/cljs"]
                             :compiler {:output-dir "resources/public/js/out"
                                        :output-to "resources/public/js/grub.js"
                                        :optimizations :none
                                        :source-map true}}
                       :prod {:source-paths ["src/cljs" "target/generated/cljs"]
                              :compiler {:output-to "resources/public/js/grub.min.js"
                                         :optimizations :advanced
                                         :pretty-print false
                                         :preamble ["react/react.min.js"]
                                         :externs ["react/externs/react.js"]}}}}
  :cljx {:builds [{:source-paths ["src/cljx"]
                   :output-path "target/classes"
                   :rules :clj}
                  {:source-paths ["src/cljx"]
                   :output-path "target/generated/cljs"
                   :rules :cljs}]}
  :source-paths ["src/clj" "target/classes"]
  :test-paths ["src/test"]
  :ring {:handler grub.core/app}
  :uberjar-name "grub-standalone.jar"
  :main grub.core)
