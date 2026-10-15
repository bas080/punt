Update Android and Gradle dependencies (#refine)

Audit and update application dependencies in `app/build.gradle.kts` and Gradle plugins to their latest compatible stable releases, ensuring test suite and CI emulator benchmarks pass without regressions.

## Current Dependencies in `app/build.gradle.kts`
- **Core Android**:
  - `androidx.core:core-ktx:1.12.0`
  - `androidx.appcompat:appcompat:1.6.1`
  - `androidx.constraintlayout:constraintlayout:2.1.4`
  - `androidx.recyclerview:recyclerview:1.3.1`
  - `androidx.swiperefreshlayout:swiperefreshlayout:1.1.0`
- **Lifecycle**:
  - `androidx.lifecycle:lifecycle-runtime-ktx:2.6.1`
- **Testing**:
  - `junit:junit:4.13.2`
  - `org.robolectric:robolectric:4.11.1`
  - `androidx.test.ext:junit:1.1.5`
  - `androidx.test:rules:1.5.0`
  - `androidx.test.espresso:espresso-core:3.5.1`

## Questions for @bas080
1. Should dependency updates be applied all at once, or incrementally by domain (e.g. Core Android runtime first, then Testing/Robolectric)?
2. Are there any specific version constraints or target Android SDK boundaries (e.g. Android 14 / API 34 compatibility) to observe during updates?
