(ns chloric.core
  (:use [chlorine.js]
        [chlorine.util :only [replace-map with-timeout timestamp]]
        [watchtower.core :only [watcher on-modify on-delete on-add
                                notify-on-start? file-filter rate
                                ignore-dotfiles extensions]]
        [clojure.tools.cli :only [cli]]
        [clojure.stacktrace :only [print-cause-trace]]
        [clansi.core :only [*use-ansi* style]])
  (:gen-class :main true))

(def ^:dynamic *verbose* false)
(def ^:dynamic *path-map* {#".cl2$" ".js"})
(def ^:dynamic *inclusion* nil)
(def ^:dynamic *timestamp* false)
(def profiles {})

(defn gen-state
  "Compiles a pre-defined Chlorine environment, returns that state"
  [resource-name]
  (binding [*temp-sym-count* (ref 999)
            *last-sexpr*     (ref nil)
            *macros*         (ref {})]
    (let [inclusion (eval `(js (include! [:resource
                                         ~(str resource-name ".cl2")])))]
      {:temp-sym-count @*temp-sym-count*
       :macros @*macros*
       :inclusion inclusion})))

(def ^{:doc "Pre-compiles Chlorine environments once
and saves states to this var."}
  precomplied-states
  {"dev"  (gen-state "dev")
   "prod" (gen-state "prod")
   "bare" (gen-state "bare")})

(defn compile-with-states
  "Compiles a file using pre-compiled states."
  [f state-name]
  (let [state (get precomplied-states state-name)]
    (binding [*temp-sym-count*  (ref (:temp-sym-count state))
              *last-sexpr*      (ref nil)
              *macros*          (ref (:macros state))]
      (str
       (:inclusion state)
       (tojs' f)))))

(defn js-file-for
  "Generate js file name from cl2 file name."
  [cl2-file path-map]
  (replace-map cl2-file path-map))

(defn compile-cl2
  "Compiles a list of .cl2 files"
  [timeout targets & _]
  (doseq [file targets]
    (let [f (.getAbsolutePath (clojure.java.io/file file))
          js-f (clojure.java.io/file (js-file-for f *path-map*))]
      (println "")
      (print (style (timestamp) :magenta))
      (print " ")
      (println (format "Compiling %s..." (style f :underline)))
      (try
        (when-not (.isDirectory (.getParentFile js-f))
          (.mkdirs (.getParentFile js-f)))
        (spit js-f
              (with-timeout timeout
                (str
                 (when *timestamp*
                   (eval `(js (console.log "Script compiled at: "
                                           ~(timestamp)))))
                 (if *inclusion*
                   (compile-with-states f *inclusion*)
                   (binding [*temp-sym-count* (ref 999)
                             *last-sexpr*     (ref nil)
                             *macros*         (ref {})]
                     (tojs' f))
                   ))))
        (println (style "Done!" :green))
        (catch Throwable e
          (println (format (str (style "Error: " :red) " compiling %s") f))
          (print-cause-trace e
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

(defmacro with-profile
  [m & body]
  `(binding [*print-pretty*     (or (:pretty-print ~m) *print-pretty*)
             *symbol-map*       (or (:symbol-map   ~m) *symbol-map*)
             *reserved-symbols* (or (:reserved-symbols ~m) *reserved-symbols*)]
     ~@body))

(defn -main [& args]
  (let [[options targets banner]
        (cli args
             ["-h" "--help" "Show help"]
             ["-u" "--profile"
              "Compile with a specified profile. Can be a file or a pre-defined keyword"
              :default ""]
             ["-b" "--[no-]import-boot" "Loads Chlorine's bootstrap"]
             ["-B" "--[no-]include-core" "Includes core library"]
             ["-d" "--[no-]include-dev" "Includes development environment"]
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
             ["-p" "--[no-]pretty-print" "Pretty-print javascript"]
             ["-c" "--[no-]color" "Print with colors"
              :default true]
             ["-t" "--timeout" "Timeout (in millisecond)"
              :parse-fn #(Integer. %)
              :default 5000]
             ["-s" "--[no-]timestamp"
              (str "Adds a javascript expression that logs "
                   "timestamps of compiling scripts")]
             ["-v" "--[no-]verbose" "Verbose mode"]
             )
        {:keys [watch ignore rate timeout profile once
                color pretty-print timestamp
                verbose help
                import-boot include-core include-dev]}
        options]
    (when help
      (println banner)
      (System/exit 0))
    (when (< 1 (count (filter true? [import-boot include-core include-dev])))
      (println "You can use only no more than one of three: "
               "import-boot, include-core and include-dev")
      (System/exit 0))
    (if (not= [] targets)
      (do
        (println "")
        (binding [*use-ansi* color
                  *verbose*  verbose
                  *inclusion* (cond
                               import-boot
                               "bare"
                               include-core
                               "prod"
                               include-dev
                               "dev"
                               )
                  *timestamp* timestamp]
          (let [profile (get-profile profile)]
            (with-profile (if pretty-print
                            (merge profile {:pretty-print true})
                            profile)
              (binding [*path-map* (or (:path-map profile) *path-map*)]
                  (if *verbose*
                    (do
                      (println (str (style "*symbol-map*:   " :magenta)
                                    (style *symbol-map* :blue)))
                      (println (str (style "*pretty-print*: " :magenta)
                                    (style  *print-pretty* :blue)))
                      (println (str "Watching: " (pr-str watch)))
                      (println (str "Ignoring: " (pr-str ignore)))
                      (println (str "Targets:  " (pr-str targets)))
                      (println (str "Once?:    " (pr-str once)))
                      (println (str "Path-map: " (pr-str *path-map*)))))
                  (if once
                    (do (compile-cl2 timeout targets)
                        (System/exit 0))
                    (run rate timeout watch ignore targets)))))))
      (println banner))))
