# Ginger branch visit

Added Explore the tree in Ginger's home (hidden for reduced motion). It starts a
bounded route: eased approach, crouch, ballistic ascent, landing, three-second perch,
descent, landing and eased return. Final position equals the original position;
normal motion then resumes from observe/rest, not its old walking speed. Existing
stalk/pounce/land/sit artwork is selected explicitly at a fixed actor size.

The habitat includes a horizontal support at the branch landing point. No rotated
walk poses impersonate gripping a trunk. This implements jumping onto a branch;
actual trunk climbing still requires new gripping/climbing frames. Asset anatomy and
size consistency across the entire app remain part of the broader unfinished audit.

JVM test covers three starting depths/positions, all phases, bounds and exact return.
Final debug build, test APK, targeted JVM tests and lint pass. Installed on
192.168.1.160:43041. GingerTreeRenderingTest passed on phone; contact sheet is in
`evidence/habitats/ginger-tree.png`. This test renders actual art along the route;
it does not establish full live UI/gesture acceptance or long-run performance.
