# Piano Roll: Step Input Arrow Advance

In the piano roll's Step Input tool, a key held down can be stretched with the "Move Time Selection One Step Later" shortcut (Right arrow). Bitwig then skips the automatic advance when the keys are released, so the note and the step cursor end one step short of where a plain tap would leave them.

With this mod the release always advances, after the arrow too:

| You do (grid 1/16, cursor at 0) | Bitwig | With this mod |
|---|---|---|
| Tap C | C 0-16, cursor 16 | same |
| Hold C, Right, release C | C 0-16, cursor 16 | C 0-32, cursor 32 |
| Hold C, Right, tap E, release C | C 0-16, E 16 with length 0, cursor 16 | C 0-32, E 16-32, cursor 32 |

Each extra Right adds one more step. Taps and chords without the arrow are not changed.

Built for Bitwig 6.1.7.
