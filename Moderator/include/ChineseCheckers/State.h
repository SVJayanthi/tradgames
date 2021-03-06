//===------------------------------------------------------------*- C++ -*-===//
///
/// \file
/// \brief Defines the Chinese Checkers game state
///
/// Note: Many aspects of this State are inefficient to make the code clearer
///
//===----------------------------------------------------------------------===//
#ifndef CHINESECHECKERS_STATE_H_INCLUDED
#define CHINESECHECKERS_STATE_H_INCLUDED

#include <array>
#include <climits>
#include <cstdint>
#include <map>
#include <ostream>
#include <set>
#include <string>
#include <vector>

namespace ChineseCheckers {
struct Move {
  unsigned from;
  unsigned to;

  bool isNull() const;
};

bool operator==(const Move &lhs, const Move &rhs);
bool operator<(const Move &lhs, const Move &rhs);
std::ostream &operator<<(std::ostream &out, const Move &m);

class PerfectHash {
public:
  PerfectHash();
  uint64_t &operator[](size_t idx);
  uint64_t operator[](size_t idx) const;

  friend bool operator==(const PerfectHash &lhs, const PerfectHash &rhs);
  friend bool operator<(const PerfectHash &lhs, const PerfectHash &rhs);
  friend std::ostream &operator<<(std::ostream &out, const PerfectHash &h);

  enum {
    // Bits per uint64_t
    NumBitsInElt = sizeof(uint64_t) * CHAR_BIT,

    // How many uint64_t needed
    NumElts = (81 * 2 + NumBitsInElt - 1) / NumBitsInElt,

    // How many positions per uint64_t
    PosPerElt = NumBitsInElt / 2
  };

private:
  std::array<uint64_t, NumElts> hash;
};

bool operator==(const PerfectHash &lhs, const PerfectHash &rhs);
bool operator<(const PerfectHash &lhs, const PerfectHash &rhs);
std::ostream &operator<<(std::ostream &out, const PerfectHash &h);

class State {
public:
  // Initialize with the starting state for a 2 player game
  State();

  // dtor - default since we have nothing to clean up
  ~State() = default;

  // Don't allow copies for simplicity (the functions below are for the rule of 5)
  // copy ctor
  State(const State&) = delete;
  // move ctor
  State(const State&&) = delete;
  // copy assignment
  State &operator=(const State&) = delete;
  // move assignment
  State &operator=(const State&&) = delete;

  // Put all valid moves into the vector of moves passed in by reference
  void getMoves(std::set<Move> &moves) const;

  // Apply the move m, returning true if m is a valid move, false if not
  bool applyMove(Move m);

  // Undo the move m, returning true if m is a move that can be undone, false if not
  bool undoMove(Move m);

  // Returns true iff the move m is valid
  bool isValidMove(const Move &m) const;

  // Returns true iff the game is over
  bool gameOver() const;

  // Return the player who won, assuming the game is over
  int winner() const;

  // Reset the board to the initial state
  void reset();

  // Loads the state stored in the string, returning true if it is a valid state, false if not
  bool loadState(const std::string &newState);

  // Dump out the current state, usable with loadState
  std::string dumpState() const;

  // Translates a sequence of tokens from the move format used to the local move type
  Move translateToLocal(const std::vector<std::string> &tokens) const;

  // Dumps a list of the possible moves
  std::string listMoves() const;

  // Returns a perfect hash of the current state
  PerfectHash getHash() const;

  // Returns true iff there has been a duplicated state
  bool seenDuplicatedState() const;
private:
  // mutable due to how we find jump moves
  mutable std::array<int, 81> board;
  int currentPlayer;

  void getMovesSingleStep(std::set<Move> &moves, unsigned from) const;
  void getMovesJumps(std::set<Move> &moves, unsigned from, unsigned current) const;


  void swapTurn();

  bool player1Wins() const;
  bool player2Wins() const;

  void addStateAsSeen();
  void removeStateAsSeen();

  std::set<PerfectHash> statesSeen;
  std::map<PerfectHash, int> duplicatedStates;
};
} // namespace ChineseCheckers

#endif
