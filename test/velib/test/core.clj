(ns velib.test.core
  (:use [velib.core])
  (:use [clojure.test]))

(deftest serialization-test
         (let [tmpdict (read-station "42424242")]
           (write-station tmpdict)
           (if (= tmpdict (read-station "42424242"))
             (is true "serialization is ok")
             (is false "not the same data once unserialized"))))
