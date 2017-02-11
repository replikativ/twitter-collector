(ns twitter-collector.core
  (:require [gezwitscher.core :refer [stream]]
            [clojure.core.async :refer [chan timeout]]
            [kabel.peer :refer [start stop]]
            [konserve
             [filestore :refer [new-fs-store delete-store]]
             [memory :refer [new-mem-store]]]
            [konserve-leveldb.core :refer [new-leveldb-store]]
            [replikativ
             [peer :refer [server-peer client-peer]]
             [stage :refer [connect! create-stage!]]]
            [replikativ.crdt.cdvcs.stage :as cs]
            [replikativ.stage :as s]
            [taoensso.timbre :as timbre]
            [superv.async :refer [go-try <? <?? go-loop-try S]]
            [konserve.core :as k]))

(timbre/set-level! :warn)

(def user "mail:twitter@crawler.com") ;; will be used to authenticate you (not yet)

(def cdvcs-id #uuid "12d49511-e733-4007-937b-460c3794fae9")


(defn new-tweet [pending status]
  (swap! pending (fn [[prev cur] status] [prev (conj cur status)]) status))

(defn store-tweets [stage pending]
  (go-try S
   (let [st (.getTime (java.util.Date.))
         tweets (vec (first (swap! pending (fn [[prev cur]] [cur '()]))))
         tweet-txs (mapv (fn [t] ['add-tweet t]) tweets)]
     (when-not (empty? tweets)
       (<? S (cs/transact! stage [user cdvcs-id] tweet-txs))
       ;; print a bit of stats from time to time
       (when (< (rand) 0.05)
         (println "Date: " (java.util.Date.))
         (println "Pending: " (count (second @pending)))
         (println "Commit count:" (count (get-in @stage [user cdvcs-id :state :commit-graph])))
         (println "Time taken: " (- (.getTime (java.util.Date.))
                                    st) " ms")
         (println "Transaction count: " (count tweet-txs))
         (println "First tweet:" (first tweets)))))))

(defn -main [store-path & topics]
  #_(delete-store store-path)
  (println "Tracking topics:" topics)
  ;; defing here for simple API access on the REPL, use Stuart Sierras component in larger systems
  (let [_ (def store (<?? S (new-leveldb-store store-path)))
        _ (def peer (<?? S (server-peer S store "ws://127.0.0.1:9095")))
        ;; TODO use a queue
        _ (def pending (atom ['() '()]))
        _ (start peer)
        _ (def stage (<?? S (create-stage! user peer)))
        _ (<?? S (cs/create-cdvcs! stage :id cdvcs-id))
        c (chan)]
    (go-loop-try S []
                 (<? S (store-tweets stage pending))
                 (<? S (timeout (* 10 1000)))
                 (recur))
    ;; we def things here, so we can independently stop and start the stream from the REPL
    (defn start-filter-stream []
      (def twitter-stream
        (stream
         (read-string (slurp "credentials.edn"))
         []
         (vec topics)
         (partial new-tweet pending)
         (fn [e]
           (println "Restarting stream due to:" e)
           (twitter-stream)
           (println "Waiting 15 minutes for rate limit.")
           (go-try S (<? S (timeout (* 15 60 1000)))
                   (start-filter-stream))))))
    (start-filter-stream)
    ;; HACK block main thread
    (<?? S c)))



(comment
  (require '[konserve.serializers :as ser] :verbose)

  (import '[konserve.serializers FressianSerializer])

  (use 'konserve.serializers)

  (-main "/tmp/twitter/" "bitcoin")

  (def foo (<?? S (new-mem-store)))


  (<?? S (k/assoc-in foo [:foo] :bar))

  (<?? S (k/get-in foo [:foo]))

  (def test-store (<?? S (new-fs-store "/tmp/bar")))

  (<?? S (k/get-in test-store [:foo]))

  (<?? S (k/assoc-in test-store [:foo] :bar))

  (count (second @pending))
  (<?? (cs/merge! client-stage [user cdvcs-id]
                  (seq (get-in @client-stage [user cdvcs-id :state :heads]))))

  (<?? (connect! client-stage "ws://127.0.0.1:9095"))



  (type (. (clojure.java.io/file "/var/tmp/sstb/") toPath))

  (seq (.list (clojure.java.io/path "/var/tmp/sstb/")))

  (keys @(:state (get-in @peer [:volatile])))

  @(get-in @peer [:volatile :log])

  (keys (:volatile @stage))

  (count (get-in @client-stage [user cdvcs-id :state :commit-graph]))

  (get-in @client-stage [user cdvcs-id :state :heads])

  (count (get-in @stage [user cdvcs-id :state :commit-graph]))


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

  (let [hist (r/commit-history (get-in @client-stage [user cdvcs-id :state :commit-graph])
                               (first (get-in @client-stage [user cdvcs-id :state :heads])))
        commits (->> (take-last 10 hist)
                     (map #(k/get-in client-store [%]))
                     async/merge
                     (async/into [])
                     <??
                     )]
    (->> commits
         (map (fn [{[[_ v]] :transactions :as elem}] v))
         (map #(go-try [% (<? (k/get-in client-store [%]))]))
         async/merge
         (async/into [])
         <??
         (mapcat second)
         (map :text)
         ))


  (count @tweets)

  (take-last 10 @tweets)

  (count (filter (fn [s] (re-find #"racist" s)) @tweets))


  (def realize-it
    (real/reduce-commits client-store {'add-tweets (fn [old txs]
                                                     (swap! old into (doall (map :text txs)))
                                                     old)}
                         tweets
                         (r/commit-history (get-in @client-stage [user cdvcs-id :state :commit-graph])
                                           (first (get-in @client-stage [user cdvcs-id :state :heads])))))


  (<?? (k/get-in mem-store [(first hist)]))

  (-> stage-a
     deref
     (get-in [user gset-id :state :elements])
     count
     )
  (stop peer-a)
  (stop-stream)


  (def error (read-string (slurp "/usr/src/replikativ/error.edn")))

  (keys error)

  (def commit-graph (:commit-graph (:state error)))
  (map (fn [e] (commit-graph e)) (:heads error))
  (def heads(:heads error))

  (require '[replikativ.crdt.cdvcs.meta :as meta])

  (def lcas (:lcas (meta/lowest-common-ancestors commit-graph #{(first heads)}
                                                 commit-graph #{(second heads)})))

  (commit-graph (first lcas))


  )

