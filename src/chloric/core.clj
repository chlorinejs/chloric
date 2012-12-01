(ns chloric.core
  (:use [chlorine.js]
        [chlorine.util :only [with-timeout]]
        [watchtower.core]
        [clojure.tools.cli :only [cli]]
        [clojure.stacktrace :only [print-stack-trace]]
        [clansi.core])
  (:import [java.util Calendar]
           [java.text SimpleDateFormat])
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
      (println "")
      (print (gen-timestamp))
      (print " ")
      (println (format "Compiling %s..." (style f :underline)))
      (try
        (spit (js-file-of f)
              (with-timeout timeout
                (tojs f)))
        (println (style "Done!" :green))
        (catch Throwable e
          (println (format (str (style "Error: " :red) " compiling %s") f))
          (print-stack-trace e 3))))))

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
  (let [[{:keys [rate timeout profile color pretty-print help]} dirs banner]
        (cli args
             ["-h" "--help" "Show help"]
             ["-u" "--profile"
              "Compile with a specified profile. Can be a file or a pre-defined keyword"
              :default ""]
             ["-r" "--rate" "Rate (in millisecond)" :parse-fn #(Integer. %)
              :default 500]
             ["-pp" "--[no-]pretty-print" "Pretty-print javascript"]
             ["-c" "--[no-]color" "Print with colors"
              :default true]
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
        (with-profile (let [profile (get-profile profile)]
                        (if pretty-print
                          (merge profile {:pretty-print true} )
                          profile))
          (println (str (style "*symbol-map*:   " :magenta)
                        (style *symbol-map* :blue)))
          (println (str (style "*pretty-print*: " :magenta)
                        (style  *print-pretty* :blue)))
          (binding [*use-ansi* color]
            (run rate timeout dirs))))
      (println banner))))
