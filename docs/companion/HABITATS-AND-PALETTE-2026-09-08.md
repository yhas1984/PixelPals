# Species habitats and shared UI palette

Added native vector habitat compositions for all pets: Corgi retains the cozy room;
Yuki snow; Piru ice; Ginger a branching forest; Moki canopy/vines; Menta bamboo;
Tela forest/web; Lumi forest/fireflies; Taro meadow/rocks; Patito reed pond; Bloop
lagoon/bubbles; Nube Michi clouds; Angel starlit clouds; Jelly mushroom garden;
Diablillo warm volcanic rocks. Common pastel colors, rounded forms and night lighting
keep them in the same illustration family. Cozy adds shelter/lantern framing; garden
shows open habitat; night uses night lighting. Movable furniture retains its grid.

Home and care backgrounds receive the selected pet. Postcards captured from the
home therefore include the habitat. Gradient and parsed colors are cached rather
than rebuilt per frame. These are initial native illustrations, not final art signoff.
Trees/branches are currently scenery: Ginger climbing them remains outstanding.

Unified shared resource surfaces, text, strokes, navigation/primary accent to the
existing HomeUi cream/sage palette. Historical purple resource names alias the new
brand accent for existing consumers; species/prop artwork keeps its own colors.
Checked key contrast ratios: primary 10.67, secondary 5.19, button 6.43, info 5.67.
This is not an exhaustive audit of hardcoded colors or every screen state.

Validation: debug and instrumentation builds and debug lint passed. Installed final
APK on 192.168.1.160:43041. PetHabitatRenderingTest passed on the phone (0.972s),
rendering 15 distinct scenes in both lighting states. Reviewed contact sheets in
`evidence/habitats/day.png` and `night.png`. Full interaction/occlusion and physical
performance review still pending; the wider original plan remains incomplete.
