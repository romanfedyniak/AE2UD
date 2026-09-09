# Drawing a network

The network visualiser is a held item that draws the network it is bound to: a cube at every block that has a
node, a box along every connection between them, and, close up, the number of channels each connection is
carrying. It is there so that a cable which is not carrying what it should can be found by looking at the
base rather than by taking it apart.

The idea and the shape of the tool come from AE2Stuff by bdew, and from the port of it in GregTech: New
Horizons. None of the code does; AE2Stuff is under the MMPL, which cannot be mixed into an LGPL tree.

## Using it

- **Right click a machine or a cable** to bind to it. It binds to the *face* that was clicked, so clicking
  the face of a P2P tunnel shows the sub-network behind that face rather than the network the tunnel sits in.
- **Right click the air** for the next mode: everything with numbers, everything without them, nodes alone,
  links alone. The mode is only a filter on the client - the same picture serves all four, so switching costs
  nothing on the server and sends nothing over the wire.
- **Sneak and right click the air** to unbind, which also stops the server building anything.

Binding needs `BUILD` on the target network, and that is checked again every time the picture is rebuilt: a
tool bound while you had access stops updating the moment you lose it. Without this, a visualiser would be a
way to read a stranger's base through its walls.

## What the colours mean

- A **link** is coloured between `linkIdleColor` and `linkFullColor` by how much of its capacity is in use.
  The defaults are green and red, which is the pair the commonest colour blindness cannot separate; both ends
  of the gradient are in the client config so that a player who needs a different pair can have one.
- A **link is as thick as it can carry**. The thickness comes from the channel tier, through
  `GridNode.maxChannelsOf`, so a tier an addon registers is drawn wider without a line of code here.
- A **P2P link** wears the four colours of its frequency - the same four the tunnel itself shows, from
  `Platform.p2p().toColors` - repeated along its length so the frequency can be read from anywhere near it.
  Several tunnels in one block draw several links between the same two positions, so they are also pushed
  sideways off each other; without that they would land on the same box and fight over it.
- A **node** turns red when something at that position is asking for a channel it did not get.
- A link whose far end is **not in this world** - which is what a cross-dimensional wireless connection from
  an addon looks like - is drawn as a short stub, and its node is marked. Drawing nothing, as the donor does,
  leaves a node that appears to be connected to nothing while channels flow through it, and sends the player
  looking for a fault that is not there.

## What it costs the server

Nothing at all while nobody is holding one. There is no grid cache, no field on `IGrid`, and no per-tick work
for the several hundred networks on a server that nobody is looking at.

A held visualiser says so once a tick, and `NetworkVisualiserService` does the rest at the end of the server
tick:

- The network is walked at most once every `visualiserUpdateInterval` ticks, twenty by default.
- **One walk serves everyone watching**, keyed by the network and by the block the walk starts from. Five
  players in one base bound to one machine cost one walk, not five.
- The walk **follows connections outwards from the bound block** and stops at `visualiserMaxNodes`, 16 384 by
  default. Stopping this way leaves a connected picture around what the player bound; taking the first
  16 384 of `IGrid.getNodes()` instead would scatter unconnected fragments across the screen and look like a
  broken network.
- The encoded picture is **compared with the last one and not sent if it is the same**, so an idle network
  costs one walk a second and no traffic.

Nodes are merged by block position: a cable carrying four parts is five nodes in the grid, one cube on the
screen, and one entry in the packet. Links between two nodes in the same block are dropped. Links between two
positions are *not* merged, because that is where the P2P frequencies live.

At the cap the packet is about 245 KB (`VisualiserGraphTest` measures it). Positions are eight bytes, flags
one, and a link's far end is written as a signed distance from its near end, which is one byte for the
neighbouring node the walk almost always numbers next.

## What it costs the client

The two things that make the donor's renderer expensive are both gone.

**The geometry is built once, not every frame.** A link is a thin box and a node is a cube - world-space
shapes that look the same from every angle - so the whole network goes into one `VertexBuffer` and a frame is
one `glDrawArrays`. It is rebuilt only when the network changes, when the mode changes, or when the player
has walked far enough that the render distance would cut somewhere else. Drawing it every frame through the
tessellator, which is what AE2Stuff does, means rebuilding a quarter of a million vertices sixty times a
second.

Boxes rather than `GL_LINES` also fix how it looks. `glLineWidth` is measured in **screen pixels**, so as a
network recedes its lines keep their four pixels while everything between them shrinks, and a base seen from
outside turns into a solid mat of lines. A box is measured in blocks and thins away with distance like the
cable it follows.

**The numbers are two draw calls, not one per number.** `FontRenderer` finishes a buffer and rebinds its
texture once per string; several hundred labels in a dense base is several hundred calls a frame. The
alphabet here is the ten digits, so `VisualiserLabels` lays the glyphs out of the vanilla font sheet by hand:
every background in one buffer, every digit in another. That is why there is no cap on how many numbers may
be on screen - a cap would need an answer to which sixty of five hundred labels matter, and there isn't one.

The picture is drawn **through** the world but not through itself: the depth buffer is cleared before it is
drawn rather than the depth test being switched off, so a link running behind a node is hidden by it while
everything is still visible through walls. With the test off entirely, whatever happens to be later in the
buffer paints over whatever is earlier and nothing looks solid. Vanilla clears depth again immediately
afterwards for the player's hand, so nothing downstream cares.

Vertices are floats relative to an origin near the player rather than absolute world coordinates, which is
what the rebuild-after-walking rule is really for: a float loses a tenth of a block of precision out at the
edge of a Minecraft world.

## Client config

`NetworkVisualiser` in the client config carries the render distance, the distance at which numbers appear,
the base thickness of a link and the size of a node, and every colour as `AARRGGBB`.
