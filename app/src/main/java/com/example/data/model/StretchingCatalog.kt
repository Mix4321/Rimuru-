package com.example.data.model

enum class StretchAnimationType {
    NECK_SHOULDER_CIRCLE,
    ARM_ACROSS_CHEST,
    OVERHEAD_TRICEPS,
    WRIST_FOREARM,
    HIP_ROTATION,
    QUAD_PULL,
    HAMSTRING_REACH,
    CALF_LUNGE,
    HIGH_KNEES_MARCH,
    ANKLE_CIRCLES,
    DEEP_SQUAT_HIP_OPENER
}

data class StretchExercise(
    val id: String,
    val thaiName: String,
    val targetMuscles: String,
    val steps: List<String>,
    val safetyTip: String,
    val animationType: StretchAnimationType,
    val hasLeftRightSides: Boolean = false
)

object StretchingCatalog {

    private val neckShoulderRoll = StretchExercise(
        id = "neck_shoulder_roll",
        thaiName = "หมุนหัวไหล่และคลายต้นคอ",
        targetMuscles = "ต้นคอ • บ่า • ข้อไหล่",
        steps = listOf(
            "ยืนตัวตรง เท้าห่างเท่าความกว้างสะโพก ปล่อยแขนสบายข้างลำตัว",
            "ยกหัวไหล่ทั้งสองข้างขึ้นแล้วหมุนวนไปด้านหลังเป็นวงกลมช้าๆ 5 รอบ แล้วหมุนมาด้านหน้า 5 รอบ",
            "เอียงศีรษะซ้าย–ขวาสลับกันเบาๆ พร้อมหายใจเข้าลึกและผ่อนออกช้าๆ"
        ),
        safetyTip = "ห้ามสะบัดคอแรงหรือหมุนคอเป็นวงกลมเต็มรอบอย่างรวดเร็ว",
        animationType = StretchAnimationType.NECK_SHOULDER_CIRCLE,
        hasLeftRightSides = false
    )

    private val armAcrossChest = StretchExercise(
        id = "arm_across_chest",
        thaiName = "ยืดหัวไหล่และแขนไขว้อก",
        targetMuscles = "หัวไหล่ด้านข้าง/หลัง • สะบัก",
        steps = listOf(
            "เหยียดแขนข้างหนึ่งพาดขวางหน้าอกในระดับไหล่",
            "ใช้มืออีกข้างจับบริเวณต้นแขน (หลีกเลี่ยงการกดตรงข้อศอก) แล้วดึงเข้าหาลำตัวเบาๆ",
            "ค้างไว้แล้วสลับข้างซ้าย–ขวา รักษาหัวไหล่ให้กดต่ำไม่ยกห่อขึ้นหาหู"
        ),
        safetyTip = "รู้สึกตึงสบายที่หัวไหล่ ไม่ควรกดจนเจ็บข้อศอก",
        animationType = StretchAnimationType.ARM_ACROSS_CHEST,
        hasLeftRightSides = true
    )

    private val overheadTriceps = StretchExercise(
        id = "overhead_triceps",
        thaiName = "ยืดหลังแขนและลำตัวด้านข้าง",
        targetMuscles = "หลังแขน (Triceps) • สีข้าง • หัวไหล่",
        steps = listOf(
            "ยกแขนข้างหนึ่งขึ้นเหนือศีรษะ แล้วพับศอกให้มือแตะบริเวณสะบักด้านหลัง",
            "ใช้มืออีกข้างจับข้อศอกที่พับอยู่ แล้วออกแรงดึงไปด้านข้างเบาๆ",
            "เอนลำตัวไปด้านตรงข้ามเล็กน้อยเพื่อเพิ่มการยืดสีข้าง แล้วสลับทำอีกข้าง"
        ),
        safetyTip = "ตั้งศีรษะตรง อย่าก้มคอมาด้านหน้าขณะดึงข้อศอก",
        animationType = StretchAnimationType.OVERHEAD_TRICEPS,
        hasLeftRightSides = true
    )

    private val wristForearm = StretchExercise(
        id = "wrist_forearm",
        thaiName = "ยืดข้อมือและปลายแขน",
        targetMuscles = "ข้อมือ • ปลายแขน • เอ็นนิ้วมือ",
        steps = listOf(
            "เหยียดแขนข้างหนึ่งตรงไปด้านหน้า คว่ำหรือหงายฝ่ามือออก",
            "ใช้มืออีกข้างดึงปลายนิ้วเข้าหาลำตัวเบาๆ จนรู้สึกตึงบริเวณท้องแขน",
            "หมุนข้อมือเป็นวงกลมช้าๆ ทั้งสองทิศทาง แล้วสลับข้างซ้าย–ขวา"
        ),
        safetyTip = "ดึงอย่างนุ่มนวล เหมาะมากก่อนเล่นแบดมินตัน บาสเกตบอล หรือกระโดดเชือก",
        animationType = StretchAnimationType.WRIST_FOREARM,
        hasLeftRightSides = true
    )

    private val hipRotation = StretchExercise(
        id = "hip_rotation",
        thaiName = "หมุนสะโพกเปิดข้อต่อเชิงกราน",
        targetMuscles = "ข้อสะโพก • หลังส่วนล่าง • แกนกลางลำตัว",
        steps = listOf(
            "ยืนแยกเท้ากว้างกว่าช่วงไหล่เล็กน้อย มือทั้งสองข้างเท้าเอว",
            "หมุนสะโพกเป็นวงกลมกว้างช้าๆ ตามเข็มนาฬิกา ต่อเนื่องเป็นจังหวะสม่ำเสมอ",
            "เปลี่ยนทิศทางหมุนทวนเข็มนาฬิกาในครึ่งเวลาหลัง โดยให้เท้าติดพื้นมั่นคง"
        ),
        safetyTip = "หลังตรง ไม่แอ่นหลังล่างเกินไปขณะดันสะโพกไปด้านหน้า",
        animationType = StretchAnimationType.HIP_ROTATION,
        hasLeftRightSides = false
    )

    private val quadPull = StretchExercise(
        id = "quad_pull",
        thaiName = "ยืดกล้ามเนื้อต้นขาด้านหน้า",
        targetMuscles = "ต้นขาด้านหน้า (Quadriceps) • ข้อเข่า",
        steps = listOf(
            "ยืนตรง (จับผนังหรือเก้าอี้ได้หากต้องการทรงตัว) พับขาข้างหนึ่งไปด้านหลัง",
            "ใช้มือข้างเดียวกันจับบริเวณข้อเท้า ดึงส้นเท้าเข้าใกล้สะโพกอย่างนุ่มนวล",
            "บีบเข่าทั้งสองข้างให้อยู่ใกล้แนวเดียวกัน ค้างไว้ตามจังหวะแล้วสลับข้าง"
        ),
        safetyTip = "อย่าแอ่นหลังหรือกางเข่าออกด้านข้างมากเกินไป",
        animationType = StretchAnimationType.QUAD_PULL,
        hasLeftRightSides = true
    )

    private val hamstringReach = StretchExercise(
        id = "hamstring_reach",
        thaiName = "พับตัวแตะปลายเท้ายืดหลังขา",
        targetMuscles = "ต้นขาด้านหลัง (Hamstrings) • หลังล่าง",
        steps = listOf(
            "ยืนตรง ก้าวขาหนึ่งข้างไปด้านหน้าเล็กน้อย เหยียดเข่าตรงและกระดกปลายเท้าขึ้น",
            "งอเข่าข้างหลังเล็กน้อย ดันสะโพกไปด้านหลังพร้อมพับลำตัวลงช้าๆ โดยหลังตรง",
            "เอื้อมมือเข้าหาหน้าแข้งหรือปลายเท้า หายใจออกขณะยืด แล้วสลับขา"
        ),
        safetyTip = "งอพับจากข้อสะโพก ไม่โก่งหลัง และไม่ล็อกเข่าตึงจนเกินไป",
        animationType = StretchAnimationType.HAMSTRING_REACH,
        hasLeftRightSides = true
    )

    private val calfLunge = StretchExercise(
        id = "calf_lunge",
        thaiName = "ก้าวย่อตัวดันน่องและเอ็นร้อยหวาย",
        targetMuscles = "น่อง (Calves) • เอ็นร้อยหวาย",
        steps = listOf(
            "ยืนหันหน้าตรง ก้าวขาหนึ่งข้างไปด้านหน้าและงอเข่าหน้าเล็กน้อย",
            "เหยียดขาหลังให้ตรง โดยกดส้นเท้าหลังให้ติดพื้นสนิท ปลายเท้าชี้ตรงไปด้านหน้า",
            "ถ่ายน้ำหนักไปด้านหน้าจนรู้สึกตึงน่องขาหลัง ค้างไว้แล้วสลับข้าง"
        ),
        safetyTip = "ส้นเท้าหลังต้องติดพื้นตลอดเวลาเพื่อยืดน่องและเอ็นร้อยหวายได้เต็มที่",
        animationType = StretchAnimationType.CALF_LUNGE,
        hasLeftRightSides = true
    )

    private val highKneesMarch = StretchExercise(
        id = "high_knees_march",
        thaiName = "ย่ำเข่าสูงกระตุ้นข้อสะโพกและหัวใจ",
        targetMuscles = "ง่ามขา • สะโพก • หัวใจและปอด",
        steps = listOf(
            "ยืนตัวตรง แกว่งแขนสลับข้างเป็นจังหวะธรรมชาติ",
            "ยกเข่าขึ้นสูงประมาณระดับสะโพกสลับซ้าย–ขวาอย่างต่อเนื่อง",
            "ลงน้ำหนักที่ปลายเท้าอย่างนุ่มนวลและหายใจเข้า–ออกเป็นจังหวะ"
        ),
        safetyTip = "หลังตรง เกร็งหน้าท้องเล็กน้อย ไม่เอนลำตัวไปด้านหลังขณะยกเข่า",
        animationType = StretchAnimationType.HIGH_KNEES_MARCH,
        hasLeftRightSides = false
    )

    private val ankleCircles = StretchExercise(
        id = "ankle_circles",
        thaiName = "หมุนข้อเท้าและเขย่งปลายเท้า",
        targetMuscles = "ข้อเท้า • ฝ่าเท้า • น่องส่วนล่าง",
        steps = listOf(
            "ยกปลายเท้าข้างหนึ่งแตะพื้นเบาๆ หรือยกขึ้นเล็กน้อยแล้วหมุนข้อเท้าเป็นวงกลม",
            "หมุนตามเข็มและทวนเข็มนาฬิกา สลับข้างซ้าย–ขวา",
            "ต่อด้วยเขย่งส้นเท้าขึ้น–ลงเบาๆ 8–10 ครั้ง เพื่อปลุกความพร้อมเอ็นข้อเท้า"
        ),
        safetyTip = "ช่วยลดความเสี่ยงข้อเท้าพลิกได้มากก่อนวิ่ง กระโดดเชือก หรือกีฬาคอร์ท",
        animationType = StretchAnimationType.ANKLE_CIRCLES,
        hasLeftRightSides = true
    )

    private val deepSquatHipOpener = StretchExercise(
        id = "deep_squat_hip_opener",
        thaiName = "ย่อเข่าเปิดสะโพกเตรียมพร้อมข้อต่อ",
        targetMuscles = "สะโพก • ต้นขาด้านใน • เข่าและหลัง",
        steps = listOf(
            "ยืนกางขาประมาณช่วงไหล่ ปลายเท้าชี้เฉียงออกด้านนอกเล็กน้อย (~15 องศา)",
            "ดันสะโพกไปด้านหลังแล้วค่อยๆ ย่อตัวลงเหมือนนั่งเก้าอี้ ใช้ศอกดันเข่าออกเบาๆ",
            "ยืดตัวกลับขึ้นช้าๆ ทำซ้ำเป็นจังหวะนุ่มนวลตลอดเวลา"
        ),
        safetyTip = "เข่าควรชี้ไปแนวเดียวกับปลายเท้า ไม่บิดเข่าเข้าด้านใน",
        animationType = StretchAnimationType.DEEP_SQUAT_HIP_OPENER,
        hasLeftRightSides = false
    )

    fun exercisesFor(sport: SportType): List<StretchExercise> = when (sport) {
        SportType.RUNNING -> listOf(
            highKneesMarch,
            quadPull,
            hamstringReach,
            calfLunge,
            ankleCircles
        )
        SportType.WALKING -> listOf(
            neckShoulderRoll,
            hipRotation,
            hamstringReach,
            calfLunge,
            ankleCircles
        )
        SportType.JUMP_ROPE -> listOf(
            wristForearm,
            neckShoulderRoll,
            calfLunge,
            ankleCircles,
            highKneesMarch
        )
        SportType.FOOTBALL -> listOf(
            hipRotation,
            deepSquatHipOpener,
            quadPull,
            hamstringReach,
            ankleCircles
        )
        SportType.BASKETBALL -> listOf(
            armAcrossChest,
            wristForearm,
            deepSquatHipOpener,
            calfLunge,
            ankleCircles
        )
        SportType.BADMINTON -> listOf(
            neckShoulderRoll,
            armAcrossChest,
            wristForearm,
            deepSquatHipOpener,
            ankleCircles
        )
        SportType.CYCLING -> listOf(
            neckShoulderRoll,
            hipRotation,
            quadPull,
            hamstringReach,
            calfLunge
        )
        SportType.GENERAL -> listOf(
            neckShoulderRoll,
            overheadTriceps,
            wristForearm,
            deepSquatHipOpener,
            hamstringReach
        )
    }

    fun summaryFocusFor(sport: SportType): String = when (sport) {
        SportType.RUNNING -> "เน้นกล้ามเนื้อขา น่อง ข้อเท้า และอุ่นหัวใจก่อนวิ่ง"
        SportType.WALKING -> "คลายคอ บ่า สะโพก และยืดน่องเพื่อให้เดินสบาย"
        SportType.JUMP_ROPE -> "เน้นข้อมือ น่อง และข้อเท้าเพื่อลดแรงกระแทก"
        SportType.FOOTBALL -> "เปิดข้อสะโพก ต้นขาหน้า–หลัง และข้อเท้าก่อนลงสนาม"
        SportType.BASKETBALL -> "เน้นหัวไหล่ ข้อมือ สะโพก และน่องสำหรับการกระโดด"
        SportType.BADMINTON -> "คลายไหล่ ข้อมือ สะโพก และข้อเท้าก่อนตีลูก"
        SportType.CYCLING -> "คลายคอ บ่า สะโพก และกล้ามเนื้อขาก่อนปั่น"
        SportType.GENERAL -> "ยืดเหยียดครบทั้งตัว คอ แขน ข้อมือ สะโพก และหลังขา"
    }
}
