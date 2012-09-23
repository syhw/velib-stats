(ns velib.serialization
  (:require [clojure.java.io :refer :all]))

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
  (with-open [out (java.io.FileOutputStream. filename)]
    (.write out (byte-array (serialize v)))))

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

