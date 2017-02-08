(defproject twitter-collector "0.1.0-SNAPSHOT"
  :description "A Twitter collector with a Datomic stream."
  :url "https://github.com/replikativ/twitter-collector"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]

                 [io.replikativ/gezwitscher "0.2.0-SNAPSHOT"]
                 [io.replikativ/konserve "0.4.8"]
                 [io.replikativ/replikativ "0.2.1"]
                 #_[com.fzakaria/slf4j-timbre "0.3.2"]
                 [com.datomic/datomic-free "0.9.5544"
                  :exclusions [commons-codec org.fressian/fressian io.netty/netty-all]]
                 #_[incanter "1.9.1"]]

  :main twitter-collector.core)
