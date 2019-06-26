(ns auxon.clj-cucumber.generative-test
  (:require
   [auxon.clj-cucumber :refer [hook run-cucumber step]]
   [auxon.clj-cucumber.generative :as cgen :refer (generator property)]
   [clojure.test :refer :all]
   [clojure.test.check :as tc]
   [clojure.test.check.generators :as gen]))

(def steps
  [cgen/before-hook

   (generator "^any positive integer (.)$"
              (fn gen-pos-int [_ var]
                [var gen/pos-int]))

   (generator "^any positive integer (.) greater than (.)$"
              (fn gen-pos-int-gt [env var min-var]
                (let [min (get env min-var)]
                  [var (->> gen/pos-int
                            (gen/fmap (partial + min)))])))

   (generator #"^any integer (.) from (\d+) to (\d+)$"
              (fn gen-int-in-range [env var lower upper]
                [var (gen/choose lower upper)]))

   (property #"^(.) \+ (.) is positive$"
             (fn sum-is-positive [env var1 var2]
               (pos? (+ (get env var1)
                        (get env var2)))))

   cgen/after-hook])

(deftest generative-feature
  (is (= 0 (run-cucumber "test/auxon/clj_cucumber/features/generative.feature" steps))))
