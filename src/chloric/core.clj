(ns chloric.core
  (:use [chlorine.js :only [tojs]]
        [watchtower.core]
        [clojure.tools.cli :only [cli]])
  (:gen-class :main true))

(defn js-file-of
  "Generate js file name from cl2 file name."
  [cl2-file]
  (clojure.string/replace cl2-file #".cl2$" ".js"))

(defn compile-cl2
  "Compiles a list of .cl2 files"
  [files]
  (doseq [file files]
    (let [f (.getAbsolutePath file)]
      (println (format "Compiling %s..." f))
      (spit (js-file-of f)
            (tojs f)))))

(defn delete-js
  "Delete .js files when their .cl2 source files are deleted."
  [cl2-files]
  (doseq [f cl2-files]
    (.delete (clojure.java.io/file (js-file-of f)))))

(defn run
  "Start the main watcher."
  [rate-ms dirs]
  (watcher dirs
           (rate rate-ms)
           (file-filter ignore-dotfiles)
           (file-filter (extensions :cl2))
           (notify-on-start? true)
           (on-modify    compile-cl2)
           (on-delete    delete-js)
           (on-add       compile-cl2)))

(defn -main [& args]
  (let [[{:keys [rate help]} dirs banner]
        (cli args
             ["-h" "--help" "Show help"]
             ["-r" "--rate" "Rate (in millisecond)" :parse-fn #(Integer. %)
              :default 50]
             )]
    (when help
      (println banner)
      (System/exit 0))
    (if (not= [] dirs)
      (do
        (println "")
        (run rate dirs))
      (println banner))))
