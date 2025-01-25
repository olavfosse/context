(ns no.olavfosse.context
  (:import #?(:clj java.util.LinkedList
              :lpy collections)))

(do
  ;; 25. Jan 2025
  ;;
  ;; It struck me just now that I might be able to circumvent having
  ;; to use host interop. Actually no that wouldn't work. But it is an
  ;; interesting idea as they are essentially the only built-in
  ;; mutable data structures.
  
  ;;    IMPLEMENTATION DETAILS
  ;; ============================
  ;;
  ;; To implement no.olavfosse.context for a new platform simply
  ;; implement the mutable-list (adhoc) interface below.
  ;;
  ;; Fwiw, the interface is not thought out at all. I merely replaced
  ;; the original java.util.LinkedList invocations with mutable-list
  ;; equivalents.
  ;;
  ;;
  ;; FIXME(blocked-by=https://github.com/basilisp-lang/basilisp/issues/1105):
  ;;
  ;; I wanted to use the ml:<fnname> syntax, but the basilisp doesn't
  ;; parse that as a symbol.
  ;;
  ;; java.util.LinkedList/.method makes Basilisp freak out, for now I
  ;; just do .method.
  (defn- mutable-list []
    #?(:clj (java.util.LinkedList.)
       :lpy (collections/deque)))
  (defn- ml-add-first! [ml x]
    #?(:clj (.addFirst ml x)
       :lpy (.appendleft ml x)))
  (defn- ml-remove-last! [ml]
    #?(:clj (.removeLast ml)
       :lpy (.pop ml)))
  (defn- ml-poll-last! [ml]
    #?(:clj (.pollLast ml)
       :lpy (try (.pop ml)
                 (catch python/IndexError _ nil))))
  (defn- ml-size [ml]
    #?(:clj (.size ml)
       :lpy (python/len ml))))

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
       (let [!context-trail (mutable-list)
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
              (do (ml-add-first! !context-trail inp)
                  (when (= (ml-size !context-trail) (inc n))
                    (ml-remove-last! !context-trail)
                    (vreset! !separate-next? (not @!nothing-forwarded?)))
                  acc)
              (do
                (vreset! !nothing-forwarded? false)
                (loop [acc (if @!separate-next?
                             (do
                               (vreset! !separate-next? false)
                               (rf acc separator))
                             acc)]
                  (if-some [trail-inp (ml-poll-last! !context-trail)]
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
       (let [!context-trail (mutable-list)
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
                             (if-some [trail-inp (ml-poll-last! !context-trail)]
                               (let [rv (rf acc trail-inp)]
                                 (if (reduced? rv) rv (recur rv)))
                               (rf acc inp))))
              (<= @!inputs-since-match n) (rf acc inp)
              :else (do (ml-add-first! !context-trail inp)
                        (when (= (ml-size !context-trail) (inc n))
                          (ml-remove-last! !context-trail))
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
