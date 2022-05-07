(ns dungeon-crawler.levels
  (:require [dungeon-crawler.config :refer [config]]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]))

(def ^:private level-map (atom {}))

(defn get-cell-origin
  "Returns the X,Y origin coordinates for a given cell."
  [x y]
  [(* x (:cell-size-x config))
   (* y (:cell-size-y config))])

(defn get-cell-center
  "Returns the X,Y coordinates for the center of a given cell."
  [x y]
  (let [[origin-x origin-y] (get-cell-origin x y)]
    [(dec (+ origin-x (/ (:cell-size-x config) 2)))
     (dec (+ origin-y (/ (:cell-size-y config) 2)))]))

(s/def ::tile #{:ground})
(s/def ::size-x pos-int?)
(s/def ::size-y pos-int?)
(s/def ::cell (s/keys :req-un [::tile]))
(s/def ::cells (s/map-of vector? ::cell))
(s/def ::level (s/keys :req-un [::size-x
                                ::size-y
                                ::cells]))

(defn- read-map
  [map-name]
  (clojure.edn/read (java.io.PushbackReader.
                     (io/reader
                      (io/resource
                       (str "resources/" map-name ".edn"))))))

(defn load-level
  "This function takes in a string `level` and returns a map with all the details from
  that level where they are stored. `level` should mirror the filename."
  [level]
  (if (contains? @level-map level)
    (get @level-map level)
    (let [data (read-map level)]
      (swap! level-map assoc level data)
      data)))
