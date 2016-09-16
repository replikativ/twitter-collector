# twitter-collector

This is a simple twitter collector dumping data into replikativ. 

## Usage

You need to supply your own credentials in a file `credentials.edn`. 

~~~clojure
{:consumer-key "***"
 :consumer-secret "***"
 :access-token "***"
 :access-token-secret "***"}
~~~

~~~bash
lein run /tmp/twitter-store-location topic1 topic2 ...
~~~

You can connect from client peers and sync the datatype while the server is running.

~~~clojure
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

(def client-store (<?? (new-fs-store "/media/void/232f2140-1c56-478d-a17d-c65ea5325c00/twitter")))

(def client (<?? (client-peer client-store :middleware fetch)))

(def client-stage (<?? (create-stage! user client)))
(<?? (cs/create-cdvcs! client-stage :id cdvcs-id))

;; simple in-memory analysis

(def tweets (atom []))

(def atom-stream (r/stream-into-identity! client-stage [user cdvcs-id]
                                          {'add-tweets (fn [old txs]
                                                         ;; doall is here to free the txs memory as we go
                                                         (swap! old into (doall (map :text txs)))
                                                         old)}
                                          tweets))






;; streaming into datomic

#_(def db-uri "datomic:mem://trail")

(def db-uri "datomic:free://localhost:4334/tweets")


(comment
  (d/create-database db-uri)

  (d/delete-database db-uri))


(def conn (d/connect db-uri))

@(d/transact conn (read-string (slurp "schema.edn")))

(defn tweet-txs [txs]
  (mapv (fn [{:keys [id text timestamp_ms user
                     retweet_count favorite_count] :as tw}]
          (when (< (rand) 0.001)
            (prn "TWEET:" tw))
          {:db/id (d/tempid :db.part/user)
           :tweet/id id
           :tweet/text text
           :tweet/ts (java.util.Date. (Long/parseLong timestamp_ms))
           :tweet/screenname (:screen_name user)
           :tweet/favourite-count favorite_count
           :tweet/retweet-count retweet_count})
        txs))


(def datomic-stream (r/stream-into-identity! client-stage
                                             [user cdvcs-id]
                                             {'add-tweets (fn [conn txs]
                                                            (let [twts (tweet-txs txs)]
                                                              (try
                                                                @(d/transact conn twts)
                                                                (catch Exception e
                                                                  (throw (ex-info "Transacting tweets failed."
                                                                                  {:tweets twts
                                                                                   :error e})))))
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



(<?? (connect! client-stage "ws://localhost:9095"))

;; datomic analysis
(d/q '[:find (count ?t)
       :where
       [?tw :tweet/text ?t]]
     (d/db conn))
~~~



## License

Copyright © 2016 Christian Weilbach, Konrad Kühne

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
