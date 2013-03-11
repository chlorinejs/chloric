(defproject chloric "0.1.5-SNAPSHOT"
  :description "Clojure/Chlorine command-line watcher/compiler"
  :url "http://github.com/myguidingstar/chloric"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojars.zcaudate/watchtower "0.1.2"]
                 [org.clojure/tools.cli "0.2.2"]
                 [myguidingstar/clansi "1.3.0"]
                 [chlorine "1.5.0"]
                 [core-cl2 "1.0.0-SNAPSHOT"]]
  :main chloric.core)
