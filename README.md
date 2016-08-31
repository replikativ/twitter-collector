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
(require '[full.async :refer [<??]]
         '[konserve
             [filestore :refer [new-fs-store delete-store]]
             [memory :refer [new-mem-store]]]
            '[replikativ
             [peer :refer [client-peer]]
             [stage :refer [connect! create-stage!]]]
            '[replikativ.crdt.cdvcs.stage :as cs]
            '[replikativ.stage :as s])


  (def client-store (<?? (new-fs-store "/tmp/another-twitter-store/")))

  (def client (<?? (client-peer client-store)))

  (def client-stage (<?? (create-stage! user client)))
  (<?? (cs/create-cdvcs! client-stage :id cdvcs-id))

  (<?? (connect! client-stage "ws://127.0.0.1:9095"))
~~~


## License

Copyright © 2016 Christian Weilbach, Konrad Kühne

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
