; <station>
;   <available>1</available>      available bikes
;   <free>5</free>                free spots
;   <total>20</total>             total spots
;   <ticket>1</ticket>            gives tickets
;   <open>1</open>                is open
;   <bonus>0</bonus>              is V+ station
;   <updated>1346403607</updated> Unix timestamp format
; </station>

(ns velib.core
  (:require [clojure.zip :as zip]
            [clj-http.client :as http]
            [clojure.xml :refer :all]
            [clojure.java.io :refer :all]
            [velib.serialization :refer :all]
            [taoensso.carmine :as r])
  (:import [java.io File])
  (:gen-class))

;;; My defines

(def cartography-file "data/cartography")
(def cartography-url "http://www.velib.paris.fr/service/carto")
(def station-file "data/station-")
(def station-url "http://www.velib.paris.fr/service/stationdetails/paris/")
(def max-rolling-average-n 200) ; that is 100 days for 1 hour windows at 2 polling/hour

;;; Simple settings for Carmine (redis interface) shamelessly c/p
(def pool (r/make-conn-pool :max-active 8))

(def spec-server1 (r/make-conn-spec :host     "127.0.0.1"
                                    :port     6379
                                    :timeout  4000))

(defmacro carmine
    "Acts like (partial with-conn pool spec-server1)."
    [& body] `(r/with-conn pool spec-server1 ~@body))
;;; /Carmine

;;; Parsing XML, my cup of tea... /sarcasm
(defn get-struct-map [xml]
  " Transforms the xml String into an InputStream and parse it (XML) "
  (let [stream (java.io.ByteArrayInputStream. (.getBytes (.trim xml)))]
    (parse stream)))

(defn get-station-attrib
  [xml tag]
  " Extract 'tag' contents in a given xml "
  (for [x (xml-seq xml) :when (= tag (:tag x))] (first (:content x))))

(defn get-station-body
  " Get updates on stations, wrapping clj-http + timouts "
  [number]
  (try
    ((http/get (str station-url number) 
               {:socket-timeout 2000, :conn-timeout 2000}) :body)
    (catch Exception e (prn (str "Couldn't get station: " number)))))

(defn is-weekend?
  " Simply tells if a given timestamp happens to be during a weekend day "
  [timestamp] ; we have a seconds based timestamp, to convert to milliseconds
  (let [day (.getDay (java.sql.Timestamp. (* 1000 timestamp)))]
    (or (= day 0) (= day 6))))

(defn average [l] 
  " An empty-list safe average "
  (if (= '() l)
    -1
    (/ (reduce + l) (count l))))

(defn update-statistics
  " Do the rolling averages for all periods of time and store it in Redis "
  [number tsl maxn]

  (defn write-redis
    " Recording all info in redis "
    [number avfree weekend hour avg]
    (let [day (if weekend ":weekend" ":weekday")]
      (carmine (r/set (str "station:" number avfree day ":" hour) avg))))

  (for [avfree '(:available :free)]
    (for [weekend '(false, true)]
      (for [hour (range 0 24)]
        (write-redis number avfree weekend hour (average 
          (map #(avfree %) 
               (take maxn 
                     (filter #(and (= weekend (is-weekend? (:timestamp %)))
                                    (= (.getHours (java.sql.Timestamp. (* 1000 (:timestamp %)))) hour)) 
                     tsl)))))))))

;(update-statistics 42424242 '({:timestamp 666666999999 :available 6 :free 9}) max-rolling-average-n)

(defn update-station-full
  " Takes a station dictionary and does the GET + updating 
  !!! full of side effects "
  [station]
  (let [station-xml (get-struct-map (get-station-body (:number station)))
        available (Integer/parseInt (first (get-station-attrib station-xml :available)))
        free (Integer/parseInt (first (get-station-attrib station-xml :available)))
        updated (Integer/parseInt (first (get-station-attrib station-xml :updated)))]
    (if (> updated (:updated station))
      (let [new-polls (conj (:polls station) 
                            {:timestamp updated, :available available, :free free})
            number (:number station)]
        (do
          (update-statistics number new-polls max-rolling-average-n))
          {:number number,
           :updated updated,
           :polls new-polls})
      station)))

(defn read-station
  " Returns the stations hash-map (either empty or to previous value) "
  [number]
  (if (.exists (File. station-file))
    (deserialize-read (str station-file number))
    {:number number,
     :updated 0,
     :polls '()})) 
  ; polls is a list of maps {:timestamp TS, :available NUMBER, :free NUMBER}

(defn write-station
  " Write/serialize the given station hash-map "
  [station]
  (serialize-write station (str station-file (station :number))))

(defn -main [& args]
  " In case we don't have the cartography, go fetch it from the Velib API "
  ;(if (not (.exists (File. cartography-file)))
  ; above commented => update it everytime to get updated stations
  (with-open [wrtr (writer cartography-file)]
    (.write wrtr ((http/get cartography-url) :body)))
  ;)

  " For each station in the cartography, get its status and update it "
  " In case we don't have all the stations, update the stations map "
  (pmap #(write-station (update-station-full (read-station ((% :attrs) :number))))
       (filter #(not= ((% :attrs) :number) nil)
               (filter #(= :marker (:tag %))
                           (xml-seq (parse (File. cartography-file)))))))

