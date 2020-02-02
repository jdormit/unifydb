(ns unifydb.query-test
  (:require [clojure.test :refer [deftest is testing]]
            [unifydb.messagequeue :as queue]
            [unifydb.messagequeue.memory :as memqueue]
            [unifydb.query :as query]
            [unifydb.service :as service]
            [unifydb.storage :as store]
            [unifydb.storage.memory :as memstore]
            [unifydb.util :as util]))

(defmacro defquerytest [name [storage-backend queue-backend] facts & body]
  `(deftest ~name
     (let [facts# ~facts
           ~storage-backend (-> {:type :memory} (store/transact-facts! facts#))
           ~queue-backend {:type :memory}
           query-service# (query/new ~queue-backend ~storage-backend)]
       (try
         (service/start! query-service#)
         ~@body
         (finally
           (memstore/empty-store!)
           (memqueue/reset-state!)
           (service/stop! query-service#))))))

(defquerytest simple-matching [storage-backend queue-backend]
  [[1 :name "Ben Bitdiddle" 0 true]
   [1 :job [:computer :wizard] 0 true]
   [1 :salary 60000 1 true]
   [2 :name "Alyssa P. Hacker" 1 true]
   [2 :job [:computer :programmer] 2 true]
   [2 :salary 40000 2 true]
   [2 :supervisor 1 2 true]
   [1 :address [:slumerville [:ridge :road] 10] 2 true]
   [2 :address [:cambridge [:mass :ave] 78] 2 true]
   [2 :address [:cambridge [:mass :ave] 78] 3 false]
   [3 :address [:slumerville [:davis :square] 42] 4 true]]
  (let [db-latest {:tx-id 4}
        db-tx-2 {:tx-id 2}]
    (doseq [{:keys [query db expected name]}
            [{:query '{:find [?e]
                       :where [[?e :name "Ben Bitdiddle"]]}
              :db db-latest
              :expected '[[1]]}
             {:query '{:find [?e ?what]
                       :where [[?e :job [:computer ?what]]]}
              :db db-latest
              :expected '[[2 :programmer]
                          [1 :wizard]]}
             {:query '{:find [?town ?road-and-number]
                       :where [[1 :address [?town & ?road-and-number]]]}
              :db db-latest
              :expected '[[:slumerville [[:ridge :road] 10]]]}
             {:query '{:find [?town ?road-and-number]
                       :where [[2 :address [?town & ?road-and-number]]]}
              :db db-tx-2
              :expected '[[:cambridge [[:mass :ave] 78]]]}
             {:query '{:find [?town ?road-and-number]
                       :where [[2 :address [?town & ?road-and-number]]]}
              :db db-latest
              :expected '[]}
             {:query '{:find [?e]
                       :where [[?e :job [:computer _]]]}
              :db db-latest
              :expected '[[2] [1]]}]]
      (testing (str query)
        (is (= (:results @(util/query queue-backend db query))
               expected))))))

(defquerytest compound-queries [storage-backend queue-backend]
  [[1 :name "Ben Bitdiddle" 0 true]
   [1 :job [:computer :wizard] 0 true]
   [1 :salary 60000 1 true]
   [2 :name "Alyssa P. Hacker" 1 true]
   [2 :job [:computer :programmer] 2 true]
   [2 :salary 40000 2 true]
   [2 :supervisor 1 2 true]
   [1 :address [:slumerville [:ridge :road] 10] 2 true]
   [2 :address [:cambridge [:mass :ave] 78] 2 true]
   [2 :address [:cambridge [:mass :ave] 78] 3 false]
   [3 :address [:slumerville [:davis :square] 42] 4 true]]
  (let [db {:tx-id 4}]
    (doseq [{:keys [query db expected name]}
            [{:query '{:find [?e ?what]
                       :where [[:and
                                [?e :job [:computer ?what]]
                                [?e :salary 60000]]]}
              :db db
              :expected '[[1 :wizard]]}
             {:query '{:find [?e ?what]
                       :where [[?e :job [:computer ?what]]
                               [?e :salary 60000]]}
              :db db
              :expected '[[1 :wizard]]}
             {:query '{:find [?e]
                       :where [[:or
                                [?e :job [:computer :wizard]]
                                [?e :job [:computer :programmer]]]]}
              :db db
              :expected '[[1] [2]]}
             {:query '{:find [?e ?what]
                       :where [[:and
                                [?e :job [:computer ?what]]
                                [:not [?e :salary 60000]]]]}
              :db db
              :expected '[[2 :programmer]]}]]
      (testing (str query)
        (is (= (:results @(util/query queue-backend db query))
               expected))))))

(defquerytest rules [storage-backend queue-backend]
  [[1 :name "Ben Bitdiddle" 0 true]
   [1 :job [:computer :wizard] 0 true]
   [1 :salary 60000 1 true]
   [2 :name "Alyssa P. Hacker" 1 true]
   [2 :job [:computer :programmer] 2 true]
   [2 :salary 40000 2 true]
   [2 :supervisor 1 2 true]
   [1 :address [:slumerville [:ridge :road] 10] 2 true]
   [2 :address [:cambridge [:mass :ave] 78] 2 true]
   [2 :address [:cambridge [:mass :ave] 78] 3 false]
   [3 :address [:slumerville [:davis :square] 42] 4 true]]
  (let [db {:tx-id 4}]
    (doseq [{:keys [query db expected name]}
            [{:query '{:find [?who]
                       :where [(:lives-near ?who 1)]
                       :rules [[(:lives-near ?person1 ?person2)
                                [?person1 :address [?town & _]]
                                [?person2 :address [?town & _]]
                                [:not (:same ?person1 ?person2)]]
                               [(:same ?x ?x)]]}
              :db db
              :expected '[[3]]}]]
      (testing (str query)
        (is (= (:results @(util/query queue-backend db query))
               expected))))))

(defquerytest cardinality [storage-backend queue-backend]
  [[1 :unifydb/schema :favorite-colors 0 true]
   [1 :unifydb/cardinality :cardinality/many 0 true]
   [2 :name "Bob" 0 true]
   [2 :favorite-colors "red" 0 true]
   [2 :favorite-colors "green" 0 true]
   [2 :favorite-colors "blue" 0 true]
   [2 :favorite-colors "blue" 1 false]
   [3 :name "Emily" 2 true]
   [3 :favorite-colors "yellow" 2 true]
   [4 :name "Joe" 3 true]
   [4 :lucky-number 7 3 true]
   [4 :lucky-number 9 4 true]
   [4 :lucky-number 9 5 false]]
  (testing "Cardinality many"
    (is (= [[2 "red"]
            [2 "green"]
            [3 "yellow"]]
           (:results @(util/query queue-backend
                                  {:tx-id 5}
                                  '{:find [?ent ?color]
                                    :where [[?ent :favorite-colors ?color]]})))))
  (testing "Cardinality one double-assertion"))
