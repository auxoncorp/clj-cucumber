(ns mullr.clj-cucumber-test
  (:require [clojure.test :refer :all]
            [mullr.clj-cucumber :refer [run-cucumber step]]))

(def test-state (atom {}))

(def steps
  [(step :Given #"^some setup$"
         (fn some-setup []
           (reset! test-state nil)
           (swap! test-state assoc :setup-happened true)))

   (step :When #"^I do a thing$"
         (fn I-do-a-thing []
           (swap! test-state assoc :thing-happened true)))

   (step :Then #"^the setup happened$"
         (fn the-setup-happened []
           (assert (:setup-happened @test-state))))

   (step :Then #"^the thing happened$"
         (fn the-thing-happened []
           (assert (:thing-happened @test-state))))])

(deftest passing-feature
  (is (= 0 (run-cucumber "test/mullr/features/passing.feature" steps))))

;; (deftest failing-feature
;;   (is (= 1 (run-cucumber "test/mullr/features/failing.feature" steps))))
