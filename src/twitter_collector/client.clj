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
  (d/q '[:find (count ?t)
         :where
         [?tw :tweet/text ?t]]
       (d/db conn))

  ;; test tweets
  (<?? (cs/transact! client-stage [user cdvcs-id] [['add-tweets [{:text "Foo bar"}]]
                                                   ['add-tweets [{:text "More foo"}]]]))

  (async/close! datomic-stream)
  (<?? (k/assoc-in client-store [:datomic-analysis] nil)) ;; reset log
  (<?? (k/log client-store :datomic-analysis))

  (d/q '[:find (count ?u)
         :where
         [?t :tweet/screenname ?u]]
       (d/db conn))

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
