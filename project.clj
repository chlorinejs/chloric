(defproject chloric "0.1.16-SNAPSHOT"
  :description "Clojure/Chlorine command-line watcher/compiler"
  :url "http://github.com/chlorinejs/chloric"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojars.zcaudate/watchtower "0.1.2"]
                 [org.clojure/tools.cli "0.2.2"]
                 [myguidingstar/clansi "1.3.0"]
                 [chlorine "1.6.4-SNAPSHOT"]
                 [core-cl2 "0.9.0-SNAPSHOT"]]
  :bin {:name "chloric"
        :bin-path "~/bin"
        :bootclasspath true}
  :main chloric.core)
