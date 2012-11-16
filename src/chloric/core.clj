(ns chloric.core
  (:use [chlorine.js :only [tojs]]
        [chlorine.util :only [with-timeout]]
        [watchtower.core]
        [clojure.tools.cli :only [cli]])
  (:gen-class :main true))

(defn js-file-of
  "Generate js file name from cl2 file name."
  [cl2-file]
  (clojure.string/replace cl2-file #".cl2$" ".js"))

(defn compile-cl2
  "Compiles a list of .cl2 files"
  [timeout files]
  (doseq [file files]
    (let [f (.getAbsolutePath file)]
      (println (format "Compiling %s..." f))
      (try
        (with-timeout timeout
          (spit (js-file-of f)
                (tojs f)))
        (catch java.util.concurrent.TimeoutException e
          (println (format "Time-out compiling %s" f))))
      (println "Done!"))))

(defn delete-js
  "Delete .js files when their .cl2 source files are deleted."
  [cl2-files]
  (doseq [f cl2-files]
    (.delete (clojure.java.io/file (js-file-of f)))))

(defn run
  "Start the main watcher."
  [rate-ms timeout-ms dirs]
  (watcher dirs
           (rate rate-ms)
           (file-filter ignore-dotfiles)
           (file-filter (extensions :cl2))
           (notify-on-start? true)
           (on-modify    (partial compile-cl2 timeout-ms))
           (on-delete    delete-js)
           (on-add       (partial compile-cl2 timeout-ms))))

(defn -main [& args]
  (let [[{:keys [rate timeout help]} dirs banner]
        (cli args
             ["-h" "--help" "Show help"]
             ["-r" "--rate" "Rate (in millisecond)" :parse-fn #(Integer. %)
              :default 500]
             ["-t" "--timeout" "Timeout (in millisecond)"
              :parse-fn #(Integer. %)
              :default 5000]
             )]
    (when help
      (println banner)
      (System/exit 0))
    (if (not= [] dirs)
      (do
        (println "")
        (run rate timeout dirs))
      (println banner))))
