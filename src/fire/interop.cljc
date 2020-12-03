(ns fire.interop
  (:require
   #?(:clj [cheshire.core :as json]))
  (:refer-clojure :exclude [read])
  (:import #?(:clj [java.util Date])))

(def exception #?(:clj Exception
                  :cljs js/Error))

(def throwable #?(:clj Throwable
                  :cljs js/Error))

(def date #?(:clj Date
             :cljs js/Date))

(defn decode [data]
  #?(:clj (json/decode data true)
     :cljs (js->clj data :keywordize-keys true)))

(defn encode [data]
  #?(:clj (json/generate-string data)
     :cljs (str (clj->js data))))