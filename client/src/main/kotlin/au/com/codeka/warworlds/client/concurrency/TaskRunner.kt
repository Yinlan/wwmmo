package au.com.codeka.warworlds.client.concurrency

import au.com.codeka.warworlds.client.concurrency.RunnableTask.*
import java.util.*
import java.util.concurrent.ThreadPoolExecutor

/**
 * This is a class for running tasks on various threads. You can run a task on any thread defined
 * in [Threads].
 */
class TaskRunner {
  val backgroundExecutor: ThreadPoolExecutor

  private val timer: Timer

  /**
   * Run the given [Runnable] on the given [Threads].
   *
   * @return A [Task] that you can use to chain further tasks after this one has finished.
   */
  fun runTask(runnable: Runnable?, thread: Threads): Task<*, *> {
    return runTask(RunnableTask<Void?, Void>(this, runnable, thread), null)
  }

  /**
   * Run the given [RunnableTask.RunnableP] on the given [Threads].
   *
   * @return A [Task] that you can use to chain further tasks after this one has finished.
   */
  fun <P> runTask(runnable: RunnableP<P>?, thread: Threads): Task<*, *> {
    return runTask(RunnableTask<P, Void>(this, runnable, thread), null)
  }

  /**
   * Run the given [RunnableTask.RunnableR] on the given [Threads].
   *
   * @return A [Task] that you can use to chain further tasks after this one has finished.
   */
  fun <R> runTask(runnable: RunnableR<R>?, thread: Threads): Task<*, *> {
    return runTask(RunnableTask<Void?, R>(this, runnable, thread), null)
  }

  /**
   * Run the given [RunnableTask.RunnablePR] on the given [Threads].
   *
   * @return A [Task] that you can use to chain further tasks after this one has finished.
   */
  fun <P, R> runTask(runnable: RunnablePR<P, R>?, thread: Threads): Task<*, *> {
    return runTask(RunnableTask(this, runnable, thread), null)
  }

  fun <P> runTask(task: Task<P, *>, param: P?): Task<*, *> {
    task.run(param)
    return task
  }

  /**
   * Runs the given GmsCore [com.google.android.gms.tasks.Task], and returns a [Task]
   * that you can then use to chain other tasks, etc.
   *
   * @param gmsTask The GmsCore task to run.
   * @param <R> The type of result to expect from the GmsCore task.
   * @return A [Task] that you can use to chain callbacks.
  </R> */
  fun <R> runTask(gmsTask: com.google.android.gms.tasks.Task<R>): Task<Void, R> {
    return GmsTask(this, gmsTask)
  }

  /** Run a task after the given delay.  */
  fun runTask(runnable: Runnable?, thread: Threads, delayMs: Long) {
    if (delayMs == 0L) {
      runTask(runnable, thread)
    } else {
      timer.schedule(object : TimerTask() {
        override fun run() {
          runTask(runnable, thread)
        }
      }, delayMs)
    }
  }

  init {
    val backgroundThreadPool = ThreadPool(
        Threads.BACKGROUND,
        750 /* maxQueuedItems */,
        5 /* minThreads */,
        20 /* maxThreads */,
        1000 /* keepAliveMs */)
    backgroundExecutor = backgroundThreadPool.executor
    Threads.BACKGROUND.setThreadPool(backgroundThreadPool)
    timer = Timer("Timer")
  }
}