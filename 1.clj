;; This code is hairy for a reason, if you think you can simplify
;; it, first try enumerating the edge cases. Good luck!
;;
;; - Separator must not be the first element to be emitted
;; - Separator must not be the last element to be emitted
;; - n=0 is valid
;;
;; - [ ] Write a bunch of tests
;;
;; I think hand compiling this from a state machine might be the way
;; to go..
(ns no.olavfosse.context
  (:require [net.cgrand.xforms.io :refer [lines-in lines-out] :as xio])
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

(comment
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

;; Leading and trailing
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
                                   (> @!inputs-since-match (inc n)) (rf separator))]
                         (vreset! !inputs-since-match 0)
                         (rf acc inp))
            (<= @!inputs-since-match n) (rf acc inp)
            :else acc)))))))


(comment
  (transduce (comp
              (map-indexed str)
              (postext 6 (partial re-matches #".*fn.*")  "--"))
             (completing #(println %2))
             nil
             (line-seq (clojure.java.io/reader *file*)))

  (transduce
   (postext 3 (partial re-matches #".*fn.*") :separator)
   conj
   []
   (line-seq (clojure.java.io/reader *file*))))
