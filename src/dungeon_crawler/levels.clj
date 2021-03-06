(ns dungeon-crawler.levels
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [dungeon-crawler.config :refer [config]]))

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
(s/def ::cell-size-x pos-int?)
(s/def ::cell-size-y pos-int?)
(s/def ::cell (s/keys :req-un [::tile]))
(s/def ::cells (s/map-of vector? ::cell))
(s/def ::level (s/keys :req-un [::cell-size-x
                                ::cell-size-y
                                ::cells]))

(defn- read-map
  [map-name]
  (clojure.edn/read (java.io.PushbackReader.
                     (io/reader
                      (io/resource
                       (str map-name ".edn"))))))

(defn load-level
  "This function takes in a string `level` and returns a map with all the details from
  that level where they are stored. `level` should mirror the filename."
  [level]
  (if (contains? @level-map level)
    (get @level-map level)
    (let [data (read-map level)]
      (if-let [problems (s/explain-data ::level data)]
        (do (s/explain-printer problems)
            (throw (ex-info "Invalid level"
                            {:level level
                             :explain-data problems})))
        (do (swap! level-map assoc level data)
            data)))))

(defn- build-quad
  [index
   [x y]]
  (let [i (* 4 index)
        x' (:cell-size-x config)
        y' (:cell-size-y config)]
    {:layout [i (+ 2 i) (inc i) (+ 2 i) (+ 3 i) (inc i)]
     :position [{:pos [x 0.0 y]}
                {:pos [(+ x x') 0.0 y]}
                {:pos [x 0.0 (+ y y')]}
                {:pos [(+ x x') 0.0 (+ y y')]}]}))

(defn- wall?
  "Returns whether there is a tile in the adjacent space, based on your `spacing`.
  Returns the new location if there is one, or nil."
  [level [x y] facing]
  (let [diff (facing {:north [0 1]
                      :south [0 -1]
                      :east [1 0]
                      :west [-1 0]})
        new-tile [(+ x (first diff))
                  (+ y (second diff))]]
    (when-not (get (:cells level) new-tile)
      [[x y] facing])))

(defn build-floor
  "This function takes a `level` and returns a map containing the vertices
  and layouts for that level's floor."
  [level]
  (loop [i 0
         size-x (:cell-size-x config)
         size-y (:cell-size-y config)
         results {:vertices [] :indices []}
         l (:cells level)]
    (if (empty? l)
      results
      (let [[[x y] _v] (first l)
            q (build-quad i [(* x size-x) (* y size-y)])]
        (recur (inc i) size-x size-y
               (-> results
                   (update :vertices #(apply conj % (:position q)))
                   (update :indices #(apply conj % (:layout q))))
               (dissoc l [x y]))))))

(defn build-walls
  "Returns a seq of `[position facing]` for each wall in the level."
  [level]
  (->> (:cells level)
       (reduce (fn [results [k _v]]
                 (let [w (partial wall? level k)]
                   (conj results (w :north) (w :east) (w :west) (w :south)))))
       (filter seq)))
