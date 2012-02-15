(ns protobuf.codec
  (:use protobuf.core
        [gloss.core.protocols :only [Reader Writer]]
        [gloss.core.formats :only [to-buf-seq]]
        [useful.fn :only [fix]]
        [clojure.java.io :only [input-stream]])
  (:require io.core
            [gloss.core :as gloss]))

(declare protobuf-codec)

(let [len-key :proto_length]
  (defn length-prefix [proto]
    (let [proto (protodef proto)
          min   (alength (protobuf-dump proto {len-key 0}))
          max   (alength (protobuf-dump proto {len-key Integer/MAX_VALUE}))]
      (letfn [(check [test msg]
                (when test
                  (throw (Exception. (format "In %s: %s %s"
                                             (.getFullName proto) (name len-key) msg)))))]
        (check (zero? min)
               "field is required for repeated protobufs")
        (check (= min max)
               "must be of type fixed32 or fixed64"))
      (gloss/compile-frame (gloss/finite-frame max (protobuf-codec proto))
                           #(hash-map len-key %)
                           len-key))))

(defn protobuf-codec [proto & {:keys [validator repeated]}]
  (let [proto (protodef proto)]
    (-> (reify
          Reader
          (read-bytes [this buf-seq]
            [true (protobuf-load-stream proto (input-stream buf-seq)) nil])
          Writer
          (sizeof [this] nil)
          (write-bytes [this _ val]
            (when (and validator (not (validator val)))
              (throw (IllegalStateException. "Invalid value in protobuf-codec")))
            (to-buf-seq
             (protobuf-dump
              (if (protobuf? val)
                val
                (protobuf proto val))))))
        (fix repeated
             #(gloss/repeated (gloss/finite-frame (length-prefix proto) %) :prefix :none))
        (with-meta {:schema {:type :map :fields (protofields proto)}}))))
