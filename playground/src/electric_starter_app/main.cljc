(ns electric-starter-app.main
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms5 :refer [Input]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :refer [reader]])
            ;; TODO: cljs version
            #?(:clj [no.olavfosse.context :refer [context]])))

(e/defn Main [ring-request]
  (e/client
    (binding [dom/node js/document.body
              e/http-request (e/server ring-request)]
      ; mandatory wrapper div https://github.com/hyperfiddle/electric/issues/74
      (dom/div (dom/props {:style {:display "contents"}})
               (let [!search (atom "defn")
                     search (e/watch !search)
                     !context-width (atom 1)
                     context-width (e/watch !context-width)]
                 search context-width
                 (dom/div (dom/code (dom/text "(context ")
                                    (reset! !context-width (abs (parse-long (Input 0 :type "range"
                                                                                   :max 10
                                                                                   :style {:vertical-align :middle}))))
                                    (dom/text " #(str/includes? % ")
                                    (reset! !search (Input search))
                                    (dom/text ") \"\\n---\\n\")")))
                 (dom/pre (dom/text (e/server
                                      (transduce (context context-width #(str/includes? % search) "\n---\n")
                                                 (completing (fn [acc inp] (str acc inp "\n")))
                                                 ""
                                                 (line-seq (reader "src/electric_starter_app/main.cljc")))))))))))
