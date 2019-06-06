(ns mullr.clj-cucumber-test
  (:require [clojure.test :refer :all]
            [mullr.clj-cucumber :refer [run-cucumber step hook]]))

(def test-state (atom {}))

(def steps
  [(hook :before
         (fn before-hook []
           (reset! test-state
                   {:before-hook-happened true
                    :before-step-count 0
                    :after-step-count 0})))

   (hook :before-step
         (fn before-step-hook []
           (swap! test-state update :before-step-count inc)))

   (hook :after-step
         (fn after-step-hook []
           (swap! test-state update :after-step-count inc)))

   (hook :after
         (fn after-hook []
           (swap! test-state assoc :after-hook-happened true)))

   (step :Given #"^some setup$"
         (fn some-setup []
           (swap! test-state assoc :setup-happened true)))

   (step :When #"^I do a thing$"
         (fn I-do-a-thing []
           (swap! test-state assoc :thing-happened true)))

   (step :Then #"^the setup happened$"
         (fn the-setup-happened []
           (assert (:setup-happened @test-state))))

   (step :Then #"^the before hook happened$"
         (fn the-before-hook-happened []
           (assert (:before-hook-happened @test-state))))

   (step :Then #"^the thing happened$"
         (fn the-thing-happened []
           (assert (:thing-happened @test-state))))

   (step :Then #"^the (\w+) step counter is (\d+)$"
         (fn the-step-counter-is [kind val]
           (case kind
             "before" (assert (= val (:before-step-count @test-state)))
             "after" (assert (= val (:after-step-count @test-state))))))])

(deftest passing-feature
  (is (= 0 (run-cucumber "test/mullr/features/passing.feature" steps)))
  ;; Can't check this inside the spec because it doesn't happen until after it's
  ;; done
  (is (:after-hook-happened @test-state)))

(deftest failing-feature
  (is (= 1 (run-cucumber "test/mullr/features/failing.feature" steps))))
