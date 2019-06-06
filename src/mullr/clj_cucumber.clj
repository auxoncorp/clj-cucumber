(ns mullr.clj-cucumber
  (:require
   [clojure.string :as str])
  (:import
   (cucumber.runtime Backend
                     BackendSupplier
                     StepDefinition)
   (cucumber.runtime.snippets Concatenator
                              FunctionNameGenerator
                              Snippet
                              SnippetGenerator)
   (io.cucumber.cucumberexpressions ParameterTypeRegistry)
   (io.cucumber.stepexpression ExpressionArgumentMatcher
                               StepExpression
                               StepExpressionFactory
                               TypeRegistry)))

(def ^:private step-expr-factory
  (StepExpressionFactory.
   (TypeRegistry. java.util.Locale/ENGLISH)))

(defn- make-step-def [pattern step-fn arg-count file line]
  (let [expression (.createExpression step-expr-factory (str pattern))
        arg-matcher (ExpressionArgumentMatcher. expression)
        arg-types (make-array java.lang.reflect.Type 0)]
    (reify StepDefinition
      (matchedArguments [_ step]
        (.argumentsFrom arg-matcher step arg-types))

      (getLocation [_ detail]
        (str file ":" line))

      (getParameterCount [_]
        nil)

      (execute [_ args]
        (apply step-fn args))

      (isDefinedAt [_ stack-trace-element]
        (and (= (.getLineNumber stack-trace-element) line)
             (= (.getFileName stack-trace-element) file)))

      (getPattern [_]
        (str pattern))

      (isScenarioScoped [_]
        false))))

(def ^:private snippet-generator
  (SnippetGenerator.
   (reify Snippet
     (template [_]
       ;; {0} : Step Keyword</li>
       ;; {1} : Value of {@link #escapePattern(String)}</li>
       ;; {2} : Function name</li>
       ;; {3} : Value of {@link #arguments(Map)}</li>
       ;; {4} : Regexp hint comment</li>
       ;; {5} : value of {@link #tableHint()} if the step has a table</li>

       (str
        "(step :{0} #\"^{1}$\"\n"
        "      (fn {2} [{3}]\n"
        "        (comment  {4})\n"
        "        (throw (cucumber.api.PendingException.))))\n"))

     (tableHint [_]
       nil)

     (arguments [_ argumentTypes]
       (str/join " " (keys argumentTypes)))

     (escapePattern [_ pattern]
       (str/replace (str pattern) "\"" "\\\"")))
   (ParameterTypeRegistry. (java.util.Locale/ENGLISH))))


(def ^:private clj-function-name-generator
  (FunctionNameGenerator.
   (reify Concatenator
     (concatenate [_ words]
       (str/join "-" words)))))

(defn- create-clj-backend [steps]
  (reify Backend
    (loadGlue [this glue gluePaths]
      (doseq [{:keys [kw pattern fn file line]} steps]
        (.addStepDefinition glue (make-step-def pattern fn nil file line))))

    (buildWorld [this])
    (disposeWorld [this])

    ;; List<String> getSnippet(PickleStep step, String keyword, FunctionNameGenerator functionNameGenerator);
    (getSnippet [this step kw _]
      (.getSnippet snippet-generator step kw clj-function-name-generator))))

(defn- create-cucumber-runtime [args steps]
  (let [backend (create-clj-backend steps)]
    (.. (cucumber.runtime.Runtime/builder)
        (withArgs args)
        (withClassLoader (.getContextClassLoader (Thread/currentThread)))
        (withBackendSupplier (reify BackendSupplier
                               (get [_] [backend])))
        (build))))

;;; Public api

(defmacro step
  "Create a step map, with line and file filled in.

   - `kw`: :Given, :When or :Then
   - `pattern`: The regex to match for this step
   - `fn`: The function to call when executing this step.
           Subgroups matched in `pattern` are provided as parameters."
  [kw pattern fn]
  (let [line (:line (meta &form))]
    `{:kw ~kw
      :pattern ~pattern
      :fn ~fn
      :line ~line
      :file ~*file*}))


(defn run-cucumber
  "Run the cucumber features at `features-path` using the given `steps`.

  `steps` should be a sequence of step definition maps; these these can be
  created easily using the `step` macro."
  [features-path steps]
  (let [args ["--plugin" "pretty"
              "--monochrome"
              features-path]
        runtime (create-cucumber-runtime args steps)]
    (.run runtime)
    (.exitStatus runtime)))
