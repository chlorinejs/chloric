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

(defn js-file-for
  "Generate js file name from cl2 file name."
  [cl2-file & regexps]
  (clojure.string/replace cl2-file #".cl2$" ".js"))

(defn compile-cl2
  "Compiles a list of .cl2 files"
  [timeout targets & _]
  (doseq [file targets]
    (let [f (.getAbsolutePath (clojure.java.io/file file))]
      (println "")
      (print (gen-timestamp))
      (print " ")
      (println (format "Compiling %s..." (style f :underline)))
      (try
        (spit (js-file-for f)
              (with-timeout timeout
                (dosync (ref-set *macros* {}))
                (tojs f)))
        (println (style "Done!" :green))
        (catch Throwable e
          (println (format (str (style "Error: " :red) " compiling %s") f))
          (print-stack-trace e
                             (if *verbose* 10 3)))))))

(defn delete-js
  "Delete .js files when their .cl2 source files are deleted."
  [cl2-files]
  (doseq [f cl2-files]
    (.delete (clojure.java.io/file (js-file-for f)))))

(defn run
  "Start the main watcher."
  [rate-ms timeout-ms watch ignore targets]
  (watcher (if watch
             (clojure.string/split watch #",")
             [(.getAbsolutePath (clojure.java.io/file ""))])
           (rate rate-ms)
           (file-filter ignore-dotfiles)
           (file-filter (fn [f] (not
                                 (contains?
                                  (apply set
                                         (clojure.string/split
                                          (or ignore "") #","))
                                  f))))
           (file-filter (extensions :cl2))
           (notify-on-start? true)
           (on-modify    (partial compile-cl2 timeout-ms targets))
           (on-delete    delete-js)
           (on-add       (partial compile-cl2 timeout-ms targets))))

(defn -main [& args]
  (let [[{:keys [watch ignore rate timeout profile once
                 color pretty-print
                 verbose help]}
         targets banner]
        (cli args
             ["-h" "--help" "Show help"]
             ["-u" "--profile"
              "Compile with a specified profile. Can be a file or a pre-defined keyword"
              :default ""]
             ["-w" "--watch"
              "A comma-delimited list of dirs or cl2 files to watch for changes.
 When a change to a cl2 file occurs, re-compile target files"]
             ["-1" "--[no-]once"
              "Don't watch, just compile once" :default nil]
             ["-i" "--ignore"
              "A comma-delimited list of folders to ignore for changes."
              :default nil]
             ["-r" "--rate" "Rate (in millisecond)" :parse-fn #(Integer. %)
              :default 500]
             ["-pp" "--[no-]pretty-print" "Pretty-print javascript"]
             ["-c" "--[no-]color" "Print with colors"
              :default true]
             ["-t" "--timeout" "Timeout (in millisecond)"
              :parse-fn #(Integer. %)
              :default 5000]
             ["-v" "--[no-]verbose" "Verbose mode"]
             )]
    (when help
      (println banner)
      (System/exit 0))
    (if (not= [] targets)
      (do
        (println "")
        (binding [*use-ansi* color
                  *verbose*  verbose]
          (with-profile (let [profile (get-profile profile)]
                          (if pretty-print
                            (merge profile {:pretty-print true} )
                            profile))
            (if *verbose*
              (do
                (println (str (style "*symbol-map*:   " :magenta)
                              (style *symbol-map* :blue)))
                (println (str (style "*pretty-print*: " :magenta)
                              (style  *print-pretty* :blue)))
                (println (str "Watching: " (pr-str watch)))
                (println (str "Ignoring: " (pr-str ignore)))
                (println (str "Targets:  " (pr-str targets)))
                (println (str "Once?:    " (pr-str once)))))

            (if once
              (compile-cl2 timeout targets)
              (run rate timeout watch ignore targets)))))
      (println banner))))
