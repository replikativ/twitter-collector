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
            [replikativ.stage :as s]))


(def client-store (<?? (new-fs-store "/media/void/1e843516-40ec-4b2b-83d2-c796ee312a59/twitter/")))

;; do not crypto hash (is default) for higher throughput (only do this for
;; connections with ;; trusted peers)
(def client (<?? (client-peer client-store :middleware fetch)))

(def client-stage (<?? (create-stage! user client)))
(<?? (cs/create-cdvcs! client-stage :id cdvcs-id))

(def tweets (atom []))

(def close-stream (r/stream-into-atom! client-stage [user cdvcs-id]
                                       {'add-tweets (fn [old txs]
                                                      ;; doall is here to free the txs memory as we go
                                                      (swap! old into (doall (map :text txs)))
                                                      old)}
                                       tweets))


(comment
  (<?? (connect! client-stage "ws://topiq.es:9095"))

  (require '[konserve.core :as k])
  (require '[replikativ.crdt.cdvcs.stage :as cs])

  (<?? (cs/transact! client-stage [user cdvcs-id] [['+ 1]]))

  (<?? (k/get-in client-store [(last (<?? (k/get-in client-store [[user cdvcs-id :log]])))]))

  (async/close! close-stream)

  (let [{{new-heads :heads
          new-commit-graph :commit-graph} :op :as op} {:heads [1 2 3]}]
    op)

  (count @tweets)

  ;; simple live analysis
  (take-last 10 @tweets)

  (count (filter (fn [s] (re-find #"racist" s)) @tweets))

  (async/close! close-stream)
  ;; flush
  (while (async/poll! close-stream)))
