;; For now this just provides a basic shader.
;; Once I'm more comfortable with ECS will instead load and compile shaders that can potentially
;; be provided in different files to aid organization.
(ns dungeon-crawler.shaders
  (:require [cljsl.compiler :as c]
            [s-expresso.shader :as sh]))

(c/defparam position "vec3"
  :layout {"location" 0})
(c/defparam color "vec3")

(c/defuniform view-projection "mat4")
(def view-projection-ident (::c/ident view-projection))

(c/defshader vert-source
  {position :in
   color :out}
  (set! color (vec3 (+ 0.5 (:x position)) 1.0 (+ 0.5 (:y position))))
  (set! gl_Position (* view-projection (vec4 position 1.0))))

(c/defparam out-color "vec4")

(c/defshader frag-source
  {color :in
   out-color :out}
  (set! out-color (vec4 color (float 1.0))))

(def basic-shader #(sh/make-shader-program-from-sources [{:source (::c/source vert-source)
                                                          :stage :vertex}
                                                         {:source (::c/source frag-source)
                                                          :stage :fragment}]))
