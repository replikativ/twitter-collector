(ns twitter-collector.client
  (:require [twitter-collector.core :refer [user cdvcs-id]]
            [replikativ.crdt.cdvcs.realize :as r]
            [replikativ.peer :refer [client-peer]]
            [replikativ.p2p.fetch :refer [fetch]]
            [replikativ.stage :refer [create-stage! connect!]]
            [replikativ.crdt.cdvcs.stage :as cs]
            [konserve.filestore :refer [new-fs-store]]
            [clojure.core.async :as async]
            [full.async :refer [<??]]
            [replikativ.stage :as s]
            [konserve.core :as k]
            [replikativ.crdt.cdvcs.stage :as cs]
            [datomic.api :as d]))

;; replikativ

;; better not to use a memory store with gib of data ;)
(def client-store (<?? (new-fs-store "/tmp/foo"
                                     #_"/media/void/1e843516-40ec-4b2b-83d2-c796ee312a59/twitter/")))

(def client (<?? (client-peer client-store)))

(def client-stage (<?? (create-stage! user client)))
(<?? (cs/create-cdvcs! client-stage :id cdvcs-id))

;; in memory

(def tweets (atom []))

(def atom-stream (r/stream-into-identity! client-stage [user cdvcs-id]
                                          {'add-tweets (fn [old txs]
                                                         ;; doall is here to free the txs memory as we go
                                                         (swap! old into (doall (map :text txs)))
                                                         old)}
                                          tweets))


;; streaming into datomic

(def db-uri "datomic:mem://trail")

(d/create-database db-uri)

(comment
  (d/delete-database "datomic:mem://trail"))

(def conn (d/connect db-uri))

@(d/transact conn (read-string (slurp "schema.edn")))

(defn tweet-txs [txs]
  (mapv (fn [{:keys [text]}]
          {:db/id
           (d/tempid :db.part/user)
           :tweet/text text})
        txs))


(def datomic-stream (r/stream-into-identity! client-stage
                                             [user cdvcs-id]
                                             {'add-tweets (fn [conn txs]
                                                            @(d/transact conn (tweet-txs txs))
                                                            conn)}
                                             conn
                                             :applied-log :datomic-analysis
                                             ;; not tested yet
                                             :reset-fn (fn [old-conn] (prn "Resetting Datomic!")
                                                         (d/delete-database db-uri)
                                                         (d/create-database db-uri)
                                                         (def conn (d/connect db-uri))
                                                         @(d/transact conn (read-string (slurp "schema.edn")))
                                                         conn)))






(comment
  ;; test server, might be broken
  (<?? (connect! client-stage "ws://topiq.es:9095"))

  (<?? (k/get-in client-store [(last (<?? (k/get-in client-store [[user cdvcs-id :log]])))]))

  (let [{{new-heads :heads
          new-commit-graph :commit-graph} :op :as op} {:heads [1 2 3]}]
    op)

  (count @tweets)

  ;; simple live analysis
  (take-last 10 @tweets)

  (count (filter (fn [s] (re-find #"racist" s)) @tweets))

  (async/close! atom-stream)

  ;; datomic analysis
  (d/q '[:find ?t
         :where
         [?tw :tweet/text ?t]]
       (d/db conn))

  ;; test tweets
  (<?? (cs/transact! client-stage [user cdvcs-id] [['add-tweets [{:text "Foo bar"}]]
                                                   ['add-tweets [{:text "More foo"}]]]))

  (async/close! datomic-stream)
  (<?? (k/assoc-in client-store [:datomic-analysis] nil)) ;; reset log
  (<?? (k/log client-store :datomic-analysis))


  )
