(ns clothello.logic)

;; Reversi rules in short bullet points:
;; - the game takes place on a square board of dimensions 8x8 squares
;; - two players take part in the game, represented by light and dark pieces
;; - the standard starting position is in the group of central four squares 
;;   which are filled with 4 pieces - 2 pieces per player in a following layout
;;   (D - dark, L - light)
;; L | D
;; -----
;; D | L
;; - player with dark pieces moves first
;; - players in turns place a piece with their color on the board 
;;   in such a position that there exists at least one straight (horizontal, 
;;   vertical, or diagonal) occupied line between the new piece and another 
;;   piece of their color, with one or more contiguous pieces of opposite color 
;;   between them
;; - after placing the piece, all pieces of opposite color lying on a straight line 
;;   between the new piece and any anchoring piece of the given player's color are
;;   changed to the same color
;; - a valid move is one where at least one piece is reversed
;; - if one player cannot make a valid move, play passes back to the other player
;; - when neither player can make a valid move, the game ends
;; - the player with the most pieces on the board at the end of the game wins

(def board-size 8)

(def empty-board (vec (repeat board-size (vec (repeat board-size :empty)))))

(def offsets (for [x (range -1 2) y (range -1 2) 
                   :when (not-every? zero? [x y])] [x y]))

(defn within-boundaries? [position]
  (every? #(< -1 % board-size) position))

(defn put-at [board piece [x y]]
  (assoc-in board [x y] piece))

(defn get-at [board [x y]]
  (get-in board [x y]))

(def classic-board
  (-> empty-board
      (put-at :light [3 3])
      (put-at :light [4 4])
      (put-at :dark [3 4])
      (put-at :dark [4 3])))

(defn generate-line [start offset]
  (take-while (fn [position] (within-boundaries? position)) 
              (iterate #(->> % (map + offset) vec) start)))

(defn get-flippable-positions [side board [_ & line]]
  (let [fields (map (partial get-at board) line)
        flip-check-fn (fn [position] (and (not= side position) 
                                          (not= :empty position)))
        flip-candidates (take-while flip-check-fn fields)
        [last-after-flips & _] (drop-while flip-check-fn fields)]
    (when (and (seq flip-candidates) (= last-after-flips side))
      (take (count flip-candidates) line))))

(defn get-pieces-to-flip [side board move]
  (let [lines (map (partial generate-line move) offsets)]
    (mapcat (partial get-flippable-positions side board) lines)))

(defn valid-move? [side board move]
  (and (= (get-at board move) :empty) 
       (seq (get-pieces-to-flip side board move))))

(defn make-move [side board move]
  (when (valid-move? side board move)
    (let [pieces-to-flip (get-pieces-to-flip side board move)]
      (reduce (fn [board position] (put-at board side position)) 
              (put-at board side move) 
              pieces-to-flip))))

(defn get-valid-moves [side board]
  (let [coordinates (for [x (range board-size) y (range board-size)] [x y])]
    (filter (partial valid-move? side board) coordinates)))

(defn game-finished? [board]
  (empty? (mapcat get-valid-moves [:dark :light] (repeat board))))

(defn get-winner [board]
  (when (game-finished? board) 
    (->> board 
         (apply concat)
         (remove (partial = :empty))
         frequencies
         (apply max-key second)
         first)))

(defn human-player [side input-seq board]
  (let [[current-input input-seq] ((juxt first rest) input-seq)] 
    {:move current-input :move-fn (partial human-player side input-seq)}))

(defn create-human-player [side input-seq]
  (partial human-player side input-seq))

(defn ai-player [side history decision-fn board]
  (let [move-fn (partial ai-player side (conj history board) decision-fn)]
    {:move (decision-fn side history board) :move-fn move-fn}))

(defn create-ai-player [side decision-fn]
  (partial ai-player side [] decision-fn))

(defn make-random-decision [side history board]
  (when-let [valid-moves (seq (get-valid-moves side board))] 
    (rand-nth valid-moves)))

(defn create-random-ai-player [side]
  (create-ai-player side make-random-decision))

(defn make-greedy-decision [side history board]
  (when-let [valid-moves (seq (get-valid-moves side board))] 
    (apply max-key #(count (get-pieces-to-flip side board %)) valid-moves)))

(defn create-greedy-ai-player [side]
  (create-ai-player side make-greedy-decision))

(defn create-initial-game [initial-board dark-player-constructor light-player-constructor]
  {:board initial-board 
   :dark {:move-fn (dark-player-constructor :dark)}
   :light {:move-fn (light-player-constructor :light)}})

(defn make-move-or-fail [move-fn side board]
  (let [{:keys [move move-fn]} (move-fn board)]
    {:new-board (make-move side board move) :move-fn move-fn}))

(defn game-step [{:keys [board] :as game-stage} side]
  (when-not (game-finished? board)
    (let [flipped-side (if (= side :dark) :light :dark)
          move-fn (-> game-stage side :move-fn)
          {:keys [new-board move-fn]} (make-move-or-fail move-fn side board)
          new-stage (-> game-stage 
                        (assoc :board (or new-board board))
                        (assoc-in [side :move-fn] move-fn))]
      (cons new-stage (lazy-seq (game-step new-stage flipped-side))))))

(defn create-game [initial-board dark-player-constructor light-player-constructor]
  (let [game-stage (create-initial-game initial-board 
                                        dark-player-constructor 
                                        light-player-constructor)]
    (cons game-stage (lazy-seq (game-step game-stage :dark)))))
