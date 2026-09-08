# Moki and Lumi rest coordination

Moki's controller now accepts a persistent rest request. It continues its perimeter
route on walls/ceiling, then exponentially brakes on the bottom surface and stays
perched there. Only that supported perch permits normal scheduled sleep. Canceling
the request resumes ordinary decisions. Reduced-motion stationary bottom crawling
can also hand off; reduced-motion wall/ceiling completion remains pending.

Lumi forwards the schedule request to its existing shouldSleep controller input.
Its adapter permits handoff only in idle/sleep, plus stationary reduced-motion
walking after configuration. Hop, pounce, social and magic states keep their
existing completion paths and interaction priority.

MokiRestTest follows the actual controller from crawling on each of four surfaces,
requires eventual bottom perch, verifies a ten-second positional hold and verifies
resumption after cancellation. This validates route logic, not rendered contact or
full physical sleep acceptance. The wider plan and other pending transitions remain
unfinished.

Validation: debug APK build, all 230 JVM tests (zero failures/errors/skips), and
debug lint passed. Installed on 192.168.1.160:43041 preserving data. No new physical
visual or instrumentation acceptance was performed for this change.
