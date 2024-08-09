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
   (line-seq (clojure.java.io/reader *file*)))




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
   (line-seq (clojure.java.io/reader *file*)))
