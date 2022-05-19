(ns build
  (:require
   [clojure.tools.build.api :as b]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def source-dir "src/")
(def resource-dir "resources/")

(def build-dir "build/")
(def target-dir (str build-dir "target/"))

(def uber-file (str build-dir "dungeon-crawler.jar"))
;; TODO(Joshua): Maybe add support for bsd or something?
(def basis (b/create-basis
            {:aliases [(condp str/includes? (System/getProperty "os.name")
                         "Windows" :windows
                         "Linux" :linux)]}))

(defn- ensure-dir
  [dir]
  (let [dir (io/file dir)]
    (when-not (.exists dir)
      (.mkdirs dir))))

(def required-modules
  ["java.base" "java.xml" "java.sql" "jdk.unsupported"])

(defn link-jre
  [opts]
  (ensure-dir build-dir)
  (b/delete {:path (str build-dir "jre/")})
  (b/process
   {:command-args
    ["jlink"
     "--no-man-pages" "--no-header-files"
     "--compress=1"
     "--output" "jre/"
     "--bind-services"
     "--add-modules" (str/join "," required-modules)]
    :dir build-dir}))

(defn jar
  [opts]
  (ensure-dir target-dir)
  (b/copy-dir {:target-dir target-dir
               :src-dirs [resource-dir]})
  (b/compile-clj {:basis basis
                  :class-dir target-dir
                  :src-dirs [source-dir]
                  :compile-opts {:elide-meta [:doc :file :line :added]
                                 :direct-linking true}})
  (b/uber {:class-dir target-dir
           :uber-file uber-file
           :basis basis
           :main 'dungeon-crawler.core}))

(defn package-app
  [opts]
  (when-not (.exists (io/file build-dir "jre/"))
    (link-jre opts))
  (when-not (.exists (io/file target-dir))
    (jar opts))
  (b/copy-file {:src uber-file
                :target (str build-dir "package/dungeon-crawler.jar")})
  (b/process
   {:command-args
    ["jpackage"
     "--name" "dungeon-crawler"
     "--input" "package/"
     "--main-jar" "dungeon-crawler.jar"
     "--main-class" "dungeon_crawler.core"
     "--runtime-image" "jre/"
     "--type" "app-image"]
    :dir build-dir}))

(defn clean
  [opts]
  (b/delete {:path build-dir}))
