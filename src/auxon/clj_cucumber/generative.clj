(ns auxon.clj-cucumber.generative
  (:require
   [auxon.clj-cucumber :as cuke]
   [clojure.test.check :as tc]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]))

(def ^{:dynamic true
       :doc "The number of quick-check iterations to run"}
  *quick-check-iterations* 100)

(def before-hook
  "A hook which initializes the test state for generative testing support."
  (cuke/hook
   :before
   (fn [_]
     {::env-gen (gen/return {})
      ::scenario-name cuke/*current-scenario-name*
      ::properties []
      ::pre-generator-steps []
      ::post-generator-steps []
      ::before-generators? true
      ::after-generators? false})))

(defmacro generator
  "Create a generator step.

  kw: The kind of step. :Given or :When

  pattern: The regex to match, just like any other step definition.

  mk-gen-fn : A function to make the generator. The first parameter is the
  environment map, which can be used to depend upon previously generated values.
  The rest of the parameters are taken from subgroups in the regex, as with
  normal steps. It should return a tuple of [var gen], where var is the new
  variable to introduce into the environment, and gen is the generator that will
  be used to populate it."
  [kw pattern mk-gen-fn]
  (let [line (:line (meta &form))]
    `{:type :step
      :kw ~kw
      :pattern ~pattern
      :line ~line
      :file ~*file*
      :fn (fn generator-step-fn# [state# & args#]
            (let [mk-gen-fn# ~mk-gen-fn]
              (assert (not (::after-generators? state#))
                      "All generators must be in a block; don't mix them with regular steps.")
              (-> state#
                  (update ::env-gen
                          gen/bind (fn generator-bind-fn# [env#]
                                     (if-let [[key# gen#] (apply mk-gen-fn# env# args#)]
                                       (->> gen# (gen/fmap (fn [val#]
                                                             (assoc env# key# val#))))
                                       ;; no new bindings; just pass through.
                                       (gen/return env#))))
                  (assoc ::before-generators? false))))}))

(defmacro step
  "Create a normal step that is executed in a generative test context.

  This is just like a regular step, except the first argument to f is the
  generative test environment. The return value of f becomes the new environment
  for subsequent steps."
  [kw pattern f]
  (let [line (:line (meta &form))]
    `{:type :step
      :kw ~kw
      :pattern ~pattern
      :line ~line
      :file ~*file*
      :fn (fn step-fn# [state# & args#]
            (let [step-closure# (fn [env#] (apply ~f env# args#))]
              (if (::before-generators? state#)
                (update state# ::pre-generator-steps conj step-closure#)
                (-> state#
                    (update ::post-generator-steps conj step-closure#)
                    (assoc ::after-generators? true)))))}))

(defmacro property
  "Create a property step.

  pattern: The regex to match, just like any other step definition.

  pred: The property predicate function. The first parameter is the environment
  map, which can be used to depend upon previously generated values. The rest of
  the parameters are taken from subgroups in the regex, as with normal steps.
  Its return value is evaluated for truthiness - true means the property holds,
  false means it does not."
  [pattern pred]
  (let [line (:line (meta &form))]
    `{:type :step
      :kw :Then
      :pattern ~pattern
      :line ~line
      :file ~*file*
      :fn (fn [state# & args#]
            (update state# ::properties conj
                    (fn [env#] (apply ~pred env# args#))))}))

(defn- run-steps [in-env steps]
  (reduce (fn [env step]
            (step env))
          in-env steps))

(defn after-hook-with-cleanup [cleanup]
  (cuke/hook
   :after
   (fn [state]
     (let [res (tc/quick-check
                *quick-check-iterations*
                (prop/for-all [generated-env (::env-gen state)]
                  (let [env (-> {::scenario-name (::scenario-name state)}
                                (run-steps (::pre-generator-steps state))
                                (merge generated-env)
                                (run-steps (::post-generator-steps state)))
                        ok (every? (fn [prop] (try
                                                (prop env)
                                                (catch Exception e
                                                  (println "Prop exception" e)
                                                  false)))
                                   (::properties state))]
                    (cleanup env)
                    ok)))]
       (assert (:pass? res) (pr-str res))))))

(def after-hook (after-hook-with-cleanup identity))

