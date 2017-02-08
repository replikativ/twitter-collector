(ns twitter-collector.ormap
  (:require [twitter-collector.core :refer [user cdvcs-id]]
            [replikativ.crdt.ormap.realize :as rors]
            [replikativ.peer :refer [client-peer]]
            [replikativ.p2p.fetch :refer [fetch]]
            [replikativ.stage :refer [create-stage! connect!]]
            [replikativ.crdt.cdvcs.stage :as cs]
            [replikativ.crdt.ormap.stage :as ors]
            [konserve.filestore :refer [new-fs-store]]
            [konserve.memory :refer [new-mem-store]]
            [clojure.core.async :as async]
            [superv.async :refer [<?? S]]
            [replikativ.stage :as s]
            [konserve.core :as k]
            [replikativ.crdt.cdvcs.stage :as cs]
            [datomic.api :as d]
            [taoensso.timbre :as timbre]))




;; better not to use a memory store with gib of data ;)
(def client-store (<?? S (new-mem-store)
                       #_(new-fs-store "/tmp/foo"
                                     #_"/media/void/1e843516-40ec-4b2b-83d2-c796ee312a59/twitter/")))

(def client (<?? S (client-peer S client-store)))

(def client-stage (<?? S (create-stage! user client)))

#_(<?? (cs/create-cdvcs! client-stage :id cdvcs-id))

(def ormap-id #uuid "dd43955b-c5c8-4163-95a3-97c0ca82fdf9")

(<?? S (ors/create-ormap! client-stage :id ormap-id))

;; in memory

(def tweets (atom 0))

(require '[taoensso.timbre :as timbre])

(timbre/set-level! :warn)

(def atom-stream (rors/stream-into-identity! client-stage [user ormap-id]
                                          {'+ (fn [old txs]
                                                (swap! old + txs)
                                                old)
                                           '- (fn [old txs]
                                                (swap! old - txs)
                                                old)}
                                          tweets))

(time
 (doseq [i (range 10000)]
   (<?? S (ors/assoc! client-stage [user ormap-id] i [['+ i]]))))

(time
 (doseq [i (range 10000)]
   (<?? S (ors/dissoc! client-stage [user ormap-id] i [['- i]]))))



