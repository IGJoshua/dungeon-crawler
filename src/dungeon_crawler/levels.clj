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

(def sample-level
  "this is intended just to be used for testing and not a permanent definition.
  For now contains just the cells, but can also potentially include information
  about styles or special properties, perhaps even a list of systems to load."
  {:size-x 15
   :size-y 15
   :cells (reduce #(assoc %1 %2 {:tile :ground}) {}
                  [[7 0] [7 1] [7 2] [1 3] [2 3] [3 3] [4 3] [5 3] [6 3] [7 3] [8 3] [9 3]
                   [1 4] [3 4] [9 4] [10 4] [11 4] [1 5] [3 5] [5 5] [6 5] [7 5] [9 5] [11 5]
                   [1 6] [3 6] [4 6] [5 6] [6 6] [7 6] [8 6] [9 6] [11 6] [1 7] [5 7] [6 7] [7 7]
                   [11 7] [1 8] [2 8] [6 8] [11 8] [2 9] [4 9] [5 9] [6 9] [9 9] [10 9] [11 9]
                   [2 10] [4 10] [4 11]])})
