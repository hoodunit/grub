(defproject grub "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2138"]
                 [org.clojure/core.async "0.1.298.0-2a82a1-alpha"]
                 [http-kit "2.1.18"]
                 [compojure "1.1.6"]
                 [ring/ring-core "1.2.2" :exclusions [org.clojure/tools.reader]]
                 [ring/ring-devel "1.2.2" :exclusions [org.clojure/tools.reader]]
                 [hiccup "1.0.5"]
                 [prismatic/dommy "0.1.2"]
                 [com.novemberain/monger "1.7.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [clj-webdriver "0.6.1" :exclusions [org.clojure/core.cache]]]
  :profiles {:dev {:dependencies []}
             :uberjar {:aot :all}}
  :min-lein-version "2.1.2"
  :plugins [[lein-cljsbuild "1.0.0"]
            [lein-ring "0.8.6"]]
  :cljsbuild {:builds {:dev {:source-paths ["src/cljs"]
                             :compiler {:output-dir "out"
                                        :output-to "public/js/grub_dev.js"
                                        ;:source-map "public/js/grub_dev.js.map"
                                        :optimizations :whitespace
                                        :pretty-print true}}
                       :prod {:source-paths ["src/cljs"]
                              :compiler {:output-to "public/js/grub.js"
                                         :optimizations :advanced}}}}
  :source-paths ["src/clj" "integration"]
  :test-paths ["spec/clj"]
  :ring {:handler grub.core/app}
  :uberjar-name "grub-standalone.jar"
  :main grub.core)
