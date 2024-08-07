;; This code is hairy for a reason, if you think you can simplify
;; it, first try enumerating the edge cases. Good luck!
;;
;; I think hand compiling this from a state machine might be the way
;; to go..
(ns no.olavfosse.context
  (:import java.util.LinkedList))

(defn pretext
  "Returns a stateful transducer which forwards all elements matching
  pred as well as elements which come at most n elements before. Each
  contionous span of forwarded elements is delivered as a vector. If
  separator is passed the elements are forwarded directly, separated
  by separator."
  ([n pred] (comp (pretext n pred ::separator)
                  (partition-by (partial = ::separator))
	          (filter (partial not= [::separator]))))
  ([n pred separator]
   (fn [rf]
     (let [!context-trail (java.util.LinkedList.)
           !nothing-forwarded? (volatile! true)
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
(comment
  ;; Check out xforms' lines-in
  (transduce (comp
              (map-indexed str)
              (pretext 1 (partial re-matches #".*fn.*")  "--"))
             (completing #(println %2))
             nil
             (line-seq (clojure.java.io/reader *file*)))

  (transduce
   (pretext 3 (partial re-matches #".*fn.*") :separator)
   conj
   []
   (line-seq (clojure.java.io/reader *file*))))

(defn context
  "Returns a stateful transducer which forwards all elements matching
  pred as well as elements which come at most n elements before or
  after. Each contionous span of forwarded elements is delivered as a
  vector. If separator is passed the elements are forwarded directly,
  separated by separator."
  [n pred]
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
               acc))))))))

(defn postext
  "Returns a stateful transducer which forwards all elements matching
  pred as well as elements which come at most n elements before or
  after. Each contionous span of forwarded elements is delivered as a
  vector. If separator is passed the elements are forwarded directly,
  separated by separator."
  [n pred]
  (fn [rf]
    (let [!inputs-since-match (volatile! ##Inf)]
      (fn
        ([] (rf))
        ([acc] (rf acc))
        ([acc inp]
         (vswap! !inputs-since-match inc)
         (cond
           (pred inp) (do (vreset! !inputs-since-match 0)
                          (rf acc inp))
           (<= @!inputs-since-match n) (rf acc inp)
           :else acc))))))
