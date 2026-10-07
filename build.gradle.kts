// Loads build-logic's classpath (AGP, Kotlin, Compose) once at the root, so modules apply those plugins by id.
plugins {
    id("memix.kmp.library") apply false
}
