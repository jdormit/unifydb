(ns unifydb.storage
  "Key-value store protocol. Implementations must support string keys
  and arbitrary EDN values."
  (:refer-clojure :exclude [get assoc! contains?]))

(defprotocol IKeyValueStore
  (store-get [store key] "Retrieves the value associated with `key` in `store`.")
  (assoc! [store key val] "Associates `key` with `val` in `store`.")
  (contains? [store key] "Whether the `store` contains the `key`."))

(defn get
  "Retrieves the value associated with `key` in `store`."
  [store key]
  (store-get store key))

;; The store is flat KV store mapping "fact keys" to facts, alongside
;; the EAVT and AVET indices.

;; Indices are implemented as b-trees on top of the KV store.

;; Each index has a top-level key that points to the b-tree root node
;; (:eavt, :avet)

;; Each b-tree node has an arbitrary key. The value is the state of
;; that node - the collection of keys and pointers to the child nodes
;; (the keys of those child nodes).

;; To find all facts that match an index prefix (e.g. all facts about
;; some entity, the prefix of the EAVT index):
;; - start traversal at the root node of that index
;; - in the current node: add all keys that match the prefix to the result list
;; - in the current node: child pointers that are directly after a key
;;   that matches the prefix may also contain keys that match the
;;   prefix. Traverse into all such children
;; - continue traversal until there are no more child nodes to traverse into
;; - return the list of matching keys that has been collected during traversal

;; OR what if the "key" for a fact is the fact itself? E.g. for EAVT the key would be [entity attribute value tx-id]. Then there would be no need to store the facts outside the indices. The downside is that each index would store duplicates of the facts, but that may be unavoidable anyways since it's not clear that I could make keys ordered by EAVT/AVET without including the entire fact in that key anyways.
