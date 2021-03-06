(ns app.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [clojure.browser.repl :as repl]
            [clojure.string :as string]
            [clojure.data :as data]
            [cljs.reader :as reader]
            [goog.events :as events]
            [goog.cssom :as cssom]
            [goog.dom :as gdom]
            [sablono.core :as html :refer-macros [html]]
            [garden.core :refer [css]]
            [cljs.core.async :refer [put! chan <!]])
  (:import goog.net.EventType
           goog.History
           goog.history.EventType
           [goog.net XhrIo]
           goog.events.EventType))

(enable-console-print!)

(def app-state (atom {:tags []}))

(def ^:private meths
  {:get "GET"
   :put "PUT"
   :post "POST"
   :delete "DELETE"})

(defn edn-xhr [{:keys [method url data on-complete]}]
  (let [xhr (XhrIo.)]
    (events/listen xhr goog.net.EventType.COMPLETE
      (fn [e]
        (on-complete (reader/read-string (.getResponseText xhr)))))
    (. xhr
       (send url (meths method) (when data (pr-str data))
         #js {"Content-Type" "application/edn"}))))

(defn display [show]
  (if show
    #js {}
    #js {:display "none"}))

(defn tag [tag]
  (reify
    om/IRenderState
    (render-state [this {:keys [delete]}]
      (dom/li nil 
        (dom/span nil tag)
        (dom/button #js {:onClick (fn [e] (put! delete tag))} "Delete")))))

(defn add-tag [app owner]
  (let [new-tag (-> (om/get-node owner "new-tag")
                    .-value)]
    (when new-tag
      (om/transact! app :tags #(conj % new-tag))
      (om/set-state! owner :text ""))))

(defn persist-tag [title]
  ;save tag to list of all tags
  (edn-xhr
    {:method :post
     :url (str "tags/")
     :data {:tag/title title}
     :on-complete
     (fn [res]
       (println "server response:" res))})

;unfinished
(defn persist-day [tag day degree positivity]
  ;save current day as having this tag present
  (edn-xhr
    {:method :post
     :url (str "days/")
     :data {:occurance/tag ""
            :occurance/day ""
            :occurance/degree ""
            :occurance/positivity ""}
     :on-complete
     (fn [res]
       (println "server response:" res))}))

(defn handle-change [e data edit-key owner]
  (om/transact! data edit-key (fn [_] (.. e -target -value))))

(defn tags-list [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:delete (chan)
       :text ""})

    om/IWillMount
    (will-mount [_]
      (let [delete (om/get-state owner :delete)]
        (go (loop []
              (let [tag (<! delete)]
                (om/transact! app :tags
                  (fn [xs] (vec (remove #(= tag %) xs))))
                (recur))))))

    om/IRenderState
    (render-state [this state]
      (dom/div nil
        (dom/h2 nil "Tags:")
        (apply dom/ul nil
          (om/build-all tag (:tags app)
            {:init-state state}))
        (dom/label nil "Add a tag:")
        (dom/input #js {:ref "new-tag" :type "text" :value (:text state)
                        :onChange #(handle-change % state :text owner)})
        (dom/button #js {:onClick #(add-tag app owner)} "Add Tag")))))

(om/root tags-list app-state
  {:target (.getElementById js/document "list")})
