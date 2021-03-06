(ns clothello.core
  (:require [clothello.logic :as logic]
            [clothello.player :as player])
  (:gen-class))

(declare lazy-input take-input)

;; Symbols used for rendering the board
(def render-symbols {:dark "D" :light "L" :empty "X"})

;; Registering a human player constructor with a proper 
;; lazy input sequence
(player/register-player "human" 
                        #(player/create-human-player % (lazy-input take-input %)))

(defn quit []
  (System/exit 0))

(defn parse-input [input]
  "Helper for parsing text input from console into a proper form for human player's
input sequence."
  (let [numbers (map #(Integer/parseInt %) (re-seq #"\d+" input))]
    (when (and (= (count numbers) 2) 
               (logic/within-boundaries? numbers))
      numbers)))

(defn take-input [side]
  "Input function tailored for command line interface."
  (println (apply str "It's " (name side) "'s turn."))
  (println "Type a position (two numbers delimited with space) or type quit: ")
  (let [position (read-line)]
    (when (= position "quit")
      (quit))
    (if-let [parsed-position (parse-input position)]
      parsed-position
      (do
        (println "Invalid input. Try again.")
        (recur side)))))

(defn lazy-input [input-fn side]
  "Constructor for lazy user input."
  (let [step (fn step []
               (cons (input-fn side) (lazy-seq (step))))]
    (lazy-seq (step))))

(defn render-board [board]
  (dorun (apply map (fn [& columns] 
                      (println (apply str (map render-symbols columns)))) 
                board))
  (println ""))

(defn end-game [{:keys [board]}]
  (println "Game finished!")
  (if-let [winner (logic/get-winner board)]
    (println (apply str (name winner) " wins!"))
    (println "It's a tie!"))
  (quit))

(defn play-game [player1 player2]
  "Initiate the lazy game sequence with given player types 
and render game's state along the way."
  (doseq [{:keys [board] :as game-stage} 
          (logic/create-game logic/classic-board player1 player2)]
    (render-board board)
    (when (logic/game-finished? board) 
      (end-game game-stage))))

(defn -main [& [player1 player2 & _]]
  "Player's constructors are retrieved from players registry by names passed 
as command-line arguments."
  (let [players (player/get-players)]
   (play-game (get players player1) (get players player2))))
