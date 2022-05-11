(ns dungeon-crawler.math
  (:require
   [clojure.core.matrix :as mat]
   [clojure.math :as math]))

(mat/set-current-implementation :vectorz)

(def compose mat/mmul)

(def inverse mat/inverse)

(defn translation
  ([pos]
   (let [ret (mat/clone (mat/identity-matrix 4))]
     (doseq [idx (range 3)]
       (mat/mset! ret idx 3 (mat/mget pos idx)))
     ret))
  ([x y z]
   (mat/array [[1 0 0 x]
               [0 1 0 y]
               [0 0 1 z]
               [0 0 0 1]])))

(defn scale
  ([factor]
   (if (number? factor)
     (scale factor factor factor)
     (let [ret (mat/clone (mat/identity-matrix 4))]
       (doseq [idx (range 3)]
         (mat/mset! ret idx idx (mat/mget factor idx)))
       ret)))
  ([x y z]
   (mat/array [[x 0 0 0]
               [0 y 0 0]
               [0 0 z 0]
               [0 0 0 1]])))

(defn revolve-x
  [r]
  (let [cos (math/cos r)
        sin (math/sin r)]
    (mat/array [[1 0 0 0]
                [0 cos (- sin) 0]
                [0 sin cos 0]
                [0 0 0 1]])))

(defn revolve-y
  [r]
  (let [cos (math/cos r)
        sin (math/sin r)]
    (mat/array [[cos 0 sin 0]
                [0 1 0 0]
                [(- sin) 0 cos 0]
                [0 0 0 1]])))

(defn revolve-z
  [r]
  (let [cos (math/cos r)
        sin (math/sin r)]
    (mat/array [[cos (- sin) 0 0]
                [sin cos 0 0]
                [0 0 1 0]
                [0 0 0 1]])))

(defn nautical-angles
  [pitch yaw roll]
  (mat/mmul
   (revolve-y yaw)
   (revolve-x pitch)
   (revolve-z roll)))

(defn euler-angles
  [alpha beta gamma]
  (mat/mmul
   (revolve-z gamma)
   (revolve-x beta)
   (revolve-z alpha)))

(defn revolve
  [axis r]
  (let [[x y z] (seq (mat/normalise axis))
        cos (math/cos r)
        sin (math/cos r)]
    (mat/array [[(+ cos (* x x (- 1 cos))) (- (* x y (- 1 cos)) (* z sin)) (+ (* x z (- 1 cos)) (* y sin)) 0]
                [(+ (* y x (- 1 cos)) (* z sin)) (+ cos (* y y (- 1 cos))) (- (* y z (- 1 cos)) (* x sin)) 0]
                [(- (* z x (- 1 cos)) (* y sin)) (+ (* z y (- 1 cos)) (* x sin)) (+ cos (* z z (- 1 cos))) 0]
                [0 0 0 1]])))

(defn perspective-projection
  [fov aspect-ratio near far]
  (let [half-height (* (math/sin (/ fov 2)) near)
        half-width (* half-height aspect-ratio)]
    (mat/array [[(/ near half-width) 0 0 0]
                [0 (/ near half-height) 0 0]
                [0 0 (/ (- (+ far near)) (- far near)) (/ (* -2 far near) (- far near))]
                [0 0 -1 0]])))

(defn orthographic-projection
  [aspect-ratio zoom near far]
  (let [half-height (/ 1 zoom 2)
        half-width (* half-height aspect-ratio)]
    (mat/array [[(/ half-width) 0 0 0]
                [0 (/ half-height) 0 0]
                [0 0 (/ -2 (- far near)) (/ (* -1 (+ far near)) (- far near))]
                [0 0 0 1]])))

(defmacro with-reference-frame
  [frame mats & body]
  (let [frame-inv-sym (gensym)]
    `(let [frame# ~frame
           ~frame-inv-sym (mat/inverse frame#)
           ~@(mapcat #(-> [% `(mat/mmul ~frame-inv-sym ~%)]) mats)]
       (mat/mmul frame# (do ~@body)))))
