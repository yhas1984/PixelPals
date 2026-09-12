# Rest exit across the reviewed catalogue

The shared rest recovery now covers 13 pets. In addition to Jelly, Menta and Bloop, this pass enables Taro, Ginger, Yuki, Angel, Nube Michi, Patito, Moki, Piru, Tela and Lumi after inspecting their source atlases and native Android rendering strips.

All preserve the existing five-second care and four-second reward. The final second uses existing waking/intermediate drawings, retires the dream and then the bed, and ends with open eyes. Tela uses frame 15 because its frame 12 has closed eyes. Lumi uses standing stretch frame 10 followed by standing frame 1, rather than finishing curled up or seated. Reduced motion retains a static pose until the awake endpoint. Body scale calibration and reward persistence are unchanged.

Corgi retains its dedicated choreography. Diablillo's attempted wing-unfolding exit was **rejected during visual inspection and removed from the implementation**: its fully spread endpoint did not match the folded wings of the ordinary desktop artwork. Its previous wing behavior and tests remain unchanged. `evidence/catalogue-wake/diablillo-rejected.png` records that rejected result; it is not an enabled animation. Diablillo still needs a coordinated wing-return sequence with an appropriate desktop handoff.

Validation of the accepted implementation:
- Debug app/test assembly, 248 JVM tests and debug lint passed.
- Ten Android tests passed in 8.114 seconds on emulator 5580: the 13-pet recovery review, species clipping/contact checks and all-pet care rendering/completion tests.
- Thirteen review strips are under `evidence/catalogue-wake/rest-recovery/`; the ten newly enabled species were visually inspected in this pass, and the three existing soft-species sequences were inspected in the preceding pass.
- Final logs: `evidence/catalogue-wake/build.txt` and `android-tests.txt`.

This closes the absent awake endpoint for those 13 shared care sequences, not full animation acceptance. Existing art-style differences, sparse intermediate poses and subsequent care-to-locomotion handoffs still require work. No physical-device installation, performance certification or release promotion is claimed.
