;; Quality Assurance
;;
;; Requirements which are not tested for:
;;
;; The context transducers must emit their elements as soon as
;; possible. Take xform (postext 2 (partial = 1))), if passed 1, it
;; must immediately forward it, not wait until it recevies 2 more
;; elements.
(ns qa
  (:require [no.olavfosse.context :refer :all]))

(def =1 (partial = 1))

(def tt '(;; n = 1
          {:input [],
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
           (context 1 =1) [[0 1 0 0 1]],
           (context 1 =1 :sep) [0 1 0 0 1]}
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
           (postext 1 =1 :sep) [1 1 0 :sep 1 1 0],
           (context 1 =1) [[0 1 1 0 0 1 1 0]],
           (context 1 =1 :sep) [0 1 1 0 0 1 1 0]}

          ;; n = 0
          {:input [],
           (pretext 0 =1) [],
           (pretext 0 =1 :sep) [],
           (postext 0 =1) [],
           (postext 0 =1 :sep) [],
           (context 0 =1) [],
           (context 0 =1 :sep) []}
          {:input [1],
           (pretext 0 =1) [[1]],
           (pretext 0 =1 :sep) [1],
           (postext 0 =1) [[1]],
           (postext 0 =1 :sep) [1],
           (context 0 =1) [[1]],
           (context 0 =1 :sep) [1]}
          {:input [0 1 1 0 0 1 1 0 0],
           (pretext 0 =1) [[1] [1] [1] [1]],
           (pretext 0 =1 :sep) [1 :sep 1 :sep 1 :sep 1],
           (postext 0 =1) [[1] [1] [1] [1]],
           (postext 0 =1 :sep) [1 :sep 1 :sep 1 :sep 1],,
           (context 0 =1) [[1] [1] [1] [1]],
           (context 0 =1 :sep) [1 :sep 1 :sep 1 :sep 1]}

          ;; n = 2
          {:input [],
           (pretext 2 =1) [],
           (pretext 2 =1 :sep) [],
           (postext 2 =1) [],
           (postext 2 =1 :sep) [],
           (context 2 =1) [],
           (context 2 =1 :sep) []}
          {:input [1],
           (pretext 2 =1) [[1]],
           (pretext 2 =1 :sep) [1],
           (postext 2 =1) [[1]],
           (postext 2 =1 :sep) [1],
           (context 2 =1) [[1]],
           (context 2 =1 :sep) [1]}
          {:input [0 1 1 0 0 1 1 0 0],
           (pretext 2 =1) [[0 1 1 0 0 1 1]],
           (pretext 2 =1 :sep) [0 1 1 0 0 1 1],
           (postext 2 =1) [[1 1 0 0 1 1 0 0]],
           (postext 2 =1 :sep) [1 1 0 0 1 1 0 0],,
           (context 2 =1) [[0 1 1 0 0 1 1 0 0]],
           (context 2 =1 :sep) [0 1 1 0 0 1 1 0 0]}))

(do 
  (doseq [{:as tc input :input} tt]
    (doseq [[k v] tc]
      (when-not (= k :input)
        (when v
          (eval `(assert (= (into [] ~k ~input)
                            ~(into [] (eval k) input)
                            ~v)))))))
  (println "All tests passed 🥳"))
    
