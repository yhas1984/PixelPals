# Keep Taro's shell rigid during care

The shared care animation applied whole-actor compression during feeding/petting, stretching while brushing and generic breathing during rest to Taro. These effects deformed the shell along with the head and limbs. Shell-touch profiles now retain unit width and height through every care; source poses and positional choreography remain active. Soft species retain their deformation.

Validation: debug assembly, lint and 245 JVM tests passed. Two new tests cover every care at 201 progress samples with motion enabled/reduced, plus continued toy-following movement and Jelly elasticity. SpeciesCareRenderingTest passed 6 tests on the physical phone in 16.838 seconds after installation. Its renderer checks cover all species, but they do not establish complete anatomical or continuous-animation acceptance.

Inspected Taro's rendered care sheet before/after and also sampled Ginger, Moki and Yuki during this review. Evidence is in `evidence/taro-rigid-care/`. The change removes procedural deformation; it does not redraw inconsistent source poses, add turn frames, or complete the all-pet visual review.
