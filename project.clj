(defproject twitter-collector "0.1.0-SNAPSHOT"
  :description "A Twitter collector with a Datomic stream."
  :url "https://github.com/replikativ/twitter-collector"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]

                 [io.replikativ/gezwitscher "0.2.0-SNAPSHOT"]
                 [io.replikativ/konserve-leveldb "0.1.1"]
                 [io.replikativ/replikativ "0.2.4-SNAPSHOT"]
                 [employeerepublic/slf4j-timbre "0.4.2"]
                 [com.datomic/datomic-free "0.9.5554"
                  :exclusions [commons-codec]]
                 [incanter "1.5.7"]]

  :main twitter-collector.core)
