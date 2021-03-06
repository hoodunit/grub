(defproject grub "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "0.0-3308"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.omcljs/om "0.8.8"]
                 [http-kit "2.1.19"]
                 [ring/ring-core "1.3.2" :exclusions [org.clojure/tools.reader]]
                 [hiccup "1.0.5"]
                 [org.clojure/tools.cli "0.3.1"]
                 [sablono "0.3.4"]
                 [cljs-uuid "0.0.4"]
                 [midje "1.8.2"]
                 [com.cognitect/transit-clj "0.8.275"]
                 [com.cognitect/transit-cljs "0.8.220"]
                 [com.datomic/datomic-pro "0.9.5344" :exclusions [com.fasterxml.jackson.core/jackson-annotations
                                                                  org.apache.httpcomponents/httpclient]]
                 [clj-webdriver "0.7.2"]
                 [org.seleniumhq.selenium/selenium-java "2.47.0"]]
  :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo" :creds :gpg}}
  :profiles {:uberjar {:aot :all}
             :dev {:plugins [[lein-midje "3.1.3"]]}}
  :min-lein-version "2.1.2"
  :plugins [[lein-cljsbuild "1.1.1"]]
  :cljsbuild {:builds {:dev  {:source-paths ["src/cljs" "src/cljc"]
                              :compiler     {:output-dir    "resources/public/js/out"
                                             :output-to     "resources/public/js/grub.js"
                                             :optimizations :none
                                             :source-map    true}}
                       :prod {:source-paths ["src/cljs" "src/cljc"]
                              :compiler     {:output-to     "resources/public/js/grub.min.js"
                                             :optimizations :advanced
                                             :pretty-print  false}}}}
  :source-paths ["src/clj" "src/cljc" "target/classes"]
  :test-paths ["src/test"]
  :ring {:handler grub.core/app}
  :uberjar-name "grub-standalone.jar"
  :main grub.core)
