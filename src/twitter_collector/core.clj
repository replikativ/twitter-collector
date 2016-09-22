(ns twitter-collector.core
  (:gen-class true)
  (:require [gezwitscher.core :refer [stream]]
            [clojure.core.async :refer [chan timeout]]
            [kabel.http-kit :refer [start stop]]
            [konserve
             [filestore :refer [new-fs-store delete-store]]
             [memory :refer [new-mem-store]]]
            [replikativ
             [peer :refer [server-peer client-peer]]
             [stage :refer [connect! create-stage!]]]
            [replikativ.crdt.cdvcs.stage :as cs]
            [replikativ.stage :as s]
            [taoensso.timbre :as timbre]
            [full.async :refer [go-try <? <?? go-loop-try]]))

(timbre/set-level! :info)

(def user "mail:twitter@crawler.com") ;; will be used to authenticate you (not yet)

(def cdvcs-id #uuid "12d49511-e733-4007-937b-460c3794fae9")


(defn new-tweet [pending status]
  (swap! pending (fn [[prev cur] status] [prev (conj cur status)]) status))

(defn store-tweets [stage pending]
  (go-try
   (let [st (.getTime (java.util.Date.))
         tweets (vec (first (swap! pending (fn [[prev cur]] [cur '()]))))
         tweet-txs [['add-tweets tweets]]]
     (when-not (empty? tweets)
       (<? (cs/transact! stage [user cdvcs-id] tweet-txs))
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
                 (<? (timeout 60000))
                 (recur))
    ;; we def things here, so we can independently stop and start the stream from the REPL
    (defn start-filter-stream []
      (def stop-stream
        (stream
         (read-string (slurp "credentials.edn"))
         []
         (vec topics)
         (partial new-tweet pending)
         (fn [e]
           (println "Restarting stream due to:" e)
           (stop-stream)
           (println "Waiting 15 minutes for rate limit.")
           (go-try (<? (timeout (* 15 60 1000)))
                   (start-filter-stream))))))
    (start-filter-stream)
    ;; HACK block main thread
    (<?? c)))



(comment

  (-main "/tmp/twitter/" "bitcoin")

  (count (second @pending))
  (<?? (cs/merge! client-stage [user cdvcs-id]
                  (seq (get-in @client-stage [user cdvcs-id :state :heads]))))

  (<?? (connect! client-stage "ws://127.0.0.1:9095"))

  (file-seq (clojure.java.io/file "/var/tmp/sstb"))

  (defn list-dir [path]
    (->>
     (for [fn (.list (clojure.java.io/file path))
           :let [afn (str path "/" fn)]]
       (if (.isDirectory (clojure.java.io/file afn))
         [fn (list-dir afn)]
         [fn (java.util.Date. (.toMillis (Files/getLastModifiedTime (. (clojure.java.io/file afn) toPath) (into-array LinkOption []))))]))
     (into {})))

  (list-dir "/var/tmp/sstb/")


  (import '[java.nio.file Files LinkOption])



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

