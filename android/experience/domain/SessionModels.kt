package musicorbs.experience.domain

enum class ShapeId { ORB, RIBBON, CLOUD, PIXEL_CLOUD }
enum class MaterialId { DOTS, PIXELS, SOFT }
enum class Quality { LOW, MEDIUM, HIGH }
data class Vec3(val x: Double, val y: Double, val z: Double)
data class ControlPoint(val center: Vec3, val delta: Vec3, val radius: Double)
data class ShapeSnapshot(val shapeId: ShapeId, val seed: Int, val generatorVersion: Int, val revision: Long, val controls: List<ControlPoint>)
data class SceneSettings(val shapeId: ShapeId, val materialId: MaterialId, val colorHex: String, val speed: Double, val amplitude: Double, val touchSensitivity: Double, val musicSync: Boolean, val quality: Quality, val targetFps: Int)
data class SessionSnapshot(val schemaVersion: Int, val revision: Long, val settings: SceneSettings, val shapes: List<ShapeSnapshot>)
