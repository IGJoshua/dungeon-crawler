(ns dungeon-crawler.levels
  (:require [dungeon-crawler.config :refer [Config]]
            [clojure.spec.alpha :as s]))

(defn get-cell-origin
  "Returns the X,Y origin coordinates for a given cell."
  [x y]
  [(* x (:cell-size-x Config))
   (* y (:cell-size-y Config))])

(defn get-cell-center
  "Returns the X,Y coordinates for the center of a given cell."
  [x y]
  (let [[origin-x origin-y] (get-cell-origin x y)]
    [(dec (+ origin-x (/ (:cell-size-x Config) 2)))
     (dec (+ origin-y (/ (:cell-size-y Config) 2)))]))

(s/def ::tile #{:ground})
(s/def ::size-x pos-int?)
(s/def ::size-y pos-int?)
(s/def ::cell (s/keys :req-un [::tile]))
(s/def ::cells (s/map-of vector? ::cell))
(s/def ::level (s/keys :req-un [::size-x
                                ::size-y
                                ::cells]))

(def ^:private level-map
  {:sample-level (delay (clojure.edn/read-string (slurp "resources/sample_level.edn")))})

(defn get-level
  "This function takes in a keyword `level` and returns a map with all the details from
  that level where they are stored."
  [level]
  @(level level-map))
