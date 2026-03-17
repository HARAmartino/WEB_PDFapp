// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
}

// Redirect all build directories to a local folder outside of Google Drive 
// to prevent "AccessDeniedException" or file locks caused by sync software.
allprojects {
    layout.buildDirectory.set(File("C:/AndroidBuilds/WEB_PDFapp", project.name))
}
