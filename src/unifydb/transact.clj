(ns unifydb.transact
  (:require [clojure.core.match :refer [match]]
            [unifydb.facts :refer [fact-entity
                                   fact-attribute
                                   fact-value
                                   fact-tx-id
                                   fact-added?]]
            [unifydb.storage :as storage]))

(defn make-new-tx-facts []
  "Returns the list of database operations to make a new transaction entity."
  [[:db/add "db.tx" :db/txInstant (System/currentTimeMillis)]])

(defn process-tx-data [tx-data]
  "Turns a list of transaction statements in the form
   [<db operation> <entity> <attribute> <value>] into
   a list of facts ready to be transacted of the form
   [<entity> <attribute> <value> \"db.tx\" <added?>]"
  (map
   (fn [tx-stmt]
     (match tx-stmt
            [:db/add e a v] [e a v "db.tx" true]
            [:db/retract e a v] [e a v "db.tx" false]))
   tx-data))

(defn resolve-temp-ids [storage-backend facts]
  "Resolves temporary ids in facts to actual database ids."
  (let [ids (reduce
             ;; First generate a unique id for each temporary id in the facts
             (fn [ids fact]
               (let [eid (fact-entity fact)]
                 (if (string? eid)
                   (let [resolved-id (get ids eid)]
                     (if-not resolved-id
                       (assoc ids eid (storage/get-next-id storage-backend))
                       ids))
                   ids)))
             {}
             facts)]
    ;; Then replace all string references to ids in the entity, value or tx-id fields
    ;; with the resolved ids
    (map
     (fn [fact]
       (let [e (or (get ids (fact-entity fact)) (fact-entity fact))
             v (or (get ids (fact-value fact)) (fact-value fact))
             tx-id (get ids (fact-tx-id fact))]
         [e (fact-attribute fact) v tx-id (fact-added? fact)]))
     facts)))


(defn do-transaction [tx-agent-state conn tx-data]
  "Does all necessary processing of `tx-data` and sends it off to the storage backend."
  (let [facts (->> tx-data
                   (into (make-new-tx-facts))
                   (process-tx-data)
                   (resolve-temp-ids (:storage-backend conn)))
        tx-id (fact-tx-id (first facts))
        tx-report {:db-after (assoc conn :tx-id tx-id)
                   :tx-data facts}]
    (storage/transact-facts! (:storage-backend conn) facts)
    tx-report))

(def tx-agent
  "The agent responsible for processing transactions serially."
  (agent {}))

(defn transact [conn tx-data]
  "Transacts `tx-data` into the database represented by `conn`."
  (send-off tx-agent do-transaction conn tx-data))
