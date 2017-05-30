(ns twitter-collector.client
  (:require [twitter-collector.core :refer [user cdvcs-id]]
            [replikativ.crdt.cdvcs.realize :as r]
            [replikativ.peer :refer [client-peer]]
            [replikativ.p2p.fetch :refer [fetch]]
            [replikativ.stage :refer [create-stage! connect!]]
            [replikativ.crdt.cdvcs.stage :as cs]
            [konserve.serializers :refer [fressian-serializer]]
            [konserve.protocols :refer [-serialize
                                        -deserialize]]
            [konserve.filestore :refer [new-fs-store]]
            [konserve-leveldb.core :refer [new-leveldb-store]]
            [clojure.core.async :as async]
            [superv.async :refer [<?? S]]
            [replikativ.stage :as s]
            [konserve.core :as k]
            [taoensso.timbre :as timbre]
            [replikativ.crdt.cdvcs.stage :as cs]
            [datomic.api :as d])
  (:import [java.io ByteArrayInputStream]))

(timbre/set-level! :warn)

(comment
  ;; causes NPE in slf4j (?)
  (timbre/merge-config! {:ns-whitelist ["replikativ.*"]})

  )

;; replikativ
(def client-store (<?? S (new-leveldb-store "/tmp/client-twitter")))


(def client (<?? S (client-peer S client-store :middleware fetch)))

(comment
  (stop client))

(def client-stage (<?? S (create-stage! user client)))
(<?? S (cs/create-cdvcs! client-stage :id cdvcs-id))

;; simple in-memory analysis

(def tweets (atom []))

(def atom-stream (r/stream-into-identity! client-stage [user cdvcs-id]
                                          {'add-tweets (fn [old id]
                                                         (let [tweets
                                                               (<?? S (k/bget client-store id
                                                                              #(-deserialize (fressian-serializer) (atom {})
                                                                                             (:input-stream %))))]
                                                           (swap! old into (map :text tweets)))
                                                        old)}
                                          tweets))






;; streaming into datomic

#_(def db-uri "datomic:mem://trail")

(def db-uri "datomic:free://localhost:4334/tweets")


(comment
  (d/create-database db-uri)

  @(d/transact conn (read-string (slurp "schema.edn")))

  (d/delete-database db-uri))


(def conn (d/connect db-uri))


(defn tweet-tx [{:keys [id text timestamp_ms user
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


(def datomic-stream (r/stream-into-identity! client-stage
                                             [user cdvcs-id]
                                             {'add-tweet (fn [conn twt]
                                                           (try
                                                             @(d/transact conn [(tweet-tx twt)])
                                                             (catch Exception e
                                                               (throw (ex-info "Transacting tweet failed."
                                                                               {:tweets twt
                                                                                :error e}))))
                                                           conn)}
                                             conn
                                             :applied-log :datomic-analysis
                                             ;; not tested yet
                                             :reset-fn (fn [old-conn conflict]
                                                         (prn "Resetting Datomic because of " conflict)
                                                         (d/delete-database db-uri)
                                                         (d/create-database db-uri)
                                                         (def conn (d/connect db-uri))
                                                         @(d/transact conn (read-string (slurp "schema.edn")))
                                                         conn)))






(comment
  ;; test server, might be broken
  (<?? S (connect! client-stage "ws://localhost:9095"))

  (<?? S (k/get-in client-store [(last (<?? S (k/get-in client-store [[user cdvcs-id :log]])))]))


  ;; whitelist authors

  (count @tweets)


  ;; simple live analysis
  (take-last 10 @tweets)

  (count (filter (fn [s] (re-find #"ethereum" s)) @tweets))

  (async/close! (:close-ch atom-stream))

  ;; datomic analysis
  (d/q '[:find (count ?tw) .
         :where
         [?tw :tweet/text ?t]]
       (d/db conn))

  (require '[incanter.core :refer :all]
           '[incanter.charts :refer :all])


  ;; test tweets, do not transact this in cdvcs when running against a concurrent
  ;; collector or you might induce conflicts
  (<?? (cs/transact! client-stage [user cdvcs-id] [['add-tweets [{:text "Foo bar"}]]
                                                   ['add-tweets [{:text "More foo"}]]]))

  (async/close! (:close-ch datomic-stream))
  (<?? (k/assoc-in client-store [:datomic-analysis] nil)) ;; reset log
  (<?? (k/log client-store :datomic-analysis))

  ;; most active accounts
  (let [res (->> (d/q '[:find ?u (count ?t)
                       :where
                       [?t :tweet/screenname ?u]]
                      (d/db conn))
                 (sort-by second)
                 reverse
                 (take 5))
        c (count res)
        screenname (map first res)
        tweet-count (map second res)]
    (view (bar-chart screenname tweet-count))) 


  (->> (d/q '[:find ?txt ?fav-count
             :where
             [?t :tweet/text ?txt]
             [?t :tweet/favourite-count ?fav-count]]
            (d/db conn))
       (sort-by second)
       reverse
       (take 10))

  (->> (d/q '[:find ?txt
              :in $
              :where
              [(fulltext $ :tweet/text "ethereum")
               [[?entity ?name ?tx ?score]]]
              [?entity :tweet/text ?txt]]
            (d/db conn))
       (take 10))



  )
(comment
  {:in_reply_to_screen_name nil,
   :is_quote_status false,
   :coordinates nil,
   :filter_level "low",
   :in_reply_to_status_id_str nil,
   :place nil,
   :timestamp_ms "1473988814804",
   :possibly_sensitive false,
   :geo nil,
   :in_reply_to_status_id nil,
   :entities {:urls [{:display_url "ow.ly/HUA3304e1gl",
                      :indices [88 111],
                      :expanded_url "http://ow.ly/HUA3304e1gl",
                      :url "https://t.co/A3eUEe8w6o"}],
              :hashtags [],
              :user_mentions [],
              :symbols []},
   :source "<a href=\"http://www.hootsuite.com\" rel=\"nofollow\">Hootsuite</a>",
   :lang "en",
   :in_reply_to_user_id_str nil,
   :id 776591492345462784,
   :contributors nil,
   :truncated false,
   :retweeted false,
   :in_reply_to_user_id nil,
   :id_str "776591492345462784",
   :favorited false,
   :user {:description "We pride ourselves on offering a professional service and are always 80% accountable for our client’s funds and thriving to be the best investment program",
          :profile_link_color "0084B4",
          :profile_sidebar_border_color "C0DEED",
          :profile_image_url "http://pbs.twimg.com/profile_images/378800000434517729/6afeb27978d0a364bedada23b2cac0db_normal.jpeg
",
          :profile_use_background_image true,
          :default_profile true,
          :profile_background_image_url "http://abs.twimg.com/images/themes/theme1/bg.png",
          :is_translator false,
          :profile_text_color "333333",
          :name "OneHourHYIP",
          :profile_background_image_url_https "https://abs.twimg.com/images/themes/theme1/bg.png",
          :favourites_count 0,
          :screen_name "onehourhyip",
          :listed_count 89,
          :profile_image_url_https "https://pbs.twimg.com/profile_images/378800000434517729/6afeb27978d0a364bedada23b2cac0db_normal.jpeg",
          :statuses_count 106074,
          :contributors_enabled false,
          :following nil, :lang "en",
          :utc_offset 25200,
          :notifications nil,
          :default_profile_image false,
          :profile_background_color "C0DEED",
          :id 1847105953,
          :follow_request_sent nil,
          :url "http://www.onehourhyip.com",
          :time_zone "Krasnoyarsk",
          :profile_sidebar_fill_color "DDEEF6",
          :protected false,
          :profile_background_tile false,
          :id_str "1847105953",
          :geo_enabled false,
          :location nil,
          :followers_count 5670,
          :friends_count 5305,
          :verified false,
          :created_at "Mon Sep 09 14:57:58 +0000 2013 "},
   :retweet_count 0,
   :favorite_count 0,
   :created_at "Fri Sep 16 01:20:14 +0000 2016",
   :text "More money won't solve your debt problems,$200,000 monthly,  forex stock fund bitcoin . https://t.co/A3eUEe8w6o"})
