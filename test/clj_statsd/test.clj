(ns clj-statsd.test
  (:use [clj-statsd]
        [clojure.test]))

(use-fixtures :each (fn [f] (setup "localhost" 8125) (f)))

(defmacro should-send-expected-stat
  "Assert that the expected stat is passed to the send-stat method
   the expected number of times."
  [expected min-times max-times & body]
  `(let [counter# (atom 0)]
    (with-redefs
      [send-stat (fn [stat#]
                   (is (= ~expected stat#))
                   (swap! counter# inc))]
      ~@body)
    (is (and (>= @counter# ~min-times) (<= @counter# ~max-times)) (str "send-stat called " @counter# " times"))))

(deftest should-send-increment
  (should-send-expected-stat "gorets:1|c" 3 3
    (increment "gorets")
    (increment :gorets)
    (increment "gorets" 1))
  (should-send-expected-stat "gorets:7|c" 1 1
    (increment :gorets 7)))

(deftest should-send-decrement
  (should-send-expected-stat "gorets:-1|c" 3 3
    (decrement "gorets")
    (decrement :gorets)
    (decrement "gorets", 1))
  (should-send-expected-stat "gorets:-7|c" 1 1
    (decrement :gorets 7)))

(deftest should-send-gauge
  (should-send-expected-stat "gaugor:333|g" 3 3
    (gauge "gaugor" 333)
    (gauge :gaugor 333)
    (gauge "gaugor" 333 {:rate 1})))

(deftest should-send-unique
  (should-send-expected-stat "unique:765|s" 2 2
    (unique "unique" 765)
    (unique :unique 765)))

(deftest should-send-timing-with-default-rate
  (should-send-expected-stat "glork:320|ms" 2 2
    (timing "glork" 320)  
    (timing :glork 320)))

(deftest should-send-timing-with-provided-rate
  (should-send-expected-stat "glork:320|ms|@0.990000" 1 10
    (dotimes [n 10] (timing "glork" 320 {:rate 0.99}))))

(deftest should-send-tags
  (should-send-expected-stat "gorets:-1|c|#country:usa" 2 2
    (decrement "gorets" 1 {:tags ["country:usa"]})
    (decrement :gorets 1 {:tags [:country:usa]}))
  (should-send-expected-stat "gaugor:333|g|@0.990000|#country:usa,state:ny" 2 2
    (gauge "gaugor" 333 {:rate 0.99 :tags ["country:usa" "state:ny"]})
    (gauge :gaugor 333 {:rate 0.99 :tags [:country:usa :state:ny]}))

  (should-send-expected-stat "glork:320|ms|#country:canada,other" 2 2
    (timing "glork" 320 {:tags ["country:canada" "other"]})
    (timing "glork" 320 {:tags [:country:canada :other]})))


(deftest should-not-send-stat-without-cfg
  (with-redefs [cfg (atom nil)]
    (should-send-expected-stat "gorets:1|c" 0 0 (increment "gorets"))))

(deftest should-time-code
  (let [cnt (atom 0)]
    (with-redefs [timing
                  (fn [k v {:keys [rate]}]
                    (is (= "test.time" k))
                    (is (>= v 200))
                    (is (= 1.0 rate))
                    (swap! cnt inc))]
      (with-timing "test.time"
        (Thread/sleep 200))
      (with-sampled-timing "test.time" 1.0
        (Thread/sleep 200))
      (with-sampled-tagged-timing "test.time" 1.0 nil
        (Thread/sleep 200))
      (is (= @cnt 3)))))

(deftest should-tag-time-code
  (let [cnt (atom 0)]
    (with-redefs [timing
                  (fn [k v {:keys [rate tags]}]
                    (is (= "test.time" k))
                    (is (>= v 200))
                    (is (= 1.0 rate))
                    (is (= tags [:a :b]))
                    (swap! cnt inc))]
      (with-tagged-timing "test.time" [:a :b]
        (Thread/sleep 200))
      (with-sampled-tagged-timing "test.time" 1.0 [:a :b]
        (Thread/sleep 200))
      (is (= @cnt 2)))))

(deftest should-prefix
  (with-redefs [cfg (atom nil)]
    (setup "localhost" 8125 :prefix "test.stats.")
    (should-send-expected-stat "test.stats.gorets:1|c" 2 2
      (increment "gorets")
      (increment :gorets))))
