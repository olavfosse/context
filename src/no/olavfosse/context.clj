;; This code is hairy for a reason, if you think you can simplify
;; it, first try enumerating the edge cases. Good luck!
;;
;; - Separator must not be the first element to be emitted
;; - Separator must not be the last element to be emitted
;; - n=0 is valid
;;
;; - [ ] update context transducer
;; - [ ] Teting
;;   - I think test data should be something like:
;;     [{:input [nil :match nil nil nil :match],
;;       :n 2
;;       :postext [:match nil nil :match]
;;       :postext:sep [:match nil nil :sep :match]
;;       :pretext [nil :match :sep nil nil :match]
;;       :context [nil :match nil nil nil :match]}]
;;     
;;
;; I think hand compiling this from a state machine might be the way
;; to go..
(ns no.olavfosse.context
  (:import java.util.LinkedList))

(defn pretext
  "Returns a stateful transducer which forwards all elements matching
  pred as well as up to n elements trailing each match. Each
  contionous span of forwarded elements is delivered as a vector. If
  separator is passed the elements are forwarded directly, separated
  by separator."
  ([n pred] (comp (pretext n pred ::separator)
                  (partition-by (partial = ::separator))
	          (filter (partial not= [::separator]))))
  ([n pred separator]
   (fn [rf]
     (let [!context-trail (java.util.LinkedList.)
           ;; There's two tricky edge cases which we use variables to
           ;; handle.
           ;;           
           ;; - We must not emit a separator prior to forwarding the
           ;;   first element
           !nothing-forwarded? (volatile! true)
           ;; - We must only emit a separator if something will come
           ;;   after the separator
           !separate-next? (volatile! false)]
       (fn self 
         ([] (rf))
         ([acc] (rf acc))
         ([acc inp]
          ;; The separator is forwarded, when we've just exited a
          ;; context span and are about to forward an element from the
          ;; next span.
          (if-not (pred inp)
            (do (.addFirst !context-trail inp)
                (when (= (.size !context-trail) (inc n))
                  (.removeLast !context-trail)
                  (vreset! !separate-next? (not @!nothing-forwarded?)))
                acc)
            (do
              (vreset! !nothing-forwarded? false)
              (loop [acc (if @!separate-next?
                           (do
                             (vreset! !separate-next? false)
                             (rf acc separator))
                           acc)]
                (if-some [trail-inp (.pollLast !context-trail)]
                  (let [rv (rf acc trail-inp)]
                    (if (reduced? rv) rv (recur rv)))
                  (rf acc inp)))))))))))



;; Leading and trailing
(defn context
  "TODO: teach context to have the same arities as pretext/postext"
  #_"Returns a stateful transducer which forwards all elements matching
  pred as well as elements which come at most n elements before or
  after. Each contionous span of forwarded elements is delivered as a
  vector. If separator is passed the elements are forwarded directly,
  separated by separator."
  ([n pred] (comp (context n pred ::separator)
                  (partition-by (partial = ::separator))
	          (filter (partial not= [::separator]))))
  ([n pred]
   (fn [rf]
     (let [context-trail (java.util.LinkedList.)
           !inputs-since-match (volatile! ##Inf)]
       (fn
         ([] (rf))
         ([acc] (rf acc))
         ([acc inp]
          (if (pred inp)
            (do
              (vreset! !inputs-since-match 0)
              (loop [acc acc]
                (if-some [trail-inp (.pollLast context-trail)]
                  (let [rv (rf acc trail-inp)]
                    (if (reduced? rv) rv (recur rv)))
                  (rf acc inp))))
            (if (< @!inputs-since-match n)
              (do (vswap! !inputs-since-match inc)
                  (rf acc inp))
              (do 
                (.addFirst context-trail inp)
                (when (= (.size context-trail) (inc n)) (.removeLast context-trail))
                acc)))))))))

(defn context
  ([n pred] (comp (conttext n pred ::separator)
                  (partition-by (partial = ::separator))
	          (filter (partial not= [::separator]))))
  ([n pred separator]
   (fn [rf]
     (let [context-trail (java.util.LinkedList.)
           !inputs-since-match (volatile! ##Inf)]
       ))))

(defn postext
  "Returns a stateful transducer which forwards all elements matching
  pred as well as up to n elements leading the match. Each contionous
  span of forwarded elements is delivered as a vector. If separator is
  passed the elements are forwarded directly, separated by separator."
  ([n pred] (comp (postext n pred ::separator)
                  (partition-by (partial = ::separator))
	          (filter (partial not= [::separator]))))
  ([n pred separator]
   (fn [rf]
     (let [!inputs-since-match (volatile! ##Inf)]
       (fn
         ([] (rf))
         ([acc] (rf acc))
         ([acc inp]
          (vswap! !inputs-since-match inc)
          (cond
            (pred inp) (let [acc (cond-> acc
                                   (and (not= @!inputs-since-match ##Inf)
                                        (> @!inputs-since-match (inc n))) (rf separator))]
                         (vreset! !inputs-since-match 0)
                         (rf acc inp))
            (<= @!inputs-since-match n) (rf acc inp)
            :else acc)))))))




(comment
  ;; Would be much cooler to do some code as data type shit
  (vec (for [n (range 1 11)] 
         (repeatedly 3 #(let [input (take n (shuffle (concat (repeat 10 0) (repeat 5 1))))]
                          (hash-map
                           (list pretext n input) nil
                           (list pretext n input :sep) nil
                           (list postext n input) nil
                           (list postext n input :sep) nil
                           (list context n input) nil
                           (list context n input :sep) nil
)))))

  ;; (def test-table '(({:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (1),
  ;;                   :postext nil,
  ;;                   :postext:sep nil}
  ;;                  {:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (0),
  ;;                   :postext nil,
  ;;                   :postext:sep nil}
  ;;                  {:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (1),
  ;;                   :postext nil,
  ;;                   :postext:sep nil})
  ;;                 ({:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (0 1),
  ;;                   :postext nil,
  ;;                   :postext:sep nil}
  ;;                  {:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (0 0),
  ;;                   :postext nil,
  ;;                   :postext:sep nil}
  ;;                  {:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (0 1),
  ;;                   :postext nil,
  ;;                   :postext:sep nil})
  ;;                 ({:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (1 1 0),
  ;;                   :postext nil,
  ;;                   :postext:sep nil}
  ;;                  {:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (1 0 0),
  ;;                   :postext nil,
  ;;                   :postext:sep nil}
  ;;                  {:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (0 0 0),
  ;;                   :postext nil,
  ;;                   :postext:sep nil})
  ;;                 ({:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (0 1 0 0),
  ;;                   :postext nil,
  ;;                   :postext:sep nil}
  ;;                  {:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (0 1 0 0),
  ;;                   :postext nil,
  ;;                   :postext:sep nil}
  ;;                  {:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (0 1 0 0),
  ;;                   :postext nil,
  ;;                   :postext:sep nil})
  ;;                 ({:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (0 1 0 0 0),
  ;;                   :postext nil,
  ;;                   :postext:sep nil}
  ;;                  {:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (0 0 0 1 0),
  ;;                   :postext nil,
  ;;                   :postext:sep nil}
  ;;                  {:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (0 0 0 1 0),
  ;;                   :postext nil,
  ;;                   :postext:sep nil})
  ;;                 ({:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (0 1 0 1 0 1),
  ;;                   :postext nil,
  ;;                   :postext:sep nil}
  ;;                  {:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (1 0 0 1 0 0),
  ;;                   :postext nil,
  ;;                   :postext:sep nil}
  ;;                  {:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (0 0 0 1 1 0),
  ;;                   :postext nil,
  ;;                   :postext:sep nil})
  ;;                 ({:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (1 0 0 0 0 1 1),
  ;;                   :postext nil,
  ;;                   :postext:sep nil}
  ;;                  {:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (0 0 1 1 0 0 1),
  ;;                   :postext nil,
  ;;                   :postext:sep nil}
  ;;                  {:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (1 0 0 1 0 0 1),
  ;;                   :postext nil,
  ;;                   :postext:sep nil})
  ;;                 ({:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (0 0 0 0 0 1 1 1),
  ;;                   :postext nil,
  ;;                   :postext:sep nil}
  ;;                  {:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (0 0 1 0 0 0 1 0),
  ;;                   :postext nil,
  ;;                   :postext:sep nil}
  ;;                  {:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (0 0 0 0 0 0 1 0),
  ;;                   :postext nil,
  ;;                   :postext:sep nil})
  ;;                 ({:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (0 1 0 1 0 0 1 1 0),
  ;;                   :postext nil,
  ;;                   :postext:sep nil}
  ;;                  {:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (1 0 0 0 1 0 1 0 0),
  ;;                   :postext nil,
  ;;                   :postext:sep nil}
  ;;                  {:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (0 0 0 0 0 0 1 0 1),
  ;;                   :postext nil,
  ;;                   :postext:sep nil})
  ;;                 ({:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (0 0 0 0 0 0 1 0 0 1),
  ;;                   :postext nil,
  ;;                   :postext:sep nil}
  ;;                  {:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (0 0 0 0 0 0 0 0 1 0),
  ;;                   :postext nil,
  ;;                   :postext:sep nil}
  ;;                  {:pretext nil,
  ;;                   :pretext:sep nil,
  ;;                   :context:sep nil,
  ;;                   :context nil,
  ;;                   :input (0 1 1 0 0 0 0 0 1 0),
  ;;                   :postext nil,
  ;;                   :postext:sep nil})))

  ;; (doseq [tc test-table]
  ;;   (doseq [[k v] tc]
  ;;     (case k
  ;;       :postext (when v (assert (= v (into )))))
  ;;     )
  ;;   (into {} (map (fn [[k v]] [k (case k
  ;;                                       :input v
  ;;                                       :postext )]))
  ;;              tc))

  ;; ;; Other edge cases:
  ;; ;; - n = 0
  )
    
