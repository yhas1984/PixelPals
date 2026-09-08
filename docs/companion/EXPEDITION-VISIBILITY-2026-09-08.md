# Expedition selection continuity — 2026-09-08

PetService previously cached whether the selected pet was travelling only when
the expedition database emitted. Changing selection without a new expedition
emission could keep an unrelated pet hidden or show the travelling pet.

The service now retains the travelling pet ID and checks it against the current
selection whenever visibility is evaluated. Until the initial persisted travel
state arrives, it keeps the overlay hidden to avoid a startup flash.

Debug assembly and the JVM suite passed. ExpeditionVisibilityTest covers initial
loading, switching away from and back to a travelling pet without another
database emission, and clearing travel after return/cancellation. Physical
end-to-end selection/expedition review remains pending; these tests establish
the visibility decision, not full expedition acceptance.
