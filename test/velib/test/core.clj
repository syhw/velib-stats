(ns velib.test.core
  (:use [velib.core])
  (:use [clojure.test]))

(deftest serialization-test
         (let [tmpdict (read-station "test-serialize")]
           (write-station tmpdict)
           (if (= tmpdict (read-station "test-serialize"))
             (is true "serialization is ok")
             (is false "not the same data once unserialized"))))
