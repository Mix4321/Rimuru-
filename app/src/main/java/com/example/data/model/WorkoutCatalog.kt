package com.example.data.model

import androidx.annotation.DrawableRes
import com.example.R

enum class WorkoutCategory(val title: String, val thaiSubtitle: String) {
    ALL("All", "ทั้งหมด"),
    STRENGTH("Strength", "สร้างกล้ามเนื้อ"),
    CARDIO("Cardio", "คาร์ดิโอ & วิ่ง"),
    HIIT("HIIT", "เผาผลาญเข้มข้น"),
    YOGA("Yoga", "โยคะ & ยืดเหยียด")
}

enum class WorkoutDifficulty(val label: String, val levelBars: Int) {
    BEGINNER("Beginner", 1),
    INTERMEDIATE("Intermediate", 2),
    ELITE("Elite", 3)
}

data class ExerciseSetSpec(
    val id: String,
    val name: String,
    val setsAndReps: String,
    val instruction: String,
    val targetMuscle: String,
    val restSeconds: Int
)

data class WorkoutProgram(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: WorkoutCategory,
    val difficulty: WorkoutDifficulty,
    val durationMinutes: Int,
    val estimatedCalories: Int,
    val coachFocus: String,
    @DrawableRes val imageRes: Int,
    val linkedSport: SportType,
    val defaultTrackingMode: TrackingMode,
    val isHiit: Boolean = false,
    val hiitWorkSec: Int = 30,
    val hiitRestSec: Int = 15,
    val hiitRounds: Int = 4,
    val defaultRestSeconds: Int = 45,
    val exercises: List<ExerciseSetSpec>
)

object WorkoutCatalog {

    val programs: List<WorkoutProgram> = listOf(
        WorkoutProgram(
            id = "hiit_emerald_ignite",
            title = "Emerald Ignite HIIT",
            subtitle = "ระเบิดการเผาผลาญไขมันด้วยจังหวะ High-Intensity Interval เต็มกำลัง",
            category = WorkoutCategory.HIIT,
            difficulty = WorkoutDifficulty.ELITE,
            durationMinutes = 24,
            estimatedCalories = 340,
            coachFocus = "Full Body • Metabolic Conditioning",
            imageRes = R.drawable.img_workout_hiit,
            linkedSport = SportType.GENERAL,
            defaultTrackingMode = TrackingMode.CAMERA_AI,
            isHiit = true,
            hiitWorkSec = 40,
            hiitRestSec = 20,
            hiitRounds = 6,
            defaultRestSeconds = 30,
            exercises = listOf(
                ExerciseSetSpec(
                    id = "hiit_1",
                    name = "Explosive Jump Squat",
                    setsAndReps = "4 Sets × 15 Reps",
                    instruction = "ย่อสะโพกต่ำขนานพื้น เกร็งแกนกลางลำตัว แล้วกระโดดระเบิดพลังขึ้นตรงๆ ลงพื้นด้วยปลายเท้าอย่างนุ่มนวล",
                    targetMuscle = "Quads & Glutes",
                    restSeconds = 30
                ),
                ExerciseSetSpec(
                    id = "hiit_2",
                    name = "Battle Rope Power Slams / High Knees",
                    setsAndReps = "4 Sets × 40 Sec",
                    instruction = "แกว่งแขนหรือยกเข่าสูงสลับซ้าย-ขวาด้วยความเร็วสูงสุด รักษาแนวหลังตรงและหายใจเป็นจังหวะ",
                    targetMuscle = "Core & Cardio",
                    restSeconds = 20
                ),
                ExerciseSetSpec(
                    id = "hiit_3",
                    name = "Burpee to Tuck Press",
                    setsAndReps = "4 Sets × 12 Reps",
                    instruction = "วางมือแนบพื้น ถีบขาไปด้านหลังในท่าแพลงก์ วิดพื้น 1 ครั้ง แล้วดีดตัวกระโดดขึ้นสุดช่วง",
                    targetMuscle = "Full Body Power",
                    restSeconds = 35
                ),
                ExerciseSetSpec(
                    id = "hiit_4",
                    name = "Mountain Climber Sprint",
                    setsAndReps = "4 Sets × 45 Sec",
                    instruction = "ตั้งท่า High Plank ไหล่ตรงกับข้อมือ ดึงเข่าเข้าหาอกสลับซ้าย-ขวาอย่างต่อเนื่องไม่ให้สะโพกส่าย",
                    targetMuscle = "Abs & Shoulders",
                    restSeconds = 30
                )
            )
        ),
        WorkoutProgram(
            id = "strength_hypertrophy_core",
            title = "Titan Strength & Power",
            subtitle = "สร้างมวลกล้ามเนื้อและความแข็งแรงแกนกลางลำตัวด้วยแรงต้านแม่นยำ",
            category = WorkoutCategory.STRENGTH,
            difficulty = WorkoutDifficulty.INTERMEDIATE,
            durationMinutes = 38,
            estimatedCalories = 290,
            coachFocus = "Upper & Lower Compound Strength",
            imageRes = R.drawable.img_workout_strength,
            linkedSport = SportType.GENERAL,
            defaultTrackingMode = TrackingMode.CAMERA_AI,
            isHiit = false,
            defaultRestSeconds = 60,
            exercises = listOf(
                ExerciseSetSpec(
                    id = "str_1",
                    name = "Tempo Dumbbell / Bodyweight Squat",
                    setsAndReps = "4 Sets × 12 Reps",
                    instruction = "ลดตัวลงช้าๆ 3 วินาที ค้างจุดต่ำสุด 1 วินาที แล้วดันส้นเท้ายืนขึ้นเต็มแรง",
                    targetMuscle = "Quads & Hamstrings",
                    restSeconds = 60
                ),
                ExerciseSetSpec(
                    id = "str_2",
                    name = "Strict Chest Push-Up",
                    setsAndReps = "4 Sets × 15 Reps",
                    instruction = "กางมือระดับหัวไหล่ เกร็งลำตัวเป็นเส้นตรง ลดหน้าอกเกือบแตะพื้นแล้วดันขึ้นสุดแขน",
                    targetMuscle = "Chest & Triceps",
                    restSeconds = 60
                ),
                ExerciseSetSpec(
                    id = "str_3",
                    name = "Alternating Reverse Lunge",
                    setsAndReps = "3 Sets × 12 Reps / ข้าง",
                    instruction = "ก้าวขาถอยหลัง ย่อเข่าให้มุมหน้า-หลัง 90 องศา รักษาสมดุลลำตัวตั้งตรง",
                    targetMuscle = "Glutes & Legs",
                    restSeconds = 45
                ),
                ExerciseSetSpec(
                    id = "str_4",
                    name = "Hollow Body Hold & Plank",
                    setsAndReps = "3 Sets × 45 Sec",
                    instruction = "กดหลังส่วนล่างแนบพื้น ยกไหล่และขาเหยียดตรงค้างไว้ หรือค้างท่า Forearm Plank",
                    targetMuscle = "Deep Core",
                    restSeconds = 45
                )
            )
        ),
        WorkoutProgram(
            id = "cardio_neon_sprint",
            title = "Neon Velocity Run",
            subtitle = "พัฒนาความฟิตหัวใจและปอดด้วยโปรแกรม Interval วิ่งสลับเร่งสปีด GPS",
            category = WorkoutCategory.CARDIO,
            difficulty = WorkoutDifficulty.INTERMEDIATE,
            durationMinutes = 30,
            estimatedCalories = 365,
            coachFocus = "VO2 Max • Pace & Endurance",
            imageRes = R.drawable.img_workout_cardio,
            linkedSport = SportType.RUNNING,
            defaultTrackingMode = TrackingMode.SENSOR_GPS,
            isHiit = true,
            hiitWorkSec = 60,
            hiitRestSec = 30,
            hiitRounds = 6,
            defaultRestSeconds = 45,
            exercises = listOf(
                ExerciseSetSpec(
                    id = "car_1",
                    name = "Aerobic Base Jog",
                    setsAndReps = "1 Set × 8 Min",
                    instruction = "วิ่งจ็อกกิ้งความเร็วสบายในโซน 2 เพื่อกระตุ้นระบบไหลเวียนเลือดและวอร์มข้อต่อ",
                    targetMuscle = "Heart & Calves",
                    restSeconds = 30
                ),
                ExerciseSetSpec(
                    id = "car_2",
                    name = "Tempo Surge Intervals",
                    setsAndReps = "6 Sets × 60 Sec Fast / 30 Sec Walk",
                    instruction = "เร่งสปีดวิ่ง 80% ความสามารถเป็นเวลา 60 วินาที สลับเดินผ่อนลมหายใจ 30 วินาที",
                    targetMuscle = "Cardiovascular Endurance",
                    restSeconds = 30
                ),
                ExerciseSetSpec(
                    id = "car_3",
                    name = "Cadence Stride Drills",
                    setsAndReps = "4 Sets × 40 Sec",
                    instruction = "ซอยเท้าถี่ขึ้น แกว่งแขน 90 องศาข้างลำตัวเพื่อเพิ่มประสิทธิภาพการลงเท้า",
                    targetMuscle = "Ankles & Running Economy",
                    restSeconds = 45
                )
            )
        ),
        WorkoutProgram(
            id = "yoga_emerald_flow",
            title = "Deep Forest Flow Yoga",
            subtitle = "ฟื้นฟูความยืดหยุ่น สมดุลจิตใจ และคลายความตึงกล้ามเนื้อหลังฝึกหนัก",
            category = WorkoutCategory.YOGA,
            difficulty = WorkoutDifficulty.BEGINNER,
            durationMinutes = 22,
            estimatedCalories = 145,
            coachFocus = "Mobility • Breathwork & Balance",
            imageRes = R.drawable.img_workout_yoga,
            linkedSport = SportType.GENERAL,
            defaultTrackingMode = TrackingMode.MANUAL,
            isHiit = false,
            defaultRestSeconds = 20,
            exercises = listOf(
                ExerciseSetSpec(
                    id = "yog_1",
                    name = "Sun Salutation Flow",
                    setsAndReps = "3 Rounds × 60 Sec",
                    instruction = "ประสานลมหายใจเข้า-ออกกับการยืดแขนขึ้น พับตัว และก้าวสู่ท่า Downward Dog อย่างลื่นไหล",
                    targetMuscle = "Spine & Hamstrings",
                    restSeconds = 15
                ),
                ExerciseSetSpec(
                    id = "yog_2",
                    name = "Warrior II & Extended Angle",
                    setsAndReps = "3 Sets × 45 Sec / ข้าง",
                    instruction = "ย่อเข่าหน้าทำมุม 90 องศา เปิดสะโพกและกางแขนขนานพื้น กดไหล่ลงห่างใบหู",
                    targetMuscle = "Hips & Quads",
                    restSeconds = 20
                ),
                ExerciseSetSpec(
                    id = "yog_3",
                    name = "Pigeon Hip Release",
                    setsAndReps = "2 Sets × 60 Sec / ข้าง",
                    instruction = "พับขาหน้าแนบพื้น เหยียดขาหลังตรง ค่อยๆ โน้มตัวลงเพื่อคลายกล้ามเนื้อสะโพกส่วนลึก",
                    targetMuscle = "Hip Flexors & Glutes",
                    restSeconds = 15
                )
            )
        ),
        WorkoutProgram(
            id = "hiit_jump_pulse",
            title = "Jump Pulse Burn",
            subtitle = "คาร์ดิโอเผาผลาญสูงด้วยเชือกกระโดดและ AI นับจังหวะแบบเรียลไทม์",
            category = WorkoutCategory.HIIT,
            difficulty = WorkoutDifficulty.INTERMEDIATE,
            durationMinutes = 18,
            estimatedCalories = 260,
            coachFocus = "Agility • Footwork & Fat Burn",
            imageRes = R.drawable.img_workout_hiit,
            linkedSport = SportType.JUMP_ROPE,
            defaultTrackingMode = TrackingMode.CAMERA_AI,
            isHiit = true,
            hiitWorkSec = 35,
            hiitRestSec = 15,
            hiitRounds = 6,
            defaultRestSeconds = 30,
            exercises = listOf(
                ExerciseSetSpec(
                    id = "jp_1",
                    name = "Basic Bounce Rhythm",
                    setsAndReps = "4 Sets × 45 Sec",
                    instruction = "กระโดดด้วยปลายเท้าสูงเพียง 2-3 ซม. ใช้ข้อมือหมุนเชือกเบาๆ ต่อเนื่อง",
                    targetMuscle = "Calves & Forearms",
                    restSeconds = 20
                ),
                ExerciseSetSpec(
                    id = "jp_2",
                    name = "Boxer Step Sprint",
                    setsAndReps = "4 Sets × 35 Sec",
                    instruction = "ถ่ายน้ำหนักสลับซ้าย-ขวาแบบนักมวย ช่วยลดแรงกระแทกและเพิ่มความเร็วรอบ",
                    targetMuscle = "Cardio & Ankles",
                    restSeconds = 25
                )
            )
        ),
        WorkoutProgram(
            id = "cardio_cycle_endurance",
            title = "Apex Cycle Endurance",
            subtitle = "ปั่นจักรยานจับระยะทางและความเร็ว GPS พร้อมช่วงเร่งรอบขา",
            category = WorkoutCategory.CARDIO,
            difficulty = WorkoutDifficulty.BEGINNER,
            durationMinutes = 40,
            estimatedCalories = 380,
            coachFocus = "Lower Body Stamina • GPS Tracked",
            imageRes = R.drawable.img_workout_cardio,
            linkedSport = SportType.CYCLING,
            defaultTrackingMode = TrackingMode.SENSOR_GPS,
            isHiit = false,
            defaultRestSeconds = 45,
            exercises = listOf(
                ExerciseSetSpec(
                    id = "cyc_1",
                    name = "Cadence Warmup Spin",
                    setsAndReps = "1 Set × 10 Min",
                    instruction = "ปั่นเกียร์เบาด้วยรอบขาคงที่ 80-90 RPM เพื่อเปิดระบบการเผาผลาญ",
                    targetMuscle = "Quads & Knees",
                    restSeconds = 30
                ),
                ExerciseSetSpec(
                    id = "cyc_2",
                    name = "Threshold Climb Simulation",
                    setsAndReps = "4 Sets × 4 Min",
                    instruction = "เพิ่มแรงต้านปานกลาง-สูง รักษาลำตัวนิ่ง กดบันไดเต็มรอบทั้งจังหวะกดและดึง",
                    targetMuscle = "Glutes & Hamstrings",
                    restSeconds = 60
                )
            )
        ),
        WorkoutProgram(
            id = "strength_upper_sculpt",
            title = "Apex Upper Sculpt",
            subtitle = "โฟกัสช่วงบน ไหล่ อก แขน และหลังด้วยท่าบอดี้เวทที่ทำได้ทุกที่",
            category = WorkoutCategory.STRENGTH,
            difficulty = WorkoutDifficulty.BEGINNER,
            durationMinutes = 25,
            estimatedCalories = 195,
            coachFocus = "Upper Body & Posture Control",
            imageRes = R.drawable.img_workout_strength,
            linkedSport = SportType.GENERAL,
            defaultTrackingMode = TrackingMode.CAMERA_AI,
            isHiit = false,
            defaultRestSeconds = 45,
            exercises = listOf(
                ExerciseSetSpec(
                    id = "up_1",
                    name = "Incline / Standard Push-Up",
                    setsAndReps = "3 Sets × 12 Reps",
                    instruction = "ควบคุมจังหวะลง 2 วินาที ดันขึ้น 1 วินาที เก็บศอกทำมุม 45 องศาข้างลำตัว",
                    targetMuscle = "Chest & Front Delts",
                    restSeconds = 45
                ),
                ExerciseSetSpec(
                    id = "up_2",
                    name = "Pike Shoulder Press",
                    setsAndReps = "3 Sets × 10 Reps",
                    instruction = "ยกสะโพกสูงเป็นรูปตัว V กลับหัว ลดศีรษะลงระหว่างมือแล้วดันกลับขึ้นเพื่อเน้นหัวไหล่",
                    targetMuscle = "Shoulders & Triceps",
                    restSeconds = 45
                ),
                ExerciseSetSpec(
                    id = "up_3",
                    name = "Superman Back Extension",
                    setsAndReps = "3 Sets × 15 Reps",
                    instruction = "นอนคว่ำ ยกอกและขาขึ้นพร้อมกัน บีบสะบักค้าง 2 วินาทีเพื่อเสริมหลังแข็งแรง",
                    targetMuscle = "Upper Back & Erectors",
                    restSeconds = 30
                )
            )
        ),
        WorkoutProgram(
            id = "yoga_mobility_reset",
            title = "Athletic Mobility Reset",
            subtitle = "คลายข้อต่อสะโพก หัวไหล่ และกระดูกสันหลัง เพิ่มพิสัยการเคลื่อนไหว",
            category = WorkoutCategory.YOGA,
            difficulty = WorkoutDifficulty.INTERMEDIATE,
            durationMinutes = 20,
            estimatedCalories = 120,
            coachFocus = "Joint Mobility • Active Recovery",
            imageRes = R.drawable.img_workout_yoga,
            linkedSport = SportType.WALKING,
            defaultTrackingMode = TrackingMode.MANUAL,
            isHiit = false,
            defaultRestSeconds = 20,
            exercises = listOf(
                ExerciseSetSpec(
                    id = "mob_1",
                    name = "World's Greatest Stretch",
                    setsAndReps = "3 Sets × 8 Reps / ข้าง",
                    instruction = "ก้าวเท้ามาข้างหน้า บิดลำตัวเปิดแขนขึ้นหาเพดานเพื่อยืดสะโพกและอกพร้อมกัน",
                    targetMuscle = "Thoracic & Hips",
                    restSeconds = 20
                ),
                ExerciseSetSpec(
                    id = "mob_2",
                    name = "Cat-Cow & Child's Pose Breath",
                    setsAndReps = "3 Sets × 60 Sec",
                    instruction = "สลับโก่งหลังและแอ่นหลังตามจังหวะหายใจเข้า-ออกอย่างช้าๆ",
                    targetMuscle = "Spine & Lower Back",
                    restSeconds = 15
                )
            )
        )
    )

    fun byId(id: String): WorkoutProgram =
        programs.firstOrNull { it.id == id } ?: programs.first()

    fun recommendedForReadiness(readinessPercent: Int): WorkoutProgram = when {
        readinessPercent >= 82 -> programs.first { it.id == "hiit_emerald_ignite" }
        readinessPercent >= 62 -> programs.first { it.id == "strength_hypertrophy_core" }
        else -> programs.first { it.id == "yoga_emerald_flow" }
    }
}
