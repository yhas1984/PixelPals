# Scheduled sleep handoff

PetState.IDLE includes autonomous travel and airborne phases. Scheduled sleep now
consults PetBehavior.canStartScheduledSleep before replacing the actor and pausing
its controller. Corgi waits for rest/alert, Ginger for sitting/sleep, Piru for sleep,
Menta for coil/sleep and Jelly for idle. The shared runtime permits idle/sleep and
rejects melt, airborne, drag, recovery and other committed actions. Unloaded runtime
art cannot hand off. This covers Yuki and Taro through their runtime adapters.

Reduced motion also permits the stationary walk/waddle/slither modes, since the
normal controller tick is suppressed in that setting. It does not permit jump,
climb, melt or landing phases. Direct touch/care retains its existing priority.

Other legacy species still use the interface's compatibility default; their flying,
wall and silk transitions need species-specific settling choreography. This change
is not a claim that every scheduled-sleep transition is visually accepted.

The instrumentation regression checks every mode of the five legacy controllers
above and every shared runtime intent in normal and reduced motion settings.

Validation: debug APK and instrumentation APK assembled; all debug JVM tests and
debug lint passed. Installed on 192.168.1.160:43041 preserving data. Both tests in
AutonomousSleepStateTest passed on that device (0.06 seconds). These inspect real
controller states; they do not constitute a live overnight or visual sleep review.
