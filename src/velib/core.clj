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
  (:gen-class))


(use 'clojure.java.io)
(use 'clojure.xml)
(require '[clojure.zip :as zip])
(require '[clj-http.client :as http])
(import '(java.io File))
(import '(java.io ByteArrayInputStream))

(defn get-struct-map [xml]
  " Transforms the xml String into an InputStream and parse it (XML) "
  (let [stream (ByteArrayInputStream. (.getBytes (.trim xml)))]
    (parse stream)))

(defn get-station-attrib
  [xml tag]
  (for [x (xml-seq xml) :when (= tag (:tag x))] (first (:content x))))

(def cartography-file "data/cartography")
(def cartography-url "http://www.velib.paris.fr/service/carto")
(def stations-file "data/stations")
(def station-url "http://www.velib.paris.fr/service/stationdetails/paris/")

(defn serializable? 
  " The most simple of databases "
  [v]
  (instance? java.io.Serializable v))

(defn serialize 
  "Serializes value, returns a byte array"
  [v]
  (let [buff (java.io.ByteArrayOutputStream. 1024)]
    (with-open [dos (java.io.ObjectOutputStream. buff)]
      (.writeObject dos v))
    (.toByteArray buff)))

(defn serialize-write
  " Serializes value, writing in file 'filename' "
  [v filename]
  (with-open [wrtr (writer filename)]
    (.write wrtr (serialize v))))

(defn deserialize 
  "Accepts a byte array, returns deserialized value"
  [bytes]
  (with-open [dis (java.io.ObjectInputStream.
                    (java.io.ByteArrayInputStream. bytes))]
    (.readObject dis)))

(defn deserialize-read
  " Accepts a filename, returns deserialized value "
  [filename]
  (with-open [rdr (reader filename)]
    (deserialize (.read rdr))))

(defn get-station-body
  [number]
  ((http/get (str station-url number)) :body))

(defn update-station
  " Takes a station number and does the GET + updating "
  [number]
  (let [station-xml (get-station-body number)
        available (get-station-attrib station-xml :available)
        free (get-station-attrib station-xml :available)
        updated (get-station-attrib station-xml :updated)]

    ))

(defn -main [& args]
  " In case we don't have the cartography, go fetch it from the Velib API "
  ; TODO update it everytime to get updated stations
  ;(if (not (.exists (File. cartography-file)))
  (with-open [wrtr (writer cartography-file)]
    (.write wrtr ((http/get cartography-url) :body)))
  ;)

  " Initialize the stations hash-map (either empty or to previous value) "
  (def stations (ref {}))
  (if (.exists (File. stations-file))
    (def stations (deserialize-read stations-file)))

  " For each station in the cartography, get its status and update it "
  " In case we don't have all the stations, update the stations map "
  (for [x (xml-seq 
            (parse (File. cartography-file)))
        :when (= :marker (:tag x))]
    (if (not= ((x :attrs) :number) nil) 
      (http/get (str station-url x)))))

