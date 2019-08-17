(ns unifydb.storage)

(defprotocol IStorageBackend
  "The storage backend persists facts and rules"
  (transact-facts! [store facts] "Stores `facts` in `store`.")
  (transact-rules! [store rules] "Stores `rules` in `store`.")
  (fetch-facts [store query tx-id]
    "Retrieves the facts persisted as of the transaction
     denoted by `tx-id` that might unify with `query` from
     the `store`")
  (fetch-rules [store query tx-id]
    "Retrieves the rules persisted as of the transaction
     denoted by `tx-id` whose conditions might unify with `query`
     from the `store`.")
  (get-next-id [store] "Retrieves the next sequential id from the `store`."))
