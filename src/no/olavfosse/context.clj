;; This code is hairy for a reason, if you think you can simplify
;; it, first try enumerating the edge cases. Good luck!
;;
;; I might be able to separate the common essence of these fns, which
;; could potentially simplify
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


(defn context
  ([n pred] (comp
             (context n pred ::separator)
             (partition-by (partial = ::separator))
             (filter (partial not= [::separator]))))
  ([n pred separator]
   (fn [rf]
     (println "newrf")
     (let [!context-trail (java.util.LinkedList.)
           !inputs-since-match (volatile! ##Inf)]
       (fn
         ([] (rf))
         ([acc] (rf acc))
         ([acc inp]
          (vswap! !inputs-since-match inc)
          (println "incced" @!inputs-since-match n)
          (println 'inp inp)
          (cond
            (pred inp) (let [acc (cond-> acc
                                   (and (not= @!inputs-since-match ##Inf)
                                        ;; This condition has to
                                        ;; change, since the current
                                        ;; input might also be part of
                                        ;; the next context span

                                        ;; might be off by one or something, i i'll just test to find out
                                        (> @!inputs-since-match (* 2 n))) (rf separator))]
                         (println "reset" @!inputs-since-match)
                         (vreset! !inputs-since-match 0)
                         (println "afterreset" @!inputs-since-match)
                         (loop [acc acc]
                           (if-some [trail-inp (.pollLast !context-trail)]
                             (let [rv (rf acc trail-inp)]
                               (if (reduced? rv) rv (recur rv)))
                             (rf acc inp))))
            (<= @!inputs-since-match n) (rf acc inp)
            :else (do (.addFirst !context-trail inp)
                      (when (= (.size !context-trail) (inc n))
                        (.removeLast !context-trail))
                      acc))))))))

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



;; generate tests
(for [n (range 0 10)] 
  {:input (vec (take n (shuffle (concat (repeat 10 0) (repeat 5 1)))))
   (list 'pretext 1 '=1) nil
   (list 'pretext 1 '=1 :sep) nil
   (list 'postext 1 '=1) nil
   (list 'postext 1 '=1 :sep) nil
   (list 'context 1 '=1) nil
   (list 'context 1 '=1 :sep) nil})

(def =1 (partial = 1))

(def tt '({:input [],
           (pretext 1 =1) [],
           (pretext 1 =1 :sep) [],
           (postext 1 =1) [],
           (postext 1 =1 :sep) [],
           (context 1 =1) [],
           (context 1 =1 :sep) []}
          {:input [1],
           (pretext 1 =1) [[1]],
           (pretext 1 =1 :sep) [1],
           (postext 1 =1) [[1]],
           (postext 1 =1 :sep) [1],
           (context 1 =1) [[1]],
           (context 1 =1 :sep) [1]}
          {:input [0 1],
           (pretext 1 =1) [[0 1]],
           (pretext 1 =1 :sep) [0 1],
           (postext 1 =1) [[1]],
           (postext 1 =1 :sep) [1],
           (context 1 =1) [[0 1]],
           (context 1 =1 :sep) [0 1]}
          {:input [1 1 0],
           (pretext 1 =1) [[1 1]],
           (pretext 1 =1 :sep) [1 1],
           (postext 1 =1) [[1 1 0]],
           (postext 1 =1 :sep) [1 1 0],
           (context 1 =1) [[1 1 0]],
           (context 1 =1 :sep) [1 1 0]}
          {:input [0 1 0 0],
           (pretext 1 =1) [[0 1]],
           (pretext 1 =1 :sep) [0 1],
           (postext 1 =1) [[1 0]],
           (postext 1 =1 :sep) [1 0],
           (context 1 =1) [[0 1 0]],
           (context 1 =1 :sep) [0 1 0]}
          {:input [0 1 0 0 1],
           (pretext 1 =1) [[0 1] [0 1]],
           (pretext 1 =1 :sep) [0 1 :sep 0 1],
           (postext 1 =1) [[1 0] [1]],
           (postext 1 =1 :sep) [1 0 :sep 1],
           (context 1 =1) [[0 1 0] [0 1]],
           (context 1 =1 :sep) [0 1 0 :sep 0 1]}
          {:input [1 1 1 0 0 0],
           (pretext 1 =1) [[1 1 1]],
           (pretext 1 =1 :sep) [1 1 1],
           (postext 1 =1) [[1 1 1 0]],
           (postext 1 =1 :sep) [1 1 1 0],
           (context 1 =1) [[1 1 1 0]],
           (context 1 =1 :sep) [1 1 1 0]}
          {:input [0 0 0 0 0 0 1],
           (pretext 1 =1) [[0 1]],
           (pretext 1 =1 :sep) [0 1],
           (postext 1 =1) [[1]],
           (postext 1 =1 :sep) [1],
           (context 1 =1) [[0 1]],
           (context 1 =1 :sep) [0 1]}
          {:input [1 1 0 0 0 0 1 0],
           (pretext 1 =1) [[1 1] [0 1]],
           (pretext 1 =1 :sep) [1 1 :sep 0 1],
           (postext 1 =1) [[1 1 0] [1 0]],
           (postext 1 =1 :sep) [1 1 0 :sep 1 0],
           (context 1 =1) [[1 1 0] [0 1 0]],
           (context 1 =1 :sep) [1 1 0 :sep 0 1 0]}
          {:input [0 1 1 0 0 1 1 0 0],
           (pretext 1 =1) [[0 1 1] [0 1 1]],
           (pretext 1 =1 :sep) [0 1 1 :sep 0 1 1],
           (postext 1 =1) [[1 1 0] [1 1 0]],
           (postext 1 =1 :sep) [1 1 0 :sep 1 1 0 :sep],
           (context 1 =1) nil,
           (context 1 =1 :sep) nil}))

(doseq [{:as tc input :input} tt]
  (doseq [[k v] tc]
    (when-not (= k :input)
      (when v
        (eval `(assert (= (into [] ~k ~input)
                          ~(into [] (eval k) input)
                          ~v)))))))
    
