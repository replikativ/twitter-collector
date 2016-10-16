(defproject twitter-collector "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]

                 [commons-codec "1.10"]
                 [io.replikativ/konserve "0.4.3"]
                 [io.replikativ/konserve-leveldb "0.1.0"]

                 [io.replikativ/gezwitscher "0.2.0-SNAPSHOT"]
                 [io.replikativ/replikativ "0.2.0-SNAPSHOT"]
                 [com.fzakaria/slf4j-timbre "0.3.1"]
                 [com.datomic/datomic-free "0.9.5394"]
                 #_[incanter "1.9.1"]]

  :main twitter-collector.core)
