(ns auxon.clj-cucumber.generative
  (:require
   [auxon.clj-cucumber :refer [hook step]]
   [clojure.test.check :as tc]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]))

(def ^:dynamic *quick-check-iterations* 100)


(def before-hook
  "A hook which initializes the test state for generative testing support."
  (hook :before (fn [_]
                  {::env-gen (gen/return {})
                   ::properties []})))

(defmacro generator
  "Create a generator step.

  re: The regex to matc,h just like any other step definition.

  mk-gen-fn : A function to make the generator. The first parameter is the
  environment map, which can be used to depend upon previously generated values.
  The rest of the parameters are taken from subgroups in the regex, as with
  normal steps. It should return a tuple of [var gen], where var is the new
  variable to introduce into the environment, and gen is the generator that will
  be used to populate it."
  [re mk-gen-fn]
  `(step :Given ~re
         (fn generator-step-fn# [state# & args#]
           (let [mk-gen-fn# ~mk-gen-fn]
             (update state#
                     ::env-gen
                     gen/bind
                     (fn generator-bind-fn# [env#]
                       (let [[key# gen#] (apply mk-gen-fn# env# args#)]
                         (->> gen# (gen/fmap (fn [val#]
                                               (assoc env# key# val#)))))))))))

(defmacro property
  "Create a property step.

  re: The regex to match, just like any other step definition.

  pred: The property predicate function. The first parameter is the environment
  map, which can be used to depend upon previously generated values. The rest of
  the parameters are taken from subgroups in the regex, as with normal steps.
  Its return value is evaluated for truthiness - true means the property holds,
  false means it does not."
  [re pred]
  `(step :Then ~re
         (fn [state# & args#]
           (update state# ::properties conj
                   (fn [env#] (apply ~pred env# args#))))))

(def after-hook
  ""
  (hook :after (fn [state]
                 (tc/quick-check
                  *quick-check-iterations*
                  (prop/for-all [env (::env-gen state)]
                    (every? #(% env) (::properties state)))))))
