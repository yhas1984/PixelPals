# Scheduled sleep integration

Home and desktop read the same persisted local schedule (23:00–07:00 by default)
and the current Android interruption filter. DND and the daily schedule have separate
switches in Settings → Sleep routine. Native time pickers edit bedtime/wake time.
Equal endpoints disable the time window. System state is read, never modified;
unavailable DND readings fall back to the chosen schedule. This does not infer
actual human sleep or import a manufacturer's private bedtime configuration.

Home holds its sleep pose and dreams, then enters its existing wake transition.
Desktop loads the shared species sleep artwork asynchronously and pauses autonomous
motion/active-minute rewards while resting. Touch wakes it for one minute; explicit
care and physics interactions take priority. This is display-only, without creating
care sessions, awarding currency or automatically applying care rewards.
DND reads are cached for one second; local clock and preferences are evaluated on
visible updates, including resume. No background alarm/wake lock is added.

Validation: debug build, JVM suite and lint passed. Four focused schedule/motion
tests passed, covering midnight boundaries, independent switches, daytime windows,
equal endpoints, staying asleep and home wake transition.
ADB currently reports no connected device, so physical visual review of all 15
sleep/wake transitions and settings remains pending. Desktop returns to its existing
behavior after scheduled rest; a dedicated desktop wake choreography is still pending.
