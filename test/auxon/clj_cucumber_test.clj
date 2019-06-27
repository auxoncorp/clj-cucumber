(ns auxon.clj-cucumber-test
  (:require
   [auxon.clj-cucumber :refer [hook run-cucumber step]]
   [clojure.test :refer :all]))

;; (def test-state (atom {}))

(def after-hook-happened (atom false))

(def steps
  [(hook :before
         (fn before-hook [_]
           {:before-hook-happened true
            :before-step-count 0
            :after-step-count 0}))

   (hook :before-step
         (fn before-step-hook [state]
           (update state :before-step-count inc)))

   (hook :after-step
         (fn after-step-hook [state]
           (update state :after-step-count inc)))

   (hook :after
         (fn after-hook [state]
           (reset! after-hook-happened true)
           state))

   (step :Given #"^some setup$"
         (fn some-setup [state]
           (assoc state :setup-happened true)))

   (step :When #"^I do a thing$"
         (fn I-do-a-thing [state]
           (assoc state :thing-happened true)))

   (step :Then #"^the setup happened$"
         (fn the-setup-happened [state]
           (assert (:setup-happened state))
           state))

   (step :Then #"^the before hook happened$"
         (fn the-before-hook-happened [state]
           (assert (:before-hook-happened state))
           state))

   (step :Then #"^the thing happened$"
         (fn the-thing-happened [state]
           (assert (:thing-happened state))
           state))

   (step :Then #"^the (\w+) step counter is (\d+)$"
         (fn the-step-counter-is [state kind val]
           (case kind
             "before" (assert (= val (:before-step-count state)))
             "after" (assert (= val (:after-step-count state))))
           state))])

(deftest passing-feature
  (is (= 0 (run-cucumber "test/auxon/clj_cucumber/features/passing.feature" steps)))
  ;; Can't check this inside the spec because it doesn't happen until after it's
  ;; done
  (is @after-hook-happened))

(deftest failing-feature
  (is (= 1 (run-cucumber "test/auxon/clj_cucumber/features/failing.feature" steps))))
