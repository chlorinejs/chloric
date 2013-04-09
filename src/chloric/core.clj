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
    (let [inclusion (eval `(js (include!
                                ~(str "r:/" resource-name ".cl2"))))]
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

(defn bare-compile
  "Compiles a file without using pre-compiled states."
  [file]
  (binding [*temp-sym-count* (ref 999)
            *last-sexpr*     (ref nil)
            *macros*         (ref {})]
    (tojs' file)))

(defn set-terminal-title
  "Sets title of current terminal window."
  [new-title]
  (printf "%s]2;%s%s" (char 27) new-title (char 7)))

(defn compile-cl2-files
  "Compiles a list of .cl2 files"
  [timeout targets & _]
  (let [status
        (->
         (fn [file]
           (let [cl2-input (.getAbsolutePath (clojure.java.io/file file))
                 js-output (clojure.java.io/file
                            (js-file-for cl2-input *path-map*))]
             (print "\n" (style (timestamp) :magenta) " ")
             (println (format "Compiling %s..." (style cl2-input :underline)))
             (try
               (when-not (.isDirectory (.getParentFile js-output))
                 (.mkdirs (.getParentFile js-output)))
               (spit js-output
                     (with-timeout timeout
                       (str
                        (when *timestamp*
                          (eval `(js (console.log "Script compiled at: "
                                                  ~(timestamp)))))
                        (if *inclusion*
                          (compile-with-states cl2-input *inclusion*)
                          (bare-compile cl2-input)))))
               (println (style "Done!" :green))
               :PASSED
               (catch Throwable e
                 (println
                  (format (str (style "Error: " :red) " compiling %s")
                          cl2-input))
                 (print-cause-trace
                  e (if *verbose* 10 3))
                 :FAILED))))
         (map targets))
        total (count status)
        failures (count (filter #(= :FAILED %) status))]
    (if (= 0 failures)
      (set-terminal-title (format "✔ %d files compiled" total))
      (set-terminal-title (format "%d/%d ✘" failures total)))))

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
           (file-filter
            (fn [f] (not
                     (contains?
                      (apply set
                             (clojure.string/split
                              (or ignore "") #","))
                      f))))
           (file-filter  (extensions :cl2))
           (notify-on-start? true)
           (on-modify    (partial compile-cl2-files timeout-ms targets))
           (on-delete    delete-js)
           (on-add       (partial compile-cl2-files timeout-ms targets))))

(defn get-profile [x]
  (if (contains? profiles x)
    (get profiles x)

    (let [f (clojure.java.io/file x)
          m (and (.isFile f)
                 (try (read-string (slurp f))
                      (catch Exception e "invalid file!")))
          m? (map? m)]
      (if m?
        m
        (if (not (nil? x))
          (if *verbose*
            (do
              (println "")
              (println "Profile not found. Using ")
              (println (style "default" :yellow)))))))))

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
              "Compile with a specified profile.
 Can be a file or a pre-defined keyword."
              :default ""]
             ["-b" "--[no-]import-boot" "Loads Chlorine's bootstrap"]
             ["-B" "--[no-]include-core" "Includes core library"]
             ["-d" "--[no-]include-dev" "Includes development environment"]
             ["-w" "--watch"
              "A comma-delimited list of dirs or cl2 files to watch for changes.
 When a change to a cl2 file occurs, re-compile target files."]
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
             ["-v" "--[no-]verbose" "Verbose mode"])
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
      (binding [*use-ansi* color
                *verbose*  verbose
                *inclusion* (cond
                             import-boot  "bare"
                             include-core "prod"
                             include-dev  "dev")
                *timestamp* timestamp]
        (let [profile (get-profile profile)]
          (with-profile (if pretty-print
                          (merge profile {:pretty-print true})
                          profile)
            (binding [*path-map* (or (:path-map profile) *path-map*)]
              (if *verbose*
                (do
                  (println)
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
                (do (compile-cl2-files timeout targets)
                    (System/exit 0))
                (run rate timeout watch ignore targets))))))
      (println banner))))
