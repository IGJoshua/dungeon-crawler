;; For now this just provides a basic shader.
;; Once I'm more comfortable with ECS will instead load and compile shaders that can potentially
;; be provided in different files to aid organization.
(ns dungeon-crawler.shaders
  (:require [cljsl.compiler :as c]
            [s-expresso.shader :as sh]))

(c/defparam v-pos "vec3"
  :layout {"location" 0})

(c/defuniform time "float")
(c/defparam v-out "float")

(c/defshader vert-source
  {v-pos :in
   time :in
   v-out :out}
  (set! gl_Position (vec4 v-pos 1.0))
  (set! v-out time))

(c/defparam frag-color "vec4")

(c/defshader frag-source
  {v-out :in
   frag-color :out}
  (set! frag-color (vec4 0 0 (abs (sin v-out)))))

(def ^:private basic-shader-source
  [{:source (::c/source vert-source)
    :stage :vertex}
   {:source (::c/source frag-source)
    :stage :fragment}])

(def basic-shader (sh/make-shader-program-from-sources basic-shader-source))
