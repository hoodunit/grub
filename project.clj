(defproject grub "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [http-kit "2.1.8"]
                 [compojure "1.1.5"]
                 [ring/ring-devel "1.2.0"]
                 [ring/ring-core "1.2.0"]
                 [hiccup "1.0.4"]
                 [prismatic/dommy "0.1.1"]
                 [org.clojure/core.async "0.1.0-SNAPSHOT"]
                 [com.novemberain/monger "1.5.0"]]
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
  :profiles {:dev {:dependencies [[speclj "2.5.0"]
                                  [specljs "2.7.4"]
                                  [clj-webdriver "0.6.0"]]}}
  :plugins [[lein-cljsbuild "0.3.2"]
            [lein-ring "0.8.6"]
            [speclj "2.5.0"]
            [specljs "2.7.4"]]
  :cljsbuild ~(let [run-specs ["phantomjs" 
                               "bin/specljs_runner.js"
                               "public/js/grub_dev.js"]]
                {:builds {:dev {:source-paths ["src/cljs" "spec/cljs"]
                               :compiler {:output-to "public/js/grub_dev.js"
                                          :optimizations :whitespace
                                          :pretty-print true}
                               :notify-command run-specs}
                         :prod {:source-paths ["src/cljs"]
                                :compiler {:output-to "public/js/grub.js"
                                           :optimizations :simple}}
                         :test-commands {"test" run-specs}}})
  :source-paths ["src/clj" "integration"]
  :test-paths ["spec/clj"]
  :ring {:handler grub.core/app}
  :main grub.core)
