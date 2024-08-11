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
   ;; It's super ugly to handly n=0 separately :/
   (if (zero? n)
     (comp (filter pred)
           (interpose separator))
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
                    (rf acc inp))))))))))))


(defn context
  "Returns a stateful transducer which forwards all elements matching
  pred as well as up to n elements trailing or leading each match. Each
  contionous span of forwarded elements is delivered as a vector. If
  separator is passed the elements are forwarded directly, separated
  by separator."
  ([n pred] (comp
             (context n pred ::separator)
             (partition-by (partial = ::separator))
             (filter (partial not= [::separator]))))
  ([n pred separator]
   (if (zero? n)
     (comp (filter pred)
           (interpose separator))
     (fn [rf]
       (let [!context-trail (java.util.LinkedList.)
             !inputs-since-match (volatile! ##Inf)]
         (fn
           ([] (rf))
           ([acc] (rf acc))
           ([acc inp]
            (vswap! !inputs-since-match inc)
            (cond
              (pred inp) (let [acc (cond-> acc
                                     (and (not= @!inputs-since-match ##Inf)
                                          (> @!inputs-since-match (inc (* 2 n)))) (rf separator))]
                           (vreset! !inputs-since-match 0)
                           (loop [acc acc]
                             (if-some [trail-inp (.pollLast !context-trail)]
                               (let [rv (rf acc trail-inp)]
                                 (if (reduced? rv) rv (recur rv)))
                               (rf acc inp))))
              (<= @!inputs-since-match n) (rf acc inp)
              :else (do (.addFirst !context-trail inp)
                        (when (= (.size !context-trail) (inc n))
                          (.removeLast !context-trail))
                        acc)))))))))

(defn postext
  "Returns a stateful transducer which forwards all elements matching
  pred as well as up to n elements leading the match. Each contionous
  span of forwarded elements is delivered as a vector. If separator is
  passed the elements are forwarded directly, separated by separator."
  ([n pred] (comp (postext n pred ::separator)
                  (partition-by (partial = ::separator))
	          (filter (partial not= [::separator]))))
  ([n pred separator]
   (if (zero? n)
     (comp (filter pred)
           (interpose separator))
     (fn [rf]
       (let [!inputs-since-match (volatile! ##Inf)
             !separate-next? (volatile! false)]
         (fn
           ([] (rf))
           ([acc] (rf acc))
           ([acc inp]
            (vswap! !inputs-since-match inc)
            (cond
              (pred inp) (let [acc (cond-> acc
                                     @!separate-next? (rf separator))]
                           (vreset! !separate-next? false)
                           (vreset! !inputs-since-match 0)
                           (rf acc inp))
              (<= @!inputs-since-match n) (rf acc inp)
              (= @!inputs-since-match (inc n)) (do (vreset! !separate-next? true)
                                                   acc)
              :else acc))))))))
