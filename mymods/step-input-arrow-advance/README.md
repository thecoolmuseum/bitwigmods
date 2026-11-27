# Piano Roll: Step Input Arrow Advance

In the piano roll's Step Input tool, keys are entered as a group: from the first key down to the last key up. Bitwig has two quirks:

- A key held down can be stretched with the "Move Time Selection One Step Later" shortcut (Right arrow), but then Bitwig skips the automatic advance when the keys are released, so the notes and the cursor end one step short of where a tap leaves them.
- A key released more than 120 ms before the last one is dropped.

This mod decides by what you pressed, not by timing. Every key in the group is written, and a key released while others are still down ends where the advance from the cursor at its release would end it.

| You do (grid 1/16, cursor at 0) | Bitwig | With this mod |
|---|---|---|
| Tap C | C 0-16, cursor 16 | same |
| Hold C, Right, release C | C 0-16, cursor 16 | C 0-32, cursor 32 |
| Hold C, Right, tap E, release C | C 0-16, E 16 with length 0, cursor 16 | C 0-32, E 16-32, cursor 32 |
| Hold C and E, release C, later E | E 0-16 only | C 0-16, E 0-16, cursor 16 |
| Hold C and E, Right, release C, later E | C 0-16, E 0-16, no advance | C 0-32, E 0-32, cursor 32 |
| Hold C and E, release C, Right, release E | not written as a chord | C 0-16, E 0-32, cursor 48 |

Each extra Right adds one more step. Holding a key and pressing Left (step back and erase) is not changed: releasing the key writes nothing, as in Bitwig.

Built for Bitwig 6.1.7.
