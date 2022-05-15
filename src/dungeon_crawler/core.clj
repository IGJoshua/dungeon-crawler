(ns dungeon-crawler.core
  (:require
   [clojure.tools.logging :as log]
   [dungeon-crawler.levels :refer [load-level build-floor]]
   [dungeon-crawler.shaders :refer [basic-shader]]
   [s-expresso.ecs :as ecs]
   [s-expresso.engine :as eng]
   [s-expresso.memory :refer [with-heap-allocator]]
   [s-expresso.mesh :as m]
   [s-expresso.resource :as res]
   [s-expresso.render :as rnd]
   [s-expresso.shader :as sh]
   [s-expresso.window :as wnd])
  (:import
   (org.lwjgl.opengl GL GL45 GLDebugMessageCallback GLDebugMessageCallbackI))
  (:gen-class))

(defn init
  []
  (wnd/init-glfw))

(defn shutdown
  []
  (wnd/shutdown-glfw))

(defonce input-events (atom []))

(def window (atom nil))
(def window-opts
  {:key-callback (fn [_window key _scancode action mods]
                   (swap! input-events conj
                          {:device :keyboard
                           :key key
                           :action action
                           :mods mods}))
   :cursor-pos-callback (fn [_window xpos ypos]
                          (swap! input-events conj
                                 {:device :mouse
                                  :action :move
                                  :pos [xpos ypos]}))
   :mouse-button-callback (fn [_window button action mods]
                            (swap! input-events conj
                                   {:device :mouse
                                    :button button
                                    :action action
                                    :mods mods}))
   :request-close-callback (fn [window]
                             (wnd/window-should-close window false)
                             (swap! input-events conj
                                    {:device :window
                                     :action :close}))
   :cursor-mode :hidden
   :debug-context true
   :title "Dungeon Crawler"})

(defn enable-debug-logging!
  []
  (let [flags (int-array 1)]
    (GL45/glGetIntegerv GL45/GL_CONTEXT_FLAGS flags)
    (when-not (zero? (bit-and GL45/GL_CONTEXT_FLAG_DEBUG_BIT
                              (first flags)))
      (GL45/glEnable GL45/GL_DEBUG_OUTPUT)
      (GL45/glEnable GL45/GL_DEBUG_OUTPUT_SYNCHRONOUS)
      (GL45/glDebugMessageControl GL45/GL_DONT_CARE
                                  GL45/GL_DONT_CARE
                                  GL45/GL_DONT_CARE
                                  (int-array 0)
                                  true)
      (GL45/glDebugMessageCallback
       (reify GLDebugMessageCallbackI
         (invoke [_this source type id severity length message user-param]
           (log/debug (GLDebugMessageCallback/getMessage length message))))
       0))))

(defn start-window
  [opts]
  (let [wnd (-> (wnd/make-window opts)
                (wnd/make-context-current-to-window)
                (wnd/center-window)
                (wnd/show-window))]
    (reset! input-events [])
    (reset! window wnd)
    (wnd/set-vsync true)
    (GL/createCapabilities)
    (enable-debug-logging!)
    wnd))

(defn shutdown-window
  [wnd]
  (res/free wnd)
  (reset! window nil))

;; TODO: Needs to account for obstacles
;; TODO: Potentially instead of just returning game-state, if a movement fails,
;; may want to play a sound or give some other form of interaction.
(defn player-input
  "This processes the player's movement. If the player turns left or right,
  this does not consume their action, but moving should."
  [game-state event]
  (let [player-uuid (:player game-state)
        player (get-in game-state [::ecs/entities player-uuid])]
    (case (:key event)
      :left (assoc-in game-state [::ecs/entities player-uuid :facing]
                      ((:facing player) {:north :west
                                         :west :south
                                         :south :east
                                         :east :north}))
      :right (assoc-in game-state [::ecs/entities player-uuid :facing]
                        ((:facing player) {:north :east
                                           :east :south
                                           :south :west
                                           :west :north}))
      :up (let [new-pos (mapv #(+ %1 %2)
                              (:position player)
                              (case (:facing player)
                                :north [0 -1]
                                :west [-1 0]
                                :south [0 1]
                                :east  [1 0]))
                tile? (get-in game-state [:level :cells new-pos])]
            (if tile?
              (assoc-in game-state [::ecs/entities player-uuid :position]
                        new-pos)
              game-state))
      :down (let [new-pos (mapv #(+ %1 %2)
                                (:position player)
                                (case (:facing player)
                                       :north [0 1]
                                       :west [1 0]
                                       :south [0 1]
                                       :east [1 0]))
                  tile? (get-in game-state [:level :cells new-pos])]
              (if tile?
                (assoc-in game-state [::ecs/entities player-uuid :position]
                          new-pos)
                game-state)))))

(defn ingest-input
  [game-state _dt]
  (let [[input-events] (reset-vals! input-events [])
        input-events (group-by :device input-events)
        mouse-pos (:pos
                   (last
                    (filter (comp #{:move} :action)
                            (:mouse input-events))))
        player-movement? (last
                          (filter #(and (= :press (:action %))
                                        (#{:left :right :up :down} (:key %)))
                                  (:keyboard input-events)))
        close? (or (some #(and (= :escape (:key %))
                               (= :press (:action %)))
                         (:keyboard input-events))
                   (some (comp #{:close} :action)
                         (:window input-events)))]
    (cond-> game-state
      mouse-pos (assoc :mouse-pos mouse-pos)
      player-movement? (player-input player-movement?)
      close? (assoc ::eng/should-close? close?))))

(def game-systems [#'ingest-input])

(defn clear-screen
  [_game-state]
  (list
   (reify rnd/RenderOp
     (op-deps [_this]
       {})
     (apply-op! [_this _render-state]
       (GL45/glClearColor 0.1 0.15 0.2 1.0)
       (GL45/glClear (bit-or GL45/GL_COLOR_BUFFER_BIT GL45/GL_DEPTH_BUFFER_BIT))))))

(def floor-mesh-layout
  {:buffer-layouts [{:attrib-layouts [{:name :pos
                                       :type :float
                                       :count 3}]}]
   :element-type :triangles
   :indices {:type :uint}})

(defn render-floor
  [game-state]
  (list (reify rnd/RenderOp
          (op-deps [_]
            {::mesh (get-in game-state [::ecs/entities (:level game-state) :mesh-data])
             ::shader-program basic-shader})
          (apply-op! [_ {{::keys [mesh shader-program]} ::rnd/resources}]
            (when (and mesh shader-program)
              (sh/with-shader-program shader-program
                (m/draw-mesh mesh)))))))

(def render-systems [#'clear-screen #'render-floor])

(def init-game-state
  (let [player (ecs/next-entity-id)
        init-level-id (ecs/next-entity-id)
        init-level (load-level "sample_level")]
    {::ecs/entities {player {:facing :north
                             :position [11 9]}
                     init-level-id {:mesh-data (with-heap-allocator
                                                 (future
                                                   (let [mesh (m/pack-verts floor-mesh-layout (build-floor init-level))]
                                                     (delay (m/make-mesh floor-mesh-layout mesh)))))
                                    :level-data init-level}}
     ::ecs/systems #'game-systems
     ::ecs/events []
     :player player
     :level init-level-id
     ::rnd/systems #'render-systems}))

(def init-render-state
  {::rnd/resolvers {}
   ::rnd/resources {}})

(defn run
  []
  (let [window (start-window window-opts)]
    (try (eng/start-engine window init-game-state init-render-state (/ 60))
      (finally (shutdown-window window)))))

(defn -main
  [& args]
  (init)
  (run)
  (shutdown))
