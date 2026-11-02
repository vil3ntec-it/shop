package androidx.lifecycle

// Local compile-only stub (offline classpath lacks androidx.lifecycle-common). Never shipped.
abstract class Lifecycle {
  enum class Event { ON_CREATE, ON_START, ON_RESUME, ON_PAUSE, ON_STOP, ON_DESTROY, ON_ANY }
  enum class State { DESTROYED, INITIALIZED, CREATED, STARTED, RESUMED }
  abstract val currentState: State
}

interface LifecycleOwner {
  val lifecycle: Lifecycle
}
