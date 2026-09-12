package com.pixelpals.app.core.motion

import com.pixelpals.app.core.domain.PetType

/** Fixed desktop source-camera calibration, shared with render review fixtures. */
object PetArtworkScale {
    /** Species proportions are independent of atlas camera calibration. */
    fun speciesSize(petType: PetType): Float = if (petType == PetType.TELA) .60f else 1f
    /** Care atlases have their own source camera; Jelly's bank otherwise shrinks at entry. */
    fun desktopCare(petType: PetType): Float = (if (petType == PetType.JELLY) 1.40f else .94f) * speciesSize(petType)

    fun forPet(petType: PetType): Float = when (petType) {
        PetType.MOKI -> 1.000f          // referencia: idle perch 0.802
        PetType.CORGI -> 0.996f         // idle REST corgi_6 0.805 -> 0.802
        PetType.JELLY -> 1.302f         // idle jelly_0 0.616 -> 0.802
        PetType.BLOOP -> 1.112f         // idle fantasma_1 0.721 -> 0.802
        PetType.NUBE_MICHI -> 1.149f    // idle gato_0 0.698 -> 0.802
        PetType.ANGEL -> 0.875f         // hover/prayer (celda sheet) 0.917 -> 0.802
        PetType.GINGER -> 0.875f        // sit/groom (celda sheet) 0.917 -> 0.802
        PetType.DIABLILLO -> 0.929f     // idle 0.863 -> 0.802
        PetType.PATITO -> 1.317f        // ciclo idle 0.36-0.60 (irregular por diseño)
        PetType.YUKI -> 0.901f          // idle alto Pixar opaco 0.891 -> 0.802
        PetType.PIRU -> 0.881f          // idle pingüino Pixar 0.910 -> 0.802
        PetType.TARO -> 0.929f          // idle tortuga (hoja nueva) 0.863 -> 0.802
        PetType.MENTA -> 0.908f         // idle serpiente Pixar opaco 0.883 -> 0.802
        PetType.TELA -> 0.880f * speciesSize(petType)
        PetType.LUMI -> 0.963f          // idle Lumi V2 0.833 -> 0.802
    }
}
