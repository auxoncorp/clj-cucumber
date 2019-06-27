(ns auxon.clj-cucumber.generative-test
  (:require
   [auxon.clj-cucumber :refer [run-cucumber]]
   [auxon.clj-cucumber.generative :as cgen :refer (generator property step)]
   [clojure.test :refer :all]
   [clojure.test.check :as tc]
   [clojure.test.check.generators :as gen]))

(def cleanup-called (atom false))

(def steps
  [cgen/before-hook

   (generator :Given "^any positive integer (.)$"
              (fn gen-pos-int [_ var]
                [var (gen/fmap inc gen/pos-int)]))

   (generator :Given "^any positive integer (.) greater than (.)$"
              (fn gen-pos-int-gt [env var min-var]
                (let [min (get env min-var)]
                  [var (->> gen/pos-int
                            (gen/fmap (partial + min)))])))

   (generator :Given #"^any integer (.) from (\d+) to (\d+)$"
              (fn gen-int-in-range [env var lower upper]
                [var (gen/choose lower upper)]))

   (step :When "^a regular step happens$"
         (fn regular-step [env]
           (assoc env :step-happened true)))

   (property #"^(.) \+ (.) is positive$"
             (fn sum-is-positive [env var1 var2]
               (pos? (+ (get env var1)
                        (get env var2)))))

   (property #"^(.) \+ (.) is negative$"
             (fn sum-is-negative [env var1 var2]
               (neg? (+ (get env var1)
                        (get env var2)))))

   (property #"^the regular step happened$"
             (fn regular-step-happened [env]
               (:step-happened env)))

   (cgen/after-hook-with-cleanup
    (fn cleanup [env]
      (reset! cleanup-called true)))])

(deftest generative-feature
  (is (= 0 (run-cucumber "test/auxon/clj_cucumber/features/generative.feature" steps)))
  (is @cleanup-called))

(deftest generative-feature-failing
  (is (not= 0 (run-cucumber "test/auxon/clj_cucumber/features/generative_failing.feature" steps))))

