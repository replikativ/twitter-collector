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
            [replikativ.realize :as real]
            [replikativ.peer :refer [client-peer]]
            [replikativ.stage :refer [create-stage! connect!]]
            [replikativ.crdt.cdvcs.stage :as cs]
            [konserve.filestore :refer [new-fs-store]]
            [clojure.core.async :as async]))


(def client-store (<?? (new-fs-store "/tmp/twitter-store-location-client/")))

(def client (<?? (client-peer client-store)))

(def client-stage (<?? (create-stage! user client)))
(<?? (cs/create-cdvcs! client-stage :id cdvcs-id))


(def tweets (atom []))

(def close-stream (r/stream-into-atom! client-stage [user cdvcs-id]
                                       {'add-tweets (fn [old txs]
                                                      (swap! old into (doall (map :text txs)))
                                                      old)}
                                       tweets))


(comment
  (<?? (connect! client-stage "ws://topiq.es:9095"))

  (count @tweets)

  ;; simple live analysis
  (take-last 10 @tweets)

  (count (filter (fn [s] (re-find #"racist" s)) @tweets))

  (async/close! close-stream)
  ;; flush
  (while (async/poll! close-stream)))
~~~



## License

Copyright © 2016 Christian Weilbach, Konrad Kühne

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
