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
            [velib.serialization :refer :all])
  (:import [java.io File])
  (:gen-class))


(defn get-struct-map [xml]
  " Transforms the xml String into an InputStream and parse it (XML) "
  (let [stream (java.io.ByteArrayInputStream. (.getBytes (.trim xml)))]
    (parse stream)))

(defn get-station-attrib
  [xml tag]
  (for [x (xml-seq xml) :when (= tag (:tag x))] (first (:content x))))

(def cartography-file "data/cartography")
(def cartography-url "http://www.velib.paris.fr/service/carto")
(def station-file "data/station-")
(def station-url "http://www.velib.paris.fr/service/stationdetails/paris/")

(defn get-station-body
  [number]
  ((http/get (str station-url number) 
             {:socket-timeout 2000, :conn-timeout 2000}) :body))

(defn update-station
  " Takes a station dictionary and does the GET + updating "
  [station]
  (let [station-xml (get-struct-map (get-station-body (:number station)))
        available (Integer/parseInt (first (get-station-attrib station-xml :available)))
        free (Integer/parseInt (first (get-station-attrib station-xml :available)))
        updated (Integer/parseInt (first (get-station-attrib station-xml :updated)))]
    (if (> updated (:updated station))
      {:number (:number station),
       :updated updated,
       :polls (conj (:polls station) 
                    {:timestamp updated, :available available, :free free})}
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
  (pmap #(write-station (update-station (read-station ((% :attrs) :number))))
       (filter #(not= ((% :attrs) :number) nil)
               (filter #(= :marker (:tag %))
                           (xml-seq (parse (File. cartography-file)))))))

