(ns twitter-collector.core
  (:gen-class true)
  (:require [gezwitscher.core :refer [start-filter-stream]]
            [clojure.core.async :refer [chan timeout]]
            [full.async :refer [<?? go-loop-try]]
            [kabel.http-kit :refer [start stop]]
            [konserve
             [filestore :refer [new-fs-store delete-store]]
             [memory :refer [new-mem-store]]]
            [replikativ
             [peer :refer [server-peer client-peer]]
             [stage :refer [connect! create-stage!]]]
            [replikativ.crdt.simple-gset.stage :as gs]
            [replikativ.crdt.cdvcs.stage :as cs]
            [replikativ.stage :as s]))


(require '[taoensso.timbre :as timbre])
(require '[full.async :refer [go-try <?]])

(timbre/set-level! :info)

(def user "mail:twitter@crawler.com") ;; will be used to authenticate you (not yet)

(def cdvcs-id #uuid "12d39511-e733-4007-937b-460c3794fae9")

(defn new-tweet [pending status]
  (swap! pending (fn [[prev cur] status] [prev (conj cur status)]) status))

(defn store-tweets [stage pending]
  (go-try
   (let [st (.getTime (java.util.Date.))
         tweets (vec (first (swap! pending (fn [[prev cur]] [cur '()]))))
         tweet-txs [['add-tweets tweets]]]
     (when-not (empty? tweets)
       (<? (cs/transact! stage [user cdvcs-id] tweet-txs)))
     (when (< (rand) 0.01)
       (println "Date: " (java.util.Date.))
       (println "Pending: " (count (second @pending)))
       (println "Time taken: " (- (.getTime (java.util.Date.))
                                  st) " ms")
       (println "Transaction count: " (count tweet-txs))
       (println "First:" (first tweet-txs))))))

(defn -main [store-path]
  #_(delete-store store-path)
  (let [_ (def store (<?? (new-fs-store store-path)))
        _ (def peer (<?? (server-peer store "ws://127.0.0.1:9095")))
        ;; TODO use a queue
        _ (def pending (atom ['() '()]))
        _ (start peer)
        _ (def stage (<?? (create-stage! user peer)))
        _ (<?? (cs/create-cdvcs! stage :id cdvcs-id))
        c (chan)]
    (go-loop-try []
                 (<? (store-tweets stage pending))
                 (<? (timeout 1000))
                 (recur))
    (def filter-stream
      (start-filter-stream
       []
       ["trump"]
       (partial new-tweet pending)
       (read-string (slurp "credentials.edn"))))
    ;; HACK block main thread
    (<?? c)))



(comment

  (-main "/tmp/twitter/")

  (count (second @pending))

  (require '[replikativ.p2p.fetch :refer [fetch]])

  (def client-store (<?? (new-fs-store "/media/void/1e843516-40ec-4b2b-83d2-c796ee312a59/twitter/")))

  (def client (<?? (client-peer client-store :middleware fetch)))

  (def client-stage (<?? (create-stage! user client)))
  (<?? (cs/create-cdvcs! client-stage :id cdvcs-id))

  (<?? (cs/merge! client-stage [user cdvcs-id]
                  (seq (get-in @client-stage [user cdvcs-id :state :heads]))))

  (<?? (connect! client-stage "ws://127.0.0.1:9095"))

  (<?? (connect! client-stage "ws://topiq.es:9095"))

  (keys @(:state (get-in @peer [:volatile])))

  @(get-in @peer [:volatile :log])

  (keys (:volatile @stage))

  (count (get-in @client-stage [user cdvcs-id :state :commit-graph]))

  (get-in @client-stage [user cdvcs-id :state :heads])

  (count (get-in @stage [user cdvcs-id :state :commit-graph]))

  (require '[replikativ.crdt.cdvcs.realize :as r])

  (def hist
    (r/commit-history (get-in @client-stage [user cdvcs-id :state :commit-graph])
                      (first (get-in @client-stage [user cdvcs-id :state :heads]))))
 
  (require '[clojure.core.async :as async]
           '[konserve.core :as k])

  (def commits
    (->> (take 10 hist)
         (map #(k/get-in client-store [%]))
         async/merge
         (async/into [])
         <??
         ))

  (filter nil? commits)

  (map (comp keys second) commits)
  (map (fn [{[[_ v]] :transactions :as elem}] (if-not v elem)) commits)

  (->> commits
       (map (fn [{[[_ v]] :transactions :as elem}] v))
       (filter identity)
       (map #(go-try [% (<? (k/get-in client-store [%]))]))
       async/merge
       (async/into [])
       <??
       (map first))

  (<?? (k/get-in mem-store [(first hist)]))

  (-> stage-a
     deref
     (get-in [user gset-id :state :elements])
     count
     )
  (stop peer-a)
  (stop-stream)



  )

