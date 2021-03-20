(ns unifydb.kvstore.memory
  (:require [unifydb.kvstore :as storage])
  (:refer-clojure :rename {contains? map-contains?}))

(defrecord InMemoryKeyValueStore [state]
  storage/IKeyValueStore
  (store-get [store key]
    (get @(:state store) key))
  (assoc! [store key val]
    (swap! (:state store) assoc key val))
  (dissoc! [store key]
    (swap! (:state store) dissoc key))
  (contains? [store key]
    (map-contains? @(:state store) key)))

(defn new []
  (->InMemoryKeyValueStore (atom {})))